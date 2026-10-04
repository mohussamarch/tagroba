package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventError
import app.masroufy.core.EventRole
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryLifeEventRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
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

/** الأحداث والنقوط على مستودعات الذاكرة (OVERRIDES §64) — كل الأسامي والمبالغ مخترعة. */
class ManageEventsTest {
    private fun txn(id: String, dir: Direction, amount: Long) = Transaction(
        id = id, occurredAt = "2026-05-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    private val txns = MemoryTransactionRepository(
        listOf(txn("hall", Direction.OUT, 3_000_000), txn("bank-in", Direction.IN, 150_000), txn("bank-out", Direction.OUT, 80_000), txn("loan", Direction.OUT, 50_000)),
    )
    private val wallets = MemoryWalletRepository(listOf(Wallet("w-cash", "كاش وهمي", Currency.SAR, "cash", 0, "2026-01-01"), Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01")))
    private val people = MemoryPersonRepository(listOf(Person("p-1", "سامي الوهمي"), Person("p-2", "خالد الوهمي"), Person("p-3", "منى الوهمية"), Person("p-long", "اسم وهمي طويل ".repeat(10))))
    private val events = MemoryLifeEventRepository()
    private val links = MemoryEventLinkRepository()
    private val plans = MemoryInstallmentPlanRepository(listOf(InstallmentPlan("ip-1", "تقسيط وهمي", "جهة وهمية", InstallmentKind.PURCHASE_PLAN, Currency.SAR, 500_000, 500_000, 50_000, 1, "2026-05-01")))
    private val installmentPayments = MemoryInstallmentPaymentRepository()
    private val ids = SequentialIdGenerator()
    private val clock = FixedClock("2026-05-02T10:00:00.000Z")
    private val manage = ManageEvents(ManageEventsDeps(events, links, txns, people, ids, clock))
    private val gifts = EventGifts(
        EventGiftsDeps(events, links, txns, wallets, people, MemoryUnitOfWork(listOf(txns, links)), ids, clock, installmentPayments = installmentPayments, plans = plans),
    )
    private val installments = ManageInstallments(
        ManageInstallmentsDeps(
            plans, installmentPayments, MemoryRoscaEntryRepository(), MemoryDebtTermsRepository(), MemoryObligationRepository(), txns,
            MemoryUnitOfWork(listOf(txns, installmentPayments)), ids, clock, MemoryCategoryRepository(), eventLinks = links,
        ),
    )

    private suspend fun myWedding() = manage.create(EventInput("فرحي الوهمي", LifeEventKind.WEDDING, "2026-05-01", mine = true))

    private suspend fun brothersWedding() = manage.create(EventInput("فرح أخو خالد", LifeEventKind.WEDDING, "2026-08-01", mine = false, hostPersonId = "p-2"))

    @Test fun createRejectsDuplicatesAndHostOnOwnEvent() = runBlocking<Unit> {
        myWedding()
        assertFailsWith<EventError> { manage.create(EventInput("  فرحي   الوهمي ", LifeEventKind.WEDDING, "2026-06-01", mine = true)) }
        assertFailsWith<EventError> { manage.create(EventInput("حدث تاني", LifeEventKind.OTHER, "2026-06-01", mine = true, hostPersonId = "p-1")) }
        assertFailsWith<EventError> { manage.create(EventInput("حدث تالت", LifeEventKind.OTHER, "2026-06-01", mine = false, hostPersonId = "p-404")) }
        val trip = manage.create(EventInput("سفرة وهمية", LifeEventKind.TRAVEL, "2026-07-01", mine = true))
        manage.setArchived(trip.id, true)
        assertEquals(listOf("فرحي الوهمي"), manage.list().active.map { it.event.name })
        assertEquals(listOf("سفرة وهمية"), manage.list().archived.map { it.event.name })
    }

    @Test fun cashGiftsAreOneTransactionPerNameAndNeverReduceSpending() = runBlocking<Unit> {
        val ev = myWedding()
        gifts.link(ev.id, "hall", EventRole.SPEND)
        val before = manage.detail(ev.id).summary.totals.single()
        val recorded = gifts.recordGifts(ev.id, Direction.IN, "w-cash", "2026-05-01", listOf(GiftEntry("p-1", 200_000), GiftEntry("p-3", 50_000)))
        assertEquals(2, recorded.size, "عملية لكل اسم")
        for (r in recorded) {
            assertEquals(EconomicKind.EVENT_GIFT, r.transaction.economicKind)
            assertEquals("w-cash", r.transaction.walletId)
            assertEquals(Direction.IN, r.transaction.observedDirection)
            assertEquals(EventRole.GIFT_IN, r.link.role)
        }
        assertEquals(listOf("سامي الوهمي", "منى الوهمية"), recorded.map { it.transaction.rawMerchantName })
        val after = manage.detail(ev.id)
        val total = after.summary.totals.single()
        assertEquals(before.spentMinor, total.spentMinor, "🔒 النقوط ما بتنقّصش المصروف")
        assertEquals(3_000_000, total.spentMinor)
        assertEquals(250_000, total.giftsInMinor)
        assertEquals(3, after.transactions.size)
        assertEquals("سامي الوهمي", after.transactions.first { it.link.personId == "p-1" }.personName)
    }

    @Test fun giftsReceivedOnlyOnYourOwnEventAndWhatYouGaveShowsOnTheirs() = runBlocking<Unit> {
        val theirs = brothersWedding()
        assertFailsWith<EventError> { gifts.recordGifts(theirs.id, Direction.IN, "w-cash", "2026-08-01", listOf(GiftEntry("p-2", 10_000))) }
        val out = gifts.recordGifts(theirs.id, Direction.OUT, "w-cash", "2026-08-01", listOf(GiftEntry("p-2", 100_000))).single()
        assertEquals(EconomicKind.SUPPORT_GIFT, out.transaction.economicKind)
        assertEquals(Direction.OUT, out.transaction.observedDirection)
        val s = manage.detail(theirs.id).summary
        assertNull(s.totals.single().giftsInMinor, "حدث حد تاني ⇒ مفيش رقم «جالك»")
        assertEquals(100_000, s.totals.single().giftsOutMinor)
        assertEquals("خالد الوهمي", manage.detail(theirs.id).hostName)
        // حدثك اللي عليه نقوط جاتلك ما يتحوّلش لحدث حد تاني
        val mine = myWedding()
        gifts.recordGifts(mine.id, Direction.IN, "w-cash", "2026-05-01", listOf(GiftEntry("p-1", 20_000)))
        assertFailsWith<EventError> { manage.update(mine.id, EventInput("فرحي الوهمي", LifeEventKind.WEDDING, "2026-05-01", mine = false, hostPersonId = "p-1")) }
    }

    @Test fun recordingIsAllOrNothingAndChecksEntries() = runBlocking<Unit> {
        val ev = myWedding()
        val count = txns.listByDateRange("2026-01-01", "2026-12-31").size
        assertFailsWith<EventError> { gifts.recordGifts(ev.id, Direction.IN, "w-cash", "2026-05-01", emptyList()) }
        assertFailsWith<EventError> { gifts.recordGifts(ev.id, Direction.IN, "w-cash", "2026-05-01", listOf(GiftEntry("p-1", 0))) }
        assertFailsWith<EventError> { gifts.recordGifts(ev.id, Direction.IN, "w-cash", "2026-05-01", listOf(GiftEntry("p-1", 10), GiftEntry("p-1", 20))) }
        assertFailsWith<EventError> { gifts.recordGifts(ev.id, Direction.IN, "w-cash", "2026-05-01", listOf(GiftEntry("p-404", 10))) }
        // المحفظة مش موجودة ⇒ ولا عملية ولا ربط
        assertFailsWith<IllegalArgumentException> { gifts.recordGifts(ev.id, Direction.IN, "w-404", "2026-05-01", listOf(GiftEntry("p-1", 10))) }
        // التاريخ الغلط ⇒ ولا عملية
        assertFailsWith<IllegalArgumentException> { gifts.recordGifts(ev.id, Direction.IN, "w-cash", "2026-02-30", listOf(GiftEntry("p-1", 10), GiftEntry("p-3", 20))) }
        // التانية بتقع (اسمها أطول من حد اسم العملية) بعد ما الأولى اتكتبت ⇒ الأولى كمان بتترجع
        assertFailsWith<IllegalArgumentException> { gifts.recordGifts(ev.id, Direction.IN, "w-cash", "2026-05-01", listOf(GiftEntry("p-1", 10), GiftEntry("p-long", 20))) }
        assertEquals(count, txns.listByDateRange("2026-01-01", "2026-12-31").size)
        assertTrue(links.all().isEmpty())
    }

    @Test fun existingBankTransactionLinksOnceAndUnlinkAsksAgain() = runBlocking<Unit> {
        val ev = myWedding()
        val other = manage.create(EventInput("عزا وهمي", LifeEventKind.CONDOLENCE, "2026-06-01", mine = false))
        gifts.link(ev.id, "bank-in", EventRole.GIFT_IN, "p-1")
        assertEquals(EconomicKind.EVENT_GIFT, txns.findByIds(listOf("bank-in")).single().economicKind)
        val e = assertFailsWith<EventError> { gifts.link(other.id, "bank-in", EventRole.SPEND) }
        assertEquals(uiText(TextKey.EVENT_TXN_ALREADY_LINKED), e.message)
        assertFailsWith<EventError>("نقطة من غير شخص") { gifts.link(ev.id, "bank-out", EventRole.GIFT_OUT) }
        assertFailsWith<EventError>("مصروف لازم طالع") { gifts.link(ev.id, "bank-in", EventRole.SPEND) }
        gifts.unlink(ev.id, "bank-in")
        val back = txns.findByIds(listOf("bank-in")).single()
        assertEquals(EconomicKind.UNCLASSIFIED, back.economicKind)
        assertEquals(ReviewState.NEEDS_REVIEW, back.reviewState)
        gifts.link(other.id, "bank-out", EventRole.GIFT_OUT, "p-2")
        assertFailsWith<EventError> { gifts.unlink(ev.id, "bank-out") }
    }

    @Test fun aGiftTransactionCannotAlsoBeADueAndViceVersa() = runBlocking<Unit> {
        val ev = myWedding()
        val theirs = brothersWedding()
        gifts.link(theirs.id, "bank-out", EventRole.GIFT_OUT, "p-2")
        assertFailsWith<DueLinkError> { installments.link("ip-1", "bank-out") }
        installments.link("ip-1", "loan")
        assertFailsWith<EventError> { gifts.link(theirs.id, "loan", EventRole.GIFT_OUT, "p-2") }
        gifts.link(ev.id, "loan", EventRole.SPEND)
        assertEquals(EconomicKind.PURCHASE, txns.findByIds(listOf("loan")).single().economicKind, "المصروف على الحدث ما بيغيّرش نوع العملية (نوع القسط زي ما هو)")
    }

    @Test fun personProfileShowsGiftBadges() = runBlocking<Unit> {
        val mine = myWedding()
        val theirs = brothersWedding()
        gifts.recordGifts(mine.id, Direction.IN, "w-cash", "2026-05-01", listOf(GiftEntry("p-2", 200_000)))
        gifts.recordGifts(theirs.id, Direction.OUT, "w-cash", "2026-08-01", listOf(GiftEntry("p-2", 100_000)))
        val badges = manage.personBadges("p-2")
        assertEquals(listOf("فرح أخو خالد" to Direction.OUT, "فرحي الوهمي" to Direction.IN), badges.map { it.eventName to it.direction })
        assertEquals(listOf(100_000L, 200_000L), badges.map { it.amountMinor })
        assertTrue(manage.personBadges("p-3").isEmpty())
    }
}
