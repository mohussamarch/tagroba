package app.masroufy.memory

import app.masroufy.core.ClassificationRule
import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.SourceRecord
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.RuleRepository
import app.masroufy.port.SourceRecordRepository

/** مستودعات الاستيراد في الذاكرة — نقل `memoryRepositories.ts` بنفس الترتيب والفلترة. */

class MemorySourceRecordRepository(seed: List<SourceRecord> = emptyList()) : SourceRecordRepository, Snapshotable {
    private var items = LinkedHashMap<Id, SourceRecord>()

    init {
        for (r in seed) items[r.id] = r
    }

    override suspend fun listByBatch(batchId: Id): List<SourceRecord> = items.values.filter { it.batchId == batchId }

    override suspend fun listByAccountIdentity(accountIdentity: String): List<SourceRecord> =
        items.values.filter { it.accountIdentity == accountIdentity }

    override suspend fun listByTransactionIds(ids: List<Id>): List<SourceRecord> {
        val wanted = ids.toSet()
        return items.values.filter { it.transactionId != null && it.transactionId in wanted }
    }

    override suspend fun saveMany(records: List<SourceRecord>) {
        for (r in records) items[r.id] = r
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }

    fun all(): List<SourceRecord> = items.values.toList()

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as LinkedHashMap<Id, SourceRecord>)
    }
}

class MemoryImportBatchRepository(seed: List<ImportBatch> = emptyList()) : ImportBatchRepository, Snapshotable {
    private var items = LinkedHashMap<Id, ImportBatch>()

    init {
        for (b in seed) items[b.id] = b
    }

    override suspend fun findById(id: Id): ImportBatch? = items[id]

    /** بيشوف `committed` بس — الدفعة المعلّقة مش استيراد سابق (ARCHITECTURE §11.1). */
    override suspend fun findByFileHash(fileHash: String): ImportBatch? =
        items.values.firstOrNull { it.fileHash == fileHash && it.state == ImportBatchState.COMMITTED }

    override suspend fun listRecent(limit: Int): List<ImportBatch> =
        items.values.sortedByDescending { it.importedAt }.take(limit)

    override suspend fun save(batch: ImportBatch) {
        items[batch.id] = batch
    }

    override suspend fun updateState(id: Id, state: ImportBatchState) {
        val existing = items[id] ?: throw IllegalStateException("دفعة غير موجودة: $id")
        items[id] = existing.copy(state = state)
    }

    fun all(): List<ImportBatch> = items.values.toList()

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as LinkedHashMap<Id, ImportBatch>)
    }
}

class MemoryRuleRepository(seed: List<ClassificationRule> = emptyList()) : RuleRepository {
    private val items = LinkedHashMap<Id, ClassificationRule>()

    init {
        for (r in seed) items[r.id] = r
    }

    /** نفس ترتيب Firestore: الأولوية الأصغر الأول (ترتيب ثابت للمتساويين). */
    override suspend fun listAll(): List<ClassificationRule> = items.values.sortedBy { it.priority }

    override suspend fun saveMany(rules: List<ClassificationRule>) {
        for (r in rules) items[r.id] = r
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }
}
