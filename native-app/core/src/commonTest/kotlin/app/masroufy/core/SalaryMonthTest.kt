package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * قرار المالك §75-3: الراتب اللي نزل قبل يوم الشهر المالي بشوية **بيتحسب للشهر الجديد** — وقت العرض بس، تاريخ العملية ما بيتغيرش.
 * «بشوية» = [SALARY_EARLY_DAYS] أيام (اختيار Claude، المالك يقدر يغيّره). كل المبالغ مخترعة.
 */
class SalaryMonthTest {
    private val owner = EstimatePolicy.OWNER_2026_10

    private fun t(date: IsoDate, kind: EconomicKind = EconomicKind.SALARY, dir: Direction = Direction.IN) = Transaction(
        id = "t-$date-${kind.wire}", occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = 1_200_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    @Test fun salaryShortlyBeforePaydayCountsInTheNewMonth() {
        assertEquals(7, SALARY_EARLY_DAYS)
        assertEquals("2026-09-28", countingDate(t("2026-09-26"), 28, owner), "يومين قبل يوم 28")
        assertEquals("2026-09-28", countingDate(t("2026-09-27"), 28, owner), "آخر يوم في الشهر اللي فات")
        assertEquals("2026-09-28", countingDate(t("2026-09-21"), 28, owner), "7 أيام بالظبط")
        assertEquals("2026-09-20", countingDate(t("2026-09-20"), 28, owner), "8 أيام ⇒ في شهره")
        assertEquals("2026-09-15", countingDate(t("2026-09-15"), 28, owner), "نص الشهر ⇒ في شهره")
        assertEquals("2026-09-28", countingDate(t("2026-09-28"), 28, owner), "يوم الراتب نفسه في شهره أصلًا")
        assertEquals("2026-10-02", countingDate(t("2026-10-02"), 28, owner), "متأخر ⇒ في شهره")
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

    @Test fun theReadStartsSevenDaysBeforeThePeriod() {
        assertEquals("2026-09-21", countingReadStart("2026-09-28", owner))
        assertEquals("2026-12-25", countingReadStart("2027-01-01", owner), "عبر السنة")
    }

    @Test fun yearEndShortMonthsAndOtherPaydays() {
        assertEquals("2026-12-28", nextPeriodStart("2026-12-24", 28))
        assertEquals("2027-01-28", nextPeriodStart("2026-12-28", 28))
        assertEquals("2027-01-01", countingDate(t("2026-12-29"), 1, owner), "يوم الراتب 1: آخر الشهر الميلادي")
        // يوم راتب 31 في فبراير بيتقيد بـ28 ⇒ الراتب يوم 25 فبراير بيتحسب من 28 فبراير
        assertEquals("2027-02-28", countingDate(t("2027-02-25"), 31, owner))
        assertEquals("2028-02-29", countingDate(t("2028-02-25"), 31, owner), "سنة كبيسة")
    }
}
