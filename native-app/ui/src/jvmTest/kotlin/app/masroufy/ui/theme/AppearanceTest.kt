package app.masroufy.ui.theme

import app.masroufy.core.Currency
import app.masroufy.core.Language
import app.masroufy.core.RIYAL_SIGN
import app.masroufy.core.Texts
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.amount
import app.masroufy.ui.text.money
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** المظهر الليلي وإخفاء الأرقام (تصميم `NightPrivacy`): الحفظ والقراية، و«••••» + العملة في مكان التنسيق الوحيد. */
class AppearanceTest {
    private class MemoryStore(val data: MutableMap<String, String> = mutableMapOf()) : PrefStore {
        override fun read(key: String): String? = data[key]
        override fun write(key: String, value: String?) { if (value == null) data.remove(key) else data[key] = value }
    }

    @AfterTest
    fun reset() {
        Appearance.resetForTest()
        Texts.language = Language.AR
    }

    @Test
    fun themeIsDecodedAndDefaultsToLight() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.decode(null))
        assertEquals(ThemeMode.LIGHT, ThemeMode.decode("garbage"))
        for (m in ThemeMode.entries) assertEquals(m, ThemeMode.decode(m.code))
        Appearance.attach(MemoryStore(), systemDark = false)
        assertEquals(ThemeMode.LIGHT, Appearance.mode)
    }

    @Test
    fun themeChoiceIsPersistedAndReadBackOnNextStart() {
        val store = MemoryStore()
        Appearance.attach(store, systemDark = false)
        Appearance.chooseMode(ThemeMode.DARK)
        assertEquals("dark", store.data["theme_mode"])
        Appearance.chooseHideOnStart(true)
        assertEquals("1", store.data["hide_numbers_on_start"])
        // «فتح التطبيق تاني»: حالة جديدة بنفس التخزين
        Appearance.resetForTest()
        Appearance.attach(store, systemDark = false)
        assertEquals(ThemeMode.DARK, Appearance.mode)
        assertTrue(Appearance.isDark)
        assertEquals(1f, Appearance.progress)
        assertTrue(Appearance.hideOnStart)
        assertTrue(Appearance.numbersHidden, "اخفي الأرقام لما أفتح البرنامج ⇒ مخفية من الأول")
    }

    @Test
    fun systemModeFollowsThePhone() {
        Appearance.attach(MemoryStore(), systemDark = true)
        assertTrue(Appearance.isDark)
        Appearance.systemDark = false
        assertFalse(Appearance.isDark)
    }

    @Test
    fun darkPaletteHasTheApprovedColours() {
        Appearance.attach(MemoryStore(mutableMapOf("theme_mode" to "dark")), systemDark = false)
        assertEquals(0xFF0F1A17.toInt(), Ink.background.toArgbInt())
        assertEquals(0xFF18241F.toInt(), Ink.card.toArgbInt())
        assertEquals(0xFFE6EFEA.toInt(), Ink.text.toArgbInt())
        assertEquals(0xFF9AB0A7.toInt(), Ink.muted.toArgbInt())
        assertEquals(0xFF3FBF8F.toInt(), Ink.primary.toArgbInt())
    }

    @Test
    fun hiddenMoneyIsDotsPlusCurrency() {
        Appearance.attach(MemoryStore(), systemDark = false)
        Texts.language = Language.AR
        val shown = money(420_000, Currency.SAR)
        assertTrue("4,200.00" in shown)
        Appearance.toggleNumbers()
        val hidden = money(420_000, Currency.SAR)
        assertTrue("••••" in hidden, hidden)
        assertTrue(RIYAL_SIGN in hidden, "العملة تفضل ظاهرة: $hidden")
        assertFalse(Regex("[0-9]").containsMatchIn(hidden), hidden)
        assertEquals("••••", amount(420_000, Currency.SAR))
        // الجنيه: العملة بعد النقط
        assertTrue(money(1_000, Currency.EGP).startsWith("••••"))
        // العلامة (+/−) كمان بتتخبى
        assertEquals("••••", amountLabel(5_000, Currency.SAR, AmountTone.INCOME, showCurrency = false))
        Appearance.toggleNumbers()
        assertEquals(shown, money(420_000, Currency.SAR))
    }
}

private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
    ((alpha * 255f + 0.5f).toInt() shl 24) or ((red * 255f + 0.5f).toInt() shl 16) or ((green * 255f + 0.5f).toInt() shl 8) or (blue * 255f + 0.5f).toInt()
