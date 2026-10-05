package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** حسبة «خطة الادخار» (OVERRIDES §68) — أرقام مخترعة. في commonTest ⇒ بتشتغل على الآيفون كمان. */
class SavingsGoalsTest {
    private fun goal(target: Long = 1_200_000, start: String = "2026-01-01", end: String = "2026-12-31") =
        SavingsGoal("g-1", "خطة وهمية", target, Currency.SAR, start, end, createdAt = "c", updatedAt = "c")

    @Test fun monthsAreCalendarMonthsNotThirtyDays() {
        assertEquals("2026-02-28", addMonthsClamped("2026-01-31", 1))
        assertEquals("2024-02-29", addMonthsClamped("2024-01-31", 1), "سنة كبيسة")
        assertEquals("2027-01-15", addMonthsClamped("2026-12-15", 1))
        assertEquals("2026-11-30", addMonthsClamped("2026-12-31", -1))
        assertEquals(1, monthsUntil("2026-01-31", "2026-02-28"), "31 يناير ⇒ 28 فبراير = شهر واحد")
        assertEquals(2, monthsUntil("2026-01-31", "2026-03-01"))
        assertEquals(1, monthsUntil("2024-01-31", "2024-02-29"))
        assertEquals(1, monthsUntil("2026-03-31", "2026-04-30"))
        assertEquals(2, monthsUntil("2026-03-31", "2026-05-01"))
        assertEquals(1, monthsUntil("2026-10-05", "2026-10-05"), "نفس اليوم ⇒ الباقي مطلوب النهارده")
        assertEquals(0, monthsUntil("2026-10-06", "2026-10-05"))
        assertEquals(6, monthsUntil("2026-07-01", "2026-12-31"))
    }

    @Test fun expectedIsLinearByDaysAcrossUnevenMonths() {
        // فبراير 28 يوم ومارس 31: يوم 1 مارس = 28 من 58 يوم (مش «شهر من اتنين = النص»)
        val g = goal(100_000, "2026-02-01", "2026-03-31")
        val p = goalProgress(g, 40_000, 0, "2026-03-01")
        assertEquals(48_276L, p.expectedMinor, "100,000 × 28 ÷ 58 = 48,275.86 ⇒ 48,276")
        assertEquals(-8_276L, p.aheadMinor)
        assertEquals(GoalState.BEHIND, p.state)
        assertEquals(1, p.monthsLeft)
        assertEquals(60_000L, p.requiredPerMonthMinor)
    }

    @Test fun midYearGoalMath() {
        val p = goalProgress(goal(), 500_000, 0, "2026-07-01")
        assertEquals(596_703L, p.expectedMinor, "1,200,000 × 181 ÷ 364")
        assertEquals(-96_703L, p.aheadMinor)
        assertEquals(700_000L, p.remainingMinor)
        assertEquals(6, p.monthsLeft)
        assertEquals(116_667L, p.requiredPerMonthMinor, "700,000 ÷ 6 لفوق")
        assertEquals(1_005_525L, p.projectedAtTargetMinor, "500,000 + 500,000 × 183 ÷ 181")
        val ahead = goalProgress(goal(), 700_000, 0, "2026-07-01")
        assertEquals(GoalState.ON_TRACK, ahead.state)
        assertEquals(103_297L, ahead.aheadMinor)
    }

    @Test fun startAmountBendsTheLine() {
        // كان معاك 200,000 يوم البداية ⇒ الخط من 200,000 للهدف مش من الصفر
        val p = goalProgress(goal(), 700_000, 200_000, "2026-07-01")
        assertEquals(697_253L, p.expectedMinor, "200,000 + 1,000,000 × 181 ÷ 364")
        assertEquals(GoalState.ON_TRACK, p.state)
    }

    @Test fun unknownNeverBecomesZero() {
        val p = goalProgress(goal(), null, 0, "2026-07-01")
        assertEquals(GoalState.UNKNOWN, p.state)
        assertNull(p.expectedMinor)
        assertNull(p.requiredPerMonthMinor)
        assertNull(p.projectedAtTargetMinor)
        val noStart = goalProgress(goal(), 300_000, null, "2026-07-01")
        assertEquals(GoalState.PACE_UNKNOWN, noStart.state)
        assertNull(noStart.aheadMinor)
        assertEquals(150_000L, noStart.requiredPerMonthMinor, "المطلوب في الشهر بيتحسب من المدّخر بس")
    }

    @Test fun reachedOverdueNotStartedAndEarlyPace() {
        assertEquals(GoalState.REACHED, goalProgress(goal(), 1_200_000, 0, "2026-07-01").state)
        val late = goalProgress(goal(), 900_000, 0, "2027-01-02")
        assertEquals(GoalState.OVERDUE, late.state)
        assertEquals(300_000L, late.remainingMinor)
        assertNull(late.requiredPerMonthMinor)
        val early = goalProgress(goal(start = "2026-11-01"), 0, 0, "2026-10-05")
        assertEquals(GoalState.NOT_STARTED, early.state)
        assertNull(goalProgress(goal(), 10_000, 0, "2026-01-30").projectedAtTargetMinor, "قبل 30 يوم مفيش معدل")
        assertEquals(GOAL_PACE_MIN_DAYS, 30)
    }

    @Test fun goalChecks() {
        assertFailsWith<SavingsGoalError> { checkSavingsGoal(goal().copy(name = "  ")) }
        assertFailsWith<SavingsGoalError> { checkSavingsGoal(goal(target = 0)) }
        assertFailsWith<SavingsGoalError> { checkSavingsGoal(goal(end = "2026-01-01")) }
        assertFailsWith<SavingsGoalError> { checkSavingsGoal(goal().copy(linkedWalletId = "w-1")) }
        assertEquals("خطة وهمية", checkSavingsGoal(goal().copy(name = "  خطة   وهمية ")).name)
        val c = listOf(GoalContribution("c-1", "g-1", "2026-01-01", 1_000, "x"), GoalContribution("c-2", "g-1", "2026-03-01", 2_000, "x"), GoalContribution("c-3", "g-2", "2026-01-01", 9_000, "x"))
        assertEquals(1_000L, manualSavedMinor(c, "g-1", "2026-01-01"))
        assertEquals(3_000L, manualSavedMinor(c, "g-1", "2026-12-31"))
        assertEquals(34L, ceilDivMoney(100, 3))
    }
}
