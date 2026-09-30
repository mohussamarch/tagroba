package app.masroufy.firestore

import app.masroufy.core.Id
import app.masroufy.core.Transaction
import app.masroufy.data.LedgerCodecs
import app.masroufy.data.storeForm
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import dev.gitlive.firebase.firestore.FieldValue

/**
 * العمليات على فايربيز — نقل `FirestoreTransactionRepository` (`firestoreRepositories.ts`) دالة دالة:
 * نفس الاستعلامات (مدى على حقل واحد، والترتيب في الذاكرة عشان مفيش فهرس مركّب)، و`in` بحد 30، والكتابة بدفعات 500.
 * **الفرق الوحيد عن قصد:** الحفظ بـmerge + مسح صريح للاختياري الفاضي (ARCHITECTURE §31.4) بدل الكتابة فوق المستند كله،
 * عشان التطبيقين شغالين على نفس البيانات.
 */
class FirestoreTransactionRepository(private val space: FirestoreSpace) : TransactionRepository {
    private val codec = LedgerCodecs.transactions
    private fun col() = space.collection(codec.group)

    override suspend fun listByDateRange(fromIso: String, toIso: String): List<Transaction> {
        val snap = col().where { "occurredAt" greaterThanOrEqualTo fromIso }.where { "occurredAt" lessThanOrEqualTo toIso }.get()
        return codec.decodeAll(snap).sortedWith(compareBy({ it.occurredAt }, { it.sourceOrder }))
    }

    override suspend fun listCreatedAfter(iso: String): List<Transaction> =
        codec.decodeAll(col().where { "createdAt" greaterThan iso }.get())

    /** العلاقة بالدفعة بتيجي من `SourceRecord` مش من العملية نفسها (spec/03) — زي التطبيق الحالي. */
    override suspend fun listByBatch(batchId: Id): List<Transaction> = emptyList()

    override suspend fun findByIds(ids: List<Id>): List<Transaction> {
        val out = mutableListOf<Transaction>()
        for (chunk in ids.chunked(IN_LIMIT)) out += codec.decodeAll(col().where { "id" inArray chunk }.get())
        return out
    }

    override suspend fun saveMany(transactions: List<Transaction>) {
        for (chunk in transactions.chunked(BATCH_LIMIT)) {
            val batch = space.db.batch()
            for (t in chunk) batch.set(col().document(codec.id(t)), codec.mergeForm(t), merge = true)
            batch.commit()
        }
    }

    /** تعديل حقول بعينها — `clear…` = مسح الحقل. النصوص الحرة بتتقص زي أي كتابة للعمليات. */
    override suspend fun update(id: Id, patch: TransactionPatch) {
        val fields = LinkedHashMap<String, Any?>()
        patch.amountMinor?.let { fields["amountMinor"] = it }
        patch.originalAmountMinor?.let { fields["originalAmountMinor"] = it }
        patch.economicKind?.let { fields["economicKind"] = it.wire }
        patch.economicKindConfirmed?.let { fields["economicKindConfirmed"] = it }
        if (patch.clearCategoryId) fields["categoryId"] = FieldValue.delete else patch.categoryId?.let { fields["categoryId"] = it }
        patch.categoryConfirmed?.let { fields["categoryConfirmed"] = it }
        patch.reviewState?.let { fields["reviewState"] = it.wire }
        patch.merchantId?.let { fields["merchantId"] = it }
        if (patch.clearNote) fields["note"] = FieldValue.delete else patch.note?.let { fields["note"] = it }
        patch.walletId?.let { fields["walletId"] = it }
        patch.excludedFromBudget?.let { fields["excludedFromBudget"] = it }
        patch.isCashTagged?.let { fields["isCashTagged"] = it }
        patch.updatedAt?.let { fields["updatedAt"] = it }
        if (fields.isEmpty()) return
        val safe = storeForm(codec.group, fields)
        col().document(id).update(*safe.map { (k, v) -> k to v }.toTypedArray())
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (chunk in ids.chunked(BATCH_LIMIT)) {
            val batch = space.db.batch()
            for (id in chunk) batch.delete(col().document(id))
            batch.commit()
        }
    }
}
