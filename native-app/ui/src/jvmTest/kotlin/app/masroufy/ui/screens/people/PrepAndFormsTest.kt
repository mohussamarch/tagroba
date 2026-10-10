package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.LifeEventKind
import app.masroufy.core.OccasionKind
import app.masroufy.core.Person
import app.masroufy.core.PrepItem
import app.masroufy.core.PrepItemStatus
import app.masroufy.core.PrepSummary
import app.masroufy.core.TextKey
import app.masroufy.ui.text.t
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «التجهيزات» ولوحتين الإدخال («مناسبة» و«النقوط») من غير رسم. */
class PrepAndFormsTest {
    @BeforeTest fun before() = resetTexts()

    @AfterTest fun after() = resetTexts()

    private val usage: (Long, Long) -> Long = { planned, spent -> spent * 1000 / planned }

    private fun item(id: String, planned: Long?, order: Int) = PrepItem(id, "ev-1", "بند وهمي $id", planned, order, false, "c")

    private fun detail(kind: LifeEventKind = LifeEventKind.WEDDING, date: String = "2026-10-20", links: Boolean = false) = runBlocking {
        val w = EventsWorld(
            listOf(event("ev-1", "فرح وهمي", date, kind)),
            if (links) listOf(link("ev-1", "t-1"), link("ev-1", "t-2").copy(prepItemId = "i-1")) else emptyList(),
            listOf(txn("t-1", 12_000), txn("t-2", 30_000)),
        )
        w.events.detail("ev-1")
    }

    @Test
    fun noSummaryMeansUnavailableAndNoItemsShowsTheSuggestions() {
        val ui = prepUi(detail(), null, listOf("قاعة", "ضيافة"), Currency.SAR, TODAY, usage)
        assertNull(ui.spent, "الملخص ما اتحمّلش ⇒ «غير متاح» مش صفر")
        assertNull(ui.planned)
        assertNull(ui.block)
        assertEquals(t(UiKey.EVENT_PREP_NO_ITEMS), ui.plannedSub)
        assertNull(ui.leftLine)
        assertEquals(listOf("قاعة", "ضيافة"), ui.suggestions)
    }

    @Test
    fun itemsCarryTheirShareOfThePlanAndTheLooseSpending() {
        val summary = PrepSummary(
            items = listOf(PrepItemStatus(item("i-1", 20_000, 0), 30_000), PrepItemStatus(item("i-2", null, 1), 0)),
            plannedTotalMinor = 20_000, unpricedCount = 1, spentMinor = 42_000, unassignedSpentMinor = 12_000, otherCurrencyCount = 0, remainingCount = 2,
        )
        val ui = prepUi(detail(links = true), summary, listOf("قاعة"), Currency.SAR, TODAY, usage)
        val (over, unpriced) = ui.items
        assertTrue(over.over, "صرفت أكتر من المخطط")
        assertEquals(1500, over.usedTenth)
        assertTrue(over.sub.endsWith(t(UiKey.EVENT_PREP_OVER)))
        assertNull(unpriced.usedTenth, "بند من غير مبلغ ⇒ مفيش شريط")
        assertTrue(unpriced.sub.contains(t(UiKey.EVENT_PREP_NO_AMOUNT)))
        assertEquals(20_000, ui.planned!!.minor)
        assertEquals(42_000, ui.spent!!.minor)
        assertEquals(listOf("t-1"), ui.loose.map { it.txnId }, "المصروف اللي مش على بند بس")
        assertTrue(ui.suggestions.isEmpty())
        assertTrue(ui.looseLine != null)
        assertNull(ui.otherCurrencyLine)
    }

    @Test
    fun condolenceAndPastEventsBlockThePreparations() {
        assertEquals(PrepBlock.CONDOLENCE, prepUi(detail(LifeEventKind.CONDOLENCE), null, emptyList(), Currency.SAR, TODAY, usage).block)
        assertEquals(PrepBlock.PAST, prepUi(detail(date = "2026-09-01"), null, emptyList(), Currency.SAR, TODAY, usage).block)
    }

    @Test
    fun occasionChecksFollowThePrototypeOrder() {
        assertEquals(OccasionCheck.Bad(t(UiKey.OCC_NEED_LABEL)), checkDraft(OccasionDraft(OccasionKind.OTHER), "p-1"))
        assertEquals(OccasionCheck.Bad(t(UiKey.OCC_NEED_MONTH)), checkDraft(OccasionDraft(), "p-1"))
        assertEquals(OccasionCheck.Bad(t(UiKey.OCC_NEED_DAY)), checkDraft(OccasionDraft(month = 3), "p-1"))
        assertEquals(OccasionCheck.Bad(t(UiKey.PPL_ONCE_NEEDS_YEAR)), checkDraft(OccasionDraft(month = 3, day = "5", yearly = false), "p-1"))
        val ok = assertIs<OccasionCheck.Ok>(checkDraft(OccasionDraft(month = 3, day = "١٥"), "p-1"), "الأرقام العربي في الخانة بتتقري")
        assertEquals(15, ok.input.day)
        assertFalse(OccasionDraft().withKind(OccasionKind.WEDDING).yearly, "الفرح مرة واحدة")
        assertEquals(60, OccasionDraft().withLead(500).lead)
    }

    @Test
    fun occasionPreviewSaysWhenTheReminderComes() {
        assertEquals(OccasionPreview(t(UiKey.OCC_PICK), t(UiKey.OCC_REMIND_PLAIN)), occasionPreview(OccasionDraft(), TODAY))
        val later = occasionPreview(OccasionDraft(month = 10, day = "18"), TODAY)
        assertEquals(t(UiKey.OCC_REMIND_USUAL, leadName(7)), later.remind, "بعد 9 أيام والتذكير قبلها بأسبوع")
        assertEquals(t(UiKey.OCC_REMIND_NOW), occasionPreview(OccasionDraft(month = 10, day = "18", lead = 14), TODAY).remind)
    }

    @Test
    fun giftLinesAreCheckedBeforeAnyWrite() {
        val people = listOf(Person("p-1", "سارة وهمية"))
        assertEquals(GiftPlan.Bad(t(UiKey.NUQOOT_NEED_ONE)), checkGiftLines(listOf(GiftLine()), people, Currency.SAR))
        assertEquals(GiftPlan.Bad(t(UiKey.NUQOOT_FILL_ROWS)), checkGiftLines(listOf(GiftLine("سارة وهمية", "0")), people, Currency.SAR))
        assertEquals(GiftPlan.Bad(t(UiKey.NUQOOT_DUP)), checkGiftLines(listOf(GiftLine("علي", "10"), GiftLine("علي", "20")), people, Currency.SAR))
        val ready = assertIs<GiftPlan.Ready>(checkGiftLines(listOf(GiftLine(" سارة وهمية ", "100"), GiftLine("اسم جديد", "50.5")), people, Currency.SAR))
        assertEquals(listOf(10_000L, 5_050L), ready.amounts)
        assertEquals(mapOf("سارة وهمية" to "p-1"), ready.known, "الاسم الموجود بيتربط بالشخص — الجديد بيتضاف وقت الحفظ")
    }
}
