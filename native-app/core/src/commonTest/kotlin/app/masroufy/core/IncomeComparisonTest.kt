package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** قبل وبعد المصدر الجديد (OVERRIDES §47 · §48 · §44.1) — أرقام مخترعة، بالهللة، من غير كسور عائمة. */
class IncomeComparisonTest {
    private var seq = 0

    private fun t(date: String, amount: Long, kind: EconomicKind, currency: Currency = Currency.SAR) = Transaction(
        id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = true,
        observedDirection = if (countsAsIncome(kind) || kind == EconomicKind.LOAN_RECEIVED) Direction.IN else Direction.OUT, amountMinor = amount,
        currency = currency, categoryConfirmed = false, excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false,
        createdAt = "x", updatedAt = "x",
    )

    private val newJob = IncomeSource("n", "شركة وهمية", "شركة وهمية", IncomeSourceKind.JOB, Currency.SAR, "2026-04-28")

    /** شهر مالي (من يوم 28): مرتب + مصروف. */
    private fun month(start: String, salary: Long, spend: Long) = listOf(t(start, salary, EconomicKind.SALARY), t(addDaysIso(start, 5), spend, EconomicKind.PURCHASE))

    private val history = month("2026-01-28", 1_000_000, 600_000) + month("2026-02-28", 1_000_000, 600_000) + month("2026-03-28", 1_000_000, 600_000) +
        month("2026-04-28", 1_200_000, 750_000) + month("2026-05-28", 1_200_000, 750_000) + month("2026-06-28", 1_200_000, 750_000)

    @Test fun periodsSkipTheMonthThatIsHalfOldHalfNew() {
        val (before, after) = comparisonPeriods("2026-04-28", 28)
        assertEquals(listOf("2026-01", "2026-02", "2026-03"), before.map { it.key })
        assertEquals(listOf("2026-04", "2026-05", "2026-06"), after.map { it.key })
        val (b2, a2) = comparisonPeriods("2026-04-10", 28)
        assertEquals(listOf("2025-12", "2026-01", "2026-02"), b2.map { it.key }, "فترة 03 فيها البداية من نصها ⇒ برا الاتنين")
        assertEquals(listOf("2026-04", "2026-05", "2026-06"), a2.map { it.key })
    }

    @Test fun incomeUpTwentyAndSpendingUpTwentyFiveAfterThreeFullMonths() {
        assertNull(compareAroundSourceStart(newJob, history, emptyList(), 28, "2026-07-27"), "آخر يوم في الشهر التالت لسه ما خلصش")
        val c = assertNotNull(compareAroundSourceStart(newJob, history, emptyList(), 28, "2026-07-28"))
        assertEquals(1_000_000, c.incomeBeforeAvgMinor)
        assertEquals(1_200_000, c.incomeAfterAvgMinor)
        assertEquals(600_000, c.expenseBeforeAvgMinor)
        assertEquals(750_000, c.expenseAfterAvgMinor)
        assertEquals(200, c.incomeChangeTenthPercent, "20.0٪")
        assertEquals(250, c.expenseChangeTenthPercent, "25.0٪")
        assertTrue(c.totalsReliable)
    }

    @Test fun nuqootStayOutOfTheIncomeAverage() {
        val withGift = history + t("2026-05-10", 5_000_000, EconomicKind.EVENT_GIFT)
        val c = assertNotNull(compareAroundSourceStart(newJob, withGift, emptyList(), 28, "2026-07-28"))
        assertEquals(1_200_000, c.incomeAfterAvgMinor, "النقوط مرة واحدة مش دخل شهري")
        assertEquals(200, c.incomeChangeTenthPercent)
    }

    @Test fun aMonthWithoutDataMeansNoNumberAtAll() {
        val gap = history.filterNot { it.occurredAt.startsWith("2026-02-28") || it.occurredAt == "2026-03-05" }
        assertNull(compareAroundSourceStart(newJob, gap, emptyList(), 28, "2026-07-28"))
        // عملة تانية ما بتسدّش الفراغ
        assertNull(compareAroundSourceStart(newJob, gap + t("2026-03-01", 1_000, EconomicKind.PURCHASE, Currency.EGP), emptyList(), 28, "2026-07-28"))
        // عملية لسه ما اتحددش نوعها ⇒ الرقم موجود والشاشة بتقول إنه ناقص
        val unsure = history + t("2026-05-01", 50_000, EconomicKind.UNCLASSIFIED)
        assertFalse(assertNotNull(compareAroundSourceStart(newJob, unsure, emptyList(), 28, "2026-07-28")).totalsReliable)
    }

    @Test fun firstJobHasAveragesButNoPercentage() {
        val firstJob = history.map { if (it.economicKind == EconomicKind.SALARY && it.occurredAt < "2026-04-28") it.copy(economicKind = EconomicKind.LOAN_RECEIVED) else it }
        val c = assertNotNull(compareAroundSourceStart(newJob, firstJob, emptyList(), 28, "2026-07-28"))
        assertEquals(0, c.incomeBeforeAvgMinor)
        assertNull(c.incomeChangeTenthPercent, "من صفر مفيش نسبة")
        assertEquals(250, c.expenseChangeTenthPercent)
    }

    @Test fun tenthsOfAPercentRoundHalfAwayFromZero() {
        assertEquals(1, changeTenthPercent(1000, 1001))
        assertEquals(333, changeTenthPercent(3, 4))
        assertEquals(-333, changeTenthPercent(3, 2))
        assertEquals(1, changeTenthPercent(2000, 2001), "0.5 ⇒ 1")
        assertEquals(-1, changeTenthPercent(2000, 1999), "-0.5 ⇒ -1")
        assertEquals(0, changeTenthPercent(500, 500))
        assertNull(changeTenthPercent(0, 500))
        assertNull(changeTenthPercent(500, -1))
        assertEquals(1000L * 1000, changeTenthPercent(1, 1001))
        assertEquals((MAX_SAFE_HALALAS - 1) * 1000, changeTenthPercent(1, MAX_SAFE_HALALAS), "أكبر رقم آمن من غير ما يفيض")
    }
}
