package app.masroufy.core

/**
 * نصوص جولة r041b (مشاكل أول تشغيل من فحص المحاكي r041): كارت الرئيسية «عندك N رسالة من البنك مستنية تأكيدك» (P3) — بيفتح «رسائل البنك»
 * على قايمة «أكّد الأشكال» على طول. فصحى مختصرة للسعودية · مصري لمصر · إنجليزي.
 */
internal val MSA_R041B_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.HOME_SMS_WAIT_ONE to "لديك رسالة من البنك بانتظار تأكيدك",
    UiKey.HOME_SMS_WAIT_TWO to "لديك رسالتان من البنك بانتظار تأكيدك",
    UiKey.HOME_SMS_WAIT_FEW to "لديك {0} رسائل من البنك بانتظار تأكيدك",
    UiKey.HOME_SMS_WAIT_MANY to "لديك {0} رسالة من البنك بانتظار تأكيدك",
    UiKey.HOME_SMS_WAIT_BODY to "أكّد رسالة واحدة من كل شكل مرة واحدة، وبعدها تُسجَّل رسائل هذا الشكل تلقائيًا.",
)

internal val EGYPTIAN_R041B_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.HOME_SMS_WAIT_ONE to "عندك رسالة من البنك مستنية تأكيدك",
    UiKey.HOME_SMS_WAIT_TWO to "عندك رسالتين من البنك مستنيين تأكيدك",
    UiKey.HOME_SMS_WAIT_FEW to "عندك {0} رسايل من البنك مستنية تأكيدك",
    UiKey.HOME_SMS_WAIT_MANY to "عندك {0} رسالة من البنك مستنية تأكيدك",
    UiKey.HOME_SMS_WAIT_BODY to "أكّد رسالة واحدة من كل شكل مرة واحدة، وبعدها رسايل الشكل ده هتتسجل لوحدها.",
)

internal val ENGLISH_R041B_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.HOME_SMS_WAIT_ONE to "You have 1 bank message awaiting your confirmation",
    UiKey.HOME_SMS_WAIT_TWO to "You have 2 bank messages awaiting your confirmation",
    UiKey.HOME_SMS_WAIT_FEW to "You have {0} bank messages awaiting your confirmation",
    UiKey.HOME_SMS_WAIT_MANY to "You have {0} bank messages awaiting your confirmation",
    UiKey.HOME_SMS_WAIT_BODY to "Confirm one message of each shape once — after that, messages of that shape are recorded by themselves.",
)
