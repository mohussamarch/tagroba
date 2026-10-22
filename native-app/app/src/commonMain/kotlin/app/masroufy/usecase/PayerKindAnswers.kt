package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.LINK_WINDOW_DAYS
import app.masroufy.core.LinkRefusal
import app.masroufy.core.PAYER_ASK_KINDS
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.asksPayerKind
import app.masroufy.core.canHaveLinkedInflows
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.employerByPayer
import app.masroufy.core.parseIsoDate
import app.masroufy.core.payerRuleKind
import app.masroufy.core.toDayNumber
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import kotlin.math.abs

/** PLAN-0.6 §7 — اللي الشاشة بتعرضه على تحويل داخل من حساب جهة شغل: [asks] = «الفلوس دي إيه؟» · [ruleKind] = القاعدة المحفوظة للحساب. */
data class PayerAskView(val employerId: Id, val employerName: String, val asks: Boolean, val ruleKind: EconomicKind?)

/** «تراجع» عن رد «الفلوس دي إيه؟» بيرجّع القاعدة اللي كانت على الحساب ([previous] null = مكانش فيه قاعدة). */
data class PayerRuleUndo(val employerId: Id, val partyKey: String, val previous: EconomicKind?)

data class PayerKindDeps(
    val txns: TransactionRepository,
    val sources: IncomeSourceRepository,
    val answers: IncomeAnswers,
    val employers: ManageEmployers,
    val links: LinkInflows,
    val clock: Clock,
    /** يوم الراتب من الملف (لو الجهة مالهاش يوم) — null = مش معروف. */
    val payday: suspend () -> Int? = { null },
)

/**
 * PLAN-0.6 §7 (قرار المالك 2026-10-17): التحويل من حساب جهة الشغل اللي مش راتب ⇒ «الفلوس دي إيه؟» بردود جاهزة ([PAYER_ASK_KINDS] + أي نوع
 * داخل من «حاجة تانية» — `IncomeAnswers.OTHER_KINDS`). الرد **بيتحفظ قاعدة على الحساب** (`IncomeSource.payerKindRules`) وبيتطبق على الجاي
 * لوحده وهو بيتسجل (`applyKnownPayerSalary`). «تراجع» بيرجّع العملية والقاعدة اللي كانت. «استرداد مصاريف» بيربط بالمشترى الأصلي بنفس
 * «عمليات تابعة» (`LinkInflows` — المشترى بيتحسب بالصافي)، و«مستني استرداد» علامة على المشترى ما بتتحسبش من المصروف لحد ما الفلوس ترجع.
 */
class PayerKindAnswers(private val deps: PayerKindDeps) {
    suspend fun view(transactionId: Id): PayerAskView? {
        val t = load(transactionId)
        val sources = deps.sources.listAll()
        val employer = employerByPayer(t, sources) ?: return null
        val key = transferPartyOf(t)?.key ?: return null
        return PayerAskView(employer.id, employer.name, asksPayerKind(t, sources, deps.payday()), payerRuleKind(employer, key))
    }

    /** الرد: النوع للتحويل ده (نفس `IncomeAnswers.markIncome`) + القاعدة للحساب. */
    suspend fun answer(transactionId: Id, kind: EconomicKind): AnswerUndo {
        if (kind !in PAYER_ASK_KINDS && kind !in IncomeAnswers.OTHER_KINDS) throw IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_KIND))
        val t = load(transactionId)
        val employer = employerByPayer(t, deps.sources.listAll())
        val key = transferPartyOf(t)?.key
        val undo = deps.answers.markIncome(transactionId, kind)
        if (employer == null || key == null) return undo
        val previous = deps.employers.setPayerRule(employer.id, key, kind)
        return undo.copy(rule = PayerRuleUndo(employer.id, key, previous))
    }

    /** «الغي الاختيار ده»: القاعدة بتتشال (العمليات اللي اتسجلت بيها زي ما هي — تتغير بإيدك). */
    suspend fun forgetRule(transactionId: Id): PayerRuleUndo? {
        val view = view(transactionId) ?: return null
        val key = transferPartyOf(load(transactionId))?.key ?: return null
        val previous = deps.employers.setPayerRule(view.employerId, key, null)
        return PayerRuleUndo(view.employerId, key, previous)
    }

    /**
     * مشتريات ممكن يكون الاسترداد ده بتاعها: صادرة في آخر [LINK_WINDOW_DAYS] يوم قبله، بنفس العملة، تنفع يتربط بيها ([canHaveLinkedInflows])،
     * ومبلغها مش أقل من الاسترداد وفي حدود ضعفه — الأقرب في المبلغ الأول، وبعدين الأحدث. «مستني استرداد» قبل الباقي.
     */
    suspend fun refundCandidates(transactionId: Id, limit: Int = 5): List<Transaction> {
        val inflow = load(transactionId)
        if (inflow.observedDirection != Direction.IN) return emptyList()
        val day = toDayNumber(parseIsoDate(inflow.occurredAt.take(10)))
        val near = deps.txns.listByDateRange(dayNumberToIso(day - LINK_WINDOW_DAYS), inflow.occurredAt.take(10))
        return near.filter {
            canHaveLinkedInflows(it) && it.currency == inflow.currency && closeAmount(it.amountMinor - (it.linkedInflowMinor ?: 0L), inflow.amountMinor)
        }.sortedWith(
            compareByDescending<Transaction> { it.awaitingRefund == true }
                .thenBy { abs(it.amountMinor - inflow.amountMinor) }.thenByDescending { it.occurredAt }.thenBy { it.id },
        ).take(limit)
    }

    /** «استرداد مصاريف» ⇒ مربوط بالمشترى [expenseId] (العلامة «مستني استرداد» بتتشال في `LinkInflows.link`). null = اتربط. */
    suspend fun linkRefund(inflowId: Id, expenseId: Id): LinkRefusal? = deps.links.link(expenseId, listOf(inflowId))

    /** «مستني استرداد» على مشترى (أو شيلها). */
    suspend fun setAwaitingRefund(expenseId: Id, on: Boolean) {
        val t = load(expenseId)
        if (on && !canHaveLinkedInflows(t)) throw IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_KIND))
        deps.txns.update(expenseId, TransactionPatch(awaitingRefund = on, updatedAt = deps.clock.nowIso()))
    }

    private fun closeAmount(room: Halalas, refund: Halalas): Boolean = refund > 0 && room >= refund && room <= refund * 2

    private suspend fun load(id: Id): Transaction =
        deps.txns.findByIds(listOf(id)).firstOrNull() ?: throw IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_TXN))
}
