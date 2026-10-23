package app.masroufy.ui.theme

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * smoke 0.5.4 ملاحظة 3: البرنامج فضل فاتح مع الجوال غامق — ده الافتراضي «فاتح» (`ThemeMode.decode` ⇒ LIGHT)، و«زي الجوال» لازم يتختار.
 * الاختبار ده بيأكد إن اختيار «داكن» جوه البرنامج بيقلب ألوان الرئيسية والعمليات والأشخاص: الألوان من [Ink] بتبقى الغامقة، والشاشات
 * التلاتة مفيهاش لون ثابت `Color(0x…)` (كل لون فيهم من الرموز اللي بتتبدل).
 */
class DarkChoiceScreensTest {
    private class MemoryStore : PrefStore {
        val data = mutableMapOf<String, String>()
        override fun read(key: String): String? = data[key]
        override fun write(key: String, value: String?) { if (value == null) data.remove(key) else data[key] = value }
    }

    @AfterTest fun reset() = Appearance.resetForTest()

    @Test fun choosingDarkInTheAppTurnsTheTokensDarkEvenWhenThePhoneIsLight() {
        val store = MemoryStore()
        Appearance.attach(store, systemDark = false)
        assertEquals(Color(0xFFEFF3ED), Ink.background, "الافتراضي فاتح")
        Appearance.chooseMode(ThemeMode.DARK)
        assertTrue(Appearance.isDark)
        // `MasroufyTheme` بيحرّك progress لـ1 في 300ms — هنا الحالة بعد الانتقال (نفس اللي `attach` بيعمله عند الفتح التالي)
        Appearance.resetForTest()
        Appearance.attach(store, systemDark = false)
        assertEquals(1f, Appearance.progress)
        assertEquals(Color(0xFF0F1A17), Ink.background)
        assertEquals(Color(0xFFE6EFEA), Ink.text)
        assertEquals(Color(0xFF18241F), Ink.card)
    }

    @Test fun homeOperationsAndPeopleUseOnlySwitchingColours() {
        val root = listOf("src/commonMain/kotlin/app/masroufy/ui", "ui/src/commonMain/kotlin/app/masroufy/ui").map(::File).first { it.isDirectory }
        val literal = Regex("""Color\(0x""")
        val found = listOf("screens/home", "screens/operations", "screens/people").flatMap { dir ->
            File(root, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }
                .filter { literal.containsMatchIn(it.readText()) }.map { it.name }.toList()
        }
        assertEquals(emptyList(), found, "لون ثابت ما بيتبدلش مع الغامق")
    }
}
