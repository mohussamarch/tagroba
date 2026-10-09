package app.masroufy.core

/**
 * عقد C0 — §77-B «الرسوم عملية لوحدها»: رسوم التحويل أو المحفظة اللي في رسالة البنك. الشريحة S2 بتملك الملف ده.
 * المرحلة دي (C0) بترجع null دايمًا = مفيش رسوم اتقرت.
 */

/** رسوم رسالة سعودية من نوع [kind] ومبلغها [amount]، أو null. */
internal fun saudiFeeOf(body: String, kind: SmsKind, amount: Halalas): SmsFee? = null

/** رسوم رسالة مصرية من نوع [kind] ومبلغها [amount]، أو null. */
internal fun egyptFeeOf(body: String, kind: SmsKind, amount: Halalas): SmsFee? = null
