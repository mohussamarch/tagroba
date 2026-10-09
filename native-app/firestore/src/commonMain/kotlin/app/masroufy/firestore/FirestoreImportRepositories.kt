package app.masroufy.firestore

import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.SourceRecord
import app.masroufy.data.LedgerCodecs
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.SourceRecordRepository

/** مصادر العمليات على فايربيز — نقل `FirestoreSourceRecordRepository` (`firestoreRepositories.ts`). */
class FirestoreSourceRecordRepository(private val space: FirestoreSpace) : SourceRecordRepository {
    private val codec = LedgerCodecs.sourceRecords

    override suspend fun listByBatch(batchId: Id): List<SourceRecord> = space.select(codec, DocQuery(listOf(Cond.Eq("batchId", batchId))))

    /** كل السجلات بتاعة هوية حساب — أساس فحص التكرار. */
    override suspend fun listByAccountIdentity(accountIdentity: String): List<SourceRecord> =
        space.select(codec, DocQuery(listOf(Cond.Eq("accountIdentity", accountIdentity))))

    override suspend fun listByTransactionIds(ids: List<Id>): List<SourceRecord> = space.findIn(codec, "transactionId", ids)

    /** مراجعة S1: مرجع رسالة البنك في أي محفظة في البلد — نفس خانة `sourceReference` اللي التطبيق القديم بيكتبها (مفيش خانة جديدة). */
    override suspend fun listBySourceReferences(references: List<String>): List<SourceRecord> =
        space.findIn(codec, "sourceReference", references.distinct())

    override suspend fun saveMany(records: List<SourceRecord>) = space.saveAll(codec, records)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}

/** دفعات الاستيراد على فايربيز — نقل `FirestoreImportBatchRepository` (`firestoreRepositories.ts`). */
class FirestoreImportBatchRepository(private val space: FirestoreSpace) : ImportBatchRepository {
    private val codec = LedgerCodecs.importBatches

    override suspend fun findById(id: Id): ImportBatch? = space.readDoc(codec.group, id)?.let(codec::decode)

    /** الدرجة الأولى من منع التكرار: ملف اتستورد قبل كده — **المحفوظ بس** (`committed`)، زي التطبيق الحالي. */
    override suspend fun findByFileHash(fileHash: String): ImportBatch? =
        space.select(codec, DocQuery(listOf(Cond.Eq("fileHash", fileHash)))).firstOrNull { it.state == ImportBatchState.COMMITTED }

    override suspend fun listRecent(limit: Int): List<ImportBatch> =
        space.select(codec, DocQuery(descendingBy = "importedAt", limit = limit))

    override suspend fun save(batch: ImportBatch) = space.saveAll(codec, listOf(batch))

    override suspend fun updateState(id: Id, state: ImportBatchState) {
        space.updateDoc(codec.group, id, mapOf("state" to state.wire))
    }
}
