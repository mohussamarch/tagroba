package app.masroufy.core

/** المظهر الليلي (فاتح · غامق · زي الجوال) وزرار إخفاء الأرقام — مفاتيح NP_ في [R06Key]. ملف لوحده عشان `TextsR06.kt` عدّى الـ300 سطر. */
internal val MSA_R06_NIGHT_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.NP_THEME_TITLE to "المظهر",
    R06Key.NP_THEME_LIGHT to "فاتح",
    R06Key.NP_THEME_DARK to "داكن",
    R06Key.NP_THEME_AUTO to "حسب الجوال",
    R06Key.NP_EYE_HIDE to "أخفِ الأرقام",
    R06Key.NP_EYE_SHOW to "أظهر الأرقام",
    R06Key.NP_START_TITLE to "أخفِ الأرقام عند فتح التطبيق",
    R06Key.NP_START_SUB to "اضغط على العين لإظهارها",
)

internal val EGYPTIAN_R06_NIGHT_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.NP_THEME_TITLE to "المظهر",
    R06Key.NP_THEME_LIGHT to "فاتح",
    R06Key.NP_THEME_DARK to "غامق",
    R06Key.NP_THEME_AUTO to "زي الجوال",
    R06Key.NP_EYE_HIDE to "اخفي الأرقام",
    R06Key.NP_EYE_SHOW to "بيّن الأرقام",
    R06Key.NP_START_TITLE to "اخفي الأرقام لما أفتح البرنامج",
    R06Key.NP_START_SUB to "تدوس على العين عشان تبان",
)

internal val ENGLISH_R06_NIGHT_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.NP_THEME_TITLE to "Appearance",
    R06Key.NP_THEME_LIGHT to "Light",
    R06Key.NP_THEME_DARK to "Dark",
    R06Key.NP_THEME_AUTO to "Same as phone",
    R06Key.NP_EYE_HIDE to "Hide numbers",
    R06Key.NP_EYE_SHOW to "Show numbers",
    R06Key.NP_START_TITLE to "Hide numbers when I open the app",
    R06Key.NP_START_SUB to "Tap the eye to show them",
)
