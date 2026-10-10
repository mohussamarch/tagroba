package app.masroufy.core

/**
 * عقد C0 — نصوص الشريحة S6: الداخل المستني · الراتب والشهر المالي · التذكير الأسبوعي (§75-1 · §75-3 · §75-15).
 * فصحى مختصرة للسعودية · مصري لمصر · إنجليزي (§66). الشريحة بتملك الملف ده وبتضيف مفاتيحها تحت علامتها في `TextKeys.kt`.
 * العدد في التذكير بأربع صيغ عشان العدد والمعدود يتطابقوا بالعربي ([weeklyAsksLine] بتختار): واحدة · اتنين · 3–10 جمع · 11 وفوق مفرد.
 */
internal val MSA_MONTH_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ADVISOR_WEEKLY_ASKS_ONE to "لديك عملية واحدة تحتاج إلى تأكيد",
    TextKey.ADVISOR_WEEKLY_ASKS_TWO to "لديك عمليتان تحتاجان إلى تأكيد",
    TextKey.ADVISOR_WEEKLY_ASKS_FEW to "لديك {0} عمليات تحتاج إلى تأكيد",
    TextKey.ADVISOR_WEEKLY_ASKS_MANY to "لديك {0} عملية تحتاج إلى تأكيد",
    TextKey.INCOME_PENDING_LABEL to "وارد بانتظار تأكيدك — غير محسوب في الدخل",
    TextKey.SALARY_COUNTS_NEXT_MONTH to "يُحتسب في الشهر الجديد",
    TextKey.ASK_INCOMING_KIND to "ما نوع هذا المبلغ الوارد؟",
)

internal val EGYPTIAN_MONTH_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ADVISOR_WEEKLY_ASKS_ONE to "عندك عملية واحدة محتاجة تأكيد",
    TextKey.ADVISOR_WEEKLY_ASKS_TWO to "عندك عمليتين محتاجين تأكيد",
    TextKey.ADVISOR_WEEKLY_ASKS_FEW to "عندك {0} عمليات محتاجة تأكيد",
    TextKey.ADVISOR_WEEKLY_ASKS_MANY to "عندك {0} عملية محتاجة تأكيد",
    TextKey.INCOME_PENDING_LABEL to "داخل مستني تأكيدك — مش محسوب في الدخل",
    TextKey.SALARY_COUNTS_NEXT_MONTH to "بيتحسب للشهر الجديد",
    TextKey.ASK_INCOMING_KIND to "الفلوس اللي دخلت دي إيه؟",
)

internal val ENGLISH_MONTH_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ADVISOR_WEEKLY_ASKS_ONE to "1 transaction needs your confirmation",
    TextKey.ADVISOR_WEEKLY_ASKS_TWO to "2 transactions need your confirmation",
    TextKey.ADVISOR_WEEKLY_ASKS_FEW to "{0} transactions need your confirmation",
    TextKey.ADVISOR_WEEKLY_ASKS_MANY to "{0} transactions need your confirmation",
    TextKey.INCOME_PENDING_LABEL to "Incoming, awaiting your confirmation — not counted as income",
    TextKey.SALARY_COUNTS_NEXT_MONTH to "Counts in the new month",
    TextKey.ASK_INCOMING_KIND to "What is this incoming money?",
)
