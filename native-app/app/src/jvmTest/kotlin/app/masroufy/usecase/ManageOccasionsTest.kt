package app.masroufy.usecase

import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.LifeEventKind
import app.masroufy.core.LocalMoment
import app.masroufy.core.OccasionError
import app.masroufy.core.OccasionKind
import app.masroufy.core.Period
import app.masroufy.core.Person
import app.masroufy.core.Wallet
import app.masroufy.core.formatMoney
import app.masroufy.core.isLockSafe
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAlertInbox
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryAlertReceipts
import app.masroufy.memory.MemoryAlertSettings
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryLifeEventRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryOccasionRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryUsualHours
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** مناسبات الشخص من التسجيل للتنبيه (OVERRIDES §64 + §61) — كل الأسامي والمبالغ مخترعة. */
class ManageOccasionsTest {
    private val txns = MemoryTransactionRepository()
    private val people = MemoryPersonRepository(listOf(Person("p-1", "سامي الوهمي"), Person("p-2", "قديم وهمي", archived = true)))
    private val events = MemoryLifeEventRepository()
    private val links = MemoryEventLinkRepository()
    private val occasionsRepo = MemoryOccasionRepository()
    private val ids = SequentialIdGenerator()
    private val clock = FixedClock("2026-10-01T10:00:00.000Z")
    private val occasions = ManageOccasions(ManageOccasionsDeps(occasionsRepo, people, events, links, txns, ids, clock))
    private val manageEvents = ManageEvents(ManageEventsDeps(events, links, txns, people, ids, clock))
    private val gifts = EventGifts(
        EventGiftsDeps(
            events, links, txns, MemoryWalletRepository(listOf(Wallet("w-cash", "كاش وهمي", Currency.SAR, "cash", 0, "2026-01-01"))), people,
            MemoryUnitOfWork(listOf(txns, links)), ids, clock, MemoryCategoryRepository(),
        ),
    )
    private val gather = GatherAlerts(
        GatherAlertsDeps(
            LoadDues(
                LoadDuesDeps(
                    MemoryRoscaRepository(), MemoryRoscaEntryRepository(), MemoryInstallmentPlanRepository(), MemoryInstallmentPaymentRepository(),
                    MemoryDebtTermsRepository(), people, MemoryObligationRepository(), MemorySettlementRepository(), MemoryRecurringRepository(), txns,
                ),
            ),
            occasions = occasions,
        ),
    )
    private val inbox = MemoryAlertInbox()
    private val engine = RunAlertEngine(AlertEngineDeps(MemoryAlertSettings(), MemoryAlertInteractions(), MemoryUsualHours(), MemoryAlertReceipts(), inbox, clock))
    private val period = Period("2026-09", "2026-09-28", "2026-10-27", 30)

    private suspend fun runDay(day: String) = engine.run(gather.gather(AlertGatherInput(day, period, Currency.SAR)), LocalMoment(day, 14))

    @Test fun addValidatesAndListsByNearest() = runBlocking<Unit> {
        occasions.add(OccasionInput("p-1", OccasionKind.BIRTHDAY, month = 12, day = 1))
        occasions.add(OccasionInput(null, OccasionKind.BIRTHDAY, month = 10, day = 20))
        occasions.add(OccasionInput("p-2", OccasionKind.BIRTHDAY, month = 10, day = 5))
        assertFailsWith<OccasionError> { occasions.add(OccasionInput("p-404", OccasionKind.BIRTHDAY, month = 1, day = 1)) }
        assertFailsWith<OccasionError> { occasions.add(OccasionInput("p-1", OccasionKind.OTHER, month = 1, day = 1, yearly = false)) }
        assertFailsWith<OccasionError> { occasions.add(OccasionInput("p-1", OccasionKind.OTHER, month = 2, day = 30)) }
        val up = occasions.upcoming("2026-10-04")
        assertEquals(listOf("2026-10-20", "2026-12-01"), up.map { it.date }, "الشخص المؤرشف بيتشال")
        assertEquals(listOf(null), occasions.forPerson(null, "2026-10-04").map { it.personName })
        val one = occasions.add(OccasionInput("p-1", OccasionKind.OTHER, label = "تخرّج", month = 9, day = 1, year = 2026, yearly = false))
        assertFalse(occasions.upcoming("2026-10-04").any { it.occasion.id == one.id }, "مرة واحدة وعدّت")
        occasions.remove(one.id)
        assertFailsWith<OccasionError> { occasions.remove(one.id) }
    }

    @Test fun ownWeddingReminderIsYearlyWithHisLeadAndOnlyForHisEvent() = runBlocking<Unit> {
        val mine = manageEvents.create(EventInput("فرحي الوهمي", LifeEventKind.WEDDING, "2024-10-20", mine = true))
        val theirs = manageEvents.create(EventInput("فرح وهمي لحد", LifeEventKind.WEDDING, "2024-10-20", mine = false, hostPersonId = "p-1"))
        val o = occasions.remindOwnEvent(mine.id, 20)
        assertEquals(OccasionKind.WEDDING_ANNIVERSARY, o.kind)
        assertEquals(20, o.leadDays)
        assertEquals(o.id, occasions.remindOwnEvent(mine.id, 25).id, "الطلب تاني بيعدّل نفس التذكير")
        assertEquals(1, occasionsRepo.listAll().size)
        assertFailsWith<OccasionError> { occasions.remindOwnEvent(theirs.id, 10) }
        // قبلها بـ25 يوم (مدته هو) ⇒ تنبيه، وفي الأسبوع العادي كان هيبقى لسه
        val candidates = gather.gather(AlertGatherInput("2026-09-26", period, Currency.SAR))
        val c = candidates.single { it.kind.group == AlertGroup.OCCASIONS }
        assertEquals("ذكرى «فرحي الوهمي»: كمان 24 يوم", c.title)
    }

    @Test fun birthdayFlowsThroughTheEngineWithReciprocityInsideAndNothingOnTheLockScreen() = runBlocking<Unit> {
        val mine = manageEvents.create(EventInput("فرحي الوهمي", LifeEventKind.WEDDING, "2025-05-01", mine = true))
        gifts.recordGifts(mine.id, Direction.IN, "w-cash", "2025-05-01", listOf(GiftEntry("p-1", 200_000)))
        occasions.add(OccasionInput("p-1", OccasionKind.BIRTHDAY, month = 10, day = 10))
        assertTrue(gather.gather(AlertGatherInput("2026-10-02", period, Currency.SAR)).none { it.kind.group == AlertGroup.OCCASIONS }, "8 أيام ⇒ لسه")
        val soon = runDay("2026-10-04")
        val entry = inbox.listAll().single { it.kind.group == AlertGroup.OCCASIONS }
        assertEquals(AlertKind.OCCASION_SOON, entry.kind)
        assertTrue(entry.title.contains("سامي الوهمي"), entry.title)
        assertTrue(entry.body.contains("نقّطك ${formatMoney(200_000, Currency.SAR)} في «فرحي الوهمي»"), entry.body)
        for (p in soon.posts) {
            assertTrue(isLockSafe(p.notice.title) && isLockSafe(p.notice.body))
            assertFalse(p.notice.body.contains("سامي") || p.notice.body.contains("فرح"), p.notice.body)
        }
        assertTrue(runDay("2026-10-05").posts.isEmpty(), "نفس الدرجة ما بتتبعتش تاني")
        runDay("2026-10-10")
        assertEquals(AlertKind.OCCASION_TODAY, inbox.listAll().single { it.kind.group == AlertGroup.OCCASIONS }.kind, "سطر واحد للموضوع")
        runDay("2026-10-11")
        assertTrue(inbox.listAll().none { it.kind.group == AlertGroup.OCCASIONS }, "عدّت ⇒ اختفت لحد السنة الجاية")
    }
}
