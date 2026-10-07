package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EVENT_BACKUP_GROUPS
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventLink
import app.masroufy.core.EventRole
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Occasion
import app.masroufy.core.OccasionKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.emptyBackupData
import app.masroufy.core.eventLinkId
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** الأحداث ومناسبات الشخص (§64) في التخزين والنسخة الشاملة — بيانات مخترعة. */
class EventBackupTest {
    private val wedding = LifeEvent("ev-1", "فرح وهمي", "فرح وهمي", LifeEventKind.WEDDING, "2026-05-01", mine = true, createdAt = "2026-04-01T00:00:00.000Z")
    private val theirs = LifeEvent("ev-2", "عزا وهمي", "عزا وهمي", LifeEventKind.CONDOLENCE, "2026-06-01", mine = false, hostPersonId = "p-1", archived = true, createdAt = "c")
    private val link = EventLink(eventLinkId("t-1"), "ev-1", "t-1", EventRole.GIFT_IN, "p-1", "2026-05-02T00:00:00.000Z")
    private val birthday = Occasion("occ-1", "p-1", OccasionKind.BIRTHDAY, null, 2, 29, null, true, null, null, "c")
    private val reminder = Occasion("occ-event-ev-1", null, OccasionKind.WEDDING_ANNIVERSARY, null, 5, 1, 2026, true, 14, "ev-1", "c")
    private val gift = Transaction(
        id = "t-1", occurredAt = "2026-05-01", datePrecision = "day", sourceOrder = 9_000_000, economicKind = EconomicKind.EVENT_GIFT,
        economicKindConfirmed = true, observedDirection = Direction.IN, amountMinor = 200_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = true, createdAt = "c", updatedAt = "c",
    )

    private fun <T> roundTrip(codec: DocCodec<T>, value: T): Doc {
        val d = codec.toStore(value)
        assertEquals(value, codec.decode(d), codec.group)
        return d
    }

    @Test
    fun `المستندات بتتكتب وتتقرا والفاضي ما بيتكتبش`() {
        assertFalse("hostPersonId" in roundTrip(EventCodecs.lifeEvents, wedding))
        assertEquals("p-1", roundTrip(EventCodecs.lifeEvents, theirs)["hostPersonId"])
        assertEquals("gift_in", roundTrip(EventCodecs.eventLinks, link)["role"])
        assertFalse("personId" in roundTrip(EventCodecs.eventLinks, link.copy(role = EventRole.SPEND, personId = null)))
        assertEquals(setOf("id", "personId", "kind", "month", "day", "yearly", "createdAt"), roundTrip(EventCodecs.occasions, birthday).keys)
        assertEquals(setOf("id", "kind", "month", "day", "year", "yearly", "leadDays", "sourceEventId", "createdAt"), roundTrip(EventCodecs.occasions, reminder).keys)
        val bad = EventCodecs.eventLinks.toStore(link) + ("role" to "bribe")
        assertTrue("bribe" in assertFailsWith<DocumentError> { EventCodecs.eventLinks.decode(bad) }.message!!)
    }

    private fun account(withEvents: Boolean) = emptyBackupData().also {
        it.getValue("people") += ReferenceCodecs.people.toStore(Person("p-1", "سامي الوهمي"))
        it.getValue("transactions") += LedgerCodecs.transactions.toStore(gift)
        if (withEvents) {
            it.getValue("lifeEvents") += EventCodecs.lifeEvents.toStore(wedding)
            it.getValue("lifeEvents") += EventCodecs.lifeEvents.toStore(theirs)
            it.getValue("eventLinks") += EventCodecs.eventLinks.toStore(link)
            it.getValue("occasions") += EventCodecs.occasions.toStore(birthday)
            it.getValue("occasions") += EventCodecs.occasions.toStore(reminder)
        }
    }

    @Test
    fun `الأحداث بتسافر في النسخة الشاملة وبترجع بنفس البصمة`() = runBlocking<Unit> {
        val source = account(withEvents = true)
        val file = FullBackup(MemoryFullBackup(source)).create("2026-06-02T00:00:00.000Z")
        val text = file.toJsonText()
        for (g in EVENT_BACKUP_GROUPS) assertTrue("\"$g\"" in text, g)
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        val outcome = restore.apply(restore.plan(text).file)
        assertEquals(listOf(2, 1, 2), EVENT_BACKUP_GROUPS.map { outcome.added[it] })
        for (g in EVENT_BACKUP_GROUPS) assertEquals(source.getValue(g), target.read().getValue(g), g)
        assertEquals(file.checksum, FullBackup(target).create("2026-06-02T00:00:00.000Z").checksum)
        // نفس النسخة تاني ⇒ مفيش تكرار
        val again = restore.apply(restore.plan(text).file)
        assertEquals(listOf(0, 0, 0), EVENT_BACKUP_GROUPS.map { again.added[it] ?: 0 })
    }

    @Test
    fun `من غير أحداث المجموعات ما بتتكتبش والعلاقة المكسورة بتترفض`() = runBlocking<Unit> {
        val text = FullBackup(MemoryFullBackup(account(withEvents = false))).create("2026-06-02T00:00:00.000Z").toJsonText()
        for (g in EVENT_BACKUP_GROUPS) assertFalse("\"$g\"" in text, g)
        // الحدث اتشال والمناسبة اللي بتشاور عليه كمان ⇒ ربط النقطة لوحده هو اللي مكسور
        val orphan = account(withEvents = true).also { it.getValue("lifeEvents").clear(); it.getValue("occasions").removeAt(1) }
        val e = assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(orphan)).create("2026-06-02T00:00:00.000Z") }
        assertTrue("eventLinks" in (e.message ?: ""), e.message)
        val badRole = account(withEvents = true).also { it.getValue("eventLinks")[0] = it.getValue("eventLinks")[0] + ("role" to "loan") }
        assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(badRole)).create("2026-06-02T00:00:00.000Z") }
        val badMonth = account(withEvents = true).also { it.getValue("occasions")[0] = it.getValue("occasions")[0] + ("month" to "May") }
        assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(badMonth)).create("2026-06-02T00:00:00.000Z") }
    }

    @Test
    fun `نسبة المصروف بتتخزن وبتسافر والربط القديم من غيرها العملية كلها`() = runBlocking<Unit> {
        val spend = EventLink(eventLinkId("t-2"), "ev-1", "t-2", EventRole.SPEND, null, "c", sharePercent = 40)
        assertEquals(40L, roundTrip(EventCodecs.eventLinks, spend)["sharePercent"])
        assertEquals(100L, roundTrip(EventCodecs.eventLinks, link)["sharePercent"], "بتتكتب دايمًا — حتى الكاملة")
        val old = EventCodecs.eventLinks.toStore(link) - "sharePercent"
        assertEquals(100, EventCodecs.eventLinks.decode(old).sharePercent, "مستند قديم من غير الحقل = 100")
        val out = gift.copy(id = "t-2", observedDirection = Direction.OUT, economicKind = EconomicKind.PURCHASE)
        val source = account(withEvents = true).also {
            it.getValue("transactions") += LedgerCodecs.transactions.toStore(out)
            it.getValue("eventLinks") += EventCodecs.eventLinks.toStore(spend)
            it.getValue("eventLinks")[0] = it.getValue("eventLinks")[0] - "sharePercent"
        }
        val text = FullBackup(MemoryFullBackup(source)).create("2026-06-02T00:00:00.000Z").toJsonText()
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        restore.apply(restore.plan(text).file)
        val back = target.read().getValue("eventLinks").map(EventCodecs.eventLinks::decode).associateBy { it.transactionId }
        assertEquals(mapOf("t-1" to 100, "t-2" to 40), back.mapValues { it.value.sharePercent })
        for (bad in listOf(0L, 101L)) {
            val wrong = account(withEvents = true).also { it.getValue("eventLinks")[0] = it.getValue("eventLinks")[0] + ("role" to "spend") + ("personId" to null) + ("sharePercent" to bad) }
            assertFailsWith<IllegalArgumentException>("$bad") { FullBackup(MemoryFullBackup(wrong)).create("2026-06-02T00:00:00.000Z") }
        }
        val partGift = account(withEvents = true).also { it.getValue("eventLinks")[0] = it.getValue("eventLinks")[0] + ("sharePercent" to 50L) }
        assertFailsWith<IllegalArgumentException>("النقطة العملية كلها") { FullBackup(MemoryFullBackup(partGift)).create("2026-06-02T00:00:00.000Z") }
    }
}
