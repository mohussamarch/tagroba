package app.masroufy.usecase

import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import app.masroufy.port.smsSenderKey

/**
 * r041c — اللي التشغيلات اللي فاتت شافته (في الذاكرة بس، طول ما البرنامج مفتوح): معرّفات الرسايل اللي اتعاينت ([seen]) وعلامة الحالة اللي
 * اتعاينت بيها ([mark]). «اسحب لتحت» (`AutoRecordSms.runNew`) بيقرا **الجديد بس** لو العلامة ما اتغيرتش — على جوال المالك كان بيعيد فهم
 * ومعاينة كل الرسايل المستنية (مئات) في كل سحبة. بيتلمس جوه `SMS_RECORD_LOCK` بس.
 */
class SmsRunMemo {
    internal var mark: String? = null
    internal val seen: MutableSet<String> = HashSet()
}

/**
 * كل اللي بيقرر إن رسالة **مستنية** تتسجل لوحدها ولا لأ من غير ما توصل رسالة جديدة: المرسلين المفعّلين · الأشكال اللي اتعلّمت (§77-A) ·
 * محفظة كل بنك (§72) · المحافظ وأرقامها (§75-11) · رد «ده راتبك؟» (§75-2). اتغير ⇒ لفة كاملة على الصندوق (زي `run`).
 * الترتيب ثابت (مرتب) عشان نفس الحالة تدّي نفس العلامة.
 */
internal suspend fun smsMarkOf(inbox: SmsInboxPort, lanes: List<SmsLane>, box: SmsInboxState): String = buildString {
    val senders = box.senders.map(::smsSenderKey).distinct().sorted()
    append(senders)
    for (lane in lanes) {
        append('|').append(lane.spaceId)
        append(inbox.learnedShapes(lane.spaceId).toList().sortedBy { it.first }.map { (k, v) -> k to v.sorted() })
        append(inbox.senderWallets(lane.spaceId).toList().sortedBy { it.first })
        append(lane.wallets.listAll().sortedBy { it.id })
        for (s in box.senders.distinct().sorted()) append(inbox.salaryAnswer(lane.spaceId, s))
    }
}
