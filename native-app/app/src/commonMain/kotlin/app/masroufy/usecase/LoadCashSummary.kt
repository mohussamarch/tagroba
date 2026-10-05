package app.masroufy.usecase

import app.masroufy.core.CashSummary
import app.masroufy.core.Period
import app.masroufy.core.summarizeCash
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * LoadCashSummary — نقل `loadCashSummary.ts`: كارت الكاش في الرئيسية وتفاصيله (OVERRIDES §32).
 * `null` = الحساب مالوش محفظة كاش (مش «صفر»، قاعدة 10). العمليات بتتحسب بنفس الأنواع
 * التقديرية اللي بيستعملها رقم «ما تم صرفه» (OVERRIDES §18).
 */

data class LoadCashSummaryDeps(
    val wallets: WalletRepository,
    val txns: TransactionRepository,
    val allocations: AllocationRepository,
    val categories: CategoryRepository,
)

class LoadCashSummary(private val deps: LoadCashSummaryDeps) {
    suspend fun load(period: Period, today: String): CashSummary? {
        val wallet = deps.wallets.listAll().find { it.kind == "cash" } ?: return null
        val from = if (wallet.openingAt < period.start) wallet.openingAt else period.start
        val to = if (today > period.end) today else period.end
        val rows = deps.txns.listByDateRange(from, to)
        val categories = deps.categories.listAll()
        val transactions = withEstimatedKinds(rows, categories.associate { it.id to it.name }).transactions
        val inPeriod = transactions.filter { it.occurredAt >= period.start && it.occurredAt <= period.end }
        val allocations = deps.allocations.listByTransactionIds(inPeriod.map { it.id })
        return summarizeCash(wallet, transactions, allocations, period.start, period.end)
    }
}
