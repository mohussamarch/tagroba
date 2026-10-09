package app.masroufy.usecase

import app.masroufy.core.CashMovement
import app.masroufy.core.Currency
import app.masroufy.core.DataCoverage
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.Liquidity
import app.masroufy.core.addMoney
import app.masroufy.core.assessCoverage
import app.masroufy.core.cashMovement
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.countsAsIncome
import app.masroufy.core.countsAsPersonalExpense
import app.masroufy.core.ruleFor
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
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
)

data class LoadMoneySummaryDeps(
    val txns: TransactionRepository,
    val categories: CategoryRepository,
    val allocations: AllocationRepository,
)

class LoadMoneySummary(private val deps: LoadMoneySummaryDeps) {
    /**
     * [payday] لما المدى شهور مالية (من يوم الراتب): الراتب اللي نزل قبل أول المدى بشوية جوه، وراتب الشهر اللي بعد المدى برّه (§75-3).
     * null (سنة ميلادية مثلًا) ⇒ العمليات بتاريخها.
     */
    suspend fun load(from: IsoDate, to: IsoDate, payday: Int? = null): MoneySummary {
        val names = deps.categories.listAll().associate { it.id to it.name }
        val raw = loadRangeRows(deps.txns, from, to, payday, names).rows
        // نفس قاعدة الرئيسية: الواضح بيتحسب بنوعه التقديري (OVERRIDES §18)، والداخل المستني برّه الدخل (§75-1)
        val view = withEstimatedKinds(raw, names)
        val rows = view.transactions
        val totals = computePeriodTotals(rows, deps.allocations.listByTransactionIds(rows.map { it.id }))
        val coverage = assessCoverage(rows)
        // الداخل المستني مش «صرف مش معروف» ⇒ ما بيخلّيش الملخص كله «غير متاح»
        val spend = assessCoverage(view.withoutPendingIncoming)
        val unknown = spend.total > 0 && spend.unclassified == spend.total

        val notIncome = LinkedHashMap<EconomicKind, Halalas>()
        val notExpense = LinkedHashMap<EconomicKind, Halalas>()
        for (t in rows) {
            if (ruleFor(t.economicKind).liquidity == Liquidity.INTERNAL) continue
            val bucket = when {
                t.observedDirection == Direction.IN && !countsAsIncome(t.economicKind) -> notIncome
                t.observedDirection == Direction.OUT && !countsAsPersonalExpense(t.economicKind) -> notExpense
                else -> null
            } ?: continue
            bucket[t.economicKind] = addMoney(bucket[t.economicKind] ?: 0, t.amountMinor)
        }
        return MoneySummary(
            from, to, cashMovement(rows),
            incomeMinor = if (unknown) null else totals.incomeMinor,
            expenseMinor = if (unknown) null else totals.personalExpenseMinor,
            inflowNotIncome = notIncome, outflowNotExpense = notExpense, coverage = coverage,
            pendingIncomingCount = view.pendingIncomingCount, pendingIncomingMinor = view.pendingIncomingByCurrency,
        )
    }
}
