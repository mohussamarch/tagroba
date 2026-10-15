package app.masroufy.core

/**
 * نصوص قرارات المالك عن البداية 2026-10-15 مساءً (OVERRIDES §91): زرارين «جاهز» («افتح الرئيسية» · «كمّل بروفايلك») · تسليم رسايل البنك
 * بعد أول تشغيل (الإذن بشرح التطبيق الأول ثم كارت هادي في الرئيسية لو اترفض) · سطر «القراءة لم تبدأ» المحايد (مش «أوقفتَ») ·
 * سطر آخر 4 أرقام في كارت البنك. فصحى مختصرة للسعودية · مصري لمصر · إنجليزي.
 */
internal val MSA_FIRST_RUN_1015_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.ONB_OPEN_HOME to "افتح الرئيسية",
    UiKey.ONB_COMPLETE_PROFILE to "كمّل بروفايلك",
    UiKey.ONB_LAST4_TRANSFER_NOTE to "بدون هذه الأرقام تنتظر رسائل التحويل بين حساباتك حتى تسألك.",
    UiKey.SMS_HANDOFF_CARD_TITLE to "اسمح بقراءة رسائل البنك",
    UiKey.SMS_HANDOFF_CARD_BODY to "اخترتَ رسائل البنك مصدرًا لعملياتك. اسمح بالإذن متى شئت لتُسجَّل عملياتك تلقائيًا.",
    UiKey.SMS_HANDOFF_ALLOW to "اسمح الآن",
    UiKey.SMS_HANDOFF_LATER to "لاحقًا",
    UiKey.SMS_HANDOFF_ON to "تعمل القراءة التلقائية الآن لرسائل بنكك.",
    UiKey.SMS_HANDOFF_ALLOWED to "سُمح بإذن الرسائل. أضف بنكك من «إعداد القراءة».",
    UiKey.BANK_SMS_STATUS_SETUP to "القراءة لم تبدأ بعد",
    UiKey.BANK_SMS_STATUS_SETUP_BODY to "اسمح بإذن الرسائل لنقرأ رسائل بنكك تلقائيًا.",
)

internal val EGYPTIAN_FIRST_RUN_1015_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.ONB_OPEN_HOME to "افتح الرئيسية",
    UiKey.ONB_COMPLETE_PROFILE to "كمّل بروفايلك",
    UiKey.ONB_LAST4_TRANSFER_NOTE to "من غير الأرقام دي، رسايل التحويل بين حساباتك هتستنى وتسألك.",
    UiKey.SMS_HANDOFF_CARD_TITLE to "اسمح بقراية رسايل البنك",
    UiKey.SMS_HANDOFF_CARD_BODY to "اخترت رسايل البنك مصدر لعملياتك. اسمح بالإذن وقت ما تحب وعملياتك هتتسجل لوحدها.",
    UiKey.SMS_HANDOFF_ALLOW to "اسمح دلوقتي",
    UiKey.SMS_HANDOFF_LATER to "بعدين",
    UiKey.SMS_HANDOFF_ON to "القراية التلقائية شغالة دلوقتي لرسايل بنكك.",
    UiKey.SMS_HANDOFF_ALLOWED to "الإذن اتسمح. ضيف بنكك من «إعداد القراية».",
    UiKey.BANK_SMS_STATUS_SETUP to "القراية لسه ما بدأتش",
    UiKey.BANK_SMS_STATUS_SETUP_BODY to "اسمح بإذن الرسايل عشان نقرا رسايل بنكك لوحدها.",
)

internal val ENGLISH_FIRST_RUN_1015_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.ONB_OPEN_HOME to "Open home",
    UiKey.ONB_COMPLETE_PROFILE to "Complete your profile",
    UiKey.ONB_LAST4_TRANSFER_NOTE to "Without these digits, transfer messages between your accounts will wait and ask you.",
    UiKey.SMS_HANDOFF_CARD_TITLE to "Allow reading bank messages",
    UiKey.SMS_HANDOFF_CARD_BODY to "You chose bank messages as your source. Allow the permission whenever you like and your operations record themselves.",
    UiKey.SMS_HANDOFF_ALLOW to "Allow now",
    UiKey.SMS_HANDOFF_LATER to "Later",
    UiKey.SMS_HANDOFF_ON to "Automatic reading of your bank's messages is on.",
    UiKey.SMS_HANDOFF_ALLOWED to "Messages permission allowed. Add your bank in reading settings.",
    UiKey.BANK_SMS_STATUS_SETUP to "Reading hasn't started yet",
    UiKey.BANK_SMS_STATUS_SETUP_BODY to "Allow the messages permission so we can read your bank's messages automatically.",
)
