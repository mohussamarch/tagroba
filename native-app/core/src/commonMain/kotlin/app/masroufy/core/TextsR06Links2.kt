package app.masroufy.core

/**
 * r06-links2 — فجوات الربط الخمسة والمسح لكل حاجة لسه ناقصها (مفاتيح L2_ في [R06Key]). ملف لوحده زي `TextsR06Split.kt`.
 * قاعدة الكتابة (المالك 2026-10-22): الوسم كلمة أو اتنين · الشرح سطر واحد لو لازم.
 */
internal val MSA_R06_LINKS2_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.L2_SUB_ADD to "أضف اشتراكًا",
    R06Key.L2_SUB_ADD_TITLE to "اشتراك أو فاتورة جديدة",
    R06Key.L2_SUB_NAME to "الاسم",
    R06Key.L2_SUB_NAME_HINT to "كما يظهر في رسالة البنك",
    R06Key.L2_SUB_ADDED to "أُضيف: {0}",
    R06Key.L2_PLUS_DEBT to "أضف دينًا",
)

internal val EGYPTIAN_R06_LINKS2_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.L2_SUB_ADD to "ضيف اشتراك",
    R06Key.L2_SUB_ADD_TITLE to "اشتراك أو فاتورة جديدة",
    R06Key.L2_SUB_NAME to "الاسم",
    R06Key.L2_SUB_NAME_HINT to "زي ما بيظهر في رسالة البنك",
    R06Key.L2_SUB_ADDED to "اتضاف: {0}",
    R06Key.L2_PLUS_DEBT to "ضيف دين",
)

internal val ENGLISH_R06_LINKS2_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.L2_SUB_ADD to "Add subscription",
    R06Key.L2_SUB_ADD_TITLE to "New subscription or bill",
    R06Key.L2_SUB_NAME to "Name",
    R06Key.L2_SUB_NAME_HINT to "As it appears in the bank SMS",
    R06Key.L2_SUB_ADDED to "Added: {0}",
    R06Key.L2_PLUS_DEBT to "Add debt",
)
