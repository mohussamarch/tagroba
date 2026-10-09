package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * قرار المالك §75-3: الراتب اللي نزل قبل يوم الشهر المالي بشوية **بيتحسب للشهر الجديد** — وقت العرض بس، تاريخ العملية ما بيتغيرش.
 * «بشوية» = [SALARY_EARLY_DAYS] يوم برقم اليوم في الشهر (اختيار Claude، المالك يقدر يغيّره). كل المبالغ مخترعة.
 */
class SalaryMonthTest {
    private val owner = EstimatePolicy.OWNER_2026_10

    private fun t(date: IsoDate, kind: EconomicKind = EconomicKind.SALARY, dir: Direction = Direction.IN) = Transaction(
        id = "t-$date-${kind.wire}", occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = 1_200_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    @Test fun salaryInTheSecondHalfBeforePaydayCountsInTheNewMonth() {
        assertEquals(15, SALARY_EARLY_DAYS)
        assertEquals(INCOME_EARLY_WINDOW_DAYS, SALARY_EARLY_DAYS, "نفس مهلة المرتب البدري في تنبيه المرتب المتأخر")
        assertEquals("2026-09-28", countingDate(t("2026-09-26"), 28, owner), "يومين قبل يوم 28")
        assertEquals("2026-09-28", countingDate(t("2026-09-27"), 28, owner), "آخر يوم في الشهر اللي فات")
        assertEquals("2026-09-28", countingDate(t("2026-09-21"), 28, owner), "أسبوع قبله")
        assertEquals("2026-09-28", countingDate(t("2026-09-20"), 28, owner), "8 أيام (أسبوع ويوم — الإجازة قدّمته يوم)")
        assertEquals("2026-09-28", countingDate(t("2026-09-13"), 28, owner), "15 يوم بالظبط")
        assertEquals("2026-09-12", countingDate(t("2026-09-12"), 28, owner), "16 يوم ⇒ في شهره (النص الأول)")
        assertEquals("2026-09-28", countingDate(t("2026-09-28"), 28, owner), "يوم الراتب نفسه في شهره أصلًا")
        assertEquals("2026-10-02", countingDate(t("2026-10-02"), 28, owner), "متأخر ⇒ في شهره")
        assertEquals("2026-10-12", countingDate(t("2026-10-12"), 28, owner), "متأخر أسبوعين ⇒ لسه في شهره")
    }

    @Test fun onlyIncomingSalaryMoves() {
        assertEquals("2026-09-26", countingDate(t("2026-09-26", EconomicKind.BONUS), 28, owner), "المكافأة مش راتب")
        assertEquals("2026-09-26", countingDate(t("2026-09-26", EconomicKind.UNCLASSIFIED), 28, owner), "الداخل المستني مش راتب")
        assertEquals("2026-09-26", countingDate(t("2026-09-26", dir = Direction.OUT), 28, owner), "صادر غلط النوع ما بيتنقلش")
    }

    @Test fun legacyNeverMoves() {
        assertEquals("2026-09-26", countingDate(t("2026-09-26"), 28, EstimatePolicy.LEGACY))
        assertEquals("2026-09-28", countingReadStart("2026-09-28", EstimatePolicy.LEGACY))
    }

    @Test fun theReadStartsFifteenDaysBeforeThePeriod() {
        assertEquals("2026-09-13", countingReadStart("2026-09-28", owner))
        assertEquals("2026-12-17", countingReadStart("2027-01-01", owner), "عبر السنة")
    }

    @Test fun yearEndShortMonthsAndOtherPaydays() {
        assertEquals("2026-12-28", nextPeriodStart("2026-12-24", 28))
        assertEquals("2027-01-28", nextPeriodStart("2026-12-28", 28))
        assertEquals("2027-01-01", countingDate(t("2026-12-29"), 1, owner), "يوم الراتب 1: آخر الشهر الميلادي")
        // يوم راتب 31 في فبراير بيتقيد بـ28 ⇒ الراتب يوم 25 فبراير بيتحسب من 28 فبراير
        assertEquals("2027-02-28", countingDate(t("2027-02-25"), 31, owner))
        assertEquals("2028-02-29", countingDate(t("2028-02-25"), 31, owner), "سنة كبيسة")
        // يوم راتب 31 والراتب آخر يوم في أبريل: الفترة بتبدأ 30 أبريل أصلًا ⇒ في مكانه
        assertEquals("2027-04-30", countingDate(t("2027-04-30"), 31, owner))
        assertEquals("2027-02-28", countingDate(t("2027-02-28"), 30, owner), "يوم راتب 30 في فبراير = 28")
    }

    /** المسافة برقم اليوم: يوم 24 ويوم راتب 1 ⇒ 8 في كل شهر (مش 7 في سبتمبر و8 في أغسطس). */
    @Test fun daysBeforePaydayIgnoresMonthLength() {
        assertEquals(8, daysBeforePayday("2026-08-24", 1))
        assertEquals(8, daysBeforePayday("2026-09-24", 1))
        assertEquals(8, daysBeforePayday("2027-02-24", 1))
        assertEquals(7, daysBeforePayday("2027-02-24", 31), "يوم راتب 31 من غير قيد فبراير")
        assertEquals(31, daysBeforePayday("2026-09-28", 28), "يوم الراتب نفسه = أبعد حاجة")
    }

    /** الحالات اللي المراجعة لقتها: نفس يوم الراتب كل شهر ⇒ نفس القرار كل شهر. */
    @Test fun theSameSalaryDayGetsTheSameDecisionEveryMonth() {
        val months = (0 until 24).map { (2027 + it / 12) to (it % 12 + 1) }
        fun moves(payday: Int, day: (Int, Int) -> Int) = months.map { (y, m) ->
            val date = formatIsoDate(DateParts(y, m, day(y, m)))
            countingDate(t(date), payday, owner) != date
        }.toSet()
        for (d in 23..25) {
            assertEquals(setOf(true), moves(1) { _, _ -> d }, "يوم راتب 1 والراتب يوم $d")
            assertEquals(setOf(true), moves(31) { _, _ -> d }, "يوم راتب 31 والراتب يوم $d")
        }
        assertEquals(setOf(true), moves(28) { _, _ -> 20 }, "يوم راتب 28 والراتب يوم 20")
        assertEquals(setOf(true), moves(28) { _, _ -> 21 }, "يوم راتب 28 والراتب يوم 21")
        assertEquals(setOf(false), moves(28) { _, _ -> 10 }, "يوم راتب 28 والراتب يوم 10")
    }

    /**
     * أي يوم راتب (1–31) وأي يوم ثابت للراتب (1–28، موجود في كل شهر): **كل فترة فيها راتب واحد بالظبط** — مفيش شهر فاضي وشهر براتبين.
     * وكمان الراتب «آخر يوم في الشهر» لأي يوم راتب برّه 13–15 (هناك آخر الشهر هو نص الفترة بالظبط، والحد بيقع جواه).
     */
    @Test fun everyPeriodGetsExactlyOneSalary() {
        val months = (0 until 36).map { (2026 + it / 12) to (it % 12 + 1) }
        fun check(payday: Int, label: String, day: (Int, Int) -> Int) {
            val counted = months.map { (y, m) -> countingDate(t(formatIsoDate(DateParts(y, m, day(y, m)))), payday, owner) }
            // من الفترة التالتة لحد قبل الأخيرة بفترتين — الأطراف ناقصها رواتب برّه المدى
            for (i in 2 until months.size - 2) {
                val (y, m) = months[i]
                val p = buildPeriod(y, m, payday)
                assertEquals(1, counted.count { it in p.start..p.end }, "$label · يوم راتب $payday · فترة ${p.key}")
            }
        }
        for (payday in 1..31) {
            for (d in 1..28) check(payday, "الراتب يوم $d") { _, _ -> d }
            if (payday !in 13..15) check(payday, "الراتب آخر الشهر") { y, m -> daysInMonth(y, m) }
        }
    }
}
