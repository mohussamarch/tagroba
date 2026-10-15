package app.masroufy.ui.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** المساعد الوحيد بيدّي خط العلامة لحرف U+20C1 **بس** — الأرقام والكلام بخط التطبيق. */
class RiyalSpanTest {
    private val font = FontFamily.Serif

    private fun signSpans(text: AnnotatedString) = text.spanStyles.filter { it.item.fontFamily == font }.map { it.start to it.end }

    @Test
    fun marksExactlyTheSign() {
        val text = "بقي \u2066\u20C1 1,250.00\u2069 من \u2066\u20C1 2,000.00\u2069"
        val marked = withRiyalSign(AnnotatedString(text), font)
        assertEquals(text, marked.text)
        val signs = text.indices.filter { text[it] == '\u20C1' }.map { it to it + 1 }
        assertEquals(signs, signSpans(marked))
    }

    @Test
    fun textWithoutTheSignIsUntouchedAndOldStylesStay() {
        val plain = AnnotatedString("1,000.00 ج.م")
        assertSame(plain, withRiyalSign(plain, font))
        val bold = buildAnnotatedString {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append("\u20C1 5.00"); pop()
        }
        val marked = withRiyalSign(bold, font)
        assertEquals(listOf(0 to 1), signSpans(marked))
        assertTrue(marked.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 6 })
    }

    /** الماسح: مفيش «ر.س» مكتوبة بالإيد في نص عرض جوه `ui/` (التعليقات مسموحة). */
    @Test
    fun noHardCodedRiyalLabelInUiCode() {
        val root = File("src/commonMain/kotlin")
        assertTrue(root.isDirectory, root.absolutePath)
        val hits = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, line) ->
                val code = line.trim()
                !code.startsWith("*") && !code.startsWith("/*") && !code.startsWith("//") && code.substringBefore("//").let { it.contains('"') && it.contains("ر.س") }
            }.map { "${f.name}:${it.index + 1}" }
        }.toList()
        assertTrue(hits.isEmpty(), "«ر.س» في كود الشاشات: $hits")
    }
}
