package app.masroufy.usecase

import app.masroufy.core.isoInstantMillis
import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxState
import app.masroufy.port.smsSenderKey

/**
 * OVERRIDES §121-12 (يكمّل §120-10): رسالتين بنفس النص من نفس المرسل في **نفس الثانية** (مش لازم نفس الجزء من الألف) = التانية نسخة.
 * النسخة بتاخد وقت وصول الأولى (الأقدم) قبل الفهم ⇒ نفس المرجع (`SMS:` + المرسل + الوقت + النص) ⇒ بتتشال كمكررة من غير تسجيل.
 * الأولى وقتها زي ما هو ⇒ مراجع الرسايل اللي اتسجلت قبل كده ما بتتغيرش (التكرار ومسح المالك بيفضلوا شغالين على القديم).
 * الحد: الأولى لازم تكون في نفس الصفحة — لو اتسجلت واتشالت في تشغيلة قبلها، النسخة لوحدها ما بتتعرفش هنا.
 */
internal fun withSameSecondCopies(state: SmsInboxState): SmsInboxState {
    if (state.messages.size < 2) return state
    val firstAt = HashMap<Triple<String, String, String>, Pair<Long, String>>()
    fun keyOf(m: QueuedSms): Triple<String, String, String>? {
        val ms = isoInstantMillis(m.receivedAt) ?: return null
        return Triple(smsSenderKey(m.sender), ms.floorDiv(1000L).toString(), m.body.trim())
    }
    for (m in state.messages) {
        val key = keyOf(m) ?: continue
        val ms = isoInstantMillis(m.receivedAt) ?: continue
        val seen = firstAt[key]
        if (seen == null || ms < seen.first) firstAt[key] = ms to m.receivedAt
    }
    var changed = false
    val out = state.messages.map { m ->
        val first = keyOf(m)?.let { firstAt[it]?.second }
        if (first == null || first == m.receivedAt) m else m.copy(receivedAt = first).also { changed = true }
    }
    return if (changed) state.copy(messages = out) else state
}
