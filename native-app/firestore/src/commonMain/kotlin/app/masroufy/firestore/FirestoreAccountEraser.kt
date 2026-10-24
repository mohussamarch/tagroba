package app.masroufy.firestore

import app.masroufy.core.SPACES_GROUP
import app.masroufy.port.AccountDataEraser
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Source

/**
 * §118-ج «امسح حسابي» (r06-fix055b — BLOCKER اتصلح): بيمسح كل اللي تحت `users/{uid}`. فايربيز على الجوال ما بيعرفش يعدّ المجموعات
 * الفرعية، فالأسامي من [USER_COLLECTIONS] — **نفس الثوابت** اللي المستودعات بتكتب بيها (المحوّلات + المستندات المفردة زي علامة
 * المراجع `initialization/references-v1`). البلاد: كل `spaces/{id}` الموجودة في السجل + [KNOWN_SPACE_IDS] (حتى لو مستند البلد
 * اتمسح وفضل اللي تحته)، وبعدهم مستندات البلاد ومستند الحساب نفسه. صفحات [PAGE] مستند — نفس القواعد (`request.auth.uid == uid`).
 * `sharedMerchants` و`inbox` ([SHARED_COLLECTIONS]) **بيفضلوا**: مش ملك المستخدم ومن غير uid (§80).
 */
class FirestoreAccountEraser(private val db: () -> FirebaseFirestore) : AccountDataEraser {
    override suspend fun eraseAll(uid: String): Int {
        val firestore = db()
        val account = FirestoreSpace.forAccount(firestore, uid)
        val spaceIds = (idsIn(account, SPACES_GROUP) + KNOWN_SPACE_IDS).distinct()
        var count = 0
        for (id in spaceIds) count += eraseRoot(FirestoreSpace(firestore, "users/$uid/$SPACES_GROUP/$id"))
        count += eraseRoot(account)
        count += eraseGroup(account, SPACES_GROUP)
        firestore.document("users/$uid").delete()
        return count + 1
    }

    private suspend fun eraseRoot(space: FirestoreSpace): Int {
        var count = 0
        for (group in USER_COLLECTIONS) count += eraseGroup(space, group)
        return count
    }

    private suspend fun eraseGroup(space: FirestoreSpace, group: String): Int {
        var count = 0
        while (true) {
            val ids = space.collection(group).orderBy(FieldPath.documentId).limit(PAGE).get(Source.SERVER).documents.map { it.id }
            if (ids.isEmpty()) break
            space.deleteAll(group, ids)
            count += ids.size
            if (ids.size < PAGE) break
        }
        return count
    }

    private suspend fun idsIn(space: FirestoreSpace, group: String): List<String> =
        space.collection(group).get(Source.SERVER).documents.map { it.id }

    companion object {
        const val PAGE = 300
    }
}
