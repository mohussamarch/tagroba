package app.masroufy.usecase

import app.masroufy.core.Golden
import app.masroufy.core.PdfPage
import app.masroufy.core.PositionedWord
import app.masroufy.core.field
import app.masroufy.core.str
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `ReadPdfStatement` **من غير ملف مرجع خاص بيه**: التطبيق الحالي بينادي مستخرج الـPDF مباشرة (مش منفذ)،
 * فمينفعش يتشغّل على صفحات وهمية من غير ملف PDF حقيقي — وكشف المالك الحقيقي ممنوع في المستودع العام.
 * البديل: صفحات `pdf.json` (25 حالة من محلل التطبيق الحالي) بتدخل من المنفذ، والناتج بيتقارن
 * بصفوفها المسجلة، والنص القانوني ورسالة الفشل مكتوبين هنا **بالحرف من `readPdfStatement.ts`**.
 */
class ReadPdfStatementTest {
    private class FakePages(private val pages: List<PdfPage>) : PdfPagesPort {
        var progressCalls = 0

        override suspend fun read(data: ByteArray, onProgress: ((page: Int, total: Int) -> Unit)?): List<PdfPage> {
            pages.forEachIndexed { i, _ -> onProgress?.invoke(i + 1, pages.size); progressCalls++ }
            return pages
        }
    }

    @Test
    fun matchesTheCurrentParserOnEveryRecordedPageSet() {
        val cases = Golden.cases("pdf", "parseAlrajhiPdf")
        assertEquals(25, cases.size, "عدد حالات pdf.json.parseAlrajhiPdf اتغير — راجع الاختبار ده")
        var failures = 0
        var successes = 0
        for (case in cases) {
            val pages = case.getValue("in").jsonArray.map { p ->
                PdfPage(
                    p.field("pageNumber").jsonPrimitive.int,
                    p.field("words").jsonArray.map { PositionedWord(it.field("x").jsonPrimitive.double, it.field("y").jsonPrimitive.double, it.field("text").str) },
                )
            }
            val expected = case.getValue("out")
            val expectedRows = expected.field("rows").jsonArray
            val port = FakePages(pages)
            if (expectedRows.isEmpty()) {
                val error = assertFailsWith<PdfReadError> { runBlocking { ReadPdfStatement(port).read(ByteArray(0)) } }
                assertEquals(
                    "قرينا ${expected.field("pagesRead").jsonPrimitive.int} صفحة بس ملقيناش أي عملية. القارئ متظبط على كشف حساب مصرف الراجحي — لو ده كشف بنك تاني، مش هينفع دلوقتي.",
                    error.message,
                )
                failures++
                continue
            }
            val result = runBlocking { ReadPdfStatement(port).read(ByteArray(0)) { _, _ -> } }
            assertEquals(expected.field("pagesRead").jsonPrimitive.int, result.pagesRead)
            assertEquals(expected.field("errors").jsonArray.size, result.errors.size)
            assertEquals(expectedRows.map { it.field("lineNumber").jsonPrimitive.int }, result.rows.map { it.lineNumber })
            // «تاريخ|مبلغ|اتجاه|رصيد» سطر لكل عملية — والرصيد الغايب فاضي (نص `canonicalContent` في التطبيق الحالي)
            val content = expectedRows.joinToString("\n") { r ->
                val balance = r.jsonObject["statedBalanceMinor"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content ?: ""
                "${r.field("date").str}|${r.field("amountMinor").jsonPrimitive.content}|${r.field("direction").str}|$balance"
            }
            assertEquals(content, result.content)
            assertEquals(pages.size, port.progressCalls)
            successes++
        }
        assertTrue(failures >= 1 && successes >= 1, "لازم الحالتين يتغطّوا: نجاح=$successes فشل=$failures")
    }
}
