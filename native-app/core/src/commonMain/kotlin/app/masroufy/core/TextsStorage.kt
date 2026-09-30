package app.masroufy.core

/**
 * نصوص طبقة التخزين (مستند ناقص أو نوعه غلط) باللغتين. ملف لوحده عشان حد الـ300 سطر؛ بيتدمج في `Texts.kt`.
 * الرسايل دي نادرة (مستند اتكتب غلط من برا) بس لازم تقول **أنهي مستند وأنهي حقل** بدل وقوع من غير سبب.
 */
internal val ARABIC_STORAGE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.DOC_FIELD_MISSING to "المستند {0} ناقصه الحقل «{1}».",
    TextKey.DOC_FIELD_TYPE to "الحقل «{1}» في المستند {0} نوعه غلط.",
    TextKey.DOC_FIELD_VALUE to "الحقل «{1}» في المستند {0} قيمته مش معروفة: {2}",
)

internal val ENGLISH_STORAGE_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.DOC_FIELD_MISSING to "Document {0} is missing the field \"{1}\".",
    TextKey.DOC_FIELD_TYPE to "The field \"{1}\" in document {0} has the wrong type.",
    TextKey.DOC_FIELD_VALUE to "The field \"{1}\" in document {0} has an unknown value: {2}",
)
