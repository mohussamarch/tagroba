package app.masroufy.firestore

import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.SourceRecord
import app.masroufy.data.LedgerCodecs
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.SourceRecordRepository
import dev.gitlive.firebase.firestore.Direction as SortDirection

/** مصادر العمليات على فايربيز — نقل `FirestoreSourceRecordRepository` (`firestoreRepositories.ts`). */
class FirestoreSourceRecordRepository(private val space: FirestoreSpace) : SourceRecordRepository {
    private val codec = LedgerCodecs.sourceRecords
    private fun col() = space.collection(codec.group)

    override suspend fun listByBatch(batchId: Id): List<SourceRecord> = codec.decodeAll(col().where { "batchId" equalTo batchId }.get(space.sourceFor(codec.group)))

    /** كل السجلات بتاعة هوية حساب — أساس فحص التكرار. */
    override suspend fun listByAccountIdentity(accountIdentity: String): List<SourceRecord> =
        codec.decodeAll(col().where { "accountIdentity" equalTo accountIdentity }.get(space.sourceFor(codec.group)))

    override suspend fun listByTransactionIds(ids: List<Id>): List<SourceRecord> = space.findIn(codec, "transactionId", ids)

    override suspend fun saveMany(records: List<SourceRecord>) = space.saveAll(codec, records)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}

/** دفعات الاستيراد على فايربيز — نقل `FirestoreImportBatchRepository` (`firestoreRepositories.ts`). */
class FirestoreImportBatchRepository(private val space: FirestoreSpace) : ImportBatchRepository {
    private val codec = LedgerCodecs.importBatches
    private fun col() = space.collection(codec.group)

    override suspend fun findById(id: Id): ImportBatch? = space.readDoc(codec.group, id)?.let(codec::decode)

    /** الدرجة الأولى من منع التكرار: ملف اتستورد قبل كده — **المحفوظ بس** (`committed`)، زي التطبيق الحالي. */
    override suspend fun findByFileHash(fileHash: String): ImportBatch? =
        codec.decodeAll(col().where { "fileHash" equalTo fileHash }.get(space.sourceFor(codec.group))).firstOrNull { it.state == ImportBatchState.COMMITTED }

    override suspend fun listRecent(limit: Int): List<ImportBatch> =
        codec.decodeAll(col().orderBy("importedAt", SortDirection.DESCENDING).limit(limit.toLong()).get(space.sourceFor(codec.group)))

    override suspend fun save(batch: ImportBatch) = space.saveAll(codec, listOf(batch))

    override suspend fun updateState(id: Id, state: ImportBatchState) {
        space.write { col().document(id).update("state" to state.wire) }
    }
}
