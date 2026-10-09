package app.masroufy.core

/**
 * عقد C0 — §77-A «وضع التعلّم»: بصمة **شكل** رسالة البنك (مش نصها). الشريحة S1 بتملك الملف ده.
 * المرحلة دي (C0) بترجع null دايمًا = ولا شكل اتعلّم.
 */

/** بصمة شكل رسالة سعودية واضحة [shape]، أو null. */
internal fun saudiLearnKey(body: String, shape: SmsShape): String? = null

/** بصمة شكل رسالة مصرية واضحة [shape]، أو null. */
internal fun egyptLearnKey(body: String, shape: SmsShape): String? = null
