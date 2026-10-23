package app.masroufy.core

/**
 * r06-links (OVERRIDES §112 — خريطة الربط): الرِجل التلقائية للتحويل بين حساباتك · تعديل العملية · الحسابات (آخر 4 · الإخفاء) ·
 * المسح والأرشفة. ملف لوحده زي `TextsR06Split.kt`. الوسم كلمة أو اتنين (قاعدة المالك 2026-10-22).
 */
internal val MSA_R06_LINKS_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.OWN_LEG_AUTO_REASON to "تحويل بين حساباتك: سُجّل دخوله في الحساب الآخر تلقائيًا",
    R06Key.OWN_LEG_AUTO_NOTE to "سُجّلت تلقائيًا من تحويلك — عدّلها لو اختلفت",
)

internal val EGYPTIAN_R06_LINKS_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.OWN_LEG_AUTO_REASON to "تحويل بين حساباتك: دخوله في الحساب التاني اتسجل لوحده",
    R06Key.OWN_LEG_AUTO_NOTE to "اتسجلت لوحدها من تحويلك — عدّلها لو مختلفة",
)

internal val ENGLISH_R06_LINKS_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.OWN_LEG_AUTO_REASON to "Transfer between your accounts: the incoming side was recorded automatically",
    R06Key.OWN_LEG_AUTO_NOTE to "Recorded automatically from your transfer. Edit it if it differs",
)
