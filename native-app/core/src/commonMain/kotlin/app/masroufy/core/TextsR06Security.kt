package app.masroufy.core

/**
 * r06-security — نصوص جولة الأمان (PLAN-0.6 §1 · مفاتيح SEC_ في [R06Key]). ملف لوحده زي `TextsR06Split.kt`.
 * قاعدة الكتابة (المالك 2026-10-22): سطر واحد قصير.
 */
internal val MSA_R06_SECURITY_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.SEC_SIGN_OUT_WIPE to "الخروج يوقف أيضًا قراءة رسائل البنك ويمسح نسخها من هذا الجوال.",
)

internal val EGYPTIAN_R06_SECURITY_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.SEC_SIGN_OUT_WIPE to "الخروج كمان بيوقف قراية رسايل البنك ويمسح نسخها من الجوال ده.",
)

internal val ENGLISH_R06_SECURITY_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.SEC_SIGN_OUT_WIPE to "Signing out also stops reading bank SMS and deletes their copies from this phone.",
)
