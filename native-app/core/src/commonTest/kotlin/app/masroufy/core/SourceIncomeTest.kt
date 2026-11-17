package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * §120 — صفحة مصدر الدخل: اللي وصل شهر بشهر + «وصل كام من المتوقع الشهر ده» (مراجعة todo-types-2). كل الأسامي والمبالغ مخترعة:
 * جهة «الندى» حسابها آخره 4321، ويوم الراتب 28.
 */
class SourceIncomeTest {
    private var seq = 0

    private fun deposit(date: String, amount: Long, kind: EconomicKind = EconomicKind.SALARY, confirmed: Boolean = true, from: String = "4321") = Transaction(
        id = "in-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = confirmed,
        observedDirection = Direction.IN, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = if (confirmed) ReviewState.CONFIRMED else ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = "W-/FRACCT/0000000000000${from}FRNADACO:ملاحظة", sourceOperationType = "حوالة واردة",
    )

    private val key = transferPartyOf(deposit("2026-01-01", 1))!!.key
    private val job = IncomeSource(
        "inc-1", "جهة الندى", "جهة الندى", IncomeSourceKind.JOB, Currency.SAR, "2026-08-01", payerKeys = listOf(key), expectedMinor = 1_200_000,
    )

    @Test fun monthByMonthNewestFirstAndThisMonthAgainstExpected() {
        val txns = listOf(
            deposit("2026-08-28", 1_200_000),
            deposit("2026-09-27", 1_200_000), // قبل يوم الراتب بيوم ⇒ بيتحسب لشهر 09-28 (§75-3)
            deposit("2026-10-28", 1_150_000),
            deposit("2026-11-02", 30_000, EconomicKind.BONUS), // مكافأة ⇒ مش مرتب
            deposit("2026-11-03", 50_000, EconomicKind.UNCLASSIFIED, confirmed = false), // لسه بيتسأل ⇒ مش محسوب
            deposit("2026-10-28", 999_900, from = "9999"), // حساب تاني
        )
        val r = sourceIncome(job, listOf(job), txns, 28, "2026-11-15")
        assertEquals(listOf("2026-10", "2026-09", "2026-08", "2026-07"), r.months.map { it.period.key })
        assertEquals(listOf(1_150_000L, 1_200_000L, 1_200_000L, 0L), r.months.map { it.receivedMinor })
        assertEquals(1_150_000L, r.thisMonth!!.receivedMinor)
        assertEquals(1_200_000L, r.expectedMinor)
        assertEquals(1, r.waitingCount)
        assertEquals(50_000L, r.waitingMinor)
    }

    @Test fun undecidedTransfersStayInTheirOwnRowNotInReceived() {
        // §121-9: مقترح «مرتب» بس لسه ما اتأكدش ⇒ مش في اللي وصل، في سطر لوحده
        val txns = listOf(
            deposit("2026-10-28", 1_200_000),
            deposit("2026-11-01", 1_200_000, confirmed = false),
            deposit("2026-11-05", 70_000, EconomicKind.UNCLASSIFIED, confirmed = false),
        )
        val r = sourceIncome(job, listOf(job), txns, 28, "2026-11-15")
        assertEquals(1_200_000L, r.thisMonth!!.receivedMinor)
        assertEquals(1, r.thisMonth!!.count)
        assertEquals(2, r.waitingCount)
        assertEquals(1_270_000L, r.waitingMinor)
        val none = sourceIncome(job, listOf(job), txns.take(1), 28, "2026-11-15")
        assertEquals(0 to 0L, none.waitingCount to none.waitingMinor)
    }

    @Test fun noExpectedIsUnavailableAndEndedSourceHasNoThisMonth() {
        val r = sourceIncome(job.copy(expectedMinor = null), listOf(job), emptyList(), 28, "2026-11-15")
        assertNull(r.expectedMinor)
        assertEquals(0L, r.thisMonth!!.receivedMinor)
        val ended = job.copy(endedAt = "2026-09-30")
        val e = sourceIncome(ended, listOf(ended), listOf(deposit("2026-09-28", 1_200_000)), 28, "2026-11-15")
        assertNull(e.thisMonth)
        assertEquals("2026-09", e.months.first().period.key)
        assertNull(sourceIncome(job.copy(payFrequency = PayFrequency.WEEKLY), listOf(job), emptyList(), 28, "2026-11-15").expectedMinor)
    }

    @Test fun atMostTwelveMonths() {
        val old = job.copy(startedAt = "2020-01-01")
        assertEquals(SOURCE_MONTHS_MAX, sourceIncome(old, listOf(old), emptyList(), 28, "2026-11-15").months.size)
    }
}
