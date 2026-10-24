package app.masroufy.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.masroufy.core.MoneyPrivacy

/**
 * المظهر المختار: فاتح · غامق · زي الجوال. الكود اللي بيتحفظ على الجهاز ثابت — ما يتغيرش.
 *
 * **الافتراضي [LIGHT] = قرار المالك (OVERRIDES §117-1، 2026-10-24): «فاتح دايمًا».** البرنامج بيبدأ فاتح مهما كان الجوال غامق، والداكن
 * بيشتغل بس لو المالك اختاره من «المظهر» في الإعدادات. ([SYSTEM] لسه اختيار موجود — بس عمره ما بيبقى الافتراضي.) قبل كده كان اختيار
 * مطوّر مؤقت (`a7de9cf6` — «theme default is light for now») — ما تغيّروش من غير قرار جديد من المالك.
 */
enum class ThemeMode(val code: String) {
    LIGHT("light"), DARK("dark"), SYSTEM("system");

    companion object {
        /** كود محفوظ ⇒ الوضع. أي حاجة مش معروفة (أو فاضي) ⇒ [LIGHT]. */
        fun decode(code: String?): ThemeMode = entries.firstOrNull { it.code == code } ?: LIGHT
    }
}

/** تخزين بسيط مفتاح/قيمة على الجهاز (أندرويد: SharedPreferences). الاختبارات بتستعمل نسخة في الذاكرة. */
interface PrefStore {
    fun read(key: String): String?

    fun write(key: String, value: String?)
}

/**
 * المظهر وإخفاء الأرقام (تصميم `NightPrivacy`): حالة واحدة مراقَبة بتتحفظ على الجهاز.
 * - الألوان في [Ink] والزجاج والظل بيقروا [progress] (0 فاتح … 1 غامق) فكل شاشة بتتبدل من غير ما تتعدّل، و[MasroufyTheme] بيحرّك [progress] في 300ms.
 * - الأرقام: [numbersHidden] بيتقري من [MoneyPrivacy] (مكان واحد — `formatMoney`).
 */
object Appearance {
    private const val KEY_MODE = "theme_mode"
    private const val KEY_HIDE_ON_START = "hide_numbers_on_start"

    private var store: PrefStore? = null
    // §117-1: فاتح أول مرة (قرار المالك) — مش زي الجوال
    var mode: ThemeMode by mutableStateOf(ThemeMode.LIGHT)
        private set
    var hideOnStart: Boolean by mutableStateOf(false)
        private set
    var numbersHidden: Boolean by mutableStateOf(false)
        private set

    /** مظهر الجوال دلوقتي (بيتحدّث من [MasroufyTheme]). */
    var systemDark: Boolean by mutableStateOf(false)

    /** 0 = فاتح · 1 = غامق (بين الاتنين أثناء الانتقال). */
    var progress: Float by mutableFloatStateOf(0f)

    /** الغامق مطلوب دلوقتي؟ (الهدف — مش وسط الانتقال). */
    val isDark: Boolean get() = when (mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> systemDark }

    /** بيقرا المحفوظ من الجهاز ويركّب قارئ الأرقام. بيتنادى مرة عند فتح التطبيق، قبل أول رسم. */
    fun attach(store: PrefStore, systemDark: Boolean) {
        this.store = store
        this.systemDark = systemDark
        mode = ThemeMode.decode(runCatching { store.read(KEY_MODE) }.getOrNull())
        hideOnStart = runCatching { store.read(KEY_HIDE_ON_START) }.getOrNull() == "1"
        numbersHidden = hideOnStart
        progress = if (isDark) 1f else 0f
        MoneyPrivacy.reader = { numbersHidden }
    }

    fun chooseMode(value: ThemeMode) {
        mode = value
        runCatching { store?.write(KEY_MODE, value.code) }
    }

    fun chooseHideOnStart(value: Boolean) {
        hideOnStart = value
        runCatching { store?.write(KEY_HIDE_ON_START, if (value) "1" else "0") }
    }

    /** زرار العين: بيخفي كل المبالغ أو يظهرها لحد الضغطة الجاية (مش بيتحفظ — اللي بيتحفظ [hideOnStart]). */
    fun toggleNumbers() {
        numbersHidden = !numbersHidden
    }

    /** للاختبارات: رجوع للوضع الأول ومن غير تخزين. */
    fun resetForTest() {
        store = null
        mode = ThemeMode.LIGHT
        hideOnStart = false
        numbersHidden = false
        systemDark = false
        progress = 0f
        MoneyPrivacy.reader = { false }
    }
}

/** لون بين الفاتح والغامق حسب [Appearance.progress] — بيتقري جوه الرسم فالشاشة بتتبدل لوحدها. `0xAARRGGBB`. */
internal fun mix(light: Long, dark: Long): Color {
    val p = Appearance.progress
    return if (p <= 0f) Color(light) else if (p >= 1f) Color(dark) else lerp(Color(light), Color(dark), p)
}
