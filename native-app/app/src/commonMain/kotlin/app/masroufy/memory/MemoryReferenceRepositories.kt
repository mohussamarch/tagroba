package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.Merchant
import app.masroufy.core.RecurringItem
import app.masroufy.core.Tag
import app.masroufy.core.TransactionTag
import app.masroufy.core.normalizeText
import app.masroufy.port.MerchantRepository
import app.masroufy.port.RecurringRepository
import app.masroufy.port.TagRepository
import app.masroufy.port.TransactionTagRepository

/**
 * مستودعات التجار والوسوم في الذاكرة — نقل `memoryReferenceRepositories.ts` و`memoryTagRepositories.ts`.
 * نفس الترتيب والفلترة بالظبط عشان المطابقة مع ملفات المرجع.
 */
class MemoryMerchantRepository(seed: List<Merchant> = emptyList()) : MerchantRepository {
    private val items = LinkedHashMap<Id, Merchant>()

    init {
        for (m in seed) items[m.id] = m
    }

    override suspend fun listAll(): List<Merchant> = items.values.toList()

    /** الاسم الأساسي الأول وبعده البديلة — نفس ترتيب التطبيق الحالي. */
    override suspend fun findByNormalizedName(normalizedName: String): Merchant? {
        val key = normalizeText(normalizedName)
        val all = items.values.toList()
        return all.find { it.normalizedName == key } ?: all.find { it.aliases.orEmpty().contains(key) }
    }

    override suspend fun saveMany(merchants: List<Merchant>) {
        for (m in merchants) items[m.id] = m
    }
}

class MemoryRecurringRepository(seed: List<RecurringItem> = emptyList()) : RecurringRepository {
    private val items = LinkedHashMap<String, RecurringItem>()

    init {
        for (i in seed) items[i.id] = i
    }

    override suspend fun listAll(): List<RecurringItem> = items.values.toList()

    override suspend fun save(item: RecurringItem) {
        items[item.id] = item
    }
}

class MemoryTagRepository(seed: List<Tag> = emptyList()) : TagRepository {
    private val items = LinkedHashMap<Id, Tag>()

    init {
        for (t in seed) items[t.id] = t
    }

    override suspend fun listAll(): List<Tag> = items.values.toList()

    override suspend fun save(tag: Tag) {
        items[tag.id] = tag
    }

    override suspend fun remove(id: Id) {
        items.remove(id)
    }
}

class MemoryTransactionTagRepository(seed: List<TransactionTag> = emptyList()) : TransactionTagRepository {
    private val items = LinkedHashMap<Id, TransactionTag>()

    init {
        for (l in seed) items[l.id] = l
    }

    override suspend fun listByTransactionIds(ids: List<Id>): List<TransactionTag> {
        val wanted = ids.toSet()
        return items.values.filter { it.transactionId in wanted }
    }

    override suspend fun listByTag(tagId: Id): List<TransactionTag> = items.values.filter { it.tagId == tagId }

    override suspend fun saveMany(links: List<TransactionTag>) {
        for (l in links) items[l.id] = l
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }
}
