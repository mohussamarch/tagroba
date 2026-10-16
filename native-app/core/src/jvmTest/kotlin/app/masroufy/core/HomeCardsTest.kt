package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** كروت الرئيسية (اختيار المالك 2026-10-15 + ردوده 2026-10-16): الترتيب والمخفي و«تقدر تصرف النهارده» و«×» — أرقام مخترعة بالهللة. */
class HomeCardsTest {
    @Test
    fun firstTimeIsTheOwnersOrderAndHidesTheOtherTwo() {
        val layout = homeCardsLayout(null, null)
        assertFalse(layout.custom)
        // رد المالك 2026-10-16: القادم ⇐ محتاجة مراجعة ⇐ تقدر تصرف ⇐ شخصيتك ⇐ أكتر حاجة ⇐ أهدافك ⇐ آخر العمليات
        assertEquals(
            listOf(HomeCardKind.UPCOMING, HomeCardKind.REVIEW, HomeCardKind.DAILY, HomeCardKind.BUDDY, HomeCardKind.TOP, HomeCardKind.GOALS, HomeCardKind.RECENT),
            layout.visible,
        )
        assertEquals(setOf(HomeCardKind.PEOPLE, HomeCardKind.INVEST), layout.hidden)
    }

    @Test
    fun storedOrderDropsUnknownAndDuplicatesAndAppendsNewCards() {
        val layout = homeCardsLayout(listOf("goals", "daily", "someFutureCard", "goals", "people"), listOf("daily"))
        assertTrue(layout.custom)
        // goals · daily · people من المحفوظ، وبعدهم الباقي بالترتيب الأول — والجديد المخفي أول مرة (invest) بيفضل مخفي
        assertEquals(
            listOf(HomeCardKind.GOALS, HomeCardKind.DAILY, HomeCardKind.PEOPLE, HomeCardKind.UPCOMING, HomeCardKind.REVIEW, HomeCardKind.BUDDY, HomeCardKind.TOP, HomeCardKind.RECENT, HomeCardKind.INVEST),
            layout.order,
        )
        assertEquals(setOf(HomeCardKind.DAILY, HomeCardKind.INVEST), layout.hidden)
        assertEquals(HomeCardKind.GOALS, layout.visible.first())
    }

    @Test
    fun arrowsMoveOneStepAndStopAtTheEnds() {
        val first = defaultHomeCards()
        assertEquals(first, moveHomeCard(first, HomeCardKind.UPCOMING, -1))
        assertEquals(first, moveHomeCard(first, HomeCardKind.INVEST, 1))
        val moved = moveHomeCard(first, HomeCardKind.DAILY, -1)
        assertEquals(listOf(HomeCardKind.UPCOMING, HomeCardKind.DAILY, HomeCardKind.REVIEW, HomeCardKind.BUDDY), moved.order.take(4))
        assertTrue(moved.custom)
    }

    @Test
    fun switchHidesAndShows() {
        val hidden = toggleHomeCard(defaultHomeCards(), HomeCardKind.REVIEW)
        assertFalse(HomeCardKind.REVIEW in hidden.visible)
        val shown = toggleHomeCard(toggleHomeCard(hidden, HomeCardKind.REVIEW), HomeCardKind.PEOPLE)
        assertTrue(HomeCardKind.REVIEW in shown.visible)
        assertTrue(HomeCardKind.PEOPLE in shown.visible)
    }

    @Test
    fun ownersExampleThreeThousandMinusOneThousandOverTenDaysIsTwoHundred() {
        // مثال المالك بالحرف: معاك 3,000 وعليك قسط واشتراكات بـ1,000 قبل المرتب وفاضل 10 أيام ⇒ 200 في اليوم
        val d = dailySpend(300_000, 100_000, 10)
        assertEquals(20_000L, d.perDayMinor)
        assertFalse(d.short)
    }

    @Test
    fun dailySpendRoundsDownToWholeUnit() {
        // 7,350.00 − 2,200.00 = 5,150.00 على 21 يوم = 245.238… ⇒ 245.00
        assertEquals(24_500L, dailySpend(735_000, 220_000, 21).perDayMinor)
    }

    @Test
    fun dailySpendUnknownIsNotZero() {
        assertNull(dailySpend(null, 0, 10).perDayMinor)
        assertNull(dailySpend(100_000, 0, null).perDayMinor)
        assertNull(dailySpend(100_000, 0, 0).perDayMinor)
    }

    @Test
    fun duesMoreThanYouHaveIsZeroAndSaidSo() {
        val d = dailySpend(100_000, 150_000, 5)
        assertEquals(0L, d.perDayMinor)
        assertTrue(d.short)
    }

    private fun item(type: CalendarItemType, date: String, amount: Long?, flow: DueFlow?, currency: Currency = Currency.SAR) =
        CalendarItem(type, "s-$date-${type.wire}", "سطر وهمي", date, amount, currency, flow, daysLeft = 0)

    @Test
    fun duesAreEveryPaymentBeforePaydayInTheSpaceCurrency() {
        val items = listOf(
            item(CalendarItemType.INSTALLMENT, "2026-10-10", 60_000, DueFlow.PAY),
            item(CalendarItemType.RECURRING, "2026-10-20", 40_000, DueFlow.PAY),
            // مناسبة من غير مبلغ · فلوس جاية ليك · بعملة تانية · بعد آخر يوم · قبل النهارده ⇒ برا
            item(CalendarItemType.OCCASION, "2026-10-12", null, null),
            item(CalendarItemType.PAYDAY, "2026-10-28", null, DueFlow.RECEIVE),
            item(CalendarItemType.DEBT, "2026-10-15", 25_000, DueFlow.RECEIVE),
            item(CalendarItemType.RECURRING, "2026-10-16", 9_900, DueFlow.PAY, Currency.USD),
            item(CalendarItemType.RECURRING, "2026-10-28", 50_000, DueFlow.PAY),
            item(CalendarItemType.RECURRING, "2026-10-01", 50_000, DueFlow.PAY),
        )
        assertEquals(100_000L, duesUntil(items, "2026-10-07", "2026-10-27", Currency.SAR))
    }

    @Test
    fun barsAreShareOfLargestAndGoalPercentIsFloored() {
        assertEquals(listOf(100, 50, 34), sharesOfLargest(listOf(184_000, 92_000, 64_000)))
        assertEquals(emptyList(), sharesOfLargest(emptyList()))
        assertEquals(62, goalPercent(620_000, 1_000_000))
        assertEquals(18, goalPercent(900_000, 5_000_000))
        assertNull(goalPercent(null, 1_000_000))
        assertNull(goalPercent(100, 0))
    }

    @Test
    fun advisorComesBackWhenTheTipChanges() {
        val coffee = advisorTipKey("القهوة هذا الشهر 640", "بهذا المعدل…")
        val closed = HomeTipsClosed(coffee, null, null)
        assertFalse(advisorTipShown(coffee, closed))
        assertTrue(advisorTipShown(advisorTipKey("القهوة هذا الشهر 700", "بهذا المعدل…"), closed))
        assertTrue(advisorTipShown(coffee, HomeTipsClosed.NONE))
    }

    @Test
    fun profileComesBackWhenTheNextQuestionChangesOrAfterSevenDays() {
        val car = profileTipKey("CAR")
        val closed = HomeTipsClosed(null, car, "2026-10-07")
        assertFalse(profileTipShown(car, closed, "2026-10-13"))
        assertTrue(profileTipShown(car, closed, "2026-10-14"))
        assertTrue(profileTipShown(profileTipKey("FAMILY"), closed, "2026-10-08"))
        assertTrue(profileTipShown(car, HomeTipsClosed.NONE, "2026-10-08"))
    }
}
