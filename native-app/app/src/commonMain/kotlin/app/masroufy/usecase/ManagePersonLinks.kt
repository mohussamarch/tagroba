package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.PersonAllocation
import app.masroufy.core.PersonLink
import app.masroufy.core.PersonMeaning
import app.masroufy.core.ReviewState
import app.masroufy.core.Settlement
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.addMoney
import app.masroufy.core.formatMoney
import app.masroufy.core.isPurchaseShaped
import app.masroufy.core.kindForMeaning
import app.masroufy.core.obligationKindFor
import app.masroufy.core.personCategoryIds
import app.masroufy.core.remainingOfObligation
import app.masroufy.core.subtractMoney
import app.masroufy.core.sumMoney
import app.masroufy.core.uiText
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.ObligationRepository
import app.masroufy.port.PersonLinkRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.SpaceTransferLegs
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransferPartyRepository
import app.masroufy.port.UnitOfWork
import app.masroufy.port.WalletRepository

/**
 * ربط عملية بشخص (جولة r05 — شكوى المالك 2026-10-16: «علّمت كتير على كذا عملية بيني وبين شخص إنه دين… ولما دخلت على الشخص الصفحة فاضية»).
 * **أي طريق** بيقول «العملية دي مع الشخص ده» بيعدّي من هنا: «اربطها بـ › شخص» لأي نوع (حتى الاسترداد ورسايل البنك) · التصنيف اللي معناه فلوس مع
 * شخص (`isPersonCategory` ⇒ «مع مين؟») · «محتاجة تربطها بشخص» · اقتراحات صفحة الشخص · الحسابات المربوطة («سجّل تحويلاته لوحده»).
 * الربط بيكتب [PersonLink] وأثره في الدفتر في خطوة واحدة: دين جديد ([Obligation]) · سداد على ديونه المفتوحة الأقدم الأول ([Settlement]) ·
 * نصيب في الشراء ([PersonAllocation]) · أو «مع الشخص ده بس». [unlink] = «فك الربط» و«تراجع» — بيشيل اللي الربط كتبه هو بس ويرجّع النوع.
 */
data class PersonLinksDeps(
    val links: PersonLinkRepository,
    val people: PersonRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val allocations: AllocationRepository,
    val txns: TransactionRepository,
    val categories: CategoryRepository,
    val parties: TransferPartyRepository,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
    /** رجول «التحويل لنفسك» (§64) ما بتتربطش بشخص. */
    val spaceLegs: SpaceTransferLegs? = null,
    /** محافظ البلد («أضف عملية مع الشخص ده» — الكاش مسموح). */
    val wallets: WalletRepository? = null,
    /** إضافة عملية يدوي (`AddTransaction`) — null ⇒ «أضف عملية» غير متاح. */
    val add: AddTransaction? = null,
)

class PersonLinkError(message: String) : IllegalStateException(message)

/** نتيجة الربط: [unmatchedMinor] = جزء من «سداد» مالقاش دين مفتوح يتسدد منه (بيتسجل في السجل بس، من غير ما يغيّر الأرصدة). */
data class LinkOutcome(val link: PersonLink, val personName: String, val unmatchedMinor: Halalas)

/** حالة العملية في تفاصيلها: مربوطة بمين · محتاجة شخص (تصنيفها فلوس مع شخص ومفيش ربط) · عليها دين أو سداد من طريق تاني. */
data class TxnPersonStatus(val link: PersonLink?, val personName: String?, val needsPerson: Boolean, val ledgerLinked: Boolean)

/** سطر «محتاجة تربطها بشخص». */
data class NeedsPersonRow(val transaction: Transaction, val categoryName: String)

class ManagePersonLinks(private val deps: PersonLinksDeps) {
    /** صفحة الشخص: «السجل بينا» · الاقتراحات · البحث. */
    val page: LoadPersonPage by lazy { LoadPersonPage(deps, this) }

    /** الحسابات المربوطة بالشخص · «سجّل تحويلاته لوحده» · «أضف عملية مع الشخص ده». */
    val accounts: PersonAccounts by lazy { PersonAccounts(deps, this) }

    suspend fun link(transactionId: Id, personId: Id, meaning: PersonMeaning, auto: Boolean = false): LinkOutcome {
        val t = deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: throw PersonLinkError(uiText(TextKey.TXN_NOT_FOUND))
        if (deps.spaceLegs?.isLeg(transactionId) == true) throw PersonLinkError(uiText(TextKey.SPACE_TRANSFER_LEG_LOCKED))
        val people = deps.people.listAll()
        val person = people.firstOrNull { it.id == personId } ?: throw PersonLinkError(uiText(TextKey.PERSON_NOT_FOUND))
        if (meaning.direction != null && meaning.direction != t.observedDirection) throw PersonLinkError(uiText(UiKey.PLINK_ERR_DIRECTION))
        deps.links.listByTransactionIds(listOf(t.id)).firstOrNull()?.let { old ->
            throw PersonLinkError(uiText(UiKey.PLINK_ERR_ALREADY, people.firstOrNull { it.id == old.personId }?.name.orEmpty()))
        }
        val debt = obligationKindFor(meaning) != null || meaning == PersonMeaning.COLLECTED || meaning == PersonMeaning.REPAID
        if (debt && (deps.obligations.listByTransactionIds(listOf(t.id)).isNotEmpty() || deps.settlements.listByTransactionIds(listOf(t.id)).isNotEmpty())) {
            throw PersonLinkError(uiText(UiKey.PLINK_ERR_HAS_DEBT))
        }
        val purchase = isPurchaseShaped(t)
        val amount = t.amountMinor
        val now = deps.clock.nowIso()
        val obligation = obligationKindFor(meaning)?.let { Obligation(deps.ids.next("obl"), personId, t.id, it, amount, t.currency) }
        val allocation = if (purchase && (meaning == PersonMeaning.LENT || meaning == PersonMeaning.GAVE)) allocationFor(t, personId, meaning) else null
        val (settlements, unmatched) = when (meaning) {
            PersonMeaning.COLLECTED -> settleOldest(personId, t, receivable = true)
            PersonMeaning.REPAID -> settleOldest(personId, t, receivable = false)
            else -> emptyList<Settlement>() to 0L
        }
        val newKind = kindForMeaning(meaning, purchase)
        val changesKind = newKind != null &&
            (newKind != t.economicKind || !t.economicKindConfirmed || t.reviewState != ReviewState.CONFIRMED || t.suggestedKind != null)
        val link = PersonLink(
            t.id, personId, meaning, amount, now, obligation?.id, allocation?.id, settlements.map { it.id },
            kindBefore = if (changesKind) t.economicKind else null,
            kindConfirmedBefore = if (changesKind) t.economicKindConfirmed else null,
            reviewBefore = if (changesKind) t.reviewState else null,
            suggestedBefore = if (changesKind) t.suggestedKind else null,
            auto = auto,
        )
        deps.uow.run {
            deps.links.saveMany(listOf(link))
            obligation?.let { deps.obligations.saveMany(listOf(it)) }
            allocation?.let { deps.allocations.saveMany(listOf(it)) }
            if (settlements.isNotEmpty()) deps.settlements.saveMany(settlements)
            if (changesKind) {
                deps.txns.update(
                    t.id,
                    TransactionPatch(economicKind = newKind, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, clearSuggestedKind = true, updatedAt = now),
                )
            }
        }
        return LinkOutcome(link, person.name, unmatched)
    }

    /**
     * «فك الربط» و«تراجع»: بيشيل الرابط واللي هو كتبه بس (الدين · النصيب · السدادات) ويرجّع نوع العملية زي ما كان. الدين اللي اتسدد منه حاجة
     * من مكان تاني ⇒ مرفوض بسببه (ما بنمسحش حاجة إنت كتبتها). false = العملية مش مربوطة.
     */
    suspend fun unlink(transactionId: Id): Boolean {
        val link = deps.links.listByTransactionIds(listOf(transactionId)).firstOrNull() ?: return false
        link.obligationId?.let { id -> if (deps.settlements.listByObligations(listOf(id)).isNotEmpty()) throw PersonLinkError(uiText(UiKey.PLINK_ERR_HAS_PAYMENTS)) }
        val now = deps.clock.nowIso()
        deps.uow.run {
            deps.links.deleteMany(listOf(transactionId))
            link.obligationId?.let { deps.obligations.deleteMany(listOf(it)) }
            link.allocationId?.let { deps.allocations.deleteMany(listOf(it)) }
            if (link.settlementIds.isNotEmpty()) deps.settlements.deleteMany(link.settlementIds)
            link.kindBefore?.let { kind ->
                deps.txns.update(
                    transactionId,
                    TransactionPatch(
                        economicKind = kind, economicKindConfirmed = link.kindConfirmedBefore, reviewState = link.reviewBefore,
                        suggestedKind = link.suggestedBefore, updatedAt = now,
                    ),
                )
            }
        }
        return true
    }

    suspend fun statusOf(transactionId: Id): TxnPersonStatus {
        val t = deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: return TxnPersonStatus(null, null, needsPerson = false, ledgerLinked = false)
        val link = deps.links.listByTransactionIds(listOf(transactionId)).firstOrNull()
        val name = link?.let { l -> deps.people.listAll().firstOrNull { it.id == l.personId }?.name }
        val ledger = link == null && ledgerLinked(listOf(transactionId)).isNotEmpty()
        val personCategory = t.categoryId != null && t.categoryId in personCategoryIds(deps.categories.listAll())
        return TxnPersonStatus(link, name, needsPerson = personCategory && link == null && !ledger, ledgerLinked = ledger)
    }

    /** «محتاجة تربطها بشخص»: العمليات اللي تصنيفها فلوس مع شخص ومش مربوطة بحد بأي طريق. الأحدث الأول. */
    suspend fun needsPerson(): List<NeedsPersonRow> {
        val categories = deps.categories.listAll()
        val ids = personCategoryIds(categories)
        if (ids.isEmpty()) return emptyList()
        val labelled = everything().filter { it.categoryId in ids && !it.id.endsWith(OWN_LEG_SUFFIX) }
        if (labelled.isEmpty()) return emptyList()
        val linked = linkedAmong(labelled.map { it.id })
        val names = categories.associate { it.id to it.name }
        return labelled.filter { it.id !in linked }
            .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenBy { it.id })
            .map { NeedsPersonRow(it, names[it.categoryId].orEmpty()) }
    }

    internal suspend fun everything(): List<Transaction> = deps.txns.listByDateRange(EARLIEST, LATEST)

    /** العمليات المربوطة بشخص بأي طريق (رابط · دين أصله العملية · سداد · نصيب). */
    internal suspend fun linkedAmong(ids: List<Id>): Set<Id> =
        if (ids.isEmpty()) emptySet() else deps.links.listByTransactionIds(ids).map { it.transactionId }.toSet() + ledgerLinked(ids)

    private suspend fun ledgerLinked(ids: List<Id>): Set<Id> =
        (deps.obligations.listByTransactionIds(ids).mapNotNull { it.originTransactionId } +
            deps.settlements.listByTransactionIds(ids).map { it.transactionId } +
            deps.allocations.listByTransactionIds(ids).map { it.transactionId }).filter { it in ids }.toSet()

    private suspend fun allocationFor(t: Transaction, personId: Id, meaning: PersonMeaning): PersonAllocation {
        val already = sumMoney(deps.allocations.listByTransactionIds(listOf(t.id)).map { it.amountMinor })
        val total = addMoney(already, t.amountMinor)
        if (total > t.amountMinor) throw PersonLinkError(uiText(TextKey.LEDGER_ALLOCATIONS_EXCEED, formatMoney(total), formatMoney(t.amountMinor)))
        val kind = if (meaning == PersonMeaning.LENT) AllocationKind.RECEIVABLE else AllocationKind.GIFT
        return PersonAllocation(deps.ids.next("alloc"), t.id, personId, kind, t.amountMinor, t.currency)
    }

    /** «سداد»: على ديونه المفتوحة بنفس العملة، الأقدم الأول (الدين القديم من غير عملية أقدم حاجة). بيرجّع السدادات واللي فضل. */
    private suspend fun settleOldest(personId: Id, t: Transaction, receivable: Boolean): Pair<List<Settlement>, Halalas> {
        val obligations = deps.obligations.listByPerson(personId).filter { it.currency == t.currency && (it.kind == ObligationKind.RECEIVABLE) == receivable }
        if (obligations.isEmpty()) return emptyList<Settlement>() to t.amountMinor
        val rows = deps.settlements.listByObligations(obligations.map { it.id })
        val dates = deps.txns.findByIds(obligations.mapNotNull { it.originTransactionId }).associate { it.id to it.occurredAt }
        var left = t.amountMinor
        val out = mutableListOf<Settlement>()
        for (o in obligations.sortedWith(compareBy<Obligation>({ o -> o.originTransactionId?.let(dates::get).orEmpty() }, { it.id }))) {
            if (left <= 0L) break
            val remaining = remainingOfObligation(o, rows)
            if (remaining <= 0L) continue
            val part = minOf(left, remaining)
            out += Settlement(deps.ids.next("stl"), t.id, o.id, part)
            left = subtractMoney(left, part)
        }
        return out to left
    }

    private companion object {
        const val EARLIEST = "0000-01-01"
        const val LATEST = "9999-12-31"
    }
}
