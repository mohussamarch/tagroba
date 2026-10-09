package app.masroufy.core

/**
 * عقد C0 — §77-D: «تم رد المبلغ» / «التحويل رجع» ⇒ [SmsKind.RETURNED]. الشريحة S3 بتملك الملف ده.
 * المرحلة دي (C0) بترجع النوع زي ما هو.
 */
internal fun refineSmsKind(body: String, kind: SmsKind, direction: Direction): SmsKind = kind
