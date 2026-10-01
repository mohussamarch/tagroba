package app.masroufy.firestore

import app.masroufy.core.BACKUP_GROUPS
import app.masroufy.core.BackupRow
import app.masroufy.core.FullBackupData
import app.masroufy.core.backupRowId
import app.masroufy.core.emptyBackupData
import app.masroufy.core.redactSms
import app.masroufy.data.receiptDocId
import app.masroufy.port.FullBackupPort
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.Source

/**
 * النسخة الشاملة على فايربيز — نقل `fullBackupRepository.ts` خطوة خطوة:
 * - **القراية من السيرفر بس** (`Source.SERVER`) على صفحات 200 بترتيب المعرّف — عمرها ما ترجع نسخة الجهاز الناقصة بصمت،
 *   ومن غير نت بتفشل برسالة (§51). مش من الذاكرة ([LocalMirror]) عن قصد: النسخة لازم تبقى من الأصل.
 * - **الاستعادة بتضيف الناقص بس**: معاملة لكل 100 صف بتقرا كل مستند وتكتب اللي مش موجود — عمرها ما تكتب فوق الموجود،
 *   وإعادتها بعد انقطاع آمنة. نصوص البنك بتتقص (`redactSms`). تسوية جديدة ⇒ عدّاد التسويات +1 (عشان `SettlementWriter`).
 * - ملف الحساب مستند واحد `profile/main` (OVERRIDES §26).
 */
class FirestoreFullBackup(private val space: FirestoreSpace) : FullBackupPort {
    private fun profileRef() = space.db.document("${space.root}/profile/main")

    private fun revisionRef() = space.db.document("${space.root}/concurrency/settlements")

    override suspend fun read(): FullBackupData {
        val data = emptyBackupData()
        for (group in BACKUP_GROUPS) {
            var cursor: DocumentSnapshot? = null
            while (true) {
                var query = space.collection(group).orderBy(FieldPath.documentId).limit(PAGE)
                cursor?.let { query = query.startAfter(it) }
                val page = query.get(Source.SERVER).documents
                data.getValue(group) += page.mapNotNull { it.rawData() }
                if (page.size < PAGE) break
                cursor = page.last()
            }
        }
        return data
    }

    override suspend fun addMissing(data: FullBackupData): Map<String, Int> {
        val added = LinkedHashMap<String, Int>()
        for (group in BACKUP_GROUPS) {
            added[group] = 0
            for (rows in data.getValue(group).chunked(TRANSACTION_ROWS)) {
                val written = space.db.runTransaction {
                    val refs = rows.map { row -> space.collection(group).document(docIdOf(group, row)) }
                    val current = refs.map { get(it) }
                    // كل القراية قبل أي كتابة (شرط المعاملة) — العدّاد بيتقرا هنا لو المجموعة تسويات
                    val revision = if (group == "settlements") get(revisionRef()).rawData()?.get("revision") else null
                    val fresh = mutableListOf<Pair<String, BackupRow>>()
                    current.forEachIndexed { i, snap ->
                        if (!snap.exists) {
                            val safe = protectBankText(rows[i])
                            set(refs[i], safe)
                            fresh += refs[i].id to safe
                        }
                    }
                    // ⚠️ مش `FieldValue.increment(1)`: في GitLive 2.7.0 بيكتب العدّاد **عدد عشري** (1.0)، و`SettlementWriter` بيرفض
                    // أي عدّاد مش صحيح ⇒ كل تسوية بعد الاستعادة كانت هتفشل (`FirestoreFullBackupTest` مسكها). القراية والكتابة في نفس المعاملة.
                    if (group == "settlements" && fresh.isNotEmpty()) set(revisionRef(), mapOf("revision" to ((revision as? Long) ?: 0L) + 1L), merge = true)
                    fresh
                }
                // اللي اتكتب يبان في الذاكرة لحظتها (المستمع هيجيبه كمان)
                space.mirror?.let { m -> written.forEach { (id, row) -> m.applySet(group, id, row) } }
                added[group] = added.getValue(group) + written.size
            }
        }
        return added
    }

    override suspend fun readProfile(): BackupRow? = profileRef().get(Source.SERVER).rawData()

    override suspend fun addProfileIfMissing(profile: BackupRow): Boolean = space.db.runTransaction {
        if (get(profileRef()).exists) return@runTransaction false
        set(profileRef(), profile)
        true
    }

    private companion object {
        const val PAGE = 200
        const val TRANSACTION_ROWS = 100
        val BANK_TEXT_FIELDS = listOf("accountIdentity", "rawLine", "rawDescription", "rawMerchantName")

        fun docIdOf(group: String, row: BackupRow): String = backupRowId(group, row).let { if (group == "notificationReceipts") receiptDocId(it) else it }

        fun protectBankText(row: BackupRow): BackupRow = row.mapValues { (key, value) -> if (key in BANK_TEXT_FIELDS && value is String) redactSms(value) else value }
    }
}
