package app.masroufy.core

/**
 * r06-income (OVERRIDES §112-6) — «مصادر الدخل» بعد الدمج (`IncomeHub.dc.html`، فرع التصميم 0eee77c5): 7 أنواع ثابتة · جوه النوع
 * «دلوقتي» و«اللي فات» · سؤال «ده مرتب؟» اتنقل لـ«محتاجة مراجعة» في «العمليات» (مفاتيح INC_ في [R06Key]). ملف لوحده زي `TextsR06Split.kt`.
 */
internal val MSA_R06_INCOME_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.INC_HUB_TITLE to "مصادر الدخل",
    R06Key.INC_KIND_JOB to "دوام كامل",
    R06Key.INC_KIND_PART_TIME to "دوام جزئي",
    R06Key.INC_KIND_CLIENT to "عميل أو عمل حر",
    R06Key.INC_KIND_RENT to "إيجار",
    R06Key.INC_KIND_INVESTMENT to "عائد استثمار",
    R06Key.INC_KIND_PENSION to "معاش",
    R06Key.INC_KIND_OTHER to "دخل آخر",
    R06Key.INC_ADD to "أضف",
    R06Key.INC_BEFORE to "سابقًا: {0}",
    R06Key.INC_NOW_WORK to "عملك الحالي",
    R06Key.INC_NOW to "الحالي",
    R06Key.INC_NONE_WORK to "لا عمل حاليًا",
    R06Key.INC_NONE to "لا شيء حاليًا",
    R06Key.INC_CHANGED to "غيّرت عملي",
    R06Key.INC_ADD_OTHER to "أضف آخر",
    R06Key.INC_PAST_WORK to "أماكن عملت فيها",
    R06Key.INC_PAST to "السابق",
    R06Key.INC_NOW_LINE to "{0} · {1}",
    R06Key.INC_REVIEW_SALARY to "وصل إيداع: هل هو راتب؟",
)

internal val EGYPTIAN_R06_INCOME_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.INC_HUB_TITLE to "مصادر الدخل",
    R06Key.INC_KIND_JOB to "دوام كامل",
    R06Key.INC_KIND_PART_TIME to "بارت تايم",
    R06Key.INC_KIND_CLIENT to "عميل أو فري لانس",
    R06Key.INC_KIND_RENT to "إيجار",
    R06Key.INC_KIND_INVESTMENT to "عائد استثمار",
    R06Key.INC_KIND_PENSION to "معاش",
    R06Key.INC_KIND_OTHER to "دخل تاني",
    R06Key.INC_ADD to "ضيف",
    R06Key.INC_BEFORE to "قبل كده: {0}",
    R06Key.INC_NOW_WORK to "شغلك دلوقتي",
    R06Key.INC_NOW to "دلوقتي",
    R06Key.INC_NONE_WORK to "مش شغال دلوقتي",
    R06Key.INC_NONE to "مفيش حاجة دلوقتي",
    R06Key.INC_CHANGED to "غيّرت شغلي",
    R06Key.INC_ADD_OTHER to "ضيف تاني",
    R06Key.INC_PAST_WORK to "أماكن اشتغلت فيها",
    R06Key.INC_PAST to "اللي فات",
    R06Key.INC_NOW_LINE to "{0} · {1}",
    R06Key.INC_REVIEW_SALARY to "فيه فلوس دخلت: ده مرتب؟",
)

internal val ENGLISH_R06_INCOME_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.INC_HUB_TITLE to "Income sources",
    R06Key.INC_KIND_JOB to "Full-time",
    R06Key.INC_KIND_PART_TIME to "Part-time",
    R06Key.INC_KIND_CLIENT to "Client or freelance",
    R06Key.INC_KIND_RENT to "Rent",
    R06Key.INC_KIND_INVESTMENT to "Investment return",
    R06Key.INC_KIND_PENSION to "Pension",
    R06Key.INC_KIND_OTHER to "Other income",
    R06Key.INC_ADD to "Add",
    R06Key.INC_BEFORE to "Before: {0}",
    R06Key.INC_NOW_WORK to "Your current work",
    R06Key.INC_NOW to "Current",
    R06Key.INC_NONE_WORK to "Not working now",
    R06Key.INC_NONE to "Nothing now",
    R06Key.INC_CHANGED to "I changed jobs",
    R06Key.INC_ADD_OTHER to "Add another",
    R06Key.INC_PAST_WORK to "Places you worked",
    R06Key.INC_PAST to "Earlier",
    R06Key.INC_NOW_LINE to "{0} · {1}",
    R06Key.INC_REVIEW_SALARY to "Money came in: is it salary?",
)
