package app.masroufy.firestore

import app.masroufy.core.BACKUP_GROUPS
import app.masroufy.core.LATER_BACKUP_GROUPS
import app.masroufy.core.SPACES_GROUP
import app.masroufy.port.AccountDataEraser
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Source

/**
 * r06-links2 (§118-ج «امسح حسابي»): بيمسح كل اللي تحت `users/{uid}` — فايربيز على الجوال ما بيعرفش يعدّ المجموعات الفرعية، فالمجموعات
 * معروفة بالاسم: مجموعات النسخة الشاملة ([BACKUP_GROUPS] + [LATER_BACKUP_GROUPS]) + اللي برّاها ([EXTRA_GROUPS]). البلاد التانية
 * (`users/{uid}/spaces/{id}`) نفس الكلام، وبعدهم مستندات البلاد ومستند الحساب نفسه. صفحات [PAGE] مستند — نفس القواعد (`request.auth.uid == uid`).
 * ⚠️ ما اتجربش على محاكي فايربيز (HANDOVER §7) — الاختبار في الذاكرة بس.
 */
class FirestoreAccountEraser(private val db: () -> FirebaseFirestore) : AccountDataEraser {
    override suspend fun eraseAll(uid: String): Int {
        val firestore = db()
        val account = FirestoreSpace.forAccount(firestore, uid)
        val spaceIds = idsIn(account, SPACES_GROUP)
        var count = 0
        for (id in spaceIds) count += eraseRoot(FirestoreSpace(firestore, "users/$uid/$SPACES_GROUP/$id"))
        count += eraseRoot(account)
        account.deleteAll(SPACES_GROUP, spaceIds)
        firestore.document("users/$uid").delete()
        return count + spaceIds.size + 1
    }

    private suspend fun eraseRoot(space: FirestoreSpace): Int {
        var count = 0
        for (group in (BACKUP_GROUPS + LATER_BACKUP_GROUPS + EXTRA_GROUPS).distinct()) {
            while (true) {
                val ids = space.collection(group).orderBy(FieldPath.documentId).limit(PAGE).get(Source.SERVER).documents.map { it.id }
                if (ids.isEmpty()) break
                space.deleteAll(group, ids)
                count += ids.size
                if (ids.size < PAGE) break
            }
        }
        return count
    }

    private suspend fun idsIn(space: FirestoreSpace, group: String): List<String> =
        space.collection(group).get(Source.SERVER).documents.map { it.id }

    companion object {
        const val PAGE = 300

        /** مجموعات مش في النسخة الشاملة (سجلات الجرس والمزامنة والإعدادات والتحويل بين البلاد). */
        val EXTRA_GROUPS = listOf("alertDismissals", "alertReceipts", "spaceTransfers", "userSettings", "profile", "syncCursors", "feedback")
    }
}
