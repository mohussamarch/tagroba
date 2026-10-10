package app.masroufy.wiring.budgets

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.uiText
import app.masroufy.ui.screens.budgets.GoalCheck
import app.masroufy.ui.screens.budgets.GoalEditCheck
import app.masroufy.ui.screens.budgets.GoalEditDraft
import app.masroufy.ui.screens.budgets.GoalKind
import app.masroufy.ui.screens.budgets.GoalNewDraft
import app.masroufy.ui.screens.budgets.GoalWhen
import app.masroufy.ui.screens.budgets.StatTone
import app.masroufy.ui.screens.budgets.Tone
import app.masroufy.ui.screens.budgets.checkGoalEdit
import app.masroufy.ui.screens.budgets.checkNewGoal
import app.masroufy.ui.screens.budgets.detailProgressLine
import app.masroufy.ui.screens.budgets.goalDate
import app.masroufy.ui.screens.budgets.loadGoals
import app.masroufy.ui.screens.budgets.needLines
import app.masroufy.usecase.GoalInput
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «خطط الادخار» + تفاصيل الخطة + الخطة الجديدة والتعديل: من `LoadGoalsOverview` (`GoalProgress`) لحالة الشاشة، والخانات قبل `ManageSavingsGoals`. */
class GoalsPresenterTest {
    private val savings = Wallet("w-save", "حساب ادخار وهمي", Currency.SAR, "bank", 300_000, "2026-01-01", accountLast4 = "1234")
    private val later = Wallet("w-later", "حساب لسه ما اتفتحش", Currency.SAR, "bank", 0, "2026-12-01")

    @BeforeTest fun texts() = resetTexts()

    private fun world() = BudgetsWorld(wallets = listOf(BANK, savings, later))

    private suspend fun BudgetsWorld.goal(name: String, target: Long, start: String, end: String, wallet: Wallet? = null) =
        deps.goals.create(GoalInput(name, target, Currency.SAR, start, end, wallet?.id, wallet?.let { SAUDI.id }))

    @Test fun manualGoalBehindItsLine() = runBlocking<Unit> {
        val w = world()
        val g = w.goal("السفر", 1_200_000, "2026-01-01", "2026-12-31")
        w.deps.goals.recordContribution(g.id, "2026-03-01", 400_000)
        w.deps.goals.recordContribution(g.id, "2026-06-01", 300_000)
        val card = loadGoals(w.deps, TODAY, listOf(BANK, savings)).active.single()
        assertEquals(700_000L, card.savedMinor)
        assertEquals(58, card.percent, "٧٠٠٠ من ١٢٠٠٠ = ٥٨٪ لتحت (عرض الشريط بس)")
        assertEquals(uiText(UiKey.GOALS_CHIP_BEHIND), card.chip)
        assertEquals(Tone.OVER, card.chipTone)
        assertEquals(uiText(UiKey.GOALS_STAT_BEHIND), card.stats[1].label)
        assertEquals(StatTone.BAD, card.stats[1].tone)
        assertTrue(card.sub.contains(uiText(UiKey.GOALS_MANUAL)))
        assertTrue(card.stats.none { it.value == uiText(TextKey.NOT_AVAILABLE) }, "كل الأرقام معروفة من حالة الاستخدام")
    }

    @Test fun linkedGoalShowsTheAccountWithLastFourOnly() = runBlocking<Unit> {
        val w = world()
        w.goal("الطوارئ", 2_000_000, "2026-09-01", "2027-06-01", savings)
        val card = loadGoals(w.deps, TODAY, listOf(BANK, savings)).active.single()
        assertTrue(card.linked)
        assertEquals(300_000L, card.savedMinor, "رصيد الحساب المربوط كله")
        assertEquals(uiText(UiKey.GOALS_WALLET_LAST4, savings.name, "1234"), card.walletLabel)
    }

    @Test fun unknownBalanceIsNotAvailableEverywhere() = runBlocking<Unit> {
        val w = world()
        w.goal("جوال", 300_000, "2026-09-01", "2027-03-01", later)
        val card = loadGoals(w.deps, TODAY, listOf(BANK, savings, later)).active.single()
        assertNull(card.savedMinor)
        assertNull(card.percent)
        assertEquals(uiText(TextKey.NOT_AVAILABLE), card.chip)
        assertTrue(card.stats.all { it.value == uiText(TextKey.NOT_AVAILABLE) && it.tone == StatTone.MUTED }, "غير متاح — مش صفر")
        assertEquals(uiText(UiKey.GOAL_DETAIL_UNKNOWN), detailProgressLine(card))
    }

    @Test fun youngReachedAndArchivedGoals() = runBlocking<Unit> {
        val w = world()
        val young = w.goal("جديدة", 500_000, "2026-10-01", "2027-10-01")
        w.deps.goals.recordContribution(young.id, "2026-10-05", 50_000)
        val done = w.goal("كملت", 100_000, "2026-01-01", "2026-12-31")
        w.deps.goals.recordContribution(done.id, "2026-05-01", 100_000)
        val old = w.goal("قديمة", 100_000, "2026-01-01", "2026-12-31")
        w.deps.goals.archive(old.id, true)
        val ui = loadGoals(w.deps, TODAY, listOf(BANK))
        val y = ui.active.single { it.id == young.id }
        assertEquals(uiText(UiKey.GOALS_CHIP_NEW), y.chip, "أقل من ٣٠ يوم من البداية")
        assertEquals(uiText(UiKey.GOALS_STAT_AFTER_30), y.stats[3].value)
        val r = ui.active.single { it.id == done.id }
        assertEquals(uiText(UiKey.GOALS_CHIP_REACHED), r.chip)
        assertTrue(r.reached)
        assertEquals(100, r.percent)
        assertEquals(listOf(old.id), ui.archived.map { it.id })
    }

    @Test fun newGoalFieldsAreCheckedBeforeTheUseCase() {
        assertEquals(uiText(TextKey.GOAL_NAME_REQUIRED), assertIs<GoalCheck.Bad>(checkNewGoal(GoalNewDraft(" ", "100"), TODAY, Currency.SAR, "sa")).message)
        assertEquals(uiText(TextKey.GOAL_TARGET_POSITIVE), assertIs<GoalCheck.Bad>(checkNewGoal(GoalNewDraft("سفر", "0"), TODAY, Currency.SAR, "sa")).message)
        val noWallet = GoalNewDraft("سفر", "100", kind = GoalKind.LINKED)
        assertEquals(uiText(UiKey.GOAL_NEW_PICK_WALLET), assertIs<GoalCheck.Bad>(checkNewGoal(noWallet, TODAY, Currency.SAR, "sa")).message)
        val ok = assertIs<GoalCheck.Ok>(checkNewGoal(GoalNewDraft("  سفر   الصيف ", "١٢٬٠٠٠"), TODAY, Currency.SAR, "sa")).input
        assertEquals("سفر الصيف", ok.name)
        assertEquals(1_200_000L, ok.targetMinor)
        assertEquals("2026-12-31", ok.targetDate)
        assertNull(ok.linkedWalletId)
        assertEquals("2027-12-31", goalDate(GoalWhen.YEAR_END, "2026-12-31"), "آخر يوم في السنة ⇒ آخر السنة الجاية")
        assertEquals("2027-10-09", goalDate(GoalWhen.ONE_YEAR, TODAY))
    }

    @Test fun editingChecksChangesAndSaysTheNewMonthlyComesAfterSaving() = runBlocking<Unit> {
        val w = world()
        w.goal("السفر", 1_200_000, "2026-01-01", "2026-12-31")
        val p = w.deps.goalsOverview.load(TODAY).single()
        val draft = GoalEditDraft.from(p)
        assertEquals("12000", draft.targetText)
        assertIs<GoalEditCheck.Unchanged>(checkGoalEdit(draft, p, TODAY))
        assertEquals(uiText(UiKey.GOAL_EDIT_DATE_AFTER), assertIs<GoalEditCheck.Bad>(checkGoalEdit(draft.copy(date = TODAY), p, TODAY)).message)
        val archive = assertIs<GoalEditCheck.Ok>(checkGoalEdit(draft.copy(archived = true), p, TODAY)).change
        assertNull(archive.input, "الأرشفة لوحدها ما بتعدّلش البيانات")
        assertEquals(true, archive.archived)
        val (head, note) = needLines(draft.copy(targetText = "15000"), p)
        assertEquals(uiText(UiKey.GOAL_EDIT_NEED_NA), head)
        assertEquals(uiText(UiKey.GOAL_EDIT_NEED_AFTER_SAVE), note, "مفيش معاينة قبل الحفظ ⇒ مش بنخترع رقم")
    }
}
