package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** التقويم والحساب من الفلوس والملخص الذكي (OVERRIDES §65) — القلب النقي. كل الأسامي والمبالغ مخترعة. */
class CalendarTest {
    private val today = "2026-10-04"

    private fun due(source: DueSource, id: String, date: String, amount: Long, flow: DueFlow = DueFlow.PAY, currency: Currency = Currency.SAR) =
        DueItem(source, id, "بند وهمي $id", date, amount, currency, flow, dueStatusOf(date, today))

    private val dues = listOf(
        due(DueSource.RECURRING, "r-1", "2026-10-10", 5_000),
        due(DueSource.INSTALLMENT, "ip-1", "2026-10-25", 100_000),
        due(DueSource.ROSCA_PAYOUT, "a-rosca", "2026-10-25", 1_000_000, DueFlow.RECEIVE),
        due(DueSource.DEBT, "o-1", "2026-10-26", 30_000),
        due(DueSource.INSTALLMENT, "ip-2", "2026-09-01", 100_000),
    )
    private val friendWedding = LifeEvent("ev-1", "فرح صاحب وهمي", "x", LifeEventKind.WEDDING, "2026-10-15", mine = false, createdAt = "c")
    private val oldEvent = LifeEvent("ev-2", "حدث مؤرشف", "y", LifeEventKind.OTHER, "2026-10-16", mine = true, archived = true, createdAt = "c")
    private val birthday = Occasion("occ-1", "p-1", OccasionKind.BIRTHDAY, month = 10, day = 12, yearly = true, createdAt = "c")
    private val archivedPersonBirthday = Occasion("occ-2", "p-gone", OccasionKind.BIRTHDAY, month = 10, day = 13, yearly = true, createdAt = "c")
    private val openYear = ZakatYear("2026-10-22", "2025-10-30", "2026-10-22", Currency.SAR, "c")
    private val paidYear = ZakatYear("2026-10-23", "2025-10-31", "2026-10-23", Currency.SAR, "c", closedAt = "c", lines = listOf(ZakatYearLine(ZakatLineKind.CASH, 100, 0)))

    private fun sources(islamic: Boolean = true) = CalendarSources(
        dues = dues,
        projects = listOf(
            Project("pr-1", "تجديد وهمي", "x", false, "c", deadline = "2026-10-20"),
            Project("pr-2", "مؤرشف", "y", true, "c", deadline = "2026-10-21"),
            Project("pr-3", "من غير ميعاد", "z", false, "c"),
        ),
        events = listOf(friendWedding, oldEvent),
        occasions = listOf(birthday, archivedPersonBirthday),
        personNames = mapOf("p-1" to "سامي الوهمي"),
        payday = 28,
        zakat = listOf(openYear to null, paidYear to 0L),
        publicOccasions = listOf(PublicOccasion(PublicOccasionKind.EID_AL_ADHA, "2026-10-30", 1448)),
        islamicVisible = islamic,
    )

    private fun october(islamic: Boolean = true, reservations: List<Reservation> = emptyList()) =
        buildCalendar("2026-10-01", "2026-10-31", today, Currency.SAR, sources(islamic), reservations)

    @Test fun everySourceInRangeInOrderWithDaysLeftAndUnknownAmounts() {
        val items = october()
        assertEquals(
            listOf(
                "2026-10-10 recurring", "2026-10-12 occasion", "2026-10-15 event", "2026-10-20 project", "2026-10-22 zakat",
                "2026-10-25 installment", "2026-10-25 rosca_payout", "2026-10-26 debt", "2026-10-28 payday", "2026-10-30 public_occasion",
            ),
            items.map { "${it.date} ${it.type.wire}" },
        )
        assertEquals(6, items.first().daysLeft)
        val byType = items.associateBy { it.type }
        assertNull(byType.getValue(CalendarItemType.EVENT).amountMinor, "الحدث من غير تجهيزات مسعّرة ⇒ من غير مبلغ")
        assertNull(byType.getValue(CalendarItemType.ZAKAT).amountMinor, "سنة لسه ما اتثبتتش ⇒ مش معروف")
        assertNull(byType.getValue(CalendarItemType.PAYDAY).amountMinor, "المرتب ما بنخترعلوش رقم")
        assertEquals(DueFlow.RECEIVE, byType.getValue(CalendarItemType.PAYDAY).flow)
        assertNull(byType.getValue(CalendarItemType.OCCASION).flow)
        assertTrue(byType.getValue(CalendarItemType.PUBLIC_OCCASION).approximate)
        assertTrue("تجديد وهمي" in byType.getValue(CalendarItemType.PROJECT).title)
        assertEquals("عيد ميلاد سامي الوهمي", byType.getValue(CalendarItemType.OCCASION).title)
    }

    @Test fun islamicContentHiddenDropsZakatAndPublicOccasions() {
        val types = october(islamic = false).map { it.type }.toSet()
        assertTrue(CalendarItemType.ZAKAT !in types && CalendarItemType.PUBLIC_OCCASION !in types)
        assertTrue(CalendarItemType.PAYDAY in types && CalendarItemType.EVENT in types)
    }

    @Test fun eventAmountComesFromAFullyPricedPlanOnly() {
        val priced = buildCalendar("2026-10-01", "2026-10-31", today, Currency.SAR, sources().copy(eventPlannedMinor = mapOf("ev-1" to 70_000)))
        assertEquals(70_000L, priced.first { it.type == CalendarItemType.EVENT }.amountMinor)
        val items = listOf(PrepItem("a", "ev-1", "قاعة", 50_000, 1, false, "c"), PrepItem("b", "ev-1", "لبس", null, 2, false, "c"))
        assertNull(eventCalendarAmount(items), "بند من غير مبلغ ⇒ الحدث من غير مبلغ")
        assertEquals(60_000L, eventCalendarAmount(items.map { it.copy(plannedMinor = 30_000) }))
        assertNull(eventCalendarAmount(emptyList()))
    }

    @Test fun paydaysOccasionDatesAndNextPayday() {
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31"), paydaysIn("2026-01-01", "2026-03-31", 31))
        assertEquals("2026-10-28", nextPaydayAfter("2026-10-27", 28))
        assertEquals("2026-11-28", nextPaydayAfter("2026-10-28", 28), "يوم المرتب نفسه ⇒ الجاي الشهر اللي بعده")
        val future = birthday.copy(year = 2027)
        assertEquals(listOf("2027-10-12"), occasionDatesIn(future, "2026-01-01", "2027-12-31"), "السنوية ما بتتعدّش قبل سنتها")
        val once = birthday.copy(yearly = false, year = 2026)
        assertEquals(listOf("2026-10-12"), occasionDatesIn(once, "2026-01-01", "2027-12-31"))
    }

    @Test fun countingNeverChangesTheItemAmountAndShowsXOfY() {
        val item = october().first { it.type == CalendarItemType.INSTALLMENT }
        val reservation = Reservation(reservationIdOf(item), item.type, item.sourceId, item.date, 40_000, Currency.SAR, "c")
        val counted = october(reservations = listOf(reservation)).first { it.type == CalendarItemType.INSTALLMENT }
        assertEquals(100_000L, counted.amountMinor, "مبلغ السطر هو هو")
        assertEquals(40_000L, counted.reservedMinor)
        assertEquals(ReservationState.PARTIAL, reservationState(counted))
        assertTrue(reservationText(counted).contains(formatMoney(100_000, Currency.SAR)))
        assertEquals(uiText(TextKey.RESERVATION_NONE), reservationText(item))
        assertEquals(ReservationState.COVERED, reservationState(counted.copy(reservedMinor = 100_000)))
        assertEquals(ReservationState.RESERVED, reservationState(counted.copy(amountMinor = null)))
        assertEquals("rsv-installment-ip-1-2026-10-25", reservationIdOf(item))
    }

    @Test fun countedAmountDefaultsToTheKnownAmountAndNeverInventsOne() {
        val items = october().associateBy { it.type }
        assertEquals(100_000L, countedAmount(items.getValue(CalendarItemType.INSTALLMENT), null, today))
        assertEquals(7_000L, countedAmount(items.getValue(CalendarItemType.INSTALLMENT), 7_000, today))
        assertFailsWith<ReservationError>("حدث من غير مبلغ ومحدش كتب") { countedAmount(items.getValue(CalendarItemType.EVENT), null, today) }
        assertEquals(25_000L, countedAmount(items.getValue(CalendarItemType.EVENT), 25_000, today))
        assertFailsWith<ReservationError>("المرتب فلوس جاية") { countedAmount(items.getValue(CalendarItemType.PAYDAY), 1_000, today) }
        assertFailsWith<ReservationError>("قبض الجمعية فلوس جاية") { countedAmount(items.getValue(CalendarItemType.ROSCA_PAYOUT), null, today) }
        assertFailsWith<ReservationError> { countedAmount(items.getValue(CalendarItemType.INSTALLMENT), 0, today) }
        assertFailsWith<ReservationError>("ميعاد عدّى") { countedAmount(items.getValue(CalendarItemType.INSTALLMENT), null, "2026-10-26") }
    }

    @Test fun schoolStartIsAnEventTheUserAddsNotAPublicOccasion() {
        // رد المالك §65: بداية الدراسة المستخدم بيحطها بإيده كحدث نوعه «دخول مدرسة» — بتظهر سطر حدث عادي
        val school = LifeEvent("ev-s", "دخول مدرسة وهمي", "s", LifeEventKind.SCHOOL, "2026-10-18", mine = true, createdAt = "c")
        val items = buildCalendar("2026-10-01", "2026-10-31", today, Currency.SAR, sources().copy(events = listOf(school)))
        val line = items.single { it.sourceId == "ev-s" }
        assertEquals(CalendarItemType.EVENT to "2026-10-18", line.type to line.date)
        assertEquals(listOf("eid_al_adha-1448"), items.filter { it.type == CalendarItemType.PUBLIC_OCCASION }.map { it.sourceId })
    }

    @Test fun publicOccasionsFromUmmAlQuraAndNoSchoolStart() {
        val year = publicOccasions("2026-01-01", "2026-12-31", "SA")
        assertEquals(
            listOf("ramadan_start 2026-02-18 1447", "eid_al_fitr 2026-03-20 1447", "eid_al_adha 2026-05-27 1447"),
            year.map { "${it.kind.wire} ${it.date} ${it.hijriYear}" },
        )
        assertEquals(year, publicOccasions("2026-01-01", "2026-12-31", "eg"))
        assertTrue(publicOccasions("2026-01-01", "2026-12-31", "US").isEmpty())
        assertTrue(publicOccasions("2026-01-01", "2026-12-31", null).isEmpty())
        assertTrue(PublicOccasionKind.entries.none { "school" in it.wire }, "بداية الدراسة مش مناسبة عامة — المستخدم بيحطها بإيده كحدث")
        assertTrue(publicOccasions("2026-08-01", "2026-09-30", "SA").isEmpty(), "موسم الدراسة مافيهوش أي مناسبة عامة")
    }
}
