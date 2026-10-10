package app.masroufy.wiring.budgets

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.TextKey
import app.masroufy.core.periodForDate
import app.masroufy.core.sentenceNumber
import app.masroufy.core.uiText
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.screens.budgets.Tone
import app.masroufy.ui.screens.budgets.loadCategoryBudget
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «ميزانية تصنيف» (`CategoryBudget`): سطر التصنيف من `LoadBudgetScreen` + عملياته من `LoadTransactionsScreen` ⇒ حالة الشاشة. */
class CategoryBudgetPresenterTest {
    private val period = periodForDate(TODAY, 28)

    @BeforeTest fun texts() = resetTexts()

    @Test fun limitStatusPaceSubsAndOperations() = runBlocking<Unit> {
        val w = BudgetsWorld(
            transactions = listOf(
                spend("2026-10-01", 20_000, FOOD.id, merchant = "مطعم وهمي"),
                spend("2026-10-03", 10_000, COFFEE.id, merchant = "مقهى وهمي"),
                spend("2026-10-04", 7_000, GROCERY.id),
            ),
        )
        w.deps.setBudget.setCategoryLimit(period, FOOD.id, 50_000, notifyEnabled = true, thresholdPercent = 80)
        val ui = assertNotNull(loadCategoryBudget(w.deps, FOOD.id, TODAY, Currency.SAR))
        assertEquals(FOOD.name, ui.name)
        assertEquals("أكتوبر", ui.monthName)
        assertTrue(ui.known)
        assertEquals(20_000L, ui.spentMinor, "مصروف التصنيف نفسه من سطر حالة الاستخدام")
        assertEquals(50_000L, ui.limitMinor)
        assertEquals(40, ui.percent)
        assertEquals(Tone.OK, ui.tone)
        assertEquals(uiText(UiKey.BUDGETS_CHIP_OK), ui.chip)
        assertEquals(uiText(UiKey.BUDGETS_LEFT, amountLabel(30_000, Currency.SAR)), ui.leftLine)
        // يوم ١٢ من ٣٠ ⇒ ٤٠٪ من الأيام عدّت (عدّ أيام مش فلوس)
        assertEquals(12, ui.dayIndex)
        assertEquals(30, ui.totalDays)
        assertEquals(uiText(UiKey.CAT_BUDGET_PACE_USED, sentenceNumber(40), sentenceNumber(40)), ui.pace1)
        assertEquals(80, ui.limit.thresholdPercent)
        assertTrue(ui.limit.notify)
        // الفرعي بمصروفه · عمليات التصنيف وفرعياته (البقالة برّه) — الأحدث الأول
        assertEquals(listOf(COFFEE.id), ui.subs.map { it.id })
        assertEquals(10_000L, ui.subs.single().spentMinor)
        assertEquals(2, ui.txCount)
        assertEquals(listOf("مقهى وهمي", "مطعم وهمي"), ui.txRows.map { it.title })
        assertTrue(ui.txRows.first().subtitle.contains(COFFEE.name), "عملية الفرعي بتقول اسمه")
    }

    @Test fun thresholdCrossedAndOverChips() = runBlocking<Unit> {
        val w = BudgetsWorld(transactions = listOf(spend("2026-10-01", 42_000, FOOD.id), spend("2026-10-02", 60_000, GROCERY.id)))
        w.deps.setBudget.setCategoryLimit(period, FOOD.id, 50_000, notifyEnabled = true, thresholdPercent = 80)
        w.deps.setBudget.setCategoryLimit(period, GROCERY.id, 50_000, notifyEnabled = false, thresholdPercent = 80)
        val food = assertNotNull(loadCategoryBudget(w.deps, FOOD.id, TODAY, Currency.SAR))
        assertEquals(uiText(UiKey.CAT_BUDGET_CHIP_THRESHOLD), food.chip, "عدّى نسبة التنبيه ولسه ما عدّاش السقف")
        val grocery = assertNotNull(loadCategoryBudget(w.deps, GROCERY.id, TODAY, Currency.SAR))
        assertEquals(Tone.OVER, grocery.tone)
        assertEquals(uiText(UiKey.BUDGETS_CHIP_OVER), grocery.chip)
        assertEquals(uiText(UiKey.BUDGETS_OVER_BY, amountLabel(10_000, Currency.SAR)), grocery.leftLine)
        assertFalse(grocery.limit.notify, "التنبيه مقفول بيتعرض مقفول")
    }

    @Test fun noLimitAndNoSpendIsAnHonestZeroWithTheEmptyList() = runBlocking<Unit> {
        val ui = assertNotNull(loadCategoryBudget(BudgetsWorld().deps, GROCERY.id, TODAY, Currency.SAR))
        assertEquals(0L, ui.spentMinor, "الأنواع معروفة ومفيش صرف ⇒ صفر معروف")
        assertNull(ui.limitMinor)
        assertNull(ui.percent)
        assertEquals(uiText(UiKey.CAT_BUDGET_CHIP_NO_LIMIT), ui.chip)
        assertEquals(uiText(UiKey.CAT_BUDGET_PACE_NONE), ui.pace1)
        assertEquals(0, ui.txCount)
        assertTrue(ui.subs.isEmpty())
    }

    @Test fun unknownKindsMakeEverythingNotAvailable() = runBlocking<Unit> {
        val w = BudgetsWorld(transactions = listOf(spend("2026-10-01", 9_000, FOOD.id, kind = EconomicKind.UNCLASSIFIED, kindConfirmed = true)))
        w.deps.setBudget.setCategoryLimit(period, FOOD.id, 50_000, notifyEnabled = true, thresholdPercent = 80)
        val ui = assertNotNull(loadCategoryBudget(w.deps, FOOD.id, TODAY, Currency.SAR))
        assertFalse(ui.known)
        assertNull(ui.spentMinor, "غير متاح — مش صفر")
        assertEquals(Tone.MUTED, ui.tone)
        assertEquals(uiText(TextKey.NOT_AVAILABLE), ui.chip)
        assertEquals(50_000L, ui.limitMinor, "السقف معروف ومستني")
        assertNull(ui.subs.firstOrNull()?.spentMinor)
    }

    @Test fun aRemovedCategoryHasNoScreen() = runBlocking<Unit> {
        assertNull(loadCategoryBudget(BudgetsWorld().deps, "c-gone", TODAY, Currency.SAR))
    }
}
