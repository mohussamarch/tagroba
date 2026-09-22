package app.masroufy.memory

import app.masroufy.core.Budget
import app.masroufy.core.Category
import app.masroufy.core.CategoryBudget
import app.masroufy.core.Id
import app.masroufy.core.PersonAllocation
import app.masroufy.core.Transaction
import app.masroufy.port.AllocationRepository
import app.masroufy.port.BudgetRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository

/**
 * مستودعات في الذاكرة — التنفيذ التاني لكل واجهة (CLAUDE.md #6)، للاختبار من fixtures.
 * بتقلّد تنفيذ التطبيق الحالي (`src/infrastructure/memory/`) **بنفس الترتيب والفلترة بالظبط**،
 * عشان نتيجة حالة الاستخدام تطابق ملف المرجع بالحرف.
 *
 * الكيانات في كوتلن `data class` (مش بتتغير)، فمفيش نسخ زي `clone` بتاعة جافاسكربت.
 */
class MemoryTransactionRepository(seed: List<Transaction> = emptyList()) : TransactionRepository, Snapshotable {
    private var items = LinkedHashMap<Id, Transaction>()

    init {
        for (t in seed) items[t.id] = t
    }

    /** نفس ترتيب التطبيق الحالي: التاريخ تصاعدي، وبعده `sourceOrder`. */
    override suspend fun listByDateRange(fromIso: String, toIso: String): List<Transaction> =
        items.values.filter { it.occurredAt >= fromIso && it.occurredAt <= toIso }
            .sortedWith(compareBy({ it.occurredAt }, { it.sourceOrder }))

    override suspend fun listCreatedAfter(iso: String): List<Transaction> =
        items.values.filter { it.createdAt > iso }.sortedBy { it.createdAt }

    /** العلاقة بالدفعة بتيجي من `SourceRecord` مش من العملية نفسها — زي التطبيق الحالي (spec/03). */
    override suspend fun listByBatch(batchId: Id): List<Transaction> = emptyList()

    override suspend fun findByIds(ids: List<Id>): List<Transaction> = ids.mapNotNull { items[it] }

    override suspend fun saveMany(transactions: List<Transaction>) {
        for (t in transactions) items[t.id] = t
    }

    override suspend fun update(id: Id, patch: TransactionPatch) {
        val old = items[id] ?: return
        items[id] = old.copy(
            amountMinor = patch.amountMinor ?: old.amountMinor,
            originalAmountMinor = patch.originalAmountMinor ?: old.originalAmountMinor,
            economicKind = patch.economicKind ?: old.economicKind,
            economicKindConfirmed = patch.economicKindConfirmed ?: old.economicKindConfirmed,
            categoryId = if (patch.clearCategoryId) null else patch.categoryId ?: old.categoryId,
            categoryConfirmed = patch.categoryConfirmed ?: old.categoryConfirmed,
            reviewState = patch.reviewState ?: old.reviewState,
            merchantId = patch.merchantId ?: old.merchantId,
            note = if (patch.clearNote) null else patch.note ?: old.note,
            walletId = patch.walletId ?: old.walletId,
            excludedFromBudget = patch.excludedFromBudget ?: old.excludedFromBudget,
            isCashTagged = patch.isCashTagged ?: old.isCashTagged,
            updatedAt = patch.updatedAt ?: old.updatedAt,
        )
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }

    fun all(): List<Transaction> = items.values.toList()

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as LinkedHashMap<Id, Transaction>)
    }
}

class MemoryCategoryRepository(seed: List<Category> = emptyList()) : CategoryRepository {
    private val items = LinkedHashMap<Id, Category>()

    init {
        for (c in seed) items[c.id] = c
    }

    override suspend fun listAll(): List<Category> = items.values.toList()

    override suspend fun save(category: Category) {
        items[category.id] = category
    }
}

class MemoryAllocationRepository(seed: List<PersonAllocation> = emptyList()) : AllocationRepository {
    private val items = LinkedHashMap<Id, PersonAllocation>()

    init {
        for (a in seed) items[a.id] = a
    }

    override suspend fun listByTransactionIds(ids: List<Id>): List<PersonAllocation> {
        val wanted = ids.toSet()
        return items.values.filter { it.transactionId in wanted }
    }

    override suspend fun saveMany(allocations: List<PersonAllocation>) {
        for (a in allocations) items[a.id] = a
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }
}

class MemoryBudgetRepository(seed: List<Budget> = emptyList(), lines: List<CategoryBudget> = emptyList()) : BudgetRepository {
    private val budgets = LinkedHashMap<String, Budget>()
    private val categoryLines = LinkedHashMap<Id, CategoryBudget>()

    init {
        for (b in seed) budgets[b.periodKey] = b
        for (l in lines) categoryLines[l.id] = l
    }

    override suspend fun findByPeriod(periodKey: String): Budget? = budgets[periodKey]

    override suspend fun save(budget: Budget) {
        budgets[budget.periodKey] = budget
    }

    override suspend fun remove(periodKey: String) {
        val removed = budgets.remove(periodKey) ?: return
        categoryLines.values.filter { it.budgetId == removed.id }.forEach { categoryLines.remove(it.id) }
    }

    override suspend fun listCategoryBudgets(budgetId: Id): List<CategoryBudget> =
        categoryLines.values.filter { it.budgetId == budgetId }

    override suspend fun saveCategoryBudget(line: CategoryBudget) {
        categoryLines[line.id] = line
    }

    override suspend fun removeCategoryBudget(id: Id) {
        categoryLines.remove(id)
    }
}
