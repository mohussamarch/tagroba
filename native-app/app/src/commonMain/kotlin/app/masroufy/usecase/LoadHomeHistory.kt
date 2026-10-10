package app.masroufy.usecase

import app.masroufy.core.Period
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.TransactionRepository

/**
 * LoadHomeHistory — نقل `loadHomeHistory.ts`: الفترة الحالية + آخر خمس فترات قبلها.
 * بتتطلب بعد ما الرئيسية تفتح (الرئيسية نفسها ما بتستناش التاريخ).
 * الأرقام بنفس قاعدة الرئيسية: الواضح بيتحسب تقديري (OVERRIDES §18)، والراتب اللي نزل قبل أول الفترة بشوية في فترته الجديدة (§75-3)،
 * والداخل المستني برّه الدخل وبيتعد لوحده (§75-1).
 */
data class LoadHomeHistoryDeps(
    val txns: TransactionRepository,
    val allocations: AllocationRepository,
    /** من غيره: أسماء التصنيفات من الرئيسية اللي اتحملت. */
    val categories: CategoryRepository? = null,
)

class LoadHomeHistory(private val deps: LoadHomeHistoryDeps) {
    suspend fun load(period: Period, payday: Int, current: HomeScreenData): List<PeriodSummary> {
        val names = (deps.categories?.listAll() ?: current.categories).associate { it.id to it.name }
        val older = (0 until 5).map { index ->
            val p = shiftPeriod(period, -1 - index, payday)
            val view = withEstimatedKinds(loadPeriodRows(deps.txns, p, payday, names).rows, names)
            val allocations = deps.allocations.listByTransactionIds(view.transactions.map { it.id })
            periodSummaryOf(p, view, computePeriodTotals(view.transactions, allocations))
        }
        return listOf(PeriodSummary(period, current.expenseMinor, current.incomeMinor, current.transactionCount, current.pendingIncomingCount)) + older
    }
}
