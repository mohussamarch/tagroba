package app.masroufy.core

/**
 * PLAN-0.6 §13 (ج) — إشعارات حالة القراية في الخلفية (مفاتيح NOTIFY_SMS_ في [R06Key]). النصوص من التصميم بالحرف (`SmsAway.dc.html` — فرع
 * التصميم، OVERRIDES «✅ القراية في الخلفية»). **شاشة القفل:** من غير أي رقم ولا مبلغ ولا اسم محل ([smsStateNotice] بيتأكد).
 * العملية اللي اتسجلت لوحدها **ما لهاش إشعار خالص** (المالك 2026-10-22).
 */
internal val MSA_R06_NOTIFY_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.NOTIFY_SMS_NEW_SHAPE to "رسالة من البنك بشكل جديد تنتظر تأكيدك",
    R06Key.NOTIFY_SMS_PERMISSION_OFF to "توقفت قراءة رسائل البنك — سُحب إذن الرسائل. افتح مصروفي وأعده",
    R06Key.NOTIFY_SMS_MISSED to "وصلت رسائل بنك لم تُقرأ تلقائيًا — افتح مصروفي لقراءتها",
)

internal val EGYPTIAN_R06_NOTIFY_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.NOTIFY_SMS_NEW_SHAPE to "رسالة من البنك شكلها جديد ومستنية تأكيدك",
    R06Key.NOTIFY_SMS_PERMISSION_OFF to "قراية رسايل البنك وقفت — إذن الرسايل اتسحب. افتح مصروفي ورجّعه",
    R06Key.NOTIFY_SMS_MISSED to "فيه رسايل بنك وصلت وما اتقرتش لوحدها — افتح مصروفي يقراها",
)

internal val ENGLISH_R06_NOTIFY_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.NOTIFY_SMS_NEW_SHAPE to "A bank message in a new format is waiting for you to confirm",
    R06Key.NOTIFY_SMS_PERMISSION_OFF to "Reading bank messages stopped — the SMS permission was removed. Open Masroufy to restore it",
    R06Key.NOTIFY_SMS_MISSED to "Bank messages arrived and were not read automatically — open Masroufy to read them",
)
