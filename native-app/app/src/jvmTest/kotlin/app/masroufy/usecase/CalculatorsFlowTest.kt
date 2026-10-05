package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.CalcReason
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EgyptContributionWageLimits
import app.masroufy.core.EgyptPensionInput
import app.masroufy.core.EosEnd
import app.masroufy.core.EosPart
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.Liquidity
import app.masroufy.core.PensionStatus
import app.masroufy.core.ReviewState
import app.masroufy.core.SaudiPensionInput
import app.masroufy.core.SavingVerdict
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.Transaction
import app.masroufy.core.ruleFor
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryGoalContributionRepository
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemorySavingsGoalRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * حاسبتي الادخار والتقاعد (§69) من البيانات للنتيجة على مستودعات الذاكرة — أرقام وأسامي مخترعة.
 * النهارده 2026-10-05 ويوم الراتب 28 ⇒ الشهر الحالي من 28 سبتمبر، وآخر 3 شهور مكتملة: 28 يونيو · 28 يوليو · 28 أغسطس.
 */
class CalculatorsFlowTest {
    private val today = "2026-10-05"
    private var n = 0
    private fun txn(date: String, kind: EconomicKind, amount: Long, currency: Currency = Currency.SAR) = Transaction(
        id = "t-${n++}", occurredAt = date, datePrecision = "day", sourceOrder = n, economicKind = kind, economicKindConfirmed = true,
        observedDirection = if (ruleFor(kind).liquidity == Liquidity.OUT) Direction.OUT else Direction.IN, amountMinor = amount, currency = currency,
        categoryConfirmed = false, excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    /** شهر يونيو: 10,000 − 4,000 = 6,000 · يوليو: 10,000 − 5,000 (والتحويل بين محافظك برا) = 5,000 · أغسطس: 10,000 − 6,000 (والجنيه برا) = 4,000. */
    private fun history(extra: List<Transaction> = emptyList(), skipJune: Boolean = false) = buildList {
        if (!skipJune) { add(txn("2026-07-01", EconomicKind.SALARY, 1_000_000)); add(txn("2026-07-02", EconomicKind.PURCHASE, 400_000)) }
        add(txn("2026-08-01", EconomicKind.SALARY, 1_000_000)); add(txn("2026-08-02", EconomicKind.PURCHASE, 500_000))
        add(txn("2026-08-03", EconomicKind.INTERNAL_TRANSFER, 300_000))
        add(txn("2026-09-01", EconomicKind.SALARY, 1_000_000)); add(txn("2026-09-02", EconomicKind.PURCHASE, 600_000))
        add(txn("2026-09-03", EconomicKind.PURCHASE, 99_900, Currency.EGP))
        add(txn("2026-10-01", EconomicKind.PURCHASE, 900_000)) // الشهر الحالي — لسه ما خلصش ⇒ برا
        addAll(extra)
    }

    private val goals = MemorySavingsGoalRepository()
    private val contributions = MemoryGoalContributionRepository()
    private val manage = ManageSavingsGoals(ManageSavingsGoalsDeps(goals, contributions, SequentialIdGenerator(), FixedClock("2026-10-05T08:00:00.000Z")))

    private fun calculator(rows: List<Transaction>) = SavingsCalculator(
        SavingsCalculatorDeps(LoadActualSaving(LoadActualSavingDeps(MemoryTransactionRepository(rows), MemoryAllocationRepository(), MemoryCategoryRepository())), manage),
    )

    @Test fun actualSavingIsTheAverageOfTheLastThreeCompleteMonths() = runBlocking<Unit> {
        val out = calculator(history()).perMonth(1_200_000, 150_000, "2027-06-30", Currency.SAR, today)
        assertEquals(listOf("2026-06-28", "2026-07-28", "2026-08-28"), out.actual!!.months.map { it.period.start })
        assertEquals(listOf(600_000L, 500_000L, 400_000L), out.actual!!.months.map { it.savedMinor })
        assertEquals(500_000L, out.actual!!.averageMinor, "(6,000 + 5,000 + 4,000) ÷ 3")
        assertEquals(116_667L, out.plan.perMonthMinor)
        assertEquals(SavingVerdict.ENOUGH, out.comparison.verdict)
        assertEquals(383_333L, out.comparison.gapMinor)
        val reach = calculator(history()).reach(600_000, 12, 0, Currency.SAR, today)
        assertEquals(7_200_000L, reach.reach.reachedMinor)
        assertEquals(SavingVerdict.SHORT, reach.comparison.verdict, "6,000 في الشهر أكتر من اللي بتحوّشه (5,000)")
        assertEquals(-100_000L, reach.comparison.gapMinor)
    }

    @Test fun anUnknownMonthMakesTheComparisonUnavailable() = runBlocking<Unit> {
        val unclassified = calculator(history(listOf(txn("2026-08-05", EconomicKind.UNCLASSIFIED, 1_000)))).perMonth(1_200_000, 0, "2027-06-30", Currency.SAR, today)
        assertNull(unclassified.actual!!.months[1].savedMinor, "يوليو فيه عملية نوعها ما اتحددش")
        assertNull(unclassified.actual!!.averageMinor)
        assertEquals(SavingVerdict.UNKNOWN, unclassified.comparison.verdict)
        assertEquals(133_334L, unclassified.plan.perMonthMinor, "الحسبة نفسها شغالة: 12,000 ÷ 9 لفوق")
        val empty = calculator(history(skipJune = true)).perMonth(1_200_000, 0, "2027-06-30", Currency.SAR, today)
        assertNull(empty.actual!!.months[0].savedMinor, "يونيو فاضي ⇒ مش معروف (مش صفر)")
        assertEquals(SavingVerdict.UNKNOWN, empty.comparison.verdict)
        val unwired = SavingsCalculator(SavingsCalculatorDeps()).perMonth(1_200_000, 0, "2027-06-30", Currency.SAR, today)
        assertNull(unwired.actual)
        assertEquals(SavingVerdict.UNKNOWN, unwired.comparison.verdict)
    }

    @Test fun oneTapTurnsTheResultIntoASavingsGoalWithTheSameMonthlyAmount() = runBlocking<Unit> {
        Texts.arabicVariant = ArabicVariant.MSA
        val calc = calculator(history())
        val plan = calc.perMonth(1_200_000, 150_000, "2027-06-30", Currency.SAR, today).plan
        val goal = calc.turnIntoGoal(plan, Currency.SAR)
        assertEquals("ادخار 12,000.00 ر.س", goal.name)
        assertEquals(today, goal.startDate)
        assertEquals("2027-06-30", goal.targetDate)
        val progress = LoadGoalsOverview(LoadGoalsOverviewDeps(goals, contributions)).load(today).single()
        assertEquals(150_000L, progress.savedMinor, "اللي معاك اتسجل إيداع يوم البداية")
        assertEquals(plan.perMonthMinor, progress.requiredPerMonthMinor, "نفس رقم الحاسبة بالظبط")
        val reach = calc.reach(75_000, 12, 0, Currency.SAR, today).reach
        val second = calc.turnIntoGoal(reach, Currency.SAR, today, "سفر وهمي")
        assertEquals(900_000L, second.targetMinor)
        assertEquals("2027-10-05", second.targetDate)
        assertEquals(1, contributions.listAll().size, "من غير مبلغ بداية ⇒ مفيش إيداع")
    }

    private fun job(id: String, kind: IncomeSourceKind = IncomeSourceKind.JOB, start: String = "2015-01-01", end: String? = null, expected: Long? = 1_000_000) =
        IncomeSource(id, "شركة وهمية $id", "شركه وهميه $id", kind, Currency.SAR, start, end, expectedMinor = expected)

    /** المستخدم كتب بدل السكن صفر صراحة؛ الأساسي null ⇒ المقترح من مصادر الدخل. */
    private val saudi = SaudiPensionInput("1990-03-15", today, false, 24, basicWageMinor = null, housingAllowanceMinor = 0)

    @Test fun retirementTakesTheSalaryAndJobStartFromIncomeSources() = runBlocking<Unit> {
        val sources = MemoryIncomeSourceRepository(listOf(job("j-1"), job("p-1", IncomeSourceKind.PART_TIME), job("old", start = "2010-01-01", end = "2014-12-31")))
        val calc = RetirementCalculator(RetirementCalculatorDeps(sources))
        val out = calc.calculate(RetirementRequest("SA", saudi, eosEnd = EosEnd.EMPLOYER_OR_CONTRACT_END, desiredMonthlyMinor = 1_500_000, years = 20), today)
        assertEquals(1_000_000L, out.defaults.salaryMinor)
        assertEquals(1_000_000L, out.defaults.suggestedBasicMinor, "مفيش تقسيم في مصادر الدخل ⇒ المرتب كله أساسي")
        assertNull(out.defaults.suggestedHousingMinor, "السكن فاضي لحد ما المستخدم يكتبه")
        assertEquals(1, out.defaults.activeJobs, "البارت تايم والشغل اللي خلص برا")
        // 10,000 × 2.25% × 365 ÷ 12 = 6,843.75
        assertEquals(684_375L, out.pension.pensionMinor)
        // من 2015-01-01 لـ2055-03-15: 40 سنة و73 يوم ⇒ 5 × نص + 35 × شهر + 73/365 شهر = 37.7 شهر = 377,000
        val eos = out.endOfService as EosPart.Known
        assertEquals(37_700_000L, eos.result.payableMinor)
        // (15,000 − 6,843.75) × 240 = 1,957,500 − 377,000 = 1,580,500 ÷ 341 = 4,634.897… ⇒ 4,634.90
        assertEquals(463_490L, out.gap!!.gap!!.perMonthMinor)
        // المستخدم عدّل: أساسي 6,000 + سكن 2,000 = 8,000 للمعاش · والمكافأة على الأجر الفعلي 10,000 من مصادر الدخل (م2 — أجر تاني)
        val edited = calc.calculate(RetirementRequest("SA", saudi.copy(basicWageMinor = 600_000, housingAllowanceMinor = 200_000), eosEnd = EosEnd.EMPLOYER_OR_CONTRACT_END), today)
        assertEquals(547_500L, edited.pension.pensionMinor, "8,000 × 2.25% × 365 ÷ 12")
        assertEquals(37_700_000L, (edited.endOfService as EosPart.Known).result.payableMinor, "10,000 × 37.7 — مش 8,000")
        assertNull(edited.gap, "من غير المبلغ المطلوب ⇒ المعاش بس")
        // أجر المكافأة مكتوب لوحده ⇒ هو اللي بيتحسب
        val eosEdited = calc.calculate(RetirementRequest("SA", saudi.copy(basicWageMinor = 600_000, housingAllowanceMinor = 200_000), eosWageMinor = 900_000, eosEnd = EosEnd.EMPLOYER_OR_CONTRACT_END), today)
        assertEquals(33_930_000L, (eosEdited.endOfService as EosPart.Known).result.payableMinor, "9,000 × 37.7")
        // السكن ما اتكتبش ⇒ المعاش «غير متاح» بسببه (مش صفر مؤكد)، والمكافأة شغالة عادي
        val noHousing = calc.calculate(RetirementRequest("SA", saudi.copy(housingAllowanceMinor = null), eosEnd = EosEnd.EMPLOYER_OR_CONTRACT_END), today)
        assertEquals(CalcReason.NEED_HOUSING, noHousing.pension.reason)
    }

    @Test fun missingInputsAreUnavailableWithReasons() = runBlocking<Unit> {
        val none = RetirementCalculator(RetirementCalculatorDeps(MemoryIncomeSourceRepository()))
        val out = none.calculate(RetirementRequest("SA", saudi, eosEnd = EosEnd.EMPLOYER_OR_CONTRACT_END, desiredMonthlyMinor = 1_500_000, years = 20), today)
        assertEquals(CalcReason.NEED_BASIC, out.pension.reason)
        assertEquals(EosPart.Unavailable(CalcReason.NEED_JOB_START), out.endOfService)
        assertEquals(CalcReason.NEED_BASIC, out.gap!!.reason)
        val noEosWage = none.calculate(RetirementRequest("SA", saudi.copy(basicWageMinor = 800_000), jobStartedAt = "2015-01-01", eosEnd = EosEnd.EMPLOYER_OR_CONTRACT_END), today)
        assertEquals(EosPart.Unavailable(CalcReason.NEED_WAGE), noEosWage.endOfService, "أجر المكافأة الفعلي مش الأساسي — مالوش اقتراح من غير مصادر الدخل")
        val noJob = none.calculate(RetirementRequest("SA", saudi.copy(basicWageMinor = 800_000), noCurrentJob = true, eosEnd = EosEnd.EMPLOYER_OR_CONTRACT_END), today)
        assertEquals(EosPart.NotApplicable(TextKey.CALC_EOS_NO_JOB), noJob.endOfService)
        val two = RetirementCalculator(RetirementCalculatorDeps(MemoryIncomeSourceRepository(listOf(job("j-1"), job("j-2", start = "2020-01-01")))))
        val d = two.defaults("SA", today)
        assertEquals(2, d.activeJobs)
        assertNull(d.salaryMinor, "وظيفتين شغالين ⇒ مش هنختار واحدة من عندنا")
        assertNull(d.jobStartedAt)
    }

    @Test fun egyptPensionFromTheTypedSettlementWageAndNoEndOfServiceAward() = runBlocking<Unit> {
        val calc = RetirementCalculator(RetirementCalculatorDeps(MemoryIncomeSourceRepository(listOf(job("e-1").copy(currency = Currency.EGP)))))
        // مولود 1985-06-01 ⇒ سن الشيخوخة 65 (يتم 60 بعد يوليو 2040) · 60 + 283 = 343 شهر · جدول 5 عمود 65 سطر 65 = 45.0
        // 10,000 × (343 ÷ 12) ÷ 45 = 6,351.85 · الفجوة (8,000 − 6,351.85) × 240 = 395,556 ÷ 283 = 1,397.72… ⇒ 1,397.73
        val input = EgyptPensionInput("1985-06-01", "2000-01-02", 60, settlementWageMinor = 1_000_000)
        val out = calc.calculate(RetirementRequest("EG", egypt = input, eosEnd = EosEnd.EMPLOYER_OR_CONTRACT_END, desiredMonthlyMinor = 800_000, years = 20), today)
        assertEquals(PensionStatus.ESTIMATED, out.pension.status)
        assertEquals(635_185L, out.pension.pensionMinor)
        assertEquals(EosPart.NotApplicable(TextKey.CALC_EOS_EGYPT_NOT_APPLICABLE), out.endOfService)
        assertEquals(139_773L, out.gap!!.gap!!.perMonthMinor)
        assertEquals(1_000_000L, out.defaults.salaryMinor, "المرتب بيتقري، بس أجر التسوية مش بيتاخد منه")
        // §69.8: آخر حد أدنى/أقصى معلن (NOSI 2025-11-30: 2,700 / 16,700 لسنة 2026) اقتراح بس — التقاعد 2050 ⇒ الحدين مش معروفين في الحسبة
        assertEquals(EgyptContributionWageLimits(2026, 270_000L, 1_670_000L), out.defaults.egyptLatestLimits)
        assertNull(out.pension.egyptDetail!!.minContributionWageMinor, "الاقتراح ما بيتحطش لوحده")
        assertNull(out.pension.egyptDetail!!.maxContributionWageMinor)
        assertNull(calc.defaults("SA", today).egyptLatestLimits, "السعودية مالهاش")
        val noWage = calc.calculate(RetirementRequest("EG", egypt = input.copy(settlementWageMinor = null), eosEnd = EosEnd.EMPLOYER_OR_CONTRACT_END, desiredMonthlyMinor = 800_000, years = 20), today)
        assertEquals(CalcReason.NEED_SETTLEMENT_WAGE, noWage.pension.reason)
        assertEquals(CalcReason.NEED_SETTLEMENT_WAGE, noWage.gap!!.reason)
    }
}
