package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * «حاسبة الادخار» (§69) — أرقام مخترعة، والحسبة على الورق في التعليق جنب كل رقم. في commonTest ⇒ بتشتغل على الآيفون كمان.
 */
class SavingsCalcTest {
    @Test fun targetAndDateGiveTheMonthlyAmountRoundedUp() {
        // 12,000 ومعاك 1,500 ⇒ الباقي 10,500. من 5 أكتوبر 2026 لـ30 يونيو 2027: 5 يونيو (8 شهور) لسه قبل 30 ⇒ 9 شهور.
        // 10,500 ÷ 9 = 1,166.666… ⇒ لفوق 1,166.67 (116,667 هللة)
        val r = savingsPerMonth(1_200_000, 150_000, "2026-10-05", "2027-06-30")
        assertEquals(1_050_000L, r.remainingMinor)
        assertEquals(9, r.months)
        assertEquals(116_667L, r.perMonthMinor)
        // 9 × 1,166.67 = 10,500.03 ≥ 10,500 (لفوق ما يوصّلش ناقص)، و8 شهور كانت هتبقى 1,312.50
        assertEquals(131_250L, savingsPerMonth(1_200_000, 150_000, "2026-10-05", "2027-06-05").perMonthMinor)
    }

    @Test fun calendarMonthsAndBoundaries() {
        // 31 يناير ⇒ 28 فبراير = شهر واحد ⇒ المبلغ كله في الشهر
        assertEquals(1, savingsPerMonth(500_000, 0, "2026-01-31", "2026-02-28").months)
        assertEquals(500_000L, savingsPerMonth(500_000, 0, "2026-01-31", "2026-02-28").perMonthMinor)
        // بكرة ⇒ شهر واحد (أقل حاجة)
        assertEquals(1, savingsPerMonth(100, 0, "2026-10-05", "2026-10-06").months)
        // معاك أكتر من الهدف ⇒ صفر في الشهر (مش سالب)
        val done = savingsPerMonth(100_000, 250_000, "2026-10-05", "2027-10-05")
        assertEquals(0L, done.remainingMinor)
        assertEquals(0L, done.perMonthMinor)
        // هللة واحدة باقية على 12 شهر ⇒ هللة في الشهر (لفوق) مش صفر
        assertEquals(1L, savingsPerMonth(100_001, 100_000, "2026-10-05", "2027-10-05").perMonthMinor)
        assertFailsWith<SavingsCalcError> { savingsPerMonth(0, 0, "2026-10-05", "2027-01-01") }
        assertFailsWith<SavingsCalcError> { savingsPerMonth(100, -1, "2026-10-05", "2027-01-01") }
        assertFailsWith<SavingsCalcError>("نفس اليوم مش «بعد النهارده»") { savingsPerMonth(100, 0, "2026-10-05", "2026-10-05") }
        assertFailsWith<SavingsCalcError> { savingsPerMonth(100, 0, "2026-10-05", "2126-10-06") }
        assertEquals(MAX_CALC_MONTHS, savingsPerMonth(100, 0, "2026-10-05", "2126-10-05").months, "100 سنة بالظبط مقبولة")
    }

    @Test fun monthlyAndDurationGiveTheAmountReachedWithoutGrowth() {
        // 750 × 12 + 1,000 = 10,000 (من غير أرباح — اختيار المالك)
        val r = savingsReach(75_000, 12, 100_000, "2026-10-05")
        assertEquals(1_000_000L, r.reachedMinor)
        assertEquals("2027-10-05", r.endDate)
        assertEquals("2027-02-28", savingsReach(1, 1, 0, "2027-01-31").endDate)
        assertFailsWith<SavingsCalcError> { savingsReach(0, 12, 0, "2026-10-05") }
        assertFailsWith<SavingsCalcError> { savingsReach(100, 0, 0, "2026-10-05") }
        assertFailsWith<SavingsCalcError> { savingsReach(100, MAX_CALC_MONTHS + 1, 0, "2026-10-05") }
    }

    @Test fun projectMonthlyWithZeroRateEqualsPlainSum() {
        assertEquals(savingsReach(75_000, 12, 0, "2026-10-05").reachedMinor, projectMonthly(75_000, 12, 0))
        assertEquals(1_000_000L, projectMonthly(75_000, 12, 0, startMinor = 100_000))
        assertEquals(100_000L, projectMonthly(75_000, 0, 1200, startMinor = 100_000), "صفر شهور ⇒ المبلغ زي ما هو")
    }

    @Test fun projectMonthlyCompoundsExactlyWithTheDocumentedRounding() {
        // 1,000 كل شهر بـ12% في السنة (1% في الشهر)، الإيداع آخر الشهر:
        // ش1: فايدة 0 ⇒ 1,000 · ش2: فايدة 10 ⇒ 1,010 + 1,000 = 2,010 · ش3: فايدة 20.10 ⇒ 2,030.10 + 1,000 = 3,030.10
        assertEquals(303_010L, projectMonthly(100_000, 3, 1200))
        // 12 شهر: المعادلة المقفولة 100,000 × ((1.01^12 − 1) ÷ 0.01) = 1,268,250.30 هللة؛ التقريب الشهري للهللة بيدّي 1,268,251
        assertEquals(1_268_251L, projectMonthly(100_000, 12, 1200))
        // 10,000 مرة واحدة بـ5% في السنة لمدة 10 سنين (مركب شهريًا): 1,000,000 × (1 + 0.05/12)^120 = 1,647,009.50 ⇒ 1,647,009
        assertEquals(1_647_009L, projectMonthly(0, 120, 500, startMinor = 1_000_000))
        // قاعدة التقريب: النص لبعيد عن الصفر — 50 هللة × 1% = 0.5 ⇒ 1 · 49 × 1% = 0.49 ⇒ 0 · السالب: −0.5 ⇒ −1
        assertEquals(51L, projectMonthly(0, 1, 1200, startMinor = 50))
        assertEquals(49L, projectMonthly(0, 1, 1200, startMinor = 49))
        assertEquals(49L, projectMonthly(0, 1, -1200, startMinor = 50))
        assertFailsWith<SavingsCalcError> { projectMonthly(100, 12, MAX_ANNUAL_RATE_BP + 1) }
        assertFailsWith<SavingsCalcError> { projectMonthly(100, 12, MIN_ANNUAL_RATE_BP - 1) }
        assertFailsWith<MoneyError>("برا الحد الآمن ⇒ خطأ مش رقم غلط") { projectMonthly(MAX_SAFE_HALALAS / 2, 1200, MAX_ANNUAL_RATE_BP) }
    }

    @Test fun mulDivIsExactBeyondLongProductRange() {
        // 9×10^15 × 900,000 يعدّي حد Long لو اتضرب الأول — هنا بالقسمة الأول: 9×10^15 × 9 ÷ 10 = 8.1×10^15
        assertEquals(8_100_000_000_000_000L, mulDivHalfUp(9_000_000_000_000_000L, 900_000, 1_000_000))
        assertEquals(-2L, mulDivHalfUp(-3, 1, 2), "−1.5 ⇒ −2 (لبعيد عن الصفر)")
        assertEquals(2L, mulDivHalfUp(3, 1, 2))
    }

    private fun txn(id: String, kind: EconomicKind, amount: Halalas, excluded: Boolean = false) = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = true,
        observedDirection = if (ruleFor(kind).liquidity == Liquidity.OUT) Direction.OUT else Direction.IN, amountMinor = amount,
        currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = excluded, reviewState = ReviewState.CONFIRMED,
        isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    @Test fun monthSavingIsRealIncomeMinusRealSpendingOnly() {
        val rows = listOf(
            txn("salary", EconomicKind.SALARY, 1_000_000), // دخل 10,000
            txn("gift", EconomicKind.EVENT_GIFT, 300_000), // نقوط ⇒ برا (مرة واحدة)
            txn("eos", EconomicKind.END_OF_SERVICE, 5_000_000), // مكافأة نهاية خدمة ⇒ برا
            txn("shop", EconomicKind.PURCHASE, 400_000), // مصروف 4,000 — منه 1,000 على شخص كدين
            txn("trip", EconomicKind.PURCHASE, 100_000, excluded = true), // مستبعد من الميزانية بس اتصرف ⇒ داخل
            txn("refund", EconomicKind.REFUND_RECEIVED, 50_000), // استرداد بينقّص المصروف 500
            txn("move", EconomicKind.INTERNAL_TRANSFER, 2_000_000), // بين محافظك أو لنفسك في بلد تانية ⇒ برا
            txn("loanIn", EconomicKind.LOAN_RECEIVED, 700_000), // سلفة ⇒ برا
            txn("gold", EconomicKind.ASSET_BUY, 600_000), // شراء دهب ⇒ برا (فلوس اتحوّشت في شكل تاني)
        )
        val shares = listOf(PersonAllocation("a-1", "shop", "p-1", AllocationKind.RECEIVABLE, 100_000, Currency.SAR))
        // 10,000 − (4,000 − 1,000 + 1,000 − 500) = 10,000 − 3,500 = 6,500
        assertEquals(650_000L, monthSavingMinor(rows, shares))
        // صرف أكتر من الدخل ⇒ سالب معروف
        assertEquals(-50_000L, monthSavingMinor(listOf(txn("s", EconomicKind.SALARY, 100_000), txn("p", EconomicKind.PURCHASE, 150_000)), emptyList()))
        assertNull(monthSavingMinor(emptyList(), emptyList()), "شهر من غير ولا عملية ⇒ مش معروف (مش صفر)")
        assertNull(monthSavingMinor(rows + txn("x", EconomicKind.UNCLASSIFIED, 10), emptyList()), "عملية نوعها لسه ما اتحددش ⇒ مش معروف")
    }

    @Test fun averageNeedsAllThreeMonthsKnown() {
        // (6,500 + 5,000 + 4,000) ÷ 3 = 5,166.666… ⇒ 5,166.67
        assertEquals(516_667L, averageSavingMinor(listOf(650_000, 500_000, 400_000)))
        assertEquals(-16_667L, averageSavingMinor(listOf(-50_000, 0, 0)), "السالب بيفضل سالب: −500 ÷ 3 = −166.67")
        assertNull(averageSavingMinor(listOf(650_000, null, 400_000)), "شهر مش معروف ⇒ المتوسط «غير متاح» (مش متوسط الاتنين)")
        assertNull(averageSavingMinor(listOf(650_000, 400_000)), "أقل من 3 شهور")
        val ok = compareWithActual(116_667, 516_667)
        assertEquals(SavingVerdict.ENOUGH, ok.verdict)
        assertEquals(400_000L, ok.gapMinor)
        val short = compareWithActual(116_667, 100_000)
        assertEquals(SavingVerdict.SHORT, short.verdict)
        assertEquals(-16_667L, short.gapMinor)
        assertEquals(SavingVerdict.ENOUGH, compareWithActual(100, 100).verdict, "قد المطلوب بالظبط = كفاية")
        val unknown = compareWithActual(116_667, null)
        assertEquals(SavingVerdict.UNKNOWN, unknown.verdict)
        assertNull(unknown.gapMinor)
    }
}
