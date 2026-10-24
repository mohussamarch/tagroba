package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.SPACES_GROUP
import dev.gitlive.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * r06-fix055b (BLOCKER «امسح حسابي») على Firestore Emulator (8088) — مستخدمين مخترعين:
 * مستند في **كل** مجموعة من [USER_COLLECTIONS] في السعودية (`users/{uid}`) ومصر (`users/{uid}/spaces/eg`) — منهم علامة المراجع
 * `initialization/references-v1` اللي فضلت في فحص 0.5.5 — + مستند البلد + مستند الحساب ⇒ [FirestoreAccountEraser] ⇒ ولا مستند فاضل.
 * المستخدم التاني ما يتلمسش. (`sharedMerchants` برا `users/` — قواعد المحاكي هنا `test.rules` ما بتسمحش بيه، والمسح ما بيلمسش غير `users/{uid}`.)
 */
@RunWith(AndroidJUnit4::class)
class AccountEraserOnFirestoreTest {
    private val db = Emulator.firestore("eraser-test")
    private val run = System.currentTimeMillis()
    private val victim = "erase-kt-user-$run"
    private val bystander = "erase-kt-other-$run"

    private fun roots(uid: String) = listOf(FirestoreSpace.forAccount(db, uid), FirestoreSpace(db, "users/$uid/$SPACES_GROUP/eg"))

    private suspend fun seed(uid: String) {
        for (space in roots(uid)) {
            for (chunk in USER_COLLECTIONS.chunked(200)) {
                val batch = db.batch()
                for (group in chunk) batch.set(space.collection(group).document(if (group == INITIALIZATION_GROUP) REFERENCES_MARKER_ID else "qa-$group"), mapOf<String, Any?>("id" to "qa-$group", "amountMinor" to 12_345L))
                batch.commit()
            }
        }
        db.collection("users/$uid/$SPACES_GROUP").document("eg").set(mapOf<String, Any?>("id" to "eg", "countryCode" to "EG"))
        db.document("users/$uid").set(mapOf<String, Any?>("note" to "qa"))
    }

    /** كل المستندات الفاضلة تحت المستخدم في كل المجموعات المعروفة (البلدين + سجل البلاد + مستند الحساب). */
    private suspend fun leftovers(uid: String): List<String> {
        val out = mutableListOf<String>()
        for (space in roots(uid)) {
            for (group in USER_COLLECTIONS) {
                for (d in space.collection(group).get(Source.SERVER).documents) out += "${space.root}/$group/${d.id}"
            }
        }
        for (d in db.collection("users/$uid/$SPACES_GROUP").get(Source.SERVER).documents) out += "spaces/${d.id}"
        if (db.document("users/$uid").get(Source.SERVER).rawData() != null) out += "users/$uid"
        return out
    }

    @Test fun eraseLeavesNothingUnderTheUserInBothCountries() = runBlocking<Unit> {
        withTimeout(240_000) {
            seed(victim)
            seed(bystander)
            assertEquals(USER_COLLECTIONS.size * 2 + 2, leftovers(victim).size, "البذر ناقص")
            assertTrue(db.document("users/$victim/initialization/$REFERENCES_MARKER_ID").get(Source.SERVER).rawData() != null, "التجهيز ما اتكتبش")
            assertTrue(db.document("users/$victim/$SPACES_GROUP/eg/initialization/$REFERENCES_MARKER_ID").get(Source.SERVER).rawData() != null)

            val erased = FirestoreAccountEraser { db }.eraseAll(victim)

            assertEquals(emptyList(), leftovers(victim), "فضل حاجة تحت users/$victim")
            assertTrue(erased >= USER_COLLECTIONS.size * 2, "عدّ الممسوح $erased")
            assertEquals(USER_COLLECTIONS.size * 2 + 2, leftovers(bystander).size, "المستخدم التاني اتلمس")
        }
    }
}
