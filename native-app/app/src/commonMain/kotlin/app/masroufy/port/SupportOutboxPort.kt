package app.masroufy.port

import app.masroufy.core.FeedbackDoc
import app.masroufy.core.SupportMessage

/**
 * صندوق «ابعتلنا» على الجهاز: **نسخة محلية دايمًا** ([add] · [all]) + قايمة «لسه ما اتبعتش» ([pending]) لحد ما الكتابة على فايربيز تنجح
 * ([sent]). كل رسالة مستنية ليها معرّف ثابت = معرّف مستندها في `inbox/{id}` (المحاولة التانية بتكتب على نفس المستند — مفيش تكرار).
 * دوال عادية (مش `suspend`) — ملفات صغيرة. التنفيذ: `FileSupportOutbox` في `:device` · `MemorySupportOutbox` للاختبار.
 */
interface SupportOutboxPort {
    /** النسخة المحلية + قايمة المستنية بالمعرّف [id]. */
    fun add(message: SupportMessage, id: String)

    /** النسخة المحلية كلها، من الأقدم للأحدث. */
    fun all(): List<SupportMessage>

    /** اللي لسه ما اتبعتش، من الأقدم للأحدث. */
    fun pending(): List<PendingSupport>

    /** اتبعت (أو مش هيتبعت أبدًا) ⇒ يتشال من المستنية. النسخة المحلية بتفضل. */
    fun sent(id: String)

    /**
     * مراجعة rules-deep-5: السيرفر رفض الكتابة ⇒ الرسالة **بتفضل مستنية** وعدّاد الرفض ([PendingSupport.refusals]) يزيد ويتحفظ —
     * دورة الخلفية بتعيد لحد [SUPPORT_MAX_REFUSALS] وبعدها بتبطّل وتفضل ظاهرة «ما وصلتش».
     */
    fun refused(id: String)

    /** OVERRIDES §121-14 «ابعتها تاني»: عدّاد الرفض يرجع صفر للي بطّلنا نعيدها ⇒ ترجع للمستنية. بيرجّع عددها. */
    fun resetRefusals(): Int
}

/** [refusals] = كام مرة السيرفر رفضها (مش النت). */
data class PendingSupport(val id: String, val message: SupportMessage, val refusals: Int = 0)

/** بعد كام رفض من السيرفر نبطّل نعيد ونعرضها «ما وصلتش» (مش لفّ للأبد). */
const val SUPPORT_MAX_REFUSALS = 5

/**
 * الكتابة في `inbox/{id}` على فايربيز (OVERRIDES §114 · PLAN-0.6 §16 — `FirestoreFeedbackInbox` في `:firestore`). بترمي لو ما اتأكدتش من
 * السيرفر في المهلة: [FeedbackRejected] = السيرفر رفض (القواعد) ⇒ مفيش فايدة من إعادة المحاولة · أي حاجة تانية (النت) ⇒ تتعاد.
 */
interface FeedbackInboxPort {
    suspend fun send(id: String, doc: FeedbackDoc)
}

class FeedbackRejected : Exception()
