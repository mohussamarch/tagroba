package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.port.SpaceTransferLegs
import app.masroufy.port.TransactionPatch
import app.masroufy.core.Id
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.IsoDate
import app.masroufy.core.PayerQuestion
import app.masroufy.core.SourceStartComparison
import app.masroufy.core.TextKey
import app.masroufy.core.TransferVerdict
import app.masroufy.core.answerPayerQuestion
import app.masroufy.core.applyKnownPayerSalary
import app.masroufy.core.transferPartyOf
import app.masroufy.core.compareAroundSourceStart
import app.masroufy.core.comparisonPeriods
import app.masroufy.core.countingReadStart
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.emptyProfile
import app.masroufy.core.lateIncomeCandidates
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.core.uiText
import app.masroufy.port.AllocationRepository
import app.masroufy.port.Clock
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.ProfileRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransferPartyRepository
import app.masroufy.port.UnitOfWork

/**
 * اللي بيطلع من مصادر الدخل والعمليات (OVERRIDES §48 · §64): سؤال «ده مرتب من …؟» على أول إيداع من طرف جديد ·
 * المرتب المتأخر لمحرك التنبيهات (§61) · مقارنة قبل وبعد المصدر الجديد. **ما بنستنتجش زيادة ولا خصم من المبلغ أبدًا.**
 */
data class IncomeSignalsDeps(
    val sources: IncomeSourceRepository,
    val txns: TransactionRepository,
    val allocations: AllocationRepository,
    val profiles: ProfileRepository,
    val uow: UnitOfWork,
    val clock: Clock,
    /** قرارات «زون التحويلات» (§60) — الطرف اللي اتقال عليه «حسابي التاني» أو «شخص» ما يتسألش عنه. null = مش متوصل. */
    val parties: TransferPartyRepository? = null,
    /** رجول التحويل لنفسك (§64): نوعها ما يتغيرش غير بالفك. null = مش متوصل. */
    val spaceLegs: SpaceTransferLegs? = null,
)

class IncomeSourceSignals(private val deps: IncomeSignalsDeps) {
    private suspend fun everything() = deps.txns.listByDateRange(EARLIEST, LATEST)

    private suspend fun skipParties(): Set<String> =
        deps.parties?.listAll().orEmpty().filter { it.verdict != TransferVerdict.DISMISSED }.map { it.key }.toSet()

    /** سؤال واحد لكل طرف جديد بيحوّل حاجة شكلها مرتب وقت ما فيه شغل شغال. */
    suspend fun payerQuestions(): List<PayerQuestion> = app.masroufy.core.payerQuestions(everything(), deps.sources.listAll(), skipParties())

    /**
     * الرد: أيوه ⇒ الطرف بيتسجل على المصدر، و**كل** إيداع منه نوعه لسه ما اتأكدش (اللي اتسأل عنه واللي بعده) بياخد «مرتب» مؤكد —
     * والجاي بعد كده بياخده لوحده وهو بيتحفظ (رد المالك §64). اللي المستخدم غيّره بإيده ما بيتلمسش. لأ ⇒ ما يتسألش عنه تاني
     * للمصدر ده. **ولا رقم بيتغير** (المرتب المتوقع زي ما هو). بيرجّع عدد العمليات اللي بقت «مرتب».
     */
    suspend fun answerPayer(question: PayerQuestion, yes: Boolean): Int {
        val current = payerQuestions().firstOrNull { it.party.key == question.party.key && it.sourceId == question.sourceId }
            ?: throw IncomeSourceError(uiText(TextKey.INCOME_PAYER_NOT_ASKED))
        val all = deps.sources.listAll()
        val updated = answerPayerQuestion(all.first { it.id == current.sourceId }, current.party.key, yes)
        val now = deps.clock.nowIso()
        val changed = if (!yes) emptyList() else {
            val after = all.map { if (it.id == updated.id) updated else it }
            everything().filter { transferPartyOf(it)?.key == current.party.key }
                .mapNotNull { t -> applyKnownPayerSalary(t, after, now).takeIf { it != t } }
        }
        deps.uow.run {
            deps.sources.saveMany(listOf(updated))
            if (changed.isNotEmpty()) deps.txns.saveMany(changed)
        }
        return changed.size
    }

    /**
     * الإيداعات اللي اتسجلت من طريق تاني (رسالة بنك · إضافة) من طرف متأكد إنه بيحوّل المرتب ⇒ «مرتب» مؤكد، لو نوعها لسه
     * ما اتأكدش. الاستيراد بيعمل ده لوحده وهو بيحفظ. بيرجّع عدد العمليات اللي اتغيرت.
     */
    suspend fun applyKnownPayers(): Int {
        val sources = deps.sources.listAll()
        if (sources.none { it.payerKeys.isNotEmpty() }) return 0
        val now = deps.clock.nowIso()
        val changed = everything().mapNotNull { t -> applyKnownPayerSalary(t, sources, now).takeIf { it != t } }
        if (changed.isNotEmpty()) deps.uow.run { deps.txns.saveMany(changed) }
        return changed.size
    }

    /**
     * «مكافأة نهاية خدمة» على إيداع (رد المالك §64-٧ — بدل السؤال عند قفل الشغل): الإيداع **داخل** ومن طرف **المستخدم أكد** إنه
     * بيحوّل مرتب مصدر (حتى لو المصدر اتقفل — المكافأة بتيجي بعد ما تسيب). النوع بيبقى [EconomicKind.END_OF_SERVICE] **مؤكد**،
     * فـ«مرتب لوحده» (`applyKnownPayerSalary`) عمره ما يكتب فوقه. [yes] = لأ ⇒ الإيداع المتعلّم بيرجع «مرتب» مؤكد (قاعدة الشركة
     * المؤكدة — رد المالك §64-١). التصنيف ما بيتغيرش.
     */
    suspend fun markEndOfService(transactionId: Id, yes: Boolean = true) {
        val t = deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: throw IncomeSourceError(uiText(TextKey.INCOME_EOS_TXN_NOT_FOUND))
        if (t.observedDirection != Direction.IN) throw IncomeSourceError(uiText(TextKey.INCOME_EOS_NEEDS_IN))
        val key = transferPartyOf(t)?.key
        if (key == null || deps.sources.listAll().none { key in it.payerKeys }) throw IncomeSourceError(uiText(TextKey.INCOME_EOS_NOT_FROM_COMPANY))
        if (!yes && t.economicKind != EconomicKind.END_OF_SERVICE) throw IncomeSourceError(uiText(TextKey.INCOME_EOS_NOT_MARKED))
        val kind = if (yes) EconomicKind.END_OF_SERVICE else EconomicKind.SALARY
        if (kind != t.economicKind && deps.spaceLegs?.isLeg(transactionId) == true) throw IncomeSourceError(uiText(TextKey.SPACE_TRANSFER_LEG_LOCKED))
        deps.txns.update(
            transactionId,
            TransactionPatch(economicKind = kind, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = deps.clock.nowIso()),
        )
    }

    /** المرتب المتأخر النهارده (مهلة 3 أيام) — بيختفي لوحده لما الإيداع يوصل. */
    suspend fun alertCandidates(today: IsoDate): List<AlertCandidate> {
        val sources = deps.sources.listAll()
        if (sources.none { it.expectedDayOfMonth != null && it.payerKeys.isNotEmpty() }) return emptyList()
        // آخر يوم متوقع عدّت مهلته بيبقى جوه آخر شهرين، والإيداع البدري قبله بـ15 يوم
        val txns = deps.txns.listByDateRange(dayNumberToIso(toDayNumber(parseIsoDate(today)) - LOOKBACK_DAYS), today)
        return lateIncomeCandidates(sources, txns, today)
    }

    /** «دخلك ومصروفك قبل وبعد المصدر ده» — null لو لسه ما كمّلش 3 شهور كاملة أو فيه شهر من غير بيانات. */
    suspend fun compareAroundStart(sourceId: Id, today: IsoDate): SourceStartComparison? {
        val source = deps.sources.listAll().firstOrNull { it.id == sourceId } ?: throw IncomeSourceError(uiText(TextKey.INCOME_SOURCE_NOT_FOUND))
        val payday = (deps.profiles.load() ?: emptyProfile()).payday
        val (before, after) = comparisonPeriods(source.startedAt, payday)
        // §75-3: راتب أول شهر ممكن ينزل قبله بشوية ⇒ القراية من `countingReadStart` (والمقارنة بتحط كل عملية في شهر حسابها)
        val txns = deps.txns.listByDateRange(countingReadStart(before.first().start), after.last().end)
        val allocations = deps.allocations.listByTransactionIds(txns.map { it.id })
        return compareAroundSourceStart(source, txns, allocations, payday, today)
    }

    private companion object {
        const val EARLIEST = "0000-01-01"
        const val LATEST = "9999-12-31"
        const val LOOKBACK_DAYS = 70
    }
}
