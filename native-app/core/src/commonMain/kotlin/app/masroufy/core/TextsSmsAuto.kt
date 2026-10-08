package app.masroufy.core

/**
 * نصوص جلسة 31 (OVERRIDES §72): رسايل البنك بتتسجل لوحدها، وإشعار الجوال **للمحتاج تأكيد بس**.
 * `ALERT_LOCK_SMS_CONFIRM` بيظهر على شاشة القفل ⇒ **من غير أي رقم ولا اسم** (`isLockSafe` بيتفحص في الكود وفي الاختبار).
 * فصحى مختصرة للسعودية · مصري لمصر · إنجليزي (كتابة Claude ومستني مراجعة المالك زي باقي الجدول §40).
 */
internal val MSA_SMS_AUTO_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ALERT_LOCK_SMS_CONFIRM to "رسائل من البنك تنتظر تأكيدك",
    TextKey.ALERT_WHY_SMS_CONFIRM to "رسالة من البنك لم تُفهم أو تشبه عملية مسجلة، فلم تُسجَّل تلقائيًا",
    TextKey.ALERT_SMS_CONFIRM_TITLE to "رسائل بنكية تنتظر تأكيدك: {0}",
    TextKey.ALERT_SMS_CONFIRM_BODY to "افتح رسائل البنك لتأكيدها أو حذفها",
    TextKey.ALERT_CHANNEL_NAME to "تنبيهات مصروفي",
    TextKey.BACKGROUND_WORK_NOTICE to "جارٍ تحديث مصروفي",
    TextKey.SMS_AUTO_WALLET_UNKNOWN to "المحفظة غير موجودة في هذا البلد",
    TextKey.ALERT_GROUP_BANK_SMS to "رسائل البنك",
    TextKey.SMS_NOT_TRANSACTION to "ليست عملية مكتملة: حجز مبلغ أو طلب لم يُنفَّذ أو رسالة معلومات",
)

internal val EGYPTIAN_SMS_AUTO_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ALERT_LOCK_SMS_CONFIRM to "فيه رسايل من البنك مستنية تأكيدك",
    TextKey.ALERT_WHY_SMS_CONFIRM to "رسالة من البنك ما اتفهمتش أو شبه عملية متسجلة، فما اتسجلتش لوحدها",
    TextKey.ALERT_SMS_CONFIRM_TITLE to "رسايل بنك مستنية تأكيدك: {0}",
    TextKey.ALERT_SMS_CONFIRM_BODY to "افتح رسايل البنك وأكّدها أو شيلها",
    TextKey.ALERT_CHANNEL_NAME to "تنبيهات مصروفي",
    TextKey.BACKGROUND_WORK_NOTICE to "مصروفي بيحدّث",
    TextKey.SMS_AUTO_WALLET_UNKNOWN to "المحفظة دي مش موجودة في البلد دي",
    TextKey.ALERT_GROUP_BANK_SMS to "رسايل البنك",
    TextKey.SMS_NOT_TRANSACTION to "مش عملية خلصت: حجز مبلغ أو طلب لسه ما اتنفذش أو رسالة معلومات",
)

internal val ENGLISH_SMS_AUTO_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ALERT_LOCK_SMS_CONFIRM to "Bank messages are waiting for your confirmation",
    TextKey.ALERT_WHY_SMS_CONFIRM to "A bank message wasn't understood or looks like a recorded transaction, so it wasn't recorded automatically",
    TextKey.ALERT_SMS_CONFIRM_TITLE to "Bank messages waiting for you: {0}",
    TextKey.ALERT_SMS_CONFIRM_BODY to "Open bank messages to confirm or remove them",
    TextKey.ALERT_CHANNEL_NAME to "Masroufy alerts",
    TextKey.BACKGROUND_WORK_NOTICE to "Masroufy is updating",
    TextKey.SMS_AUTO_WALLET_UNKNOWN to "That wallet isn't in this country",
    TextKey.ALERT_GROUP_BANK_SMS to "Bank messages",
    TextKey.SMS_NOT_TRANSACTION to "Not a completed transaction: a hold, a pending request or an information message",
)
