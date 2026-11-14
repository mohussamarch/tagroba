package app.masroufy.usecase

import app.masroufy.core.FEEDBACK_TEXT_MAX
import app.masroufy.core.FeedbackDoc
import app.masroufy.core.SupportMessage
import app.masroufy.port.FeedbackInboxPort
import app.masroufy.port.FeedbackRejected
import app.masroufy.port.IdGenerator
import app.masroufy.port.SUPPORT_MAX_REFUSALS
import app.masroufy.port.SupportOutboxPort
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/** smoke 0.5.4: أوفلاين، كتابة فايربيز بتفضل مستنية (~20 ث) ⇒ المحاولة الأولى بتقف بعد 5 ث و«محفوظ وهيتبعت» على طول. */
const val SUPPORT_SEND_TIMEOUT_MS = 5_000L

/** [SENT] = السيرفر أكّد الكتابة ⇒ «اتبعت» · [QUEUED] = محفوظ على الجهاز وهيتبعت في دورة الخلفية الجاية. */
enum class SupportSendResult { SENT, QUEUED }

/**
 * «ابعتلنا» (OVERRIDES §114 · PLAN-0.6 §16): النسخة على الجهاز **دايمًا** + مستند في `inbox/{id}` على فايربيز. «اتبعت» بعد ما السيرفر يأكّد بس.
 * [flush] = إعادة المحاولة للمستنية (دورة الخلفية). نفس المعرّف في كل محاولة ⇒ المستند ما بيتكررش. مراجعة rules-deep-5: السيرفر رفض
 * ([FeedbackRejected] — قواعد · حقل مش مقبول · أو المحاولة الأولى وصلت من طابور فايربيز والتانية بقت تعديل ممنوع) ⇒ **مش «اتبعت»**:
 * بتفضل مستنية وعدّاد الرفض يزيد، وبعد [SUPPORT_MAX_REFUSALS] بتبطّل تتعاد وتتعد في [undelivered] (ظاهرة «ما وصلتش» — مش لفّ للأبد).
 * [inbox] = null (المعاينة · من غير فايربيز) ⇒ محفوظ بس.
 */
class SendSupportMessage(
    private val outbox: SupportOutboxPort,
    private val inbox: FeedbackInboxPort?,
    private val ids: IdGenerator,
    private val appVersion: String,
    /** حد المحاولة الأولى (الإرسال من الشاشة). الإعادة في الخلفية ([flush]) من غير حد. */
    private val sendTimeoutMs: Long = SUPPORT_SEND_TIMEOUT_MS,
) {
    suspend fun send(message: SupportMessage): SupportSendResult {
        val m = message.copy(text = message.text.trim().take(FEEDBACK_TEXT_MAX))
        val id = ids.next("fb")
        outbox.add(m, id)
        return if (attempt(id, m)) SupportSendResult.SENT else SupportSendResult.QUEUED
    }

    /** المستنية بالترتيب؛ أول فشل في النت بيوقف (الباقي للدورة الجاية). بيرجّع عدد اللي اتبعت. */
    suspend fun flush(): Int {
        if (inbox == null) return 0
        var sent = 0
        for (p in outbox.pending()) {
            if (p.refusals >= SUPPORT_MAX_REFUSALS) continue
            try {
                inbox.send(p.id, FeedbackDoc.of(p.message, appVersion))
                outbox.sent(p.id)
                sent++
            } catch (e: CancellationException) {
                throw e
            } catch (_: FeedbackRejected) {
                outbox.refused(p.id)
            } catch (_: Exception) {
                break
            }
        }
        return sent
    }

    /** رسايل السيرفر رفضها [SUPPORT_MAX_REFUSALS] مرة ⇒ بطّلنا نعيدها. محفوظة على الجهاز، والشاشة بتعرض عددها. */
    fun undelivered(): Int = outbox.pending().count { it.refusals >= SUPPORT_MAX_REFUSALS }

    private suspend fun attempt(id: String, m: SupportMessage): Boolean {
        val box = inbox ?: return false
        return try {
            // الوقت خلص ⇒ «محفوظ» — نفس المعرّف بيتعاد في [flush] (لو الكتابة وصلت من طابور فايربيز ⇒ FeedbackRejected ⇒ بتتشال)
            withTimeoutOrNull(sendTimeoutMs) { box.send(id, FeedbackDoc.of(m, appVersion)) } ?: return false
            outbox.sent(id)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: FeedbackRejected) {
            outbox.refused(id)
            false
        } catch (_: Exception) {
            false
        }
    }
}
