package app.masroufy.core

/**
 * عقد C0 — نصوص الشريحة S1: رسايل البنك — وضع التعلّم · رسالة من غير تاريخ · «ده راتبك؟» · بنك بحسابين (§77-A · §77-C · §75-2 · §75-11).
 * فصحى مختصرة للسعودية · مصري لمصر · إنجليزي (§66). الشريحة بتملك الملف ده وبتضيف مفاتيحها تحت علامتها في `TextKeys.kt`.
 * كلها جوه التطبيق بس (سبب الانتظار والسؤال على الرسالة) — مش على شاشة القفل.
 */
internal val MSA_SMS_LEARN_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.SMS_WAIT_NEW_SHAPE to "أول رسالة بهذا الشكل من هذا البنك: أكّدها مرة، وبعدها تُسجَّل رسائل هذا الشكل تلقائيًا",
    TextKey.SMS_WAIT_IS_SALARY to "رسالة راتب بلا اسم الجهة: أكّد مرة أنه راتبك، وبعدها تُسجَّل رسائل الراتب من هذا البنك تلقائيًا",
    TextKey.SMS_ASK_IS_SALARY to "هل هذا راتبك؟",
    TextKey.SMS_ASK_OWN_ACCOUNT to "آخر 4 أرقام لهذا الطرف مثل حسابك الآخر: هل هو حسابك؟",
)

internal val EGYPTIAN_SMS_LEARN_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.SMS_WAIT_NEW_SHAPE to "أول رسالة بالشكل ده من البنك ده: أكّدها مرة، وبعد كده رسايل الشكل ده بتتسجل لوحدها",
    TextKey.SMS_WAIT_IS_SALARY to "رسالة راتب من غير اسم الجهة: أكّد مرة إنه راتبك، وبعد كده رسايل الراتب من البنك ده بتتسجل لوحدها",
    TextKey.SMS_ASK_IS_SALARY to "ده راتبك؟",
    TextKey.SMS_ASK_OWN_ACCOUNT to "آخر 4 أرقام للطرف ده زي حسابك التاني: ده حسابك؟",
)

internal val ENGLISH_SMS_LEARN_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.SMS_WAIT_NEW_SHAPE to "The first message in this layout from this bank: confirm it once, and later messages in the same layout are recorded automatically",
    TextKey.SMS_WAIT_IS_SALARY to "A salary message without the payer's name: confirm once that it is your salary, and later salary messages from this bank are recorded automatically",
    TextKey.SMS_ASK_IS_SALARY to "Is this your salary?",
    TextKey.SMS_ASK_OWN_ACCOUNT to "This party's last 4 digits match your other account: is it yours?",
)
