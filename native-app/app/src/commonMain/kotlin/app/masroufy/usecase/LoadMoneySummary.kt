package app.masroufy.usecase

import app.masroufy.core.CashMovement
import app.masroufy.core.Currency
import app.masroufy.core.DataCoverage
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ObligationKind
import app.masroufy.core.Id
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.Liquidity
import app.masroufy.core.addMoney
import app.masroufy.core.assessCoverage
import app.masroufy.core.cashMovement
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.countsAsIncome
import app.masroufy.core.countsAsPersonalExpense
import app.masroufy.core.givenAwayShareOf
import app.masroufy.core.ruleFor
import app.masroufy.core.subtractMoney
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.uiText
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.TransactionRepository

/**
 * «حركة الفلوس» قدام «الدخل الحقيقي» لأي مدى (سنة مثلًا) — قرار المالك §58: «كان في حركة فلوس بمليون ريال ولكن الدخل الحقيقي كان 600».
 * والفرق **متفسّر بالنوع**: اللي دخل ومش دخل (سلفة · دين اتحصّل · أمانة · استرداد …) واللي خرج ومش مصروف (سلفة لحد · سداد · شراء أصل …).
 * المدى بيتحدد من الشاشة (سنة ميلادية أو 12 فترة من يوم الراتب) — الحالة ما بتفرضش.
 */
data class MoneySummary(
    val from: IsoDate,
    val to: IsoDate,
    val cash: CashMovement,
    /** `null` لو ولا عملية محددة النوع — مجهول مش صفر (القاعدة 10). */
    val incomeMinor: Halalas?,
    val expenseMinor: Halalas?,
    /** اللي دخل ومش دخل، بالنوع — `UNCLASSIFIED` = لسه ما اتحددش (ممكن يطلع دخل). */
    val inflowNotIncome: Map<EconomicKind, Halalas>,
    /** اللي خرج ومش مصروف شخصي، بالنوع. */
    val outflowNotExpense: Map<EconomicKind, Halalas>,
    val coverage: DataCoverage,
    /**
     * §75-1: الداخل المستني برّه الدخل لحد ما يتأكد — هو نفسه `inflowNotIncome[UNCLASSIFIED]` (غير المؤكد منه)، بعدده ومبلغه لكل عملة.
     * لو فيه: [incomeMinor] «لحد دلوقتي».
     */
    val pendingIncomingCount: Int = 0,
    val pendingIncomingMinor: Map<Currency, Halalas> = emptyMap(),
    /**
     * §75-3 (لما المدى شهور مالية): [cash] بتاريخ العملية (§58 — اللي دخل فعلًا)، والدخل بشهر الراتب. الفرق بينهم راتبين:
     * - [countedInNextPeriod]: نزل في آخر المدى وبيتحسب للشهر اللي بعده ⇒ في «اللي دخل» ومش في [incomeMinor] (لما المدى «لحد النهارده»
     *   ده راتب الشهر الجاي اللي نزل بدري — الشاشة تعرضه بعلامة «بيتحسب للشهر الجديد»).
     * - [countedFromEarlier]: نزل قبل أول المدى بشوية واتحسب فيه ⇒ في [incomeMinor] ومش في «اللي دخل».
     * مبالغهم جاهزة لكل عملة ([countedInNextPeriodMinor] · [countedFromEarlierMinor] — ما بتتجمعش بين عملتين) عشان الشاشة ما تحسبش:
     * «اللي دخل» = الدخل + [inflowNotIncome] − [countedFromEarlierMinor] + [countedInNextPeriodMinor].
     */
    val countedInNextPeriod: List<Transaction> = emptyList(),
    val countedInNextPeriodMinor: Map<Currency, Halalas> = emptyMap(),
    val countedFromEarlier: List<Transaction> = emptyList(),
    val countedFromEarlierMinor: Map<Currency, Halalas> = emptyMap(),
    /**
     * إجابة المالك 2026-10-15 (OVERRIDES §91-10): زيادة السداد اللي اتسجلت **أمانة ليك عنده** (`Obligation.custodyHeld`) — سطر لوحده «أمانة»
     * ([UiKey.SUMMARY_HELD])، مش جوه «سداد» ولا «سلفة لحد». «خرج ومش مصروف» كله = مجموع [outflowNotExpense] + ده (ما اتغيرش).
     */
    val outflowHeldMinor: Halalas = 0,
)

/** سطور «خرج ومش مصروف» للعرض: كل نوع باسمه، و«أمانة» سطر لوحدها لو فيه (OVERRIDES §91-10). */
fun MoneySummary.outflowNotExpenseLines(): List<Pair<String, Halalas>> =
    outflowNotExpense.map { (kind, minor) -> ruleFor(kind).label to minor } +
        listOfNotNull(outflowHeldMinor.takeIf { it > 0 }?.let { uiText(UiKey.SUMMARY_HELD) to it })

/** مجموع كل عملة لوحدها. */
private fun byCurrency(rows: List<Transaction>): Map<Currency, Halalas> {
    val out = LinkedHashMap<Currency, Halalas>()
    for (t in rows) out[t.currency] = addMoney(out[t.currency] ?: 0L, t.amountMinor)
    return out
}

data class LoadMoneySummaryDeps(
    val txns: TransactionRepository,
    val categories: CategoryRepository,
    val allocations: AllocationRepository,
    /**
     * مراجعة 0.3 بند 10: الزيادة في سداد دين عليك (§82-19) اللي اتسجلت «سلفة جديدة ليه» أو «أمانة ليك عنده» (دين ليك أصله العملية دي)
     * مش «سداد». null ⇒ السلوك القديم (العملية كلها «سداد»).
     */
    val obligations: ObligationRepository? = null,
)

class LoadMoneySummary(private val deps: LoadMoneySummaryDeps) {
    /**
     * [payday] لما المدى شهور مالية (من يوم الراتب): الراتب اللي نزل قبل أول المدى بشوية جوه، وراتب الشهر اللي بعد المدى برّه (§75-3).
     * null (سنة ميلادية مثلًا) ⇒ العمليات بتاريخها.
     */
    suspend fun load(from: IsoDate, to: IsoDate, payday: Int? = null): MoneySummary {
        val names = deps.categories.listAll().associate { it.id to it.name }
        val range = loadRangeRows(deps.txns, from, to, payday, names)
        val raw = range.rows
        // نفس قاعدة الرئيسية: الواضح بيتحسب بنوعه التقديري (OVERRIDES §18)، والداخل المستني برّه الدخل (§75-1)
        val view = withEstimatedKinds(raw, names)
        val rows = view.transactions
        val shares = deps.allocations.listByTransactionIds(rows.map { it.id })
        val totals = computePeriodTotals(rows, shares)
        val coverage = assessCoverage(rows)
        // الداخل المستني مش «صرف مش معروف» ⇒ ما بيخلّيش الملخص كله «غير متاح»
        val spend = assessCoverage(view.withoutPendingIncoming)
        val unknown = spend.total > 0 && spend.unclassified == spend.total

        val surplus = overpaySurplus(rows)
        val notIncome = LinkedHashMap<EconomicKind, Halalas>()
        val notExpense = LinkedHashMap<EconomicKind, Halalas>()
        var held: Halalas = 0
        for (t in rows) {
            if (ruleFor(t.economicKind).liquidity == Liquidity.INTERNAL) continue
            val bucket = when {
                t.observedDirection == Direction.IN && !countsAsIncome(t.economicKind) -> notIncome
                t.observedDirection == Direction.OUT && !countsAsPersonalExpense(t.economicKind) -> notExpense
                else -> null
            } ?: continue
            // §88: «الدعم» جوه سداد دين عليك اتحسب مصروف ⇒ مش هنا كمان
            val notCounted = subtractMoney(t.amountMinor, givenAwayShareOf(t, shares))
            // الزيادة اللي بقت دين ليك: سلفة جديدة ⇒ «سلفة لحد» · أمانة عنده ⇒ «أمانة» لوحدها (OVERRIDES §91-10) · والباقي بس «سداد»
            val extra = surplus[t.id]
            val lent = if (bucket === notExpense) minOf(extra?.first ?: 0, notCounted) else 0
            val kept = if (bucket === notExpense) minOf(extra?.second ?: 0, subtractMoney(notCounted, lent)) else 0
            val own = subtractMoney(subtractMoney(notCounted, lent), kept)
            if (own > 0) bucket[t.economicKind] = addMoney(bucket[t.economicKind] ?: 0, own)
            if (lent > 0) bucket[EconomicKind.LOAN_GRANTED] = addMoney(bucket[EconomicKind.LOAN_GRANTED] ?: 0, lent)
            if (kept > 0) held = addMoney(held, kept)
        }
        // §58: اللي دخل وخرج فعلًا في المدى **بتاريخه** — نقل الراتب للشهر الجديد (§75-3) بيخص الدخل بس. بالأنواع التقديرية زي الأول:
        // التحويل بين محافظك (زي السحب من الصرّاف اللي لسه ما اتأكدش) مش حركة
        val cash = cashMovement(if (range.arrived === raw) rows else withEstimatedKinds(range.arrived, names).transactions)
        return MoneySummary(
            from, to, cash,
            incomeMinor = if (unknown) null else totals.incomeMinor,
            expenseMinor = if (unknown) null else totals.personalExpenseMinor,
            inflowNotIncome = notIncome, outflowNotExpense = notExpense, coverage = coverage,
            pendingIncomingCount = view.pendingIncomingCount, pendingIncomingMinor = view.pendingIncomingByCurrency,
            countedInNextPeriod = range.countedInNextPeriod,
            countedInNextPeriodMinor = byCurrency(range.countedInNextPeriod),
            countedFromEarlier = range.countedFromEarlier,
            countedFromEarlierMinor = byCurrency(range.countedFromEarlier),
            outflowHeldMinor = held,
        )
    }

    /**
     * لكل «سداد» طالع: الدين اللي ليك وأصله العملية دي = الزيادة اللي اتسجلت (سلفة · أمانة) — `AnswerTransferAsks.answerOverpay` ·
     * `SettleOverpay`. الأولى = سلفة جديدة، التانية = أمانة عنده (`Obligation.custodyHeld`).
     */
    private suspend fun overpaySurplus(rows: List<Transaction>): Map<Id, Pair<Halalas, Halalas>> {
        val repo = deps.obligations ?: return emptyMap()
        val ids = rows.filter { it.economicKind == EconomicKind.DEBT_REPAID && it.observedDirection == Direction.OUT }.map { it.id }
        if (ids.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Id, Pair<Halalas, Halalas>>()
        for (o in repo.listByTransactionIds(ids)) {
            val origin = o.originTransactionId ?: continue
            if (o.kind != ObligationKind.RECEIVABLE || origin !in ids) continue
            val (lent, kept) = out[origin] ?: (0L to 0L)
            out[origin] = if (o.custodyHeld) lent to addMoney(kept, o.originalMinor) else addMoney(lent, o.originalMinor) to kept
        }
        return out
    }
}
