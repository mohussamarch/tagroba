package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.EconomicKind
import app.masroufy.core.Transaction
import app.masroufy.core.shiftDays
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.TransactionRepository

/**
 * «آخر اللي استخدمته» في لوحة «إيه الصنف؟» بعد اختيار «شراء» (طلب المالك 2026-10-16): التصنيفات اللي اتحطت على مشتريات آخر [WINDOW_DAYS] يوم،
 * الأحدث الأول ومن غير تكرار — الشغالة بس. قراية واحدة بالتاريخ (مش الجدول كله).
 */
class RecentCategories(private val txns: TransactionRepository, private val categories: CategoryRepository, private val clock: Clock) {
    suspend fun forPurchase(limit: Int = 6): List<Category> {
        val today = clock.nowIso().take(10)
        return recentPurchaseCategories(txns.listByDateRange(shiftDays(today, -WINDOW_DAYS), today), categories.listAll(), limit)
    }

    companion object {
        const val WINDOW_DAYS = 90
    }
}

/** الاختيار نفسه (منفصل للاختبار): مشتريات بتصنيف · الأحدث الأول · من غير تكرار · التصنيف موجود وشغال · لحد [limit]. */
fun recentPurchaseCategories(rows: List<Transaction>, categories: List<Category>, limit: Int): List<Category> {
    val byId = categories.filter { it.active }.associateBy { it.id }
    return rows.asSequence()
        .filter { it.economicKind == EconomicKind.PURCHASE && it.categoryId != null }
        .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.sourceTime.orEmpty() })
        .mapNotNull { t -> t.categoryId?.let(byId::get) }
        .distinctBy { it.id }
        .take(limit)
        .toList()
}
