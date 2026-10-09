package app.masroufy.core

/**
 * نصوص شاشات منطقة «الاستثمار» (`ui/screens/investment/`) — **الملف ده بتاع المنطقة بس** (المناطق بتتبني بالتوازي، ARCHITECTURE §31.31).
 * المفاتيح في `TextKeys.kt` **تحت سطر «منطقة الاستثمار»** بس. الفصحى للسعودية والافتراضي · المصري لمصر (OVERRIDES §66) · الإنجليزي
 * (كتابة Claude — مستني مراجعة المالك §40). النص من النموذج التفاعلي بالحرف (الفصحى من `space = السعودية` والمصري من `مصر`).
 * **ممنوع «·» جنب رقم عربي** ⇒ «،». الملف قرّب على 300 سطر؟ اعمل `TextsAreaInvestment2.kt` بخرايط جديدة وضمّها هنا بـ`+`.
 * الحاسبات (`investment/calc/`) في ملفاتها: `TextsAreaCalcSavings.kt` · `TextsAreaCalcRetire.kt` · `TextsAreaCalcRetireEgypt.kt` ·
 * `TextsAreaCalcInherit.kt` · `TextsAreaCalcInheritResult.kt`.
 */
internal val MSA_AREA_INVESTMENT_TEXTS: Map<TextKey, String> = mapOf<TextKey, String>() +
    MSA_CALC_SAVINGS_TEXTS + MSA_CALC_RETIRE_TEXTS + MSA_CALC_RETIRE_EG_TEXTS + MSA_CALC_INHERIT_TEXTS + MSA_CALC_INHERIT_RESULT_TEXTS

internal val EGYPTIAN_AREA_INVESTMENT_TEXTS: Map<TextKey, String> = mapOf<TextKey, String>() +
    EGYPTIAN_CALC_SAVINGS_TEXTS + EGYPTIAN_CALC_RETIRE_TEXTS + EGYPTIAN_CALC_RETIRE_EG_TEXTS + EGYPTIAN_CALC_INHERIT_TEXTS + EGYPTIAN_CALC_INHERIT_RESULT_TEXTS

internal val ENGLISH_AREA_INVESTMENT_TEXTS: Map<TextKey, String> = mapOf<TextKey, String>() +
    ENGLISH_CALC_SAVINGS_TEXTS + ENGLISH_CALC_RETIRE_TEXTS + ENGLISH_CALC_RETIRE_EG_TEXTS + ENGLISH_CALC_INHERIT_TEXTS + ENGLISH_CALC_INHERIT_RESULT_TEXTS
