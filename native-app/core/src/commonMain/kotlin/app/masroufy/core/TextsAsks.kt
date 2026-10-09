package app.masroufy.core

/**
 * عقد C0 — نصوص الشريحة S5: «سلفة ولا دعم؟» · سداد السلفة · التصنيف بيتفتكر لوحده (§75-5 · §75-9 · §75-16).
 * فصحى مختصرة للسعودية · مصري لمصر · إنجليزي (§66). الشريحة بتملك الملف ده وبتضيف مفاتيحها تحت علامتها في `TextKeys.kt`.
 * المتغيرات: {0} المبلغ · {1} اسم الشخص · {2} الدين المفتوح معاه (بعملة العملية).
 */
internal val MSA_ASKS_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ASK_LOAN_OR_SUPPORT to "سلفة أم دعم؟",
    TextKey.ASK_LOAN_OR_SUPPORT_BODY to "حوّلت {0} إلى {1}. هل ستعود إليك (سلفة) أم لن تعود (دعم)؟",
    TextKey.ASK_DEBT_COLLECTED to "هل هذا سداد السلفة؟",
    TextKey.ASK_DEBT_COLLECTED_BODY to "وصلك {0} من {1}، وما زال عليه لك {2}.",
    TextKey.ASK_DEBT_REPAID to "هل هذا سداد دين عليك؟",
    TextKey.ASK_DEBT_REPAID_BODY to "حوّلت {0} إلى {1}، وما زال له عندك {2}.",
    TextKey.ASK_TRANSFER_NOT_PENDING to "لا يوجد على هذه العملية هذا السؤال الآن.",
)

internal val EGYPTIAN_ASKS_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ASK_LOAN_OR_SUPPORT to "سلفة ولا دعم؟",
    TextKey.ASK_LOAN_OR_SUPPORT_BODY to "حوّلت {0} لـ{1}. هترجعلك (سلفة) ولا مش راجعة (دعم)؟",
    TextKey.ASK_DEBT_COLLECTED to "ده سداد السلفة؟",
    TextKey.ASK_DEBT_COLLECTED_BODY to "جالك {0} من {1}، ولسه عليه ليك {2}.",
    TextKey.ASK_DEBT_REPAID to "ده سداد دين عليك؟",
    TextKey.ASK_DEBT_REPAID_BODY to "حوّلت {0} لـ{1}، ولسه ليه عندك {2}.",
    TextKey.ASK_TRANSFER_NOT_PENDING to "العملية دي ما عليهاش السؤال ده دلوقتي.",
)

internal val ENGLISH_ASKS_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ASK_LOAN_OR_SUPPORT to "Loan or support?",
    TextKey.ASK_LOAN_OR_SUPPORT_BODY to "You sent {0} to {1}. Will it come back to you (loan) or not (support)?",
    TextKey.ASK_DEBT_COLLECTED to "Is this the loan being paid back?",
    TextKey.ASK_DEBT_COLLECTED_BODY to "{1} sent you {0} and still owes you {2}.",
    TextKey.ASK_DEBT_REPAID to "Is this paying back what you owe?",
    TextKey.ASK_DEBT_REPAID_BODY to "You sent {0} to {1}, and you still owe them {2}.",
    TextKey.ASK_TRANSFER_NOT_PENDING to "This transaction does not have this question now.",
)
