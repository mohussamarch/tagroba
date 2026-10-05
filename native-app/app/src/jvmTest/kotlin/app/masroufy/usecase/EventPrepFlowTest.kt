package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventRole
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Person
import app.masroufy.core.PrepError
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryLifeEventRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryPrepItemRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** تجهيزات الحدث الجاي على مستودعات الذاكرة (OVERRIDES §65) — كل الأسامي والمبالغ مخترعة. */
class EventPrepFlowTest {
    private val today = "2026-10-04"

    private fun txn(id: String, dir: Direction, amount: Long) = Transaction(
        id = id, occurredAt = "2026-10-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    private val txns = MemoryTransactionRepository(listOf(txn("hall", Direction.OUT, 3_000_000), txn("dress", Direction.OUT, 80_000), txn("gift", Direction.OUT, 50_000)))
    private val events = MemoryLifeEventRepository(
        listOf(
            LifeEvent("ev-1", "فرحي الوهمي", "a", LifeEventKind.WEDDING, "2026-12-01", mine = true, createdAt = "c"),
            LifeEvent("ev-2", "عزا وهمي", "b", LifeEventKind.CONDOLENCE, "2026-10-20", mine = false, createdAt = "c"),
            LifeEvent("ev-3", "سفرة وهمية", "c", LifeEventKind.TRAVEL, "2026-11-01", mine = true, createdAt = "c"),
        ),
    )
    private val links = MemoryEventLinkRepository()
    private val prepRepo = MemoryPrepItemRepository()
    private val ids = SequentialIdGenerator()
    private val clock = FixedClock("2026-10-04T10:00:00.000Z")
    private val prep = ManageEventPrep(ManageEventPrepDeps(events, links, prepRepo, txns, MemoryUnitOfWork(listOf(links, prepRepo)), ids, clock))
    private val gifts = EventGifts(
        EventGiftsDeps(
            events, links, txns, MemoryWalletRepository(listOf(Wallet("w-cash", "كاش وهمي", Currency.SAR, "cash", 0, "2026-01-01"))),
            MemoryPersonRepository(listOf(Person("p-1", "سامي الوهمي"))), MemoryUnitOfWork(listOf(txns, links)), ids, clock, MemoryCategoryRepository(),
        ),
    )

    @Test fun suggestionsAreSavedOnlyWhenChosenAndWithoutAmounts() = runBlocking<Unit> {
        val names = prep.suggestions("ev-1", today)
        assertTrue(names.isNotEmpty() && prepRepo.listAll().isEmpty(), "الاقتراح ما بيتحفظش لوحده")
        val saved = prep.addMany("ev-1", names)
        assertEquals(names, saved.map { it.name })
        assertTrue(saved.all { it.plannedMinor == null }, "البنود المقترحة من غير مبالغ")
        assertEquals((1..names.size).toList(), saved.map { it.order })
        assertTrue(prep.suggestions("ev-1", today).isEmpty(), "عنده بنود ⇒ مفيش اقتراح")
        assertTrue(prep.suggestions("ev-2", today).isEmpty())
        assertFailsWith<PrepError>("العزا مالوش تجهيزات") { prep.add("ev-2", "أكل", 10_000) }
        val before = prepRepo.listAll().size
        assertFailsWith<PrepError>("اسم متكرر ⇒ ولا حاجة تتكتب") { prep.addMany("ev-3", listOf("التذاكر", "السكن", "التذاكر")) }
        assertEquals(before, prepRepo.listAll().size)
        assertFailsWith<PrepError> { prep.add("ev-3", "التأشيرة", 0) }
    }

    @Test fun spendingOnAnItemUsesTheEventShareAndRemovingTheItemKeepsItOnTheEvent() = runBlocking<Unit> {
        val hall = prep.add("ev-1", "القاعة", 2_000_000)
        val dress = prep.add("ev-1", "الفستان")
        gifts.link("ev-1", "hall", EventRole.SPEND, sharePercent = 40)
        gifts.link("ev-1", "dress", EventRole.SPEND)
        prep.assignSpend("ev-1", "hall", hall.id)
        prep.assignSpend("ev-1", "dress", dress.id)
        val s = prep.summary("ev-1", Currency.SAR)
        assertEquals(listOf(1_200_000L, 80_000L), s.items.map { it.spentMinor }, "40% من القاعة مش العملية كلها")
        assertEquals(2_000_000L, s.plannedTotalMinor)
        assertEquals(1, s.unpricedCount)
        assertEquals(0L, s.unassignedSpentMinor)
        prep.update(dress.id, "فستان الفرح", 90_000)
        prep.setDone(hall.id, true)
        assertEquals(1, prep.summary("ev-1", Currency.SAR).remainingCount)
        prep.remove(hall.id)
        assertNull(links.all().single { it.transactionId == "hall" }.prepItemId, "الربط بيفضل على الحدث من غير بند")
        assertEquals(1_200_000L, prep.summary("ev-1", Currency.SAR).unassignedSpentMinor)
        prep.assignSpend("ev-1", "dress", null)
        assertEquals(1_280_000L, prep.summary("ev-1", Currency.SAR).unassignedSpentMinor)
    }

    @Test fun onlySpendingFromTheSameEventGoesOnAnItem() = runBlocking<Unit> {
        val ticket = prep.add("ev-3", "التذاكر")
        val hall = prep.add("ev-1", "القاعة")
        gifts.link("ev-1", "hall", EventRole.SPEND)
        assertFailsWith<PrepError>("بند حدث تاني") { prep.assignSpend("ev-1", "hall", ticket.id) }
        gifts.link("ev-1", "gift", EventRole.GIFT_OUT, personId = "p-1")
        assertFailsWith<PrepError>("النقطة مش تجهيز") { prep.assignSpend("ev-1", "gift", hall.id) }
        assertFailsWith<PrepError> { prep.assignSpend("ev-1", "hall", "prep-404") }
    }
}
