package app.masroufy.core

/**
 * مفاتيح نصوص حزمة INSIGHTS في «الاستثمار» الجديد (مواصفة النقل §4.3): كارت النصيحة وصفحة النصايح · «حبل مصادر دخلك» ·
 * «اللي رجعلك» · «الأسئلة الشائعة» · كارت الرئيسية «استثماراتك النهارده». نوع لوحده عشان `UiKey` قرّب من حد الـJVM (§4.0 بند 5).
 * النصوص في `TextsInvestInsight.kt` · `TextsInvestTips.kt` · `TextsFaq.kt`.
 */
enum class InvestInsightKey : TextRef {
    // كارت النصيحة
    TIP_ALL, TIP_CLOSE, TIP_SOURCE, TIP_WHY_GENERAL,
    TIP_WHY_EMERGENCY, TIP_WHY_MONTHLY, TIP_WHY_COMPOUND, TIP_WHY_SPREAD,



    // صفحة النصايح
    TIPS_TITLE, TIPS_INTRO, TIPS_GROUP_START, TIPS_GROUP_SPREAD, TIPS_GROUP_DEBT, TIPS_GROUP_PROTECT, TIPS_GROUP_HALAL, TIPS_FOOTER,
    TIPS_TOOL_HINT,

    // حبل مصادر دخلك
    ROPE_TITLE, ROPE_NOW_ONE, ROPE_NOW_MANY, ROPE_SUB, ROPE_WORD_ONE, ROPE_WORD_TWO, ROPE_WORD_FEW, ROPE_ONE_OFF,
    ROPE_JOB_CHANGE, ROPE_KIND_SALARY, ROPE_KIND_PART_TIME, ROPE_KIND_CLIENT, ROPE_KIND_RENT, ROPE_KIND_INVESTMENT,
    ROPE_KIND_PENSION, ROPE_KIND_OTHER, ROPE_PERIOD_ACTIVE, ROPE_PERIOD_ENDED, ROPE_PERIOD_CHAIN, ROPE_SALE, ROPE_SALE_DEFAULT,
    ROPE_SALE_PERIOD, ROPE_LINE_ONE, ROPE_LINE_MANY, ROPE_NONE, ROPE_LINK, ROPE_ARIA,

    // اللي رجعلك
    RET_TITLE, RET_MONTHS, RET_MONTHS_MANY, RET_ONCE, RET_TWICE, RET_TOTAL, RET_SPEND_LABEL, RET_SPEND_VALUE, RET_RE_LABEL,
    RET_RE_VALUE, RET_NOTE, RET_NOTE_SHARES, RET_NONE, RET_DEFAULT_LABEL, RET_NA, RET_NA_NOTE,

    // كارت الرئيسية «استثماراتك النهارده»
    HOME_SINCE_YESTERDAY, HOME_SINCE_DATE,

    // الأسئلة الشائعة
    FAQ_TITLE, FAQ_MORE_SUB, FAQ_FOOTER, FAQ_Q1, FAQ_A1_1, FAQ_A1_2, FAQ_A1_3, FAQ_A1_4, FAQ_A1_5, FAQ_A1_6, FAQ_CPI_SA, FAQ_CPI_EG,
}
