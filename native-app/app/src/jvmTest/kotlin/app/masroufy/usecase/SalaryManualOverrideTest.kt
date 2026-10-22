package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * r06-1e (OVERRIDES §110-4 — المالك: «يقدر يرجع هو بشكل مانويال يدوس علي العملية و يغيرها»): الراتب اللي اتسجل لوحده (بحدود ±10% و±3 أيام)
 * بيتغير من تفاصيل العملية — كارت «ما نوع هذا المبلغ؟» (`IncomeKindCard` ⇒ [IncomeAnswers.markIncome]) بأنواع «الفلوس دي إيه؟» أو أي نوع داخل.
 * الاختيار ده **بيفضل**: «طبّق على اللي فات» بيعدّه «اتحدد بإيده» وما بيرجّعهوش راتب. الأسامي والمبالغ مخترعة.
 */
class SalaryManualOverrideTest {
    private val txns = MemoryTransactionRepository()
    private val sources = MemoryIncomeSourceRepository()
    private val clock = FixedClock("2026-10-22T10:00:00.000Z")
    private val uow = MemoryUnitOfWork(listOf(txns, sources))
    private val employers = ManageEmployers(ManageEmployersDeps(sources, txns, SequentialIdGenerator(), clock, Currency.SAR))
    private val retro = EmployerRetroApply(EmployerRetroDeps(sources, txns, uow, clock, app.masroufy.memory.MemoryRepairBackup()))
    private val answers = IncomeAnswers(IncomeAnswersDeps(txns, MemoryCategoryRepository(), employers, retro, uow, clock))

    private fun deposit(id: String, date: String, kind: EconomicKind, state: ReviewState) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = false,
        observedDirection = Direction.IN, amountMinor = 900_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = state, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = "W-/FRACCT/00000000000004321FRTESTCO:ملاحظة", sourceOperationType = "حوالة واردة داخل البنك",
    )

    private suspend fun kindOf(id: String) = txns.findByIds(listOf(id)).single().let { Triple(it.economicKind, it.economicKindConfirmed, it.reviewState) }

    @Test fun autoSalaryCanBeChangedByHandToAnyMoneyWhatChoiceAndUndone() = runBlocking<Unit> {
        // اتسجل «راتب» لوحده (مقترح — مش اختيار المالك)
        txns.saveMany(listOf(deposit("t-auto", "2026-09-27", EconomicKind.SALARY, ReviewState.SUGGESTED)))
        for (kind in listOf(EconomicKind.REFUND_RECEIVED, EconomicKind.BONUS, EconomicKind.ADVANCE_RECEIVED, EconomicKind.OTHER_INCOME)) {
            val undo = answers.markIncome("t-auto", kind)
            assertEquals(Triple(kind, true, ReviewState.CONFIRMED), kindOf("t-auto"), "الاختيار بإيده = مؤكد")
            answers.undo(undo)
            assertEquals(Triple(EconomicKind.SALARY, false, ReviewState.SUGGESTED), kindOf("t-auto"), "«تراجع» بيرجّع الراتب زي ما كان")
        }
    }

    @Test fun theHandChoiceSticksWhenThePastIsApplied() = runBlocking<Unit> {
        txns.saveMany(listOf(deposit("t-aug", "2026-08-27", EconomicKind.UNCLASSIFIED, ReviewState.NEEDS_REVIEW), deposit("t-sep", "2026-09-27", EconomicKind.SALARY, ReviewState.SUGGESTED)))
        val employer = answers.markSalary("t-aug", newEmployer = EmployerInput("جهة تجربة", "2025-01-01")).employer!!
        answers.markIncome("t-sep", EconomicKind.BONUS)
        val preview = retro.preview(employer.id)
        assertTrue("t-sep" !in preview.toSalaryIds, "اختيار المالك ما بيرجعش راتب لوحده")
        assertTrue("t-sep" in preview.handSetIds, "بيتعدّ «اتحدد بإيده» — يرجّعه بس باختيار صريح")
        retro.apply(preview)
        assertEquals(Triple(EconomicKind.BONUS, true, ReviewState.CONFIRMED), kindOf("t-sep"))
    }
}
