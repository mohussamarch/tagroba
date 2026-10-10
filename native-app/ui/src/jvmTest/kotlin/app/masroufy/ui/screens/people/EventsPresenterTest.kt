package app.masroufy.ui.screens.people

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventRole
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Person
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.AmountTone
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «الأحداث» و«تفاصيل الحدث» من `ManageEvents.list`/`detail` (مستودعات الذاكرة): الترتيب (الجاي بالأقرب ثم اللي فات) · «أقرب حدث» ·
 * المصروف بنصيب الحدث · النقوط اللي جاتلك في حدثك بس · مفيش «صافي» · قفل التجهيزات للعزاء · ربط المصروف بحدث تاني.
 */
class EventsPresenterTest {
    @BeforeTest fun start() = resetTexts()

    @AfterTest fun end() = resetTexts()

    private fun world() = EventsWorld(
        events = listOf(
            event("ev-1", "فرح وهمي", "2026-10-20"),
            event("ev-2", "عزاء وهمي", "2026-09-01", LifeEventKind.CONDOLENCE, mine = false, host = "p-2"),
            event("ev-3", "سفر وهمي", "2026-10-12", LifeEventKind.TRAVEL),
            event("ev-4", "خطوبة قديمة", "2025-01-01", LifeEventKind.ENGAGEMENT, archived = true),
        ),
        links = listOf(
            link("ev-1", "t-1", share = 50),
            link("ev-1", "t-2", EventRole.GIFT_IN, person = "p-1"),
            link("ev-2", "t-3", EventRole.GIFT_OUT, person = "p-2"),
        ),
        txns = listOf(
            txn("t-1", 40_000, date = "2026-10-03"),
            txn("t-2", 50_000, Direction.IN, date = "2026-10-04", kind = EconomicKind.EVENT_GIFT),
            txn("t-3", 30_000, date = "2026-09-01", kind = EconomicKind.SUPPORT_GIFT),
        ),
        people = listOf(Person("p-1", "سارة وهمية"), Person("p-2", "خالد وهمي")),
    )

    @Test fun upcomingFirstByNearestThenPastAndArchivedApart() = runBlocking<Unit> {
        val w = world()
        val ui = eventsUi(w.events.list(), mapOf("p-2" to "خالد وهمي"), TODAY)
        assertEquals(listOf("ev-3", "ev-1", "ev-2"), ui.active.map { it.id })
        assertEquals(listOf("ev-4"), ui.archived.map { it.id })
        val next = assertNotNull(ui.next)
        assertEquals("ev-3", next.id)
        assertEquals("بعد " + sentenceNumber(3) + " أيام", next.rel)
    }

    @Test fun statsShowSpendingAndGiftsApartNeverANet() = runBlocking<Unit> {
        val ui = eventsUi(world().events.list(), mapOf("p-2" to "خالد وهمي"), TODAY)
        val wedding = ui.active.single { it.id == "ev-1" }
        assertEquals(listOf("المصروف", "النقوط التي جاءتك"), wedding.stats.map { it.label })
        assertEquals(listOf(MoneyLine(20_000, app.masroufy.core.Currency.SAR)), wedding.stats[0].amounts, "نصيب الحدث (٥٠٪) مش العملية كلها")
        assertEquals("عملية واحدة", wedding.stats[0].sub)
        assertEquals(AmountTone.INCOME, wedding.stats[1].tone)
        val travel = ui.active.single { it.id == "ev-3" }
        assertEquals("لم يُربط بعد", travel.stats.single().emptyText, "مفيش مصروف ⇒ نص مش صفر")
        assertTrue(travel.stats.single().amounts.isEmpty())
        val condolence = ui.active.single { it.id == "ev-2" }
        assertEquals("لخالد وهمي", condolence.owner)
        assertEquals(listOf("المصروف", "ما قدّمته"), condolence.stats.map { it.label }, "حدث حد تاني: اللي إنت ادّيته بس — مفيش «جاتلك»")
        assertEquals(listOf(MoneyLine(30_000, app.masroufy.core.Currency.SAR)), condolence.stats[1].amounts)
    }

    @Test fun detailRowsCarryTheShareAndTheGiftGiversName() = runBlocking<Unit> {
        val w = world()
        val d = eventDetailUi(w.events.detail("ev-1"), mapOf("w-1" to "الكاش"), emptyList(), null, TODAY)
        assertEquals(listOf(MoneyLine(20_000, app.masroufy.core.Currency.SAR)), d.spend)
        val row = d.spends.single()
        assertEquals(20_000, row.amountMinor)
        assertEquals(sentenceNumber(50) + "٪ من 400.00", row.share)
        assertTrue(row.sub.endsWith("، الكاش"), row.sub)
        assertEquals("سارة وهمية", d.giftRows.single().name)
        assertTrue(d.hasGiftsIn)
        assertTrue(d.canPrep)
        assertEquals("لا بنود بعد", d.prepLine)
        assertNull(d.noPrep)
        assertEquals(setOf("t-1", "t-2"), d.linkedTxnIds)
        assertEquals("النقوط للمعلومية فقط — لا تُطرح من المصروف.", d.heroNote)
    }

    @Test fun condolenceHasNoPreparationsAndShowsWhatYouGave() = runBlocking<Unit> {
        val d = eventDetailUi(world().events.detail("ev-2"), emptyMap(), emptyList(), null, TODAY)
        assertEquals("لا تجهيزات للعزاء.", d.noPrep)
        assertEquals("ما قدّمته", d.giftLabel)
        assertNull(d.spend, "مفيش مصروف مربوط ⇒ نص بدل الرقم")
        assertEquals("صاحبه: خالد وهمي", d.owner)
        assertTrue(!d.hasGiftsIn)
    }

    @Test fun spendingLinkedToAnotherEventShowsLockedWithItsName() = runBlocking<Unit> {
        val w = world()
        val elsewhere = linkedElsewhere(w.events, "ev-3")
        assertEquals(mapOf("t-1" to "فرح وهمي", "t-2" to "فرح وهمي", "t-3" to "عزاء وهمي"), elsewhere)
        assertEquals(mapOf("t-3" to "عزاء وهمي"), otherEventLinks(listOf(w.events.detail("ev-1"), w.events.detail("ev-2")), "ev-1"), "حدثك نفسه مش «حدث تاني»")
        val t1 = txn("t-1", 40_000, date = "2026-10-03")
        assertEquals("مربوطة بـ«فرح وهمي» — فكّها من صفحة العملية أولًا", spendRowLine(t1, false, "فرح وهمي", emptyMap()))
        assertTrue(spendRowLine(t1, true, null, emptyMap()).endsWith("مربوطة بهذا الحدث"))
        assertTrue(spendRowLine(t1, false, null, mapOf("w-1" to "الكاش")).endsWith("الكاش"))
    }

    @Test fun recentSpendingIsNewestFirstAndSearchesByNameOrDigits() {
        val list = listOf(
            txn("a", 12_550, date = "2026-10-01", merchant = "مطعم الفرح"),
            txn("b", 40_000, date = "2026-10-05", merchant = "قاعة أفراح"),
            txn("c", 9_000, Direction.IN, date = "2026-10-06", merchant = "راتب"),
        )
        assertEquals(listOf("b", "a"), filterRecent(list, "", inbound = false).map { it.id }, "المصروف بس، والأحدث الأول")
        assertEquals(listOf("a"), filterRecent(list, "مطعم", inbound = false).map { it.id })
        assertEquals(listOf("a"), filterRecent(list, "125", inbound = false).map { it.id }, "بالأرقام")
        assertEquals(listOf("a"), filterRecent(list, "١٢٥", inbound = false).map { it.id }, "الأرقام العربي بتتحول")
        assertEquals(listOf("c"), filterRecent(list, "", inbound = true).map { it.id })
    }
}
