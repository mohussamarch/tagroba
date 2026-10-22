package app.masroufy.core

/**
 * PLAN-0.6 §13 (ج) — الحالات التلاتة **بس** اللي القراية في الخلفية بتطلّع فيها إشعار (المالك 2026-10-22: «احنا بنعمل اشعار للحجات المميزة
 * اللي تستاهل انه يتم التنبيه ليها فقط»). العملية اللي اتسجلت لوحدها مش منهم.
 * الضغطة على أي واحد منهم بتفتح «رسائل البنك» — المعرّف بيبدأ بـ[SMS_STATE_TAG_PREFIX] وتنفيذ الجهاز بيعرفه منه.
 */
enum class SmsStateNotice(internal val key: R06Key) {
    /** رسالة من البنك شكلها جديد ومستنية تأكيدك. */
    NEW_SHAPE(R06Key.NOTIFY_SMS_NEW_SHAPE),

    /** إذن الرسايل اتسحب والقراية لسه مفعّلة. */
    PERMISSION_OFF(R06Key.NOTIFY_SMS_PERMISSION_OFF),

    /** رسايل وصلت بعد آخر قراية وما اتقرتش لوحدها (نفس حساب كارت «فيه رسايل فاتته» — [missedSms]). */
    MISSED(R06Key.NOTIFY_SMS_MISSED),
    ;

    /** معرّف ثابت لكل حالة: نفس الحالة بتحل محل إشعارها القديم. */
    val tag: String get() = SMS_STATE_TAG_PREFIX + name.lowercase()
}

/** بداية معرّف إشعارات حالة القراية — الضغطة عليها بتفتح «رسائل البنك». */
const val SMS_STATE_TAG_PREFIX = "sms-state-"

/** نص الشريط وشاشة القفل للحالة — من المفتاح بس (من غير أي نص من برا)، وبيتفحص إنه من غير أرقام. */
fun smsStateNotice(kind: SmsStateNotice): SystemNotice {
    val notice = SystemNotice(uiText(TextKey.ALERT_LOCK_TITLE), uiText(kind.key))
    check(isLockSafe(notice.title) && isLockSafe(notice.body)) { "lock-screen text leaks a number or amount: ${kind.key}" }
    return notice
}
