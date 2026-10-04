package app.masroufy.data

import app.masroufy.core.CALENDAR_BACKUP_GROUPS
import app.masroufy.core.CalendarItemType
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventLink
import app.masroufy.core.EventRole
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.PrepItem
import app.masroufy.core.Reservation
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.emptyBackupData
import app.masroufy.core.eventLinkId
import app.masroufy.core.reservationId
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** المبالغ المحسوبة وتجهيزات الأحداث (§65) في التخزين والنسخة الشاملة — بيانات مخترعة. */
class CalendarBackupTest {
    private val wedding = LifeEvent("ev-1", "فرح وهمي", "فرح وهمي", LifeEventKind.WEDDING, "2026-12-01", mine = true, createdAt = "c")
    private val hall = PrepItem("prep-1", "ev-1", "القاعة", 2_000_000, 1, false, "2026-10-04T00:00:00.000Z")
    private val dress = PrepItem("prep-2", "ev-1", "الفستان", null, 2, true, "c")
    private val counted = Reservation(reservationId(CalendarItemType.INSTALLMENT, "ip-1", "2026-10-25"), CalendarItemType.INSTALLMENT, "ip-1", "2026-10-25", 100_000, Currency.SAR, "c")
    private val spend = Transaction(
        id = "t-1", occurredAt = "2026-10-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.PURCHASE,
        economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = 500_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c",
    )
    private val link = EventLink(eventLinkId("t-1"), "ev-1", "t-1", EventRole.SPEND, null, "c", 40, prepItemId = "prep-1")

    private fun <T> roundTrip(codec: DocCodec<T>, value: T): Doc {
        val d = codec.toStore(value)
        assertEquals(value, codec.decode(d), codec.group)
        return d
    }

    @Test fun `المستندات بتتكتب وتتقرا والمبلغ الفاضي ما بيتكتبش`() {
        assertEquals("installment", roundTrip(CalendarCodecs.reservations, counted)["itemType"])
        assertEquals("rsv-installment-ip-1-2026-10-25", CalendarCodecs.reservations.id(counted))
        assertEquals(2_000_000L, roundTrip(CalendarCodecs.eventPrep, hall)["plannedMinor"])
        assertFalse("plannedMinor" in roundTrip(CalendarCodecs.eventPrep, dress), "من غير مبلغ = الحقل مش مكتوب (مش صفر)")
        assertEquals("prep-1", roundTrip(EventCodecs.eventLinks, link)["prepItemId"])
        assertFalse("prepItemId" in roundTrip(EventCodecs.eventLinks, link.copy(prepItemId = null)), "الربط القديم هو هو")
        val bad = CalendarCodecs.reservations.toStore(counted) + ("itemType" to "lottery")
        assertTrue("lottery" in assertFailsWith<DocumentError> { CalendarCodecs.reservations.decode(bad) }.message!!)
    }

    private fun account(withCalendar: Boolean) = emptyBackupData().also {
        it.getValue("transactions") += LedgerCodecs.transactions.toStore(spend)
        it.getValue("lifeEvents") += EventCodecs.lifeEvents.toStore(wedding)
        it.getValue("eventLinks") += EventCodecs.eventLinks.toStore(if (withCalendar) link else link.copy(prepItemId = null))
        if (withCalendar) {
            it.getValue("eventPrep") += CalendarCodecs.eventPrep.toStore(hall)
            it.getValue("eventPrep") += CalendarCodecs.eventPrep.toStore(dress)
            it.getValue("reservations") += CalendarCodecs.reservations.toStore(counted)
        }
    }

    @Test fun `بتسافر في النسخة الشاملة وبترجع بنفس البصمة`() = runBlocking<Unit> {
        val source = account(withCalendar = true)
        val file = FullBackup(MemoryFullBackup(source)).create("2026-10-04T00:00:00.000Z")
        val text = file.toJsonText()
        for (g in CALENDAR_BACKUP_GROUPS) assertTrue("\"$g\"" in text, g)
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        val outcome = restore.apply(restore.plan(text).file)
        assertEquals(listOf(1, 2), CALENDAR_BACKUP_GROUPS.map { outcome.added[it] })
        for (g in CALENDAR_BACKUP_GROUPS + "eventLinks") assertEquals(source.getValue(g), target.read().getValue(g), g)
        assertEquals(file.checksum, FullBackup(target).create("2026-10-04T00:00:00.000Z").checksum)
    }

    @Test fun `من غيرهم المجموعات ما بتتكتبش والمكسور بيترفض`() = runBlocking<Unit> {
        val text = FullBackup(MemoryFullBackup(account(withCalendar = false))).create("2026-10-04T00:00:00.000Z").toJsonText()
        for (g in CALENDAR_BACKUP_GROUPS) assertFalse("\"$g\"" in text, g)
        fun rejects(why: String, change: (Map<String, MutableList<Map<String, Any?>>>) -> Unit) {
            val data = account(withCalendar = true).also(change)
            assertFailsWith<IllegalArgumentException>(why) { runBlocking { FullBackup(MemoryFullBackup(data)).create("2026-10-04T00:00:00.000Z") } }
        }
        rejects("بند حدث مش موجود") { it.getValue("eventPrep")[1] = it.getValue("eventPrep")[1] + ("eventId" to "ev-404") }
        rejects("ربط على بند مش موجود") { it.getValue("eventPrep").removeAt(0) }
        rejects("حجز بصفر") { it.getValue("reservations")[0] = it.getValue("reservations")[0] + ("amountMinor" to 0L) }
        rejects("نوع سطر مش معروف") { it.getValue("reservations")[0] = it.getValue("reservations")[0] + ("itemType" to "lottery") }
        rejects("تاريخ غلط") { it.getValue("reservations")[0] = it.getValue("reservations")[0] + ("occurrenceDate" to "2026-02-30") }
        rejects("بند بمبلغ صفر") { it.getValue("eventPrep")[0] = it.getValue("eventPrep")[0] + ("plannedMinor" to 0L) }
        rejects("آخر ميعاد مشروع غلط") {
            it.getValue("projects") += AssetProjectCodecs.projects.toStore(app.masroufy.core.Project("pr-1", "مشروع", "مشروع", false, "c")) + ("deadline" to "bad")
        }
    }
}
