package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.IsoDate
import app.masroufy.core.PayerQuestion
import app.masroufy.core.ReviewState
import app.masroufy.core.SourceStartComparison
import app.masroufy.core.TextKey
import app.masroufy.core.TransferVerdict
import app.masroufy.core.answerPayerQuestion
import app.masroufy.core.compareAroundSourceStart
import app.masroufy.core.comparisonPeriods
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
)

class IncomeSourceSignals(private val deps: IncomeSignalsDeps) {
    private suspend fun everything() = deps.txns.listByDateRange(EARLIEST, LATEST)

    private suspend fun skipParties(): Set<String> =
        deps.parties?.listAll().orEmpty().filter { it.verdict != TransferVerdict.DISMISSED }.map { it.key }.toSet()

    /** سؤال واحد لكل طرف جديد بيحوّل حاجة شكلها مرتب وقت ما فيه شغل شغال. */
    suspend fun payerQuestions(): List<PayerQuestion> = app.masroufy.core.payerQuestions(everything(), deps.sources.listAll(), skipParties())

    /**
     * الرد: أيوه ⇒ الطرف بيتسجل على المصدر (الإيداعات الجاية منه بتتنسب لوحدها من غير سؤال)، والإيداع اللي اتسأل عنه بياخد
     * «مرتب» مؤكد لو نوعه لسه ما اتأكدش. لأ ⇒ ما يتسألش عنه تاني للمصدر ده. **ولا رقم بيتغير** (المرتب المتوقع زي ما هو).
     */
    suspend fun answerPayer(question: PayerQuestion, yes: Boolean) {
        val current = payerQuestions().firstOrNull { it.party.key == question.party.key && it.sourceId == question.sourceId }
            ?: throw IncomeSourceError(uiText(TextKey.INCOME_PAYER_NOT_ASKED))
        val source = deps.sources.listAll().first { it.id == current.sourceId }
        val updated = answerPayerQuestion(source, current.party.key, yes)
        val txn = if (yes) deps.txns.findByIds(listOf(current.transactionId)).firstOrNull()?.takeIf { !it.economicKindConfirmed } else null
        val now = deps.clock.nowIso()
        deps.uow.run {
            deps.sources.saveMany(listOf(updated))
            txn?.let {
                deps.txns.saveMany(
                    listOf(it.copy(economicKind = EconomicKind.SALARY, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = now)),
                )
            }
        }
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
        val txns = deps.txns.listByDateRange(before.first().start, after.last().end)
        val allocations = deps.allocations.listByTransactionIds(txns.map { it.id })
        return compareAroundSourceStart(source, txns, allocations, payday, today)
    }

    private companion object {
        const val EARLIEST = "0000-01-01"
        const val LATEST = "9999-12-31"
        const val LOOKBACK_DAYS = 70
    }
}
