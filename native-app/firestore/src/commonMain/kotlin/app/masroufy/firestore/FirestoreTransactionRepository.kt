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

    override suspend fun listByDateRange(fromIso: String, toIso: String): List<Transaction> {
        return space.select(codec, DocQuery(listOf(Cond.AtLeast("occurredAt", fromIso), Cond.AtMost("occurredAt", toIso)))).sortedWith(compareBy({ it.occurredAt }, { it.sourceOrder }))
    }

    override suspend fun listCreatedAfter(iso: String): List<Transaction> =
        space.select(codec, DocQuery(listOf(Cond.After("createdAt", iso))))

    /** العلاقة بالدفعة بتيجي من `SourceRecord` مش من العملية نفسها (spec/03) — زي التطبيق الحالي. */
    override suspend fun listByBatch(batchId: Id): List<Transaction> = emptyList()

    override suspend fun findByIds(ids: List<Id>): List<Transaction> = space.findIn(codec, "id", ids)

    /**
     * الشريحة S3 (§77-D): `>= ""` على الحقل = المستندات اللي فيها الحقل بس (فايربيز بيستبعد اللي مفيهوش — والذاكرة بتقلّده `DocQuery`)
     * ⇒ القراية على قد الأزواج (فهرس الحقل الواحد تلقائي). استعلامين لأن الحقلين مش في مستند واحد.
     */
    override suspend fun listReversalLinked(): List<Transaction> {
        val found = LinkedHashMap<Id, Transaction>()
        for (field in listOf("reversalOfId", "reversedById")) for (t in space.select(codec, DocQuery(listOf(Cond.AtLeast(field, ""))))) found[t.id] = t
        return found.values.sortedWith(compareBy({ it.occurredAt }, { it.sourceOrder }))
    }

    override suspend fun saveMany(transactions: List<Transaction>) = space.saveAll(codec, transactions)

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
        // الشريحة S3 (§75-6 · §77-D): الاقتراح وربط العملية اللي رجعت — آخر اسم الربط `Id` ⇒ `storeForm` ما بيقصّش المعرّف
        if (patch.clearSuggestedKind) fields["suggestedKind"] = FieldValue.delete else patch.suggestedKind?.let { fields["suggestedKind"] = it.wire }
        if (patch.clearReversalOfId) fields["reversalOfId"] = FieldValue.delete else patch.reversalOfId?.let { fields["reversalOfId"] = it }
        if (patch.clearReversedById) fields["reversedById"] = FieldValue.delete else patch.reversedById?.let { fields["reversedById"] = it }
        if (patch.clearKindBeforeReversal) fields["kindBeforeReversal"] = FieldValue.delete else patch.kindBeforeReversal?.let { fields["kindBeforeReversal"] = it.wire }
        if (fields.isEmpty()) return
        val safe = storeForm(codec.group, fields)
        space.updateDoc(codec.group, id, safe)
    }

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}
