package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeFollowUp
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.emptyProfile
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.IncomeSignalsDeps
import app.masroufy.usecase.IncomeSourceInput
import app.masroufy.usecase.IncomeSourceSignals
import app.masroufy.usecase.JobChange
import app.masroufy.usecase.ManageIncomeSources
import app.masroufy.usecase.ManageIncomeSourcesDeps
import app.masroufy.usecase.ManageProfile
import app.masroufy.usecase.ManageProfileDeps
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * مصادر الدخل (OVERRIDES §48 · §64) بحالات الاستخدام نفسها على Firestore Emulator. أسماء وأرقام مخترعة.
 * التشغيل زي `EventRepositoriesTest` (HANDOVER: Firestore Emulator على 8088 + محاكي أندرويد).
 */
@RunWith(AndroidJUnit4::class)
class IncomeRepositoriesTest {
    private fun space() = FirestoreSpace.forUser(Emulator.firestore(), "kt-" + java.util.UUID.randomUUID())

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(90_000) { block() } }

    @Test fun jobChangePayerAndCarAnswerWorkOnFirestore() = run {
        val c = FirestoreContainer(space())
        val clock = FixedClock("2026-10-04T10:00:00.000Z")
        c.profile.save(emptyProfile().copy(payday = 28, hasCar = false))
        val profile = ManageProfile(ManageProfileDeps(c.profile, MemoryAccount(), clock, c.incomeSources))
        val manage = ManageIncomeSources(ManageIncomeSourcesDeps(c.incomeSources, profile, PassthroughUnitOfWork(), SequentialIdGenerator(), clock))
        val old = manage.add(IncomeSourceInput("شركة النجمة الوهمية", "2024-01-01", expectedDayOfMonth = 27))
        val change = manage.changeJob(JobChange(old.id, "2026-09-30", IncomeSourceInput("شركة القمر الوهمية", "2026-10-01")))
        assertTrue(change.congratulate)
        val new = change.opened!!
        assertEquals(listOf<IncomeFollowUp>(IncomeFollowUp.ChangeMonthStart(new.id, 25)), manage.answerPayday(new.id, 25))
        manage.applyMonthStart(25)
        assertEquals(25, profile.load().payday)
        val stored = c.incomeSources.listAll().associateBy { it.id }
        assertEquals("2026-09-30", stored.getValue(old.id).endedAt, "القديم اتقفل بتاريخ وفضل موجود")
        assertNull(stored.getValue(new.id).endedAt)

        // أول مرتب من الشركة الجديدة ⇒ سؤال ⇒ أيوه ⇒ المصدر بيفتكر الطرف، والعملية بقت «مرتب»
        val salary = Transaction(
            id = "t-1", occurredAt = "2026-10-25", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
            economicKindConfirmed = false, observedDirection = Direction.IN, amountMinor = 1_000_000, currency = Currency.SAR, categoryConfirmed = false,
            excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
            rawDescription = "PAYROLL-PA1234:ملاحظة | سامي-INMAINM1234567RJ-شركة القمر الوهمية | x", sourceOperationType = "حواالت سريع الواردة",
        )
        c.transactions.saveMany(listOf(salary))
        val signals = IncomeSourceSignals(IncomeSignalsDeps(c.incomeSources, c.transactions, c.allocations, c.profile, PassthroughUnitOfWork(), clock, c.transferParties))
        val q = signals.payerQuestions().single()
        assertEquals(new.id, q.sourceId)
        signals.answerPayer(q, yes = true)
        assertEquals(listOf(q.party.key), c.incomeSources.listAll().single { it.id == new.id }.payerKeys)
        assertEquals(EconomicKind.SALARY, c.transactions.findByIds(listOf("t-1")).single().economicKind)
        assertTrue(signals.payerQuestions().isEmpty())
        assertTrue(signals.alertCandidates("2026-10-28").isEmpty(), "وصل في ميعاده")

        // اشترى عربية وعنده شغل ⇒ «هتروح بيها الشغل؟» ⇒ الرد بيتخزن في الملف
        assertEquals(listOf<IncomeFollowUp>(IncomeFollowUp.CarToWork), profile.saveWithQuestions(profile.load().copy(hasCar = true)).followUps)
        profile.answerCarToWork(false)
        assertEquals(false, c.profile.load()!!.carToWork)
    }
}
