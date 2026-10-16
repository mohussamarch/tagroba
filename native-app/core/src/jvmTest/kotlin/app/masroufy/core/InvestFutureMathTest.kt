package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «هتبقى معاك كام» (مواصفة النقل §2) — أرقام مخترعة، والمتوقع محسوب باليد من نفس القاعدة (أعداد صحيحة بس). */
class InvestFutureMathTest {
    private fun entry(key: String, bp: Int) = AverageEntry(key, bp, "2015", "2025", true, false, "مصدر تجريبي", null, "test")

    private fun rates(cpi: Int = 0, gold: Int = 0, shares: Int = 0) = InvestRates(cpi, gold, shares, entry("CPI_SA", cpi), entry("GOLD_USD", gold), entry("STOCKS_SA", shares))

    private fun inputs(
        cash: Long = 10_000, split: InvestableSplit = InvestableSplit(1_000, 2_000, 3_000, 500, 0), debts: List<DebtLine> = emptyList(),
        save: Long? = 1_000, income: Long? = 20_000, r: InvestRatesState = InvestRatesState.Ready(rates()), country: String = "SA",
        goals: List<FutureGoal> = emptyList(), passive: Long = 0,
    ) = FutureInputs("sa", country, if (country == "EG") Currency.EGP else Currency.SAR, "2026-10-16", cash, 0, split, debts, save, income, passive, r, 36, goals)

    @Test fun zeroRatesGiveExactLinearLines() {
        val p = projectFuture(inputs(), FutureChoice(years = 10))!!
        val base = 1_000L + 2_000 + 3_000 + 500 + 10_000
        assertEquals(listOf(FuturePath.KEEP, FuturePath.INVEST, FuturePath.MORE), p.lines.map { it.path })
        assertEquals(FuturePath.INVEST, p.selected, "الافتراضي «استثمر تحويشك» لما فيه تحويش")
        for (y in 0..FUTURE_MAX_YEARS) {
            assertEquals(base + 1_000L * 12 * y, p.valueAt(FuturePath.KEEP, y))
            assertEquals(base + 1_000L * 12 * y, p.valueAt(FuturePath.INVEST, y))
            assertEquals(base + 10_000L * 12 * y, p.valueAt(FuturePath.MORE, y), "مبلغ البداية خطوة واحدة 100 ر.س")
        }
        assertEquals(base + 120_000, p.endMinor)
        assertEquals(120_000L, p.deltaMinor)
    }

    @Test fun debtAmortisesAndClampsAtZero() {
        val p = projectFuture(inputs(save = 0, debts = listOf(DebtLine(5_000, 1_000), DebtLine(700, 0))), FutureChoice(path = FuturePath.KEEP))!!
        val base = 16_500L
        assertEquals(base - 5_700, p.valueAt(FuturePath.KEEP, 0))
        assertEquals(base - 700, p.valueAt(FuturePath.KEEP, 1), "القسط خلّص الدين في 5 شهور — ما بيبقاش سالب، ودين الناس ثابت")
        assertEquals(10_800L, p.netNowMinor)
    }

    @Test fun lowSavingShowsKeepAndMoreAndKeepGoesDown() {
        val p = projectFuture(inputs(save = -500), FutureChoice())!!
        assertEquals(listOf(FuturePath.KEEP, FuturePath.MORE), p.lines.map { it.path })
        assertEquals(FuturePath.MORE, p.selected)
        val keep = p.line(FuturePath.KEEP)
        assertTrue((1..FUTURE_MAX_YEARS).all { keep[it] < keep[it - 1] })
        assertEquals(keep[0] - 500L * 12 * 40, keep[40])
    }

    @Test fun unknownSavingIsNotZero() {
        val i = inputs(save = null)
        assertTrue(i.saveUnknown)
        val p = projectFuture(i, FutureChoice(path = FuturePath.INVEST))!!
        assertEquals(listOf(FuturePath.KEEP, FuturePath.MORE), p.lines.map { it.path }, "مفيش «استثمر تحويشك» من غير تحويش معروف")
        assertEquals(FuturePath.MORE, p.selected)
        assertEquals(p.valueAt(FuturePath.KEEP, 0), p.valueAt(FuturePath.KEEP, 40), "زي ما إنت = الحاجات بس")
    }

    @Test fun tenPercentRuleRoundsToTheStep() {
        assertEquals(340_000L, startAmount(inputs(save = 180_000, income = 1_596_667)), "18,000 + 1,596 ⇒ 3,396 ⇒ 3,400")
        assertEquals(160_000L, startAmount(inputs(save = -45_000, income = 1_596_667)), "منخفض ⇒ العشر بس")
        assertEquals(50_000L, startAmount(inputs(save = -45_000, income = null)), "الدخل مش معروف ⇒ 500 ر.س")
        assertEquals(200_000L, startAmount(inputs(save = null, income = null, country = "EG")), "مصر ⇒ 2,000 ج.م")
        assertEquals(10_000L, startAmount(inputs(save = 1, income = 0)), "أقله خطوة")
        val i = inputs(save = 180_000, income = 1_596_667)
        assertEquals(1_596_667L, maxAmount(i, 340_000), "الدخل أكبر من 4 أضعاف البداية")
    }

    @Test fun amountIsClampedToStepAndMax() {
        val p = projectFuture(inputs(), FutureChoice(path = FuturePath.MORE, amountMinor = 999_999_999))!!
        assertEquals(p.maxAmountMinor, p.amountMinor)
        val q = projectFuture(inputs(), FutureChoice(path = FuturePath.MORE, amountMinor = 1))!!
        assertEquals(10_000L, q.amountMinor)
    }

    @Test fun holdingsMixIsWeightedAndFloored() {
        val r = rates(cpi = 174, gold = 1_488, shares = 795)
        assertEquals(1_159, holdingsMixBp(InvestableSplit(1_000, 3_000, 0, 9_999, 0), r), "(1000×174 + 3000×1488) ÷ 4000 = 1159.5 ⇒ 1159")
        assertNull(holdingsMixBp(InvestableSplit(0, 0, 0, 5_000, 0), r), "الثابت مش داخل الوزن")
        assertEquals(795, mixBpOf(FutureMix.MINE, InvestableSplit.EMPTY, r), "مفيش أملاك بتكبر ⇒ الأسهم")
        assertEquals(174, mixBpOf(FutureMix.PROPERTY, InvestableSplit.EMPTY, r))
    }

    @Test fun milestonesPassedReachedNever() {
        val rules = futureRules("SA")
        val values = List(FUTURE_MAX_YEARS + 1) { 19_000_000L + it * 2_000_000L }
        val rows = milestones(values, rules)
        assertEquals(MileState.PASSED, rows[0].state)
        assertEquals(MileState.REACHED, rows[1].state)
        assertEquals(16, rows[1].yearsFromNow, "190 ألف + 20 ألف × 16 ≥ نص مليون")
        assertEquals(MileState.NEVER, rows[2].state)
        assertEquals(InvestFutureKey.FUT_MILE_FIRST_M, futureRules("EG").milestones[0].second)
    }

    @Test fun deflateWorksOnNegativeBase() {
        val r = rates(cpi = 1_000)
        val p = projectFuture(inputs(cash = 0, split = InvestableSplit.EMPTY, debts = listOf(DebtLine(100_000, 0)), save = 0, r = InvestRatesState.Ready(r)), FutureChoice(path = FuturePath.KEEP))!!
        assertEquals(-100_000L, p.valueAt(FuturePath.KEEP, 0))
        assertEquals(deflate(-100_000, 12, 1_000), p.valueAt(FuturePath.KEEP, 1))
        assertEquals(-90_909L, p.valueAt(FuturePath.KEEP, 1))
        val nominal = projectFuture(p.inputs, FutureChoice(path = FuturePath.KEEP, todayMoney = false))!!
        assertEquals(-100_000L, nominal.valueAt(FuturePath.KEEP, 5), "بالاسم الدين ثابت")
    }

    @Test fun passiveIncomeIsGrowthAboveInflationOnly() {
        val r = rates(cpi = 200, gold = 1_400, shares = 100)
        val p = projectFuture(inputs(split = InvestableSplit(0, 1_200_000, 600_000, 0, 0), r = InvestRatesState.Ready(r), passive = 5_000), FutureChoice(path = FuturePath.KEEP))!!
        // ذهب: 1,200,000 × 1200 ÷ 120,000 = 12,000 · أسهم تحت التضخم ⇒ صفر · + 5,000 المعروف
        assertEquals(17_000L, p.passive.nowMinor)
        assertEquals(5_000L, p.passive.knownMinor)
    }

    @Test fun unavailableRatesGiveNoProjection() {
        assertNull(projectFuture(inputs(r = InvestRatesState.Unavailable(listOf("CPI_SA"))), FutureChoice()))
    }

    @Test fun pinsReadTheSelectedLine() {
        val p = projectFuture(inputs(), FutureChoice(path = FuturePath.MORE, marks = listOf(2050, 2030, 2070)))!!
        assertEquals(listOf(2030 to p.valueAt(FuturePath.MORE, 4), 2050 to p.valueAt(FuturePath.MORE, 24)), p.pins, "بعد 40 سنة ما بتتحسبش")
        assertEquals(25, stepAtLeast(24), "أصغر خطوة ≥ 24")
        assertEquals(40, stepAtLeast(41))
    }

    @Test fun countriesTotalUsesTodaysRate() {
        val fx = FxRate(egpPerSar4(375, 5_225), "مصدر", "2026-10-10")
        assertEquals(139_333L, fx.egpPerSar4)
        val sa = CountryRow("sa", "SA", Currency.SAR, 100_000)
        val eg = CountryRow("eg", "EG", Currency.EGP, 1_000_000)
        assertEquals(100_000L + 71_771, countriesTotal(sa, listOf(eg), fx), "1,000,000 قرش × 10,000 ÷ 139,333 = 71,770.51")
        assertEquals(1_000_000L + 1_393_330, countriesTotal(eg, listOf(sa), fx))
        assertNull(countriesTotal(sa, listOf(eg), null), "سعر الصرف ناقص ⇒ غير متاح")
        assertNull(countriesTotal(sa, listOf(eg.copy(valueMinor = null)), fx))
    }

    @Test fun goalsGetMonthsForTheAxis() {
        val p = projectFuture(inputs(goals = listOf(FutureGoal("سفر", "2027-04-16"), FutureGoal("بلا", null))), FutureChoice())!!
        assertEquals(listOf(6, null), p.goals.map { it.monthsFromToday })
    }
}
