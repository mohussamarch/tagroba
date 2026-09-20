package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **الجزء الوحيد في القلب اللي بيختلف من جهاز للتاني**: `nfkc` و`compareArabic` (`expect/actual`).
 * باقي الكود واحد بالحرف على الجهازين، فالخطر كله هنا.
 *
 * ملفات المرجع بتتقرا من القرص فبتشتغل على الكمبيوتر بس؛ الاختبار ده بيشتغل **على الجهازين**
 * (وعلى محاكي الآيفون في GitHub)، وقيمه المتوقعة **متولّدة من التطبيق الحالي** (Node):
 * `normalize('NFKC')` و`localeCompare(…, 'ar')` — مش مكتوبة بالإيد.
 */
class PlatformTextTest {
    @Test
    fun `التطبيع بيطابق جافاسكربت`() {
        val cases = listOf(
            "\uFE8D\uFEDF\uFE92\uFEE7\uFEDA" to "البنك",
            "\uFEFB" to "لا",
            "\u0663\u0665" to "\u0663\u0665",
            "\u06F3\u06F5" to "\u06F3\u06F5",
            "\uFDFA" to "صلى الله عليه وسلم",
            "A\u0301" to "\u00C1",
            "\uFF76" to "\u30AB",
            "\u2460" to "1",
            "\uFB01" to "fi",
            "ــ" to "ــ",
        )
        for ((input, expected) in cases) {
            assertEquals(expected, nfkc(input), "التطبيع اختلف")
        }
    }

    @Test
    fun `ترتيب الأسماء العربي بيطابق جافاسكربت`() {
        val names = listOf(
            "أحمد", "إبراهيم", "آمال", "عمر", "عبدالله", "محمد", "مُحمد", "هدى", "زينب", "ياسر", "الشركة", "شركة",
            "\uFE8D\uFEDF\uFE92\uFEE7\uFEDA", "البنك", "كافيه", "كافية",
        )
        val expected = listOf(
            "آمال", "إبراهيم", "أحمد", "\uFE8D\uFEDF\uFE92\uFEE7\uFEDA", "البنك", "الشركة", "زينب", "شركة",
            "عبدالله", "عمر", "كافية", "كافيه", "محمد", "مُحمد", "هدى", "ياسر",
        )
        assertEquals(expected, names.sortedWith { a, b -> compareArabic(a, b) })
    }

    @Test
    fun `المقارنة متسقة مع نفسها`() {
        assertEquals(0, compareArabic("محمد", "محمد"))
        assertEquals(true, compareArabic("أحمد", "محمد") < 0)
        assertEquals(true, compareArabic("محمد", "أحمد") > 0)
    }
}
