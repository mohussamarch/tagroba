package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsKind
import app.masroufy.core.transferPartyOf
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.smsSenderKey

/**
 * **§75-2 «ده راتبك؟»** (قرار المالك 2026-10-08): رسالة **راتب** من غير اسم الجهة بتتسأل مرة لكل مرسل في البلد (السؤال على الرسالة —
 * `SmsReviewLine.question`، والرد `AutoRecordSms.answerSalary`)، و**بعدها لوحدها**: الرد «أيوه» محفوظ ⇒ رسايل الراتب من المرسل ده بتتسجل
 * «راتب» مؤكد وهي بتتسجل (المالك أو التسجيل التلقائي). «لأ» ⇒ ما بتتسألش تاني وبتتسجل «مش متصنف» (مستنية برّه الدخل — §75-1).
 * رسالة راتب **فيها** الجهة (طرف التحويل — `transferPartyOf`) ما بتتلمسش هنا: ليها سؤال «ده مرتب من …؟» (§64 — `applyKnownPayerSalary`).
 * رسايل البنك بس، والعملية اللي نوعها اتأكد قبل كده ما بتتكتبش فوقها.
 */
class SmsSalaryEffect(private val inbox: SmsInboxPort, private val spaceId: String) : RecordEffect {
    override suspend fun prepare(ctx: RecordContext) {
        val answers = mutableMapOf<String, Boolean?>()
        for (line in ctx.lines) {
            val sms = line.sms ?: continue
            val t = line.transaction
            if (sms.kind != SmsKind.SALARY || t.observedDirection != Direction.IN || t.economicKindConfirmed) continue
            if (transferPartyOf(t) != null) continue
            val sender = smsSenderKey(sms.sourceName)
            val yes = if (sender in answers) answers[sender] else inbox.salaryAnswer(spaceId, sender).also { answers[sender] = it }
            if (yes != true) continue
            line.transaction = t.copy(
                economicKind = EconomicKind.SALARY, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = ctx.nowIso,
            )
        }
    }
}
