package app.masroufy.core

/**
 * عقد C0 — نصوص الشريحة S4: الكشف والرسالة نفس العملية · الاشتراكات · الأقساط والجمعيات (§75-10 · §75-7 · §75-8).
 * فصحى مختصرة للسعودية · مصري لمصر · إنجليزي (§66). الشريحة بتملك الملف ده وبتضيف مفاتيحها تحت علامتها في `TextKeys.kt`.
 * `MATCH_*` = سبب حالة سطر في المعاينة (وبيتخزن مع سجل المصدر زي باقي الأسباب) · `DUE_LINK_ASK_*` = سؤال «نربطه؟» على العملية.
 */
internal val MSA_MATCHING_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.MATCH_SOURCE_STATEMENT to "كشف الحساب",
    TextKey.MATCH_SOURCE_SMS to "رسالة البنك",
    TextKey.MATCH_MERGE to "العملية نفسها المسجلة من {0} بتاريخ {1}: تُدمج معها ولا تُضاف مرة ثانية",
    TextKey.MATCH_AMBIGUOUS to "أكثر من احتمال مع {0} خلال {1} يوم؛ لذلك لم تُدمج تلقائيًا. اختر بنفسك",
    TextKey.MATCH_ALREADY_MERGED to "مسجلة بالفعل: دُمجت مع {0} في عملية واحدة",
    TextKey.DUE_LINK_ASK_INSTALLMENT to "هل هذه العملية قسط «{0}» المستحق في {1}؟",
    TextKey.DUE_LINK_ASK_ROSCA_CONTRIBUTION to "هل هذه العملية قسط جمعية «{0}» المستحق في {1}؟",
    TextKey.DUE_LINK_ASK_ROSCA_PAYOUT to "هل هذا المبلغ قبض دورك من جمعية «{0}» المستحق في {1}؟",
    TextKey.DUE_LINK_ASK_FINANCING_RECEIVED to "هل هذا المبلغ هو التمويل المستلم «{0}»؟",
)

internal val EGYPTIAN_MATCHING_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.MATCH_SOURCE_STATEMENT to "كشف الحساب",
    TextKey.MATCH_SOURCE_SMS to "رسالة البنك",
    TextKey.MATCH_MERGE to "نفس العملية اللي اتسجلت من {0} يوم {1}: هتتدمج معاها ومش هتتضاف تاني",
    TextKey.MATCH_AMBIGUOUS to "فيه أكتر من احتمال مع {0} في خلال {1} يوم، فما اتدمجتش لوحدها. اختار بنفسك",
    TextKey.MATCH_ALREADY_MERGED to "متسجلة خلاص: اتدمجت مع {0} في عملية واحدة",
    TextKey.DUE_LINK_ASK_INSTALLMENT to "العملية دي قسط «{0}» اللي ميعاده {1}؟",
    TextKey.DUE_LINK_ASK_ROSCA_CONTRIBUTION to "العملية دي قسط جمعية «{0}» اللي ميعاده {1}؟",
    TextKey.DUE_LINK_ASK_ROSCA_PAYOUT to "المبلغ ده قبض دورك من جمعية «{0}» اللي ميعاده {1}؟",
    TextKey.DUE_LINK_ASK_FINANCING_RECEIVED to "المبلغ ده التمويل «{0}» اللي استلمته؟",
)

internal val ENGLISH_MATCHING_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.MATCH_SOURCE_STATEMENT to "the statement",
    TextKey.MATCH_SOURCE_SMS to "the bank message",
    TextKey.MATCH_MERGE to "The same transaction recorded from {0} on {1}: merged into it, not added again",
    TextKey.MATCH_AMBIGUOUS to "More than one possible match with {0} within {1} days, so it was not merged automatically. Choose yourself",
    TextKey.MATCH_ALREADY_MERGED to "Already recorded: merged with {0} into one transaction",
    TextKey.DUE_LINK_ASK_INSTALLMENT to "Is this the “{0}” installment due on {1}?",
    TextKey.DUE_LINK_ASK_ROSCA_CONTRIBUTION to "Is this your “{0}” savings circle contribution due on {1}?",
    TextKey.DUE_LINK_ASK_ROSCA_PAYOUT to "Is this your payout from the “{0}” savings circle due on {1}?",
    TextKey.DUE_LINK_ASK_FINANCING_RECEIVED to "Is this the “{0}” financing you received?",
)
