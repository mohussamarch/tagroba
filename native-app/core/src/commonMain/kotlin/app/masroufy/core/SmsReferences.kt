package app.masroufy.core

/**
 * عقد C0 — §77-D: رقم البنك المرجعي للعملية في الرسالة (عشان «التحويل رجع» يلاقي العملية الأصلية). الشريحة S3 بتملك الملف ده.
 * المرحلة دي (C0) بترجع null دايمًا.
 */
internal fun smsReferenceOf(body: String): String? = null
