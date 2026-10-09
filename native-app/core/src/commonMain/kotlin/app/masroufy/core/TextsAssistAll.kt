package app.masroufy.core

/**
 * كل نصوص المساعد «مصروفي» (§78) في جدول واحد لكل نسخة — بتتضاف لجداول `Texts.kt`، و`TextsTest` بيعرف إنها جات بعد لقطة المصري.
 * مقسومة على كذا ملف عشان حد الـ300 سطر: الشاشات · العام والردود المتنوعة · الإجابات · الأفعال والبداية والذاكرة.
 */
internal val MSA_ASSIST_ALL_TEXTS: Map<TextKey, String> = MSA_ASSIST_SCREEN_TEXTS + MSA_ASSIST_TEXTS

internal val EGYPTIAN_ASSIST_ALL_TEXTS: Map<TextKey, String> = EGYPTIAN_ASSIST_SCREEN_TEXTS + EGYPTIAN_ASSIST_TEXTS

internal val ENGLISH_ASSIST_ALL_TEXTS: Map<TextKey, String> = ENGLISH_ASSIST_SCREEN_TEXTS + ENGLISH_ASSIST_TEXTS
