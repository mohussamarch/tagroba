package app.masroufy.core

/**
 * نصوص شاشات منطقة «الاستثمار» (`ui/screens/investment/`) — **الملف ده بتاع المنطقة بس** (المناطق بتتبني بالتوازي، ARCHITECTURE §31.31).
 * المفاتيح في `TextKeys.kt` **تحت سطر «منطقة الاستثمار»** بس. الفصحى للسعودية والافتراضي · المصري لمصر (OVERRIDES §66) · الإنجليزي
 * (كتابة Claude — مستني مراجعة المالك §40). النص من النموذج التفاعلي بالحرف (الفصحى من `space = السعودية` والمصري من `مصر`).
 * **ممنوع «·» جنب رقم عربي** ⇒ «،». الملف قرّب على 300 سطر؟ اعمل `TextsAreaInvestment2.kt` بخرايط جديدة وضمّها هنا بـ`+`.
 */
internal val MSA_AREA_INVESTMENT_TEXTS: Map<TextKey, String> = mapOf<TextKey, String>() + MSA_AREA_CALC_TEXTS

internal val EGYPTIAN_AREA_INVESTMENT_TEXTS: Map<TextKey, String> = mapOf<TextKey, String>() + EGYPTIAN_AREA_CALC_TEXTS

internal val ENGLISH_AREA_INVESTMENT_TEXTS: Map<TextKey, String> = mapOf<TextKey, String>() + ENGLISH_AREA_CALC_TEXTS
