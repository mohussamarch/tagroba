package app.masroufy.core

/**
 * نصوص جولة r041 (إصلاحات فحص 0.4 على المحاكي): الرئيسية الفاضية حسب حالة القراءة (A8) · شريط «مربوطة بـ» وتأكيد «فك الربط» وتراجعه
 * في تفاصيل العملية التابعة (A11). فصحى مختصرة للسعودية · مصري لمصر · إنجليزي.
 */
internal val MSA_R041_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.HOME_EMPTY_BODY_READING to "القراءة تعمل — أول رسالة بنك مفهومة تظهر هنا تلقائيًا.",
    UiKey.HOME_EMPTY_BODY_NO_SMS to "أضف أول عملية أو استورد كشف حسابك.",
    UiKey.LINKED_BANNER to "مربوطة بـ «{0}»",
    UiKey.LINKED_BANNER_GONE to "المصروف حُذف",
    UiKey.LINKED_UNLINK_ASK_TITLE to "فك الربط؟",
    UiKey.LINKED_UNLINK_ASK_BODY to "ستُحسب «{0}» دخلًا من جديد، ويرجع المصروف لمبلغه الكامل.",
    UiKey.LINKED_UNLINK_DONE to "فُكّ الربط",
    UiKey.LINKED_RELINK_FAILED to "تعذّر إرجاع الربط — تغيّرت العملية أو المصروف.",
)

internal val EGYPTIAN_R041_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.HOME_EMPTY_BODY_READING to "القراية شغالة — أول رسالة بنك مفهومة هتظهر هنا لوحدها.",
    UiKey.HOME_EMPTY_BODY_NO_SMS to "ضيف أول عملية أو استورد كشف حسابك.",
    UiKey.LINKED_BANNER to "مربوطة بـ «{0}»",
    UiKey.LINKED_BANNER_GONE to "المصروف اتمسح",
    UiKey.LINKED_UNLINK_ASK_TITLE to "نفك الربط؟",
    UiKey.LINKED_UNLINK_ASK_BODY to "«{0}» هتتحسب دخل تاني، والمصروف يرجع لمبلغه كامل.",
    UiKey.LINKED_UNLINK_DONE to "اتفك الربط",
    UiKey.LINKED_RELINK_FAILED to "ما قدرناش نرجّع الربط — العملية أو المصروف اتغيّر.",
)

internal val ENGLISH_R041_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.HOME_EMPTY_BODY_READING to "Reading is on — your first bank message we understand shows up here by itself.",
    UiKey.HOME_EMPTY_BODY_NO_SMS to "Add your first transaction or import your bank statement.",
    UiKey.LINKED_BANNER to "Linked to \"{0}\"",
    UiKey.LINKED_BANNER_GONE to "The expense was deleted",
    UiKey.LINKED_UNLINK_ASK_TITLE to "Unlink?",
    UiKey.LINKED_UNLINK_ASK_BODY to "\"{0}\" counts as income again, and the expense goes back to its full amount.",
    UiKey.LINKED_UNLINK_DONE to "Unlinked",
    UiKey.LINKED_RELINK_FAILED to "Couldn't link it back — the transaction or the expense changed.",
)
