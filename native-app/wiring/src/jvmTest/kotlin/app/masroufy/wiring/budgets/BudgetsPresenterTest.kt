package app.masroufy.wiring.budgets

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.TextKey
import app.masroufy.core.periodForDate
import app.masroufy.core.uiText
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.screens.budgets.TotalCard
import app.masroufy.ui.screens.budgets.Tone
import app.masroufy.ui.screens.budgets.loadBudgets
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «الميزانيات» (خانة جوه «العمليات»): من نتايج حالات الاستخدام (`LoadBudgetScreen` · `LoadCalendar` · `LoadLeftover`) لحالة الشاشة. */
class BudgetsPresenterTest {
    private val period = periodForDate(TODAY, 28)

    @BeforeTest fun texts() = resetTexts()

    @Test fun noTotalLimitShowsTheEmptyCardWithLastMonthToCopy() = runBlocking<Unit> {
        val w = BudgetsWorld(transactions = listOf(spend("2026-10-01", 12_000, FOOD.id)))
        val ui = loadBudgets(w.deps, TODAY, Currency.SAR)
        val card = assertIs<TotalCard.NoLimit>(ui.total)
        assertEquals(uiText(TextKey.BUDGETS_NO_TOTAL_TITLE, "أكتوبر"), card.title, "الشهر المالي باسم الشهر اللي بيخلص فيه")
        assertEquals("سبتمبر", ui.prevMonthName)
        assertEquals("2026-08", ui.prevPeriodKey, "النسخ من مفتاح الفترة اللي فاتت (كوتلن بيسمّي المفتاح بشهر البداية)")
        assertEquals(12_000L, ui.spentMinor)
        assertNull(ui.totalLimit.limitMinor)
    }

    @Test fun knownSpendAgainstTheTotalLimitComesFromTheStatus() = runBlocking<Unit> {
        val w = BudgetsWorld(transactions = listOf(spend("2026-10-01", 40_000, FOOD.id), spend("2026-10-05", 25_000, GROCERY.id)))
        w.deps.setBudget.setTotalLimit(period, 100_000, 80)
        val ui = loadBudgets(w.deps, TODAY, Currency.SAR)
        val card = assertIs<TotalCard.Known>(ui.total)
        assertEquals(65_000L, card.spentMinor)
        assertEquals(100_000L, card.limitMinor)
        assertEquals(65, card.percent)
        assertEquals(Tone.OK, card.tone)
        assertEquals(uiText(TextKey.BUDGETS_CHIP_OK), card.chip)
        assertEquals(uiText(TextKey.BUDGETS_LEFT, amountLabel(35_000, Currency.SAR)), card.leftLine)
        assertTrue(card.dailyLine!!.isNotBlank(), "المتاح يوميًا من حالة الاستخدام (السقف موجود)")
        assertEquals(80, ui.totalLimit.thresholdPercent)
        // سطر لكل تصنيف — بلا سقف ⇒ «اضغط لتحديد سقف»، والأكبر صرفًا الأول
        assertEquals(listOf(FOOD.id, GROCERY.id), ui.lines.map { it.categoryId })
        assertTrue(ui.lines.all { it.noLimit && it.note == uiText(TextKey.BUDGETS_CAT_NO_LIMIT) })
        assertEquals(FOOD.lightColor, ui.lines.first().colorHex)
    }

    @Test fun overTheLimitIsRedAndSaysByHowMuch() = runBlocking<Unit> {
        val w = BudgetsWorld(transactions = listOf(spend("2026-10-01", 130_000, FOOD.id)))
        w.deps.setBudget.setTotalLimit(period, 100_000, null)
        w.deps.setBudget.setCategoryLimit(period, FOOD.id, 120_000, notifyEnabled = true, thresholdPercent = 90)
        val ui = loadBudgets(w.deps, TODAY, Currency.SAR)
        val card = assertIs<TotalCard.Known>(ui.total)
        assertEquals(Tone.OVER, card.tone)
        assertEquals(100, card.percent, "الشريط ما بيعدّيش ١٠٠")
        assertEquals(uiText(TextKey.BUDGETS_OVER_BY, amountLabel(30_000, Currency.SAR)), card.leftLine)
        assertNull(card.dailyLine, "مفيش متاح يومي بعد ما السقف اتعدّى")
        val line = ui.lines.single()
        assertTrue(line.over)
        assertEquals(120_000L, line.limitMinor)
        assertEquals(uiText(TextKey.BUDGETS_CAT_OVER, amountLabel(10_000, Currency.SAR)), line.note)
    }

    @Test fun nearTheLimitIsAmber() = runBlocking<Unit> {
        val w = BudgetsWorld(transactions = listOf(spend("2026-10-01", 92_000, FOOD.id)))
        w.deps.setBudget.setTotalLimit(period, 100_000, 80)
        val card = assertIs<TotalCard.Known>(loadBudgets(w.deps, TODAY, Currency.SAR).total)
        assertEquals(Tone.NEAR, card.tone)
        assertEquals(uiText(TextKey.BUDGETS_CHIP_NEAR), card.chip)
    }

    @Test fun unknownSpendIsNotAvailableNeverZero() = runBlocking<Unit> {
        // كل عمليات الشهر من غير نوع (ومتأكد إنها من غير نوع) ⇒ المصروف مش معروف (القاعدة 10)
        val w = BudgetsWorld(transactions = listOf(spend("2026-10-01", 50_000, null, kind = EconomicKind.UNCLASSIFIED, kindConfirmed = true)))
        w.deps.setBudget.setTotalLimit(period, 100_000, 80)
        w.deps.setBudget.setCategoryLimit(period, FOOD.id, 30_000, notifyEnabled = true, thresholdPercent = 80)
        val ui = loadBudgets(w.deps, TODAY, Currency.SAR)
        val card = assertIs<TotalCard.Unknown>(ui.total)
        assertEquals(100_000L, card.limitMinor)
        assertNull(ui.spentMinor, "لوحة السقف بتقول «غير متاح» مش صفر")
        assertEquals(uiText(TextKey.NOT_AVAILABLE), ui.anomalyText)
        assertFalse(ui.anomalyAlert)
        val line = ui.lines.single { it.categoryId == FOOD.id }
        assertNull(line.spentMinor)
        assertEquals(uiText(TextKey.NOT_AVAILABLE), line.note)
    }

    @Test fun estimatedKindsMakeTheSpendApproximateWithItsReason() = runBlocking<Unit> {
        val w = BudgetsWorld(transactions = listOf(spend("2026-10-01", 20_000, FOOD.id), spend("2026-10-02", 5_000, null, kind = EconomicKind.UNCLASSIFIED)))
        w.deps.setBudget.setTotalLimit(period, 100_000, 80)
        val card = assertIs<TotalCard.Known>(loadBudgets(w.deps, TODAY, Currency.SAR).total)
        assertEquals(25_000L, card.spentMinor, "المخمَّن بيتحسب — بس «تقريبي»")
        assertTrue(card.approxNote!!.isNotBlank(), "سبب «تقريبي» من حالة الاستخدام")
    }

    @Test fun averageIsInformationNotALimitAndSaysWhyItIsMissing() = runBlocking<Unit> {
        val ui = loadBudgets(BudgetsWorld(transactions = listOf(spend("2026-10-01", 1_000, FOOD.id))).deps, TODAY, Currency.SAR)
        assertNull(ui.averageMinor, "مفيش ٣ شهور مكتملة")
        assertTrue(ui.averageReason.isNotBlank(), "السبب بيتعرض مكان الرقم")
    }

    @Test fun egyptWordingFollowsTheCountry() = runBlocking<Unit> {
        val w = BudgetsWorld(transactions = listOf(spend("2026-10-01", 92_000, FOOD.id)))
        w.deps.setBudget.setTotalLimit(period, 100_000, 80)
        val saudi = assertIs<TotalCard.Known>(loadBudgets(w.deps, TODAY, Currency.SAR).total).chip
        resetTexts(ArabicVariant.EGYPTIAN)
        val egypt = assertIs<TotalCard.Known>(loadBudgets(w.deps, TODAY, Currency.SAR).total).chip
        assertNotEquals(saudi, egypt, "«اقتربت من السقف» ⇄ «قرّبت من السقف»")
    }
}
