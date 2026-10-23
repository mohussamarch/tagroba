package app.masroufy.core

/**
 * r06-countries — منطق البلدين (OVERRIDES §111): السداد بعملة البلد التانية (CTY_FX_) · دين البلد التانية في صفحة الشخص · تحذير الأرشفة ·
 * بلد الحاجة اللي بتضيفها. ملف لوحده زي `TextsR06Split.kt`. الكلام قصير (كلمة أو اتنين للوسم — قاعدة المالك 2026-10-22).
 */
internal val MSA_R06_COUNTRY_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.CTY_FX_TOGGLE to "بعملة أخرى",
    R06Key.CTY_FX_PAID to "المبلغ المدفوع",
    R06Key.CTY_FX_RATE to "سعر الصرف (كم {0} لكل 1 {1})",
    R06Key.CTY_FX_RATE_NEEDED to "اكتب سعر الصرف",
    R06Key.CTY_FX_RESULT to "يُخصم من الدين {0}",
    R06Key.CTY_FX_SAME_CURRENCY to "هذه عملة الدين نفسها",
    R06Key.CTY_FX_DONE to "سُجّل السداد: {0} بسعر {1}",
    R06Key.CTY_ABROAD_DEBT to "هذا الدين في {0} — انتقل إليها لتسديده",
    R06Key.CTY_ABROAD_SWITCH to "انتقل",
    R06Key.CTY_ARCHIVE_OPEN to "لدى {0} دين مفتوح — هل أنت متأكد؟",
    R06Key.CTY_ADD_COUNTRY to "الدولة",
)

internal val EGYPTIAN_R06_COUNTRY_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.CTY_FX_TOGGLE to "بعملة تانية",
    R06Key.CTY_FX_PAID to "المبلغ اللي اتدفع",
    R06Key.CTY_FX_RATE to "سعر الصرف (كام {0} لكل 1 {1})",
    R06Key.CTY_FX_RATE_NEEDED to "اكتب سعر الصرف",
    R06Key.CTY_FX_RESULT to "بينزل من الدين {0}",
    R06Key.CTY_FX_SAME_CURRENCY to "دي نفس عملة الدين",
    R06Key.CTY_FX_DONE to "اتسجل السداد: {0} بسعر {1}",
    R06Key.CTY_ABROAD_DEBT to "الدين ده في {0} — بدّل ليها عشان تسدده",
    R06Key.CTY_ABROAD_SWITCH to "بدّل",
    R06Key.CTY_ARCHIVE_OPEN to "{0} ليه أو عليه دين مفتوح — متأكد؟",
    R06Key.CTY_ADD_COUNTRY to "البلد",
)

internal val ENGLISH_R06_COUNTRY_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.CTY_FX_TOGGLE to "Other currency",
    R06Key.CTY_FX_PAID to "Amount paid",
    R06Key.CTY_FX_RATE to "Rate ({0} per 1 {1})",
    R06Key.CTY_FX_RATE_NEEDED to "Enter the rate",
    R06Key.CTY_FX_RESULT to "Reduces the debt by {0}",
    R06Key.CTY_FX_SAME_CURRENCY to "That is the debt's own currency",
    R06Key.CTY_FX_DONE to "Settled {0} at {1}",
    R06Key.CTY_ABROAD_DEBT to "This debt is in {0} — switch there to settle it",
    R06Key.CTY_ABROAD_SWITCH to "Switch",
    R06Key.CTY_ARCHIVE_OPEN to "{0} still has an open debt — sure?",
    R06Key.CTY_ADD_COUNTRY to "Country",
)
