package app.masroufy.firestore

import app.masroufy.core.Budget
import app.masroufy.core.CategoryBudget
import app.masroufy.core.Id
import app.masroufy.core.NotificationReceipt
import app.masroufy.core.RecurringItem
import app.masroufy.core.Tag
import app.masroufy.core.TransactionTag
import app.masroufy.data.LedgerCodecs
import app.masroufy.data.ReferenceCodecs
import app.masroufy.data.receiptDocId
import app.masroufy.port.BudgetRepository
import app.masroufy.port.NotificationReceiptRepository
import app.masroufy.port.RecurringRepository
import app.masroufy.port.TagRepository
import app.masroufy.port.TransactionTagRepository

/** الوسوم والميزانيات والاشتراكات والتنبيهات — نقل `tagRepositories.ts` و`budgetRepository.ts` و`recurringRepository.ts` و`notificationRepository.ts`. */

class FirestoreTagRepository(private val space: FirestoreSpace) : TagRepository {
    private val codec = ReferenceCodecs.tags

    override suspend fun listAll(): List<Tag> = space.select(codec)

    override suspend fun save(tag: Tag) = space.saveAll(codec, listOf(tag))

    override suspend fun remove(id: Id) = space.deleteAll(codec.group, listOf(id))
}

class FirestoreTransactionTagRepository(private val space: FirestoreSpace) : TransactionTagRepository {
    private val codec = LedgerCodecs.transactionTags

    override suspend fun listByTransactionIds(ids: List<Id>): List<TransactionTag> = space.findIn(codec, "transactionId", ids)

    override suspend fun listByTag(tagId: Id): List<TransactionTag> = space.findIn(codec, "tagId", listOf(tagId))

    override suspend fun saveMany(links: List<TransactionTag>) = space.saveAll(codec, links)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}

/** الميزانية بمعرّف الفترة (`budgets/{periodKey}`)، وسقوف التصنيفات مجموعة لوحدها مربوطة بـ`budgetId`. */
class FirestoreBudgetRepository(private val space: FirestoreSpace) : BudgetRepository {
    private val budgets = ReferenceCodecs.budgets
    private val lines = ReferenceCodecs.categoryBudgets

    override suspend fun findByPeriod(periodKey: String): Budget? = space.readDoc(budgets.group, periodKey)?.let(budgets::decode)

    override suspend fun save(budget: Budget) = space.saveAll(budgets, listOf(budget))

    /** السقوف الأول وبعدها الميزانية: لو اتقطع بينهم الميزانية بتفضل فالمحاولة تتعاد، ومفيش سقوف يتيمة — زي التطبيق الحالي. */
    override suspend fun remove(periodKey: String) {
        val budget = findByPeriod(periodKey) ?: return
        space.deleteAll(lines.group, listCategoryBudgets(budget.id).map { it.id })
        space.deleteAll(budgets.group, listOf(periodKey))
    }

    override suspend fun listCategoryBudgets(budgetId: Id): List<CategoryBudget> =
        space.select(lines, DocQuery(listOf(Cond.Eq("budgetId", budgetId))))

    override suspend fun saveCategoryBudget(line: CategoryBudget) = space.saveAll(lines, listOf(line))

    override suspend fun removeCategoryBudget(id: Id) = space.deleteAll(lines.group, listOf(id))
}

class FirestoreRecurringRepository(private val space: FirestoreSpace) : RecurringRepository {
    private val codec = ReferenceCodecs.recurringItems

    override suspend fun listAll(): List<RecurringItem> = space.select(codec)

    override suspend fun save(item: RecurringItem) = space.saveAll(codec, listOf(item))
}

/** معرّف المستند = مفتاح الحدث متشفّر (`receiptDocId`) — نفس الإيصال مرتين بيكتب فوق نفسه. */
class FirestoreNotificationReceiptRepository(private val space: FirestoreSpace) : NotificationReceiptRepository {
    private val codec = ReferenceCodecs.notificationReceipts

    override suspend fun listAll(): List<NotificationReceipt> = space.select(codec)

    override suspend fun saveMany(receipts: List<NotificationReceipt>) = space.saveAll(codec, receipts)

    override suspend fun deleteMany(eventKeys: List<String>) = space.deleteAll(codec.group, eventKeys.map(::receiptDocId))
}
