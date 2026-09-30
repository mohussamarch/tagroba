package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.CycleUnit
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.RoscaDraft
import app.masroufy.core.RoscaError
import app.masroufy.core.RoscaForecast
import app.masroufy.core.RoscaMember
import app.masroufy.core.RoscaStatus
import app.masroufy.core.TextKey
import app.masroufy.core.checkRosca
import app.masroufy.core.checkRoscaEntry
import app.masroufy.core.defaultRoscaPayout
import app.masroufy.core.roscaForecast
import app.masroufy.core.roscaFromDraft
import app.masroufy.core.roscaStatus
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.RoscaRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/**
 * الجمعيات (OVERRIDES §50): إنشاء وتعديل، وربط عمليات الكشف بيها (قسط أو قبض دور).
 * الربط بيغيّر نوع العملية لـ«قسط جمعية» أو «دور جمعية» — **الاتنين مش مصروف ولا دخل**.
 */
data class RoscaInput(
    val id: Id? = null,
    val name: String,
    val currency: Currency,
    val contributionMinor: Halalas,
    /** كل كام [unit]. */
    val every: Int = 1,
    val unit: CycleUnit = CycleUnit.MONTH,
    val firstDueAt: IsoDate,
    val cycleCount: Int,
    val myTurns: List<Int>,
    /** null = المحسوبة (قسطك × الأدوار ÷ أدوارك) لو بتتقسم بالظبط. */
    val payoutMinor: Halalas? = null,
    val members: List<RoscaMember> = emptyList(),
    val organizerPersonId: Id? = null,
)

data class RoscaView(val rosca: Rosca, val status: RoscaStatus)

data class ManageRoscasDeps(
    val roscas: RoscaRepository,
    val entries: RoscaEntryRepository,
    val payments: InstallmentPaymentRepository,
    val txns: TransactionRepository,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
)

class ManageRoscas(private val deps: ManageRoscasDeps) {
    private val links = DueLinks(deps.txns, deps.entries, deps.payments, deps.clock)

    private suspend fun find(id: Id): Rosca = deps.roscas.listAll().firstOrNull { it.id == id } ?: throw RoscaError(uiText(TextKey.ROSCA_NOT_FOUND))

    suspend fun list(today: IsoDate): List<RoscaView> =
        deps.roscas.listAll().map { RoscaView(it, roscaStatus(it, deps.entries.listByRosca(it.id), today)) }

    /**
     * الحفظ بيفحص الجمعية **مع المربوط بيها فعلًا**: تعديل يخلّي المدفوع أكبر من الإجمالي الجديد بيترفض،
     * بدل ما يتحفظ ويبوّظ الحساب بعدين.
     */
    suspend fun save(input: RoscaInput): Rosca {
        val payout = input.payoutMinor ?: defaultRoscaPayout(input.contributionMinor, input.cycleCount, input.myTurns.size)
            ?: throw RoscaError(uiText(TextKey.ROSCA_PAYOUT_UNEVEN))
        val existing = input.id?.let { find(it) }
        val draft = Rosca(
            id = existing?.id ?: deps.ids.next("rosca"),
            name = input.name,
            currency = input.currency,
            contributionMinor = input.contributionMinor,
            every = input.every,
            unit = input.unit,
            firstDueAt = input.firstDueAt,
            cycleCount = input.cycleCount,
            myTurns = input.myTurns.sorted(),
            payoutMinor = payout,
            members = input.members.sortedBy { it.turn },
            organizerPersonId = input.organizerPersonId,
            createdAt = existing?.createdAt ?: deps.clock.nowIso(),
        )
        val rosca = draft.copy(name = checkRosca(draft).name)
        if (existing != null) {
            if (existing.currency != rosca.currency && deps.entries.listByRosca(rosca.id).isNotEmpty()) {
                throw RoscaError(uiText(TextKey.DUE_LOCKED_AFTER_LINK))
            }
            roscaStatus(rosca, deps.entries.listByRosca(rosca.id), rosca.firstDueAt)
        }
        deps.roscas.save(rosca)
        return rosca
    }

    /** نهاية الأسئلة بالخطوات ([RoscaSetup]): المسودة الكاملة بتتحفظ جمعية. */
    suspend fun createFromDraft(draft: RoscaDraft): Rosca {
        val rosca = roscaFromDraft(draft, deps.ids.next("rosca"), deps.clock.nowIso())
        deps.roscas.save(rosca)
        return rosca
    }

    /** التحليل: هتقبض إمتى وهتدفع إيه (من الجمعية نفسها، مش من الكشف). */
    suspend fun forecast(roscaId: Id): RoscaForecast = roscaForecast(find(roscaId))

    /** ربط قسط (فلوس طالعة) أو قبض دور (فلوس داخلة). [amountMinor] null = مبلغ العملية كله. */
    suspend fun link(roscaId: Id, transactionId: Id, kind: RoscaEntryKind, amountMinor: Halalas? = null): RoscaEntry {
        val rosca = find(roscaId)
        val direction = if (kind == RoscaEntryKind.CONTRIBUTION) Direction.OUT else Direction.IN
        val (_, amount) = links.check(transactionId, direction, rosca.currency, rosca.name, amountMinor)
        checkRoscaEntry(rosca, deps.entries.listByRosca(roscaId), kind, amount)?.let { throw RoscaError(it) }
        val entry = RoscaEntry(deps.ids.next("roscaentry"), roscaId, transactionId, kind, amount)
        deps.uow.run {
            deps.entries.saveMany(listOf(entry))
            links.markKind(transactionId, if (kind == RoscaEntryKind.CONTRIBUTION) EconomicKind.ROSCA_CONTRIBUTION else EconomicKind.ROSCA_PAYOUT)
        }
        return entry
    }

    suspend fun unlink(transactionId: Id) {
        val found = deps.entries.listByTransactionIds(listOf(transactionId))
        if (found.isEmpty()) return
        deps.uow.run {
            deps.entries.deleteMany(found.map { it.id })
            links.clearKind(transactionId)
        }
    }
}
