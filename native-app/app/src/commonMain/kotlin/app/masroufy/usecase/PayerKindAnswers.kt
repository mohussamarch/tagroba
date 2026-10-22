package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.IsoDate
import app.masroufy.core.LINK_WINDOW_DAYS
import app.masroufy.core.LinkRefusal
import app.masroufy.core.PAYER_ASK_KINDS
import app.masroufy.core.PayerCustomKind
import app.masroufy.core.PayerWhy
import app.masroufy.core.RefundSum
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.asksPayerKind
import app.masroufy.core.canHaveLinkedInflows
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.employerByPayer
import app.masroufy.core.netAfterLinks
import app.masroufy.core.parseIsoDate
import app.masroufy.core.payerCustomKindsOf
import app.masroufy.core.payerRuleOf
import app.masroufy.core.payerWhyOf
import app.masroufy.core.refundSumOf
import app.masroufy.core.splitRefund
import app.masroufy.core.toDayNumber
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.core.validPayerCustomKind
import app.masroufy.port.Clock
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import kotlin.math.abs

/**
 * PLAN-0.6 §7 — اللي الشاشة بتعرضه على تحويل داخل من حساب جهة شغل: [asks] = «الفلوس دي إيه؟» · [ruleKind]/[ruleLabel] = القاعدة المحفوظة
 * للحساب · [customs] = اختيارات «حاجة تانية» المحفوظة للحساب ده بس · [why] = ليه مش شبه المرتب (السطر الأصفر).
 */
data class PayerAskView(
    val employerId: Id,
    val employerName: String,
    val asks: Boolean,
    val ruleKind: EconomicKind?,
    val ruleLabel: String? = null,
    val customs: List<PayerCustomKind> = emptyList(),
    val why: PayerWhy? = null,
    val amountMinor: Halalas = 0L,
    val currency: Currency = Currency.SAR,
    val date: IsoDate = "",
)

/** «تراجع» عن رد «الفلوس دي إيه؟» بيرجّع القاعدة اللي كانت على الحساب ([previous] null = مكانش فيه قاعدة). */
data class PayerRuleUndo(val employerId: Id, val partyKey: String, val previous: app.masroufy.core.PayerRule?)

/** مشترى في «ده استرداد لأنهي مشترى؟»: [netMinor] اللي فاضل منه بعد التابعة · [waiting] عليه «مستني استرداد». */
data class RefundCandidate(val expense: Transaction, val netMinor: Halalas, val waiting: Boolean)

/** القايمة + المختار من الأول (اللي عليها «مستني استرداد» لو مجموعها قريب من التحويل). */
data class RefundPicks(val candidates: List<RefundCandidate>, val preselected: Set<Id>)

/** نتيجة «اربط واحفظ»: [undo] null = ما اتحفظش ([refusal] السبب). */
data class RefundAnswer(val undo: AnswerUndo?, val refusal: LinkRefusal?)

/** حالة «مستني استرداد» على مشترى: اتعلّم · أو اترد (من مين · يوم إيه · قد إيه · كله ولا جزء). */
sealed interface RefundStatus {
    data object Waiting : RefundStatus

    data class Refunded(val from: String, val date: IsoDate, val refundedMinor: Halalas, val full: Boolean) : RefundStatus
}

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
 * PLAN-0.6 §7 + تعديلات المالك 2026-10-22 (r06-1b3): التحويل من حساب جهة الشغل اللي مش راتب ⇒ «الفلوس دي إيه؟» بأربع ردود: استرداد مصاريف
 * · مكافأة أو بدل · سلفة · حاجة تانية (اسم + إيموجي، وبتفضل اختيار ثابت لنفس الحساب بس). **الرد مش قاعدة لوحده** — القاعدة بس لو المالك
 * علّم «خليها قاعدة للمرات الجاية» ([makeRule])، وإلا هيتسأل تاني المرة الجاية. «تراجع» بيرجّع العملية والقاعدة والملاحظة والربط.
 * «استرداد مصاريف» بيتربط بمشترى واحد أو أكتر (`LinkInflows.linkSplit`) — المشتريات «اترد» والتحويل ما بيتحسبش دخل.
 */
class PayerKindAnswers(private val deps: PayerKindDeps) {
    suspend fun view(transactionId: Id): PayerAskView? {
        val t = load(transactionId)
        val sources = deps.sources.listAll()
        val employer = employerByPayer(t, sources) ?: return null
        val key = transferPartyOf(t)?.key ?: return null
        val payday = deps.payday()
        val asks = asksPayerKind(t, sources, payday)
        val rule = payerRuleOf(employer, key)
        return PayerAskView(
            employer.id, employer.name, asks, rule?.kind, rule?.label, payerCustomKindsOf(employer, key),
            if (asks) payerWhyOf(t, employer, payday) else null, t.amountMinor, t.currency, t.occurredAt.take(10),
        )
    }

    /** الرد: النوع للتحويل ده (نفس `IncomeAnswers.markIncome`) + القاعدة للحساب **لو [makeRule] بس**. */
    suspend fun answer(transactionId: Id, kind: EconomicKind, makeRule: Boolean = false): AnswerUndo =
        answerAs(transactionId, kind, makeRule, null)

    /** «حاجة تانية»: دخل بالاسم ده (ملاحظة على العملية لو مفيش) — والاختيار بيتحفظ لنفس الحساب بس. */
    suspend fun answerCustom(transactionId: Id, custom: PayerCustomKind, makeRule: Boolean = false): AnswerUndo {
        val clean = custom.copy(name = custom.name.trim())
        if (!validPayerCustomKind(clean)) throw IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_KIND))
        return answerAs(transactionId, EconomicKind.OTHER_INCOME, makeRule, clean)
    }

    private suspend fun answerAs(transactionId: Id, kind: EconomicKind, makeRule: Boolean, custom: PayerCustomKind?): AnswerUndo {
        if (kind !in PAYER_ASK_KINDS && kind !in IncomeAnswers.OTHER_KINDS) throw IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_KIND))
        val t = load(transactionId)
        val employer = employerByPayer(t, deps.sources.listAll())
        val key = transferPartyOf(t)?.key
        var undo = deps.answers.markIncome(transactionId, kind)
        if (custom != null) {
            if (t.note.isNullOrBlank()) {
                deps.txns.update(transactionId, TransactionPatch(note = custom.label, updatedAt = deps.clock.nowIso()))
                undo = undo.copy(restoreNote = true, noteBefore = t.note)
            }
            if (employer != null && key != null) deps.employers.addPayerCustomKind(employer.id, key, custom)
        }
        if (!makeRule || employer == null || key == null) return undo
        val previous = deps.employers.setPayerRule(employer.id, key, kind, custom?.label)
        return undo.copy(rule = PayerRuleUndo(employer.id, key, previous))
    }

    /**
     * «استرداد مصاريف» على أكتر من مشترى: التحويل بيتوزع عليهم بالترتيب ([splitRefund] — كل واحد لحد اللي فاضل فيه) وبيتربط بيهم في خطوة
     * واحدة. الربط اترفض ⇒ الرد بيترجع (ولا حاجة بتفضل متغيرة).
     */
    suspend fun answerRefund(inflowId: Id, expenseIds: List<Id>, makeRule: Boolean = false): RefundAnswer {
        val inflow = load(inflowId)
        val rooms = expenseIds.distinct().map { id -> id to ((deps.links.load(id) as? LinkedInflowsView.Expense)?.roomMinor ?: 0L) }
        val shares = splitRefund(inflow.amountMinor, rooms)
        if (shares.isEmpty()) return RefundAnswer(null, LinkRefusal.NOT_EXPENSE)
        val undo = answer(inflowId, EconomicKind.REFUND_RECEIVED, makeRule)
        val refusal = deps.links.linkSplit(inflowId, shares)
        if (refusal != null) {
            deps.answers.undo(undo)
            return RefundAnswer(null, refusal)
        }
        return RefundAnswer(undo, null)
    }

    /** «تراجع» بعد الحفظ: الربط (لو اتعمل) بيتفك، وبعدين العملية والقاعدة والملاحظة ترجع زي ما كانت. */
    suspend fun undo(transactionId: Id, undo: AnswerUndo) {
        deps.links.unlink(transactionId)
        deps.answers.undo(undo)
    }

    /** «الغي الاختيار ده»: القاعدة بتتشال (العمليات اللي اتسجلت بيها زي ما هي — تتغير بإيدك). */
    suspend fun forgetRule(transactionId: Id): PayerRuleUndo? {
        val view = view(transactionId) ?: return null
        val key = transferPartyOf(load(transactionId))?.key ?: return null
        val previous = deps.employers.setPayerRule(view.employerId, key, null)
        return PayerRuleUndo(view.employerId, key, previous)
    }

    /**
     * مشتريات ممكن يكون الاسترداد ده بتاعها: صادرة في آخر [LINK_WINDOW_DAYS] يوم قبله، بنفس العملة، تنفع يتربط بيها ([canHaveLinkedInflows])
     * وفاضل فيها حاجة — اللي عليها «مستني استرداد» دايمًا، والباقي لو مش أكتر من ضعف الاسترداد. «مستني استرداد» الأول، وبعدين الأقرب في المبلغ.
     */
    suspend fun refundCandidates(transactionId: Id, limit: Int = 8): List<Transaction> = candidatesOf(load(transactionId), limit).map { it.expense }

    /** القايمة + المختار من الأول: «مستني استرداد» لو مجموعها في حدود ±10% من التحويل، وإلا مشترى مبلغه = التحويل بالظبط. */
    suspend fun refundPicks(transactionId: Id, limit: Int = 8): RefundPicks {
        val inflow = load(transactionId)
        val list = candidatesOf(inflow, limit)
        val waiting = list.filter { it.waiting }
        val waitSum = refundSumOf(inflow.amountMinor, waiting.map { it.netMinor })
        val chosen = when {
            waiting.isNotEmpty() && abs(waitSum.diffMinor) * 100 <= inflow.amountMinor * 10 -> waiting.map { it.expense.id }
            else -> list.firstOrNull { it.netMinor == inflow.amountMinor }?.let { listOf(it.expense.id) }.orEmpty()
        }
        return RefundPicks(list, chosen.toSet())
    }

    /** سطر المجموع تحت القايمة (مطابق · فاضل · زيادة) — الحساب هنا مش في الشاشة. */
    fun refundSum(transferMinor: Halalas, picked: List<RefundCandidate>): RefundSum = refundSumOf(transferMinor, picked.map { it.netMinor })

    private suspend fun candidatesOf(inflow: Transaction, limit: Int): List<RefundCandidate> {
        if (inflow.observedDirection != Direction.IN) return emptyList()
        val day = toDayNumber(parseIsoDate(inflow.occurredAt.take(10)))
        val near = deps.txns.listByDateRange(dayNumberToIso(day - LINK_WINDOW_DAYS), inflow.occurredAt.take(10))
        return near.asSequence()
            .filter { canHaveLinkedInflows(it) && it.currency == inflow.currency }
            .map { RefundCandidate(it, netAfterLinks(it), it.awaitingRefund == true) }
            .filter { it.netMinor > 0 && (it.waiting || it.netMinor <= inflow.amountMinor * 2) }
            .sortedWith(
                compareByDescending<RefundCandidate> { it.waiting }
                    .thenBy { abs(it.netMinor - inflow.amountMinor) }.thenByDescending { it.expense.occurredAt }.thenBy { it.expense.id },
            ).take(limit).toList()
    }

    /** «استرداد مصاريف» ⇒ مربوط بالمشترى [expenseId] (العلامة «مستني استرداد» بتتشال في `LinkInflows.link`). null = اتربط. */
    suspend fun linkRefund(inflowId: Id, expenseId: Id): LinkRefusal? = deps.links.link(expenseId, listOf(inflowId))

    /** «مستني استرداد» على مشترى (أو شيلها). */
    suspend fun setAwaitingRefund(expenseId: Id, on: Boolean) {
        val t = load(expenseId)
        if (on && !canHaveLinkedInflows(t)) throw IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_KIND))
        deps.txns.update(expenseId, TransactionPatch(awaitingRefund = on, updatedAt = deps.clock.nowIso()))
    }

    /** حالة المشترى: «مستني استرداد» · «اترد — من «الجهة» · التاريخ» (استرداد مربوط بيه) · null = ولا حاجة. */
    suspend fun refundStatus(expenseId: Id): RefundStatus? {
        val e = load(expenseId)
        if (e.awaitingRefund == true) return RefundStatus.Waiting
        val offset = e.linkedInflowMinor ?: return null
        val refunds = (deps.links.load(expenseId) as? LinkedInflowsView.Expense)?.linked.orEmpty().filter { it.economicKind == EconomicKind.REFUND_RECEIVED }
        val last = refunds.maxByOrNull { it.occurredAt } ?: return null
        val from = employerByPayer(last, deps.sources.listAll())?.name ?: transferPartyOf(last)?.label ?: last.rawMerchantName.orEmpty()
        return RefundStatus.Refunded(from, last.occurredAt.take(10), offset, offset >= e.amountMinor)
    }

    /** تحويلات آخر [LINK_WINDOW_DAYS] يوم اللي مستنية «الفلوس دي إيه؟» (الأحدث الأول) — لصفحة رسايل البنك تحت كارت المرتب. */
    suspend fun pendingAsks(limit: Int = 3): List<Id> {
        val sources = deps.sources.listAll()
        if (sources.isEmpty()) return emptyList()
        val today = deps.clock.nowIso().take(10)
        val from = dayNumberToIso(toDayNumber(parseIsoDate(today)) - LINK_WINDOW_DAYS)
        val payday = deps.payday()
        return deps.txns.listByDateRange(from, today).filter { asksPayerKind(it, sources, payday) }
            .sortedByDescending { it.occurredAt }.take(limit).map { it.id }
    }

    private suspend fun load(id: Id): Transaction =
        deps.txns.findByIds(listOf(id)).firstOrNull() ?: throw IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_TXN))
}
