package app.masroufy.core

/**
 * نصوص الحاسبات (`ui/screens/investment/calc/` — جزء من منطقة «الاستثمار»، OVERRIDES §69) مجمّعة في خريطة واحدة لكل نسخة، ومضمومة في
 * `TextsAreaInvestment.kt` بكلمة واحدة (`+ MSA_AREA_CALC_TEXTS`) — عشان الدمج مع شاشات «الاستثمار» يبقى كلمة في كل سطر.
 * المفاتيح في `TextKeys.kt` تحت سطر «حاسبات الاستثمار». القيم: `TextsAreaCalcSavings.kt` (المشترك + الادخار + «لو وضعتها في…») ·
 * `TextsAreaCalcRetire.kt` (التقاعد + السعودية) · `TextsAreaCalcRetireEgypt.kt` (مصر) · `TextsAreaCalcInherit.kt` (الورث وخطواته) ·
 * `TextsAreaCalcInheritResult.kt` (النتيجة والقسمة والمحفوظة).
 */
internal val MSA_AREA_CALC_TEXTS: Map<TextKey, String> =
    MSA_CALC_SAVINGS_TEXTS + MSA_CALC_RETIRE_TEXTS + MSA_CALC_RETIRE_EG_TEXTS + MSA_CALC_INHERIT_TEXTS + MSA_CALC_INHERIT_RESULT_TEXTS

internal val EGYPTIAN_AREA_CALC_TEXTS: Map<TextKey, String> =
    EGYPTIAN_CALC_SAVINGS_TEXTS + EGYPTIAN_CALC_RETIRE_TEXTS + EGYPTIAN_CALC_RETIRE_EG_TEXTS + EGYPTIAN_CALC_INHERIT_TEXTS +
        EGYPTIAN_CALC_INHERIT_RESULT_TEXTS

internal val ENGLISH_AREA_CALC_TEXTS: Map<TextKey, String> =
    ENGLISH_CALC_SAVINGS_TEXTS + ENGLISH_CALC_RETIRE_TEXTS + ENGLISH_CALC_RETIRE_EG_TEXTS + ENGLISH_CALC_INHERIT_TEXTS + ENGLISH_CALC_INHERIT_RESULT_TEXTS
