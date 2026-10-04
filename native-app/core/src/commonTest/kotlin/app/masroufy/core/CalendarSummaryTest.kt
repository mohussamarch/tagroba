package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** الملخص الذكي و«فاضلك تقريبًا» (OVERRIDES §65) — أعداد صحيحة، والمجهول عمره ما بيتحسب صفر. بيانات مخترعة. */
class CalendarSummaryTest {
    private val today = "2026-10-04"
    private val period = periodForDate(today, 28)

    private fun item(type: CalendarItemType, id: String, date: String, amount: Long?, flow: DueFlow? = DueFlow.PAY, currency: Currency = Currency.SAR) =
        CalendarItem(type, id, "سطر وهمي", date, amount, currency, flow, daysBetween(today, date))

    private val items = listOf(
        item(CalendarItemType.RECURRING, "r-1", "2026-10-10", 5_000),
        item(CalendarItemType.OCCASION, "occ-1", "2026-10-12", null, null),
        item(CalendarItemType.EVENT, "ev-1", "2026-10-15", null),
        item(CalendarItemType.ZAKAT, "z-1", "2026-10-22", null),
        item(CalendarItemType.INSTALLMENT, "ip-1", "2026-10-25", 100_000),
        item(CalendarItemType.ROSCA_PAYOUT, "rc-1", "2026-10-25", 1_000_000, DueFlow.RECEIVE),
        item(CalendarItemType.DEBT, "o-1", "2026-10-26", 30_000),
        item(CalendarItemType.PAYDAY, PAYDAY_SOURCE_ID, "2026-10-28", null, DueFlow.RECEIVE),
        item(CalendarItemType.PUBLIC_OCCASION, "eid_al_adha-1448", "2026-10-30", null, null),
        item(CalendarItemType.RECURRING, "r-2", "2026-11-02", 9_000),
    )

    private fun counted(vararg pairs: Pair<String, Long>): List<CalendarItem> {
        val map = pairs.toMap()
        return items.map { i -> map[i.sourceId]?.let { i.copy(reservedMinor = it) } ?: i }
    }

    @Test fun untilPaydayCountsUnknownAmountsSeparatelyNeverAsZero() {
        val outlook = paydayOutlook(items, today, "2026-10-28", Currency.SAR)
        assertEquals(3, outlook.knownCount)
        assertEquals(135_000L, outlook.knownTotalMinor)
        assertEquals(2, outlook.unknownAmountCount, "الحدث والزكاة من غير مبلغ")
        val withForeign = items + item(CalendarItemType.INSTALLMENT, "ip-egp", "2026-10-20", 50_000, currency = Currency.EGP)
        val o2 = paydayOutlook(withForeign, today, "2026-10-28", Currency.SAR)
        assertEquals(135_000L, o2.knownTotalMinor, "عملة تانية ما بتتجمعش")
        assertEquals(1, o2.otherCurrencyCount)
    }

    @Test fun heaviestWeekIsByKnownPayAmountsAndTiesGoEarliest() {
        val week = heaviestWeek(items, period, Currency.SAR)!!
        assertEquals(HeavyWeek(4, "2026-10-19", "2026-10-25", 100_000), week)
        val tie = listOf(item(CalendarItemType.RECURRING, "a", "2026-09-29", 10_000), item(CalendarItemType.RECURRING, "b", "2026-10-06", 10_000))
        assertEquals(1, heaviestWeek(tie, period, Currency.SAR)!!.number)
        assertNull(heaviestWeek(listOf(item(CalendarItemType.EVENT, "e", "2026-10-06", null)), period, Currency.SAR), "مفيش مبلغ معروف ⇒ مفيش أسبوع")
        assertEquals(week, heaviestWeek(counted("ip-1" to 100_000, "ev-1" to 900_000), period, Currency.SAR), "الحساب من الفلوس ما بيغيّرش الأسبوع")
    }

    @Test fun shortlyBeforePaydayAndSoonWithoutCounting() {
        assertEquals(listOf("ip-1", "o-1"), dueShortlyBeforePayday(items, today, "2026-10-28").map { it.sourceId })
        assertEquals(listOf("occ-1", "ev-1", "eid_al_adha-1448"), unreservedSoon(items).map { it.sourceId })
        assertEquals(listOf("occ-1", "eid_al_adha-1448"), unreservedSoon(counted("ev-1" to 50_000)).map { it.sourceId })
        val summary = smartSummary(items, today, 28, period, Currency.SAR)
        assertEquals("2026-10-28", summary.untilPayday!!.nextPayday)
        val none = smartSummary(items, today, null, period, Currency.SAR)
        assertNull(none.untilPayday)
        assertTrue(none.beforePayday.isEmpty())
    }

    @Test fun countingNeverChangesAnyTotal() {
        val before = smartSummary(items, today, 28, period, Currency.SAR)
        val after = smartSummary(counted("ip-1" to 100_000, "r-1" to 5_000, "ev-1" to 70_000), today, 28, period, Currency.SAR)
        assertEquals(before.untilPayday, after.untilPayday)
        assertEquals(before.heaviestWeek, after.heaviestWeek)
        assertEquals(before.beforePayday.map { it.sourceId }, after.beforePayday.map { it.sourceId })
    }

    private val reserved = counted("ip-1" to 100_000, "ev-1" to 25_000, "eid_al_adha-1448" to 40_000)

    @Test fun salariedSubtractsOnlyCountedItemsBeforeNextPayday() {
        val p = projectLeftover(listOf(500_000, 200_000), reserved, today, Currency.SAR, salaried = true, nextPayday = "2026-10-28", unreconciled = false)
        assertEquals(LeftoverMode.UNTIL_MONTH_END, p.mode)
        assertEquals(700_000L, p.onHandMinor)
        assertEquals(125_000L, p.countedMinor, "العيد بعد المرتب ما بيتطرحش")
        assertEquals(575_000L, p.leftoverMinor)
        assertEquals("2026-10-28", p.until)
        assertEquals(uiText(TextKey.LEFTOVER_MONTH_END), leftoverLabel(p))
    }

    @Test fun notSalariedSubtractsAllCountedUpcomingWithoutMonthEndWording() {
        val p = projectLeftover(listOf(500_000, 200_000), reserved, today, Currency.SAR, salaried = false, nextPayday = "2026-10-28", unreconciled = false)
        assertEquals(LeftoverMode.FROM_WHAT_YOU_HAVE, p.mode)
        assertEquals(165_000L, p.countedMinor)
        assertEquals(535_000L, p.leftoverMinor)
        assertNull(p.until)
        assertFalse("آخر الشهر" in leftoverLabel(p))
        assertEquals(LeftoverMode.FROM_WHAT_YOU_HAVE, projectLeftover(listOf(1), reserved, today, Currency.SAR, true, null, false).mode, "بمرتب من غير يوم مرتب معروف")
        val later = projectLeftover(listOf(500_000), reserved, "2026-10-16", Currency.SAR, salaried = false, nextPayday = null, unreconciled = false)
        assertEquals(140_000L, later.countedMinor, "الحدث اللي عدّى ما بيتطرحش")
        assertEquals(360_000L, later.leftoverMinor)
    }

    @Test fun unknownBalanceMeansNotAvailableAndMismatchMeansApproximate() {
        val unknown = projectLeftover(listOf(500_000, null), reserved, today, Currency.SAR, true, "2026-10-28", false)
        assertNull(unknown.onHandMinor)
        assertNull(unknown.leftoverMinor)
        assertEquals(uiText(TextKey.NOT_AVAILABLE), leftoverAmountText(unknown, Currency.SAR))
        assertNull(projectLeftover(emptyList(), reserved, today, Currency.SAR, true, "2026-10-28", false).leftoverMinor, "مفيش محافظ ⇒ مش صفر")
        assertTrue(projectLeftover(listOf(1), reserved, today, Currency.SAR, true, "2026-10-28", true).approximate)
        val short = projectLeftover(listOf(50_000), reserved, today, Currency.SAR, false, null, false)
        assertTrue(short.leftoverMinor!! < 0, "السالب بيبان")
    }

    @Test fun salariedMeansAnActiveJobSource() {
        fun src(kind: IncomeSourceKind, ended: String? = null) = IncomeSource("s", "جهة وهمية", "x", kind, Currency.SAR, "2025-01-01", ended)
        assertTrue(isSalaried(listOf(src(IncomeSourceKind.JOB)), today))
        assertFalse(isSalaried(listOf(src(IncomeSourceKind.JOB, ended = "2026-01-01")), today))
        assertFalse(isSalaried(listOf(src(IncomeSourceKind.CLIENT)), today))
        assertFalse(isSalaried(emptyList(), today))
    }
}
