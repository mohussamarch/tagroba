package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.data.SavingsGoalCodecs
import app.masroufy.memory.FixedClock
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.GoalInput
import app.masroufy.usecase.LoadGoalsOverview
import app.masroufy.usecase.LoadGoalsOverviewDeps
import app.masroufy.usecase.ManageSavingsGoals
import app.masroufy.usecase.ManageSavingsGoalsDeps
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * خطط الادخار (المساعد المالي §68) على Firestore Emulator: الخطة على **الحساب** (`users/{uid}`) مش جوه البلد،
 * فاللي اتكتب من مصر بيتقري من السعودية بنفس القيم. أسماء وأرقام مخترعة. التشغيل زي `IncomeRepositoriesTest`.
 */
@RunWith(AndroidJUnit4::class)
class SavingsGoalsOnFirestoreTest {
    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(90_000) { block() } }

    @Test fun goalsAndContributionsLiveOnTheAccountAndReadTheSameFromAnyCountry() = run {
        val uid = "kt-goals-" + java.util.UUID.randomUUID()
        val db = Emulator.firestore()
        val account = FirestoreSpace.forAccount(db, uid)
        val egRoot = FirestoreSpace.forSpace(db, uid, "eg")
        val saudi = FirestoreContainer(FirestoreSpace.forUser(db, uid))
        val eg = FirestoreContainer(account, egRoot, "eg")
        val manage = ManageSavingsGoals(ManageSavingsGoalsDeps(eg.savingsGoals, eg.goalContributions, SequentialIdGenerator(), FixedClock("2026-10-05T10:00:00.000Z")))

        val goal = manage.create(GoalInput("سفر وهمي", 1_200_000, Currency.SAR, "2026-01-01", "2026-12-31"))
        val first = manage.recordContribution(goal.id, "2026-03-01", 300_000)
        val second = manage.recordContribution(goal.id, "2026-06-01", 400_000, "ملاحظة وهمية")
        manage.removeContribution(first.id)

        // المكان: جذر الحساب، ومفيش نسخة جوه البلد
        assertNotNull(account.readDoc(SavingsGoalCodecs.savingsGoals.group, goal.id))
        assertNull(egRoot.readDoc(SavingsGoalCodecs.savingsGoals.group, goal.id))
        assertNull(egRoot.readDoc(SavingsGoalCodecs.goalContributions.group, second.id))

        // نفس القيم من السعودية بعد الرحلة لفايربيز والرجوع
        assertEquals(listOf(goal), saudi.savingsGoals.listAll())
        assertEquals(listOf(second), saudi.goalContributions.listAll())
        val overview = LoadGoalsOverview(LoadGoalsOverviewDeps(saudi.savingsGoals, saudi.goalContributions))
        assertEquals(400_000L, overview.load("2026-10-05").single().savedMinor, "الإيداع اللي اتشال ما اتحسبش")

        // الأرشفة: تختفي من الملخص وتفضل متخزنة
        manage.archive(goal.id)
        assertTrue(overview.load("2026-10-05").isEmpty())
        assertEquals(true, saudi.savingsGoals.listAll().single().archived)
        assertEquals(1, overview.load("2026-10-05", includeArchived = true).size)
    }

    /** النجمة ⭐ (رد المالك، صفحة الضبط): خطة واحدة بس — النقل بيمسح الحقل من القديمة في نفس الدفعة، والمستند من غير نجمة من غير الحقل. */
    @Test fun theStarMovesAndTheOldDocumentLosesTheField() = run {
        val uid = "kt-goal-star-" + java.util.UUID.randomUUID()
        val db = Emulator.firestore()
        val account = FirestoreSpace.forAccount(db, uid)
        val app = FirestoreContainer(FirestoreSpace.forUser(db, uid))
        val manage = ManageSavingsGoals(ManageSavingsGoalsDeps(app.savingsGoals, app.goalContributions, SequentialIdGenerator(), FixedClock("2026-10-05T10:00:00.000Z")))
        val a = manage.create(GoalInput("سفر وهمي", 1_200_000, Currency.SAR, "2026-01-01", "2026-12-31"))
        val b = manage.create(GoalInput("سيارة وهمية", 2_000_000, Currency.SAR, "2026-01-01", "2027-06-30"))
        val group = SavingsGoalCodecs.savingsGoals.group
        assertNull(assertNotNull(account.readDoc(group, a.id))["starred"], "من غير نجمة ⇒ الحقل مش موجود")
        manage.star(a.id)
        assertEquals(true, assertNotNull(account.readDoc(group, a.id))["starred"])
        manage.star(b.id)
        assertTrue("starred" !in assertNotNull(account.readDoc(group, a.id)), "النجمة اتشالت ⇒ الحقل اتمسح")
        assertEquals(listOf(b.id), app.savingsGoals.listAll().filter { it.starred }.map { it.id })
    }
}
