package app.masroufy.ui.screens.dues

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.DueFlow
import app.masroufy.core.DueSource
import app.masroufy.core.Period
import app.masroufy.core.dayMonth
import app.masroufy.core.monthName
import app.masroufy.core.sentenceNumber
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * خانة «المستحقات» (لوحة `Dues`): نتيجة `LoadDues.load` ⇒ حالة الشاشة. كل رقم بيتنقل زي ما هو، ومجموع «لك» و«عليك» «غير متاح» (null)
 * لأن حالة الاستخدام ما بتجمعهمش — مش صفر.
 */
class DuesPanelModelTest {
    private val period = Period("2026-09", "2026-09-28", "2026-10-27", 30)

    @BeforeTest fun arabic() = useArabic()

    @AfterTest fun reset() = resetTexts()

    @Test fun totalsArePassedThroughLineByLineAndTheSumsAreUnavailable() {
        val view = duesView(totals(receivable = 150_000, roscaSaved = 0, loans = 80_000, custody = 20_000, installments = 0, roscaOwed = 5_000))
        val ui = duesPanelUi(view, DuesCounts(2, 1, 0, 0), period, TODAY, Currency.SAR)
        assertEquals(listOf("ديون لك" to 150_000L), ui.forYou.map { it.label to it.minor }, "سطر الجمعية المدّخر بيختفي لو صفر")
        assertEquals(listOf("ديون عليك" to 80_000L, "أمانات عندك" to 20_000L, "عليك للجمعيات" to 5_000L), ui.onYou.map { it.label to it.minor })
        assertNull(ui.forYouTotal, "مجموع «لك» مش محسوب في حالة الاستخدام ⇒ غير متاح")
        assertNull(ui.onYouTotal)
        assertEquals(monthName(10), ui.monthLabel, "الشهر باسم آخر يوم في الفترة")
        assertFalse(ui.empty)
    }

    @Test fun agendaRowsOpenTheRightScreenAndSayWhichWayTheMoneyGoes() {
        val view = duesView(
            totals(),
            agenda = listOf(
                dueItem(DueSource.DEBT, "o1", "سلفة فهد", "2026-09-20", 30_000, DueFlow.RECEIVE),
                dueItem(DueSource.RECURRING, "r1", "اشتراك النادي", "2026-10-11", 9_900, DueFlow.PAY),
                dueItem(DueSource.ROSCA_PAYOUT, "j1", "جمعية العائلة", "2026-11-01", 500_000, DueFlow.RECEIVE),
            ),
            pay = 9_900, receive = 530_000,
        )
        val ui = duesPanelUi(view, DuesCounts(1, 1, 0, 1), period, TODAY, Currency.SAR)
        val (debt, sub, rosca) = ui.agenda
        assertEquals(Chip.OVERDUE, debt.chip)
        assertEquals("متأخر", debt.chipText)
        assertEquals(DuesTarget.Debts(DebtSide.FOR_YOU), debt.target, "دين بتستلمه ⇒ «لك»")
        assertEquals("ستستلم", debt.dirText)
        assertEquals(dayMonth("2026-09-20") + "، منذ " + sentenceNumber(19) + " يومًا", debt.sub)
        assertEquals(Chip.SOON, sub.chip)
        assertEquals(DuesTarget.Subscriptions, sub.target)
        assertEquals("ستدفع", sub.dirText)
        assertEquals(dayMonth("2026-11-01") + "، وفيه دورك", rosca.sub, "قبض الدور بيقول «وفيه دورك» مكان «بعد …»")
        assertEquals(DuesTarget.Roscas, rosca.target)
        assertEquals(9_900L, ui.monthPayMinor)
        assertEquals(530_000L, ui.monthReceiveMinor)
    }

    @Test fun tilesCountItemsAndTheFinancingLineHidesAtZero() {
        val ui = duesPanelUi(duesView(totals(), financing = 0), DuesCounts(3, 2, 1, 4), period, TODAY, Currency.SAR)
        assertEquals(listOf(3, 2, 1, 4), ui.tiles.map { it.count })
        assertEquals(listOf(DuesTarget.Debts(DebtSide.ALL), DuesTarget.Roscas, DuesTarget.Installments, DuesTarget.Subscriptions), ui.tiles.map { it.target })
        assertNull(ui.financingMinor)
        val withProfit = duesPanelUi(duesView(totals(), financing = 12_345), DuesCounts(0, 0, 1, 0), period, TODAY, Currency.SAR)
        assertEquals(12_345L, withProfit.financingMinor)
    }

    @Test fun nothingAtAllIsTheEmptyState() {
        val ui = duesPanelUi(duesView(totals()), DuesCounts(0, 0, 0, 0), period, TODAY, Currency.SAR)
        assertTrue(ui.empty)
        assertTrue(ui.agenda.isEmpty())
    }

    @Test fun egyptWordingComesFromTheEgyptianTable() {
        useArabic(ArabicVariant.EGYPTIAN)
        val view = duesView(totals(receivable = 100), agenda = listOf(dueItem(DueSource.INSTALLMENT, "p1", "قسط", "2026-10-30", 100, DueFlow.PAY)))
        val ui = duesPanelUi(view, DuesCounts(1, 0, 1, 0), period, TODAY, Currency.EGP)
        assertEquals("ديون ليك", ui.forYou.single().label)
        assertEquals("جاي", ui.agenda.single().chipText)
        assertEquals("هتدفع", ui.agenda.single().dirText)
        assertEquals(DuesTarget.Installments, ui.agenda.single().target)
    }
}
