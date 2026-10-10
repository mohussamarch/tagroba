package app.masroufy.usecase

import app.masroufy.core.IsoDate
import app.masroufy.core.PendingAsk
import app.masroufy.port.AskSource

/**
 * أسئلة رسايل البنك لعدّ «محتاجة تأكيد» والتذكير الأسبوعي (§75-15 — عقد C0): **سؤال لكل رسالة مستنية** في الصندوق —
 * «ده راتبك؟» ([app.masroufy.core.AskKind.IS_SALARY] — §75-2) لو عليها، وإلا «مستنية تأكيدك» ([app.masroufy.core.AskKind.SMS_WAITING]).
 * الرسالة المستنية مستنية **دلوقتي** مهما كان يومها ⇒ نافذة الأيام ما بتتطبقش. مفيش إشعار جديد: عدد المستني بيغذّي تنبيه `SMS_CONFIRM` القديم.
 */
class SmsAskSource(private val auto: AutoRecordSms) : AskSource {
    override suspend fun pending(from: IsoDate, to: IsoDate): List<PendingAsk> = auto.waitingAsks()
}
