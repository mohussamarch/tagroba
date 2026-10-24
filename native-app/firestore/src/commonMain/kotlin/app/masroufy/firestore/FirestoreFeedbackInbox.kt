package app.masroufy.firestore

import app.masroufy.core.FeedbackDoc
import app.masroufy.port.FeedbackInboxPort
import app.masroufy.port.FeedbackRejected
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code
import kotlinx.coroutines.withTimeoutOrNull

/**
 * «ابعتلنا» ⇒ `inbox/{id}` (OVERRIDES §114 · PLAN-0.6 §16) — **برا مساحة المستخدم**، كتابة بس (القواعد: داخل · المفاتيح دي بالظبط ·
 * `createdAt` = وقت السيرفر · لا قراية ولا تعديل ولا مسح). مفيش uid ولا إيميل في المستند.
 * السيرفر ما أكّدش في [timeoutMs] (غالبًا من غير نت — فايربيز بيفضل شايلها في طابوره) ⇒ خطأ عادي والمحاولة بتتعاد بنفس المعرّف.
 * رفض القواعد ⇒ [FeedbackRejected].
 */
class FirestoreFeedbackInbox(private val dbOf: () -> FirebaseFirestore, private val timeoutMs: Long = 15_000) : FeedbackInboxPort {
    constructor(db: FirebaseFirestore, timeoutMs: Long = 15_000) : this({ db }, timeoutMs)

    override suspend fun send(id: String, doc: FeedbackDoc) {
        val data = mapOf(
            "kind" to doc.kind, "country" to doc.country, "text" to doc.text, "appVersion" to doc.appVersion,
            "createdAt" to FieldValue.serverTimestamp,
        )
        val done = try {
            withTimeoutOrNull(timeoutMs) { dbOf().collection(COLLECTION).document(id).set(data) }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirestoreExceptionCode.PERMISSION_DENIED) throw FeedbackRejected()
            throw IllegalStateException("inbox write failed: ${e.code}")
        }
        if (done == null) throw IllegalStateException("inbox write not confirmed")
    }

    private companion object {
        const val COLLECTION = "inbox"
    }
}
