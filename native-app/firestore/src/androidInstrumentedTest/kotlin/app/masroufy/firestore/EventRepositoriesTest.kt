package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventRole
import app.masroufy.core.LifeEventKind
import app.masroufy.core.OccasionKind
import app.masroufy.core.Person
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.EventGifts
import app.masroufy.usecase.EventGiftsDeps
import app.masroufy.usecase.EventInput
import app.masroufy.usecase.GiftEntry
import app.masroufy.usecase.ManageEvents
import app.masroufy.usecase.ManageEventsDeps
import app.masroufy.usecase.ManageOccasions
import app.masroufy.usecase.ManageOccasionsDeps
import app.masroufy.usecase.OccasionInput
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import app.masroufy.core.GiftCategories
import app.masroufy.usecase.AddTransaction
import app.masroufy.usecase.AddTransactionDeps
import app.masroufy.usecase.NewTransactionInput
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الأحداث والنقوط ومناسبات الشخص (OVERRIDES §64) بحالات الاستخدام نفسها على Firestore Emulator. أسماء وأرقام مخترعة.
 * التشغيل زي `RemainingRepositoriesTest` (HANDOVER: Firestore Emulator على 8088 + محاكي أندرويد).
 */
@RunWith(AndroidJUnit4::class)
class EventRepositoriesTest {
    private fun space() = FirestoreSpace.forUser(Emulator.firestore(), "kt-" + java.util.UUID.randomUUID())

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(90_000) { block() } }

    @Test fun eventsGiftsBadgesAndOccasionsWorkOnFirestore() = run {
        val s = space()
        val c = FirestoreContainer(s)
        c.people.save(Person("p-1", "سامي الوهمي"))
        c.people.save(Person("p-2", "خالد الوهمي"))
        c.wallets.save(Wallet("w-cash", "كاش وهمي", Currency.SAR, "cash", 0, "2026-01-01"))
        val ids = SequentialIdGenerator()
        val clock = FixedClock("2026-10-01T10:00:00.000Z")
        val events = ManageEvents(ManageEventsDeps(c.lifeEvents, c.eventLinks, c.transactions, c.people, ids, clock))
        val gifts = EventGifts(EventGiftsDeps(c.lifeEvents, c.eventLinks, c.transactions, c.wallets, c.people, PassthroughUnitOfWork(), ids, clock, c.categories))
        val mine = events.create(EventInput("فرحي الوهمي", LifeEventKind.WEDDING, "2025-10-10", mine = true))
        val theirs = events.create(EventInput("فرح وهمي لخالد", LifeEventKind.WEDDING, "2026-08-01", mine = false, hostPersonId = "p-2"))
        val recorded = gifts.recordGifts(mine.id, Direction.IN, "w-cash", "2025-10-10", listOf(GiftEntry("p-1", 200_000), GiftEntry("p-2", 50_000)))
        assertEquals(setOf(EconomicKind.EVENT_GIFT), c.transactions.findByIds(recorded.map { it.transaction.id }).map { it.economicKind }.toSet())
        gifts.recordGifts(theirs.id, Direction.OUT, "w-cash", "2026-08-01", listOf(GiftEntry("p-2", 100_000)))
        assertEquals(2, c.eventLinks.listByEvent(mine.id).size)
        assertEquals(250_000, events.detail(mine.id).summary.totals.single().giftsInMinor)
        assertNull(events.detail(theirs.id).summary.totals.single().giftsInMinor)
        assertEquals(listOf(Direction.OUT, Direction.IN), events.personBadges("p-2").map { it.direction })
        // النقطة بتاخد «هدايا › نقوط» على فايربيز كمان (§64)
        assertEquals(setOf(GiftCategories.EVENT_GIFTS), c.transactions.findByIds(recorded.map { it.transaction.id }).map { it.categoryId }.toSet())
        assertTrue(c.categories.listAll().map { it.id }.containsAll(listOf(GiftCategories.ROOT, GiftCategories.EVENT_GIFTS)))
        // مصروف بنسبة: النسبة بتتخزن في المستند وبترجع زي ما هي
        val hall = AddTransaction(AddTransactionDeps(c.transactions, c.wallets, ids, clock))
            .add(NewTransactionInput(amountMinor = 1_000_001, occurredAt = "2025-10-09", walletId = "w-cash", economicKind = EconomicKind.PURCHASE, merchantName = "قاعة وهمية"))
        gifts.link(mine.id, hall.id, EventRole.SPEND, sharePercent = 25)
        assertEquals(25, FirestoreEventLinkRepository(s).listByTransactionIds(listOf(hall.id)).single().sharePercent)
        assertEquals(250_000, events.detail(mine.id).summary.totals.single().spentMinor, "25% من 10,000.01 ⇒ 2,500.00")
        // فك النقطة: الربط اتمسح والنوع رجع يتسأل
        gifts.unlink(mine.id, recorded[1].transaction.id)
        assertTrue(c.eventLinks.listByTransactionIds(listOf(recorded[1].transaction.id)).isEmpty())
        assertEquals(EconomicKind.UNCLASSIFIED, c.transactions.findByIds(listOf(recorded[1].transaction.id)).single().economicKind)
        // تعديل الحدث لحد تاني ⇒ صاحب الحدث بيتمسح من المستند (الكتابة merge)
        val other = events.create(EventInput("عزا وهمي", LifeEventKind.CONDOLENCE, "2026-06-01", mine = false, hostPersonId = "p-1"))
        events.update(other.id, EventInput("عزا وهمي", LifeEventKind.CONDOLENCE, "2026-06-01", mine = true))
        assertNull(FirestoreLifeEventRepository(s).listAll().single { it.id == other.id }.hostPersonId)

        val occasions = ManageOccasions(ManageOccasionsDeps(c.occasions, c.people, c.lifeEvents, c.eventLinks, c.transactions, ids, clock))
        val birthday = occasions.add(OccasionInput("p-1", OccasionKind.BIRTHDAY, month = 10, day = 6))
        val reminder = occasions.remindOwnEvent(mine.id, 14)
        val alerts = occasions.alertCandidates("2026-10-04")
        assertEquals(2, alerts.size)
        assertTrue(alerts.any { it.body.contains("«فرحي الوهمي»") && it.title.contains("سامي الوهمي") }, alerts.toString())
        occasions.remove(birthday.id)
        assertEquals(listOf(reminder.id), FirestoreOccasionRepository(s).listAll().map { it.id })
    }
}
