package app.masroufy.device

import app.masroufy.core.ParsedRow
import app.masroufy.core.PdfPage
import app.masroufy.core.PositionedWord
import app.masroufy.core.parseQnbPdf
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * كشف QNB **مخترع** (نفس شكل `QnbPdfTest` في `core`) بيتكتب ملف PDF حقيقي بالكود — عشان قارئ الـPDF على كل جهاز
 * (PdfBox على أندرويد · PDFKit على الآيفون) يتختبر **من غير كشف المالك** (مش موجود على GitHub ولا على الماك).
 * كل الأرقام والأسماء هنا مخترعة.
 *
 * المرجع: الكلمات اللي اتكتب بيها الملف (كل قطعة `Tj` لوحدها عند `x` و`y` = خط القاعدة) — وهي نفس اللي pdfjs (قارئ
 * التطبيق الحالي) بيطلّعه من الملف ده بالظبط (اتأكد بـ`scripts/real/exportPdfPages.mjs` على الكمبيوتر 2026-10-02).
 * مكتبة الجهاز لازم تطلّع نفس الكلمات في حدود [TOLERANCE] نقطة، ونفس العمليات من `parseQnbPdf`.
 */
object SyntheticStatement {
    const val FONT_SIZE = 8
    const val TOLERANCE = 0.6
    private const val WIDTH = 842
    private const val HEIGHT = 842

    private fun w(x: Double, y: Double, text: String) = PositionedWord(x, y, text)

    private fun header(): List<PositionedWord> = listOf(
        w(21.0, 769.0, "Date"), w(111.0, 769.0, "Description"), w(439.0, 769.0, "Debit"), w(553.0, 769.0, "Credit"), w(667.0, 769.0, "Balance"),
        w(21.0, 797.0, "Account 1234567890123"),
    )

    val pages: List<PdfPage> = listOf(
        PdfPage(
            1,
            header() + listOf(
                w(21.0, 749.0, "2026-01-01"), w(111.0, 749.0, "BALANCE BROUGHT FORWARD----2026-01-01"), w(542.0, 749.0, "-"), w(621.0, 749.0, "1000.00"), w(735.0, 749.0, "1000.00"),
                w(111.0, 731.0, "IPN TRANSFER-VC123456789-IPNTEST_PERSON1a2b3cACC -2026-01-"),
                w(21.0, 727.0, "2026-01-02"), w(542.0, 727.0, "-"), w(634.0, 727.0, "250"), w(735.0, 727.0, "1250.00"),
                w(111.0, 722.0, "02"),
                w(111.0, 706.0, "CARD PURCHASE-DB987654321-CARD PURCHASE 03/01/26 10:15-SAMPLE"),
                w(21.0, 702.0, "2026-01-03"), w(519.0, 702.0, "-41.25"), w(656.0, 702.0, "-"), w(735.0, 702.0, "1208.75"),
                w(111.0, 697.0, "STORE FUEL12345 CAIRO 1234-2026-01-03"),
            ),
        ),
        PdfPage(
            2,
            header() + listOf(
                w(111.0, 749.0, "CARD PURCHASE-DB987654322-CARD PURCHASE 05/01/26 18:40-DEMO"),
                w(21.0, 745.0, "2026-01-05"), w(523.0, 745.0, "-8.75"), w(656.0, 745.0, "-"), w(735.0, 745.0, "1200.00"),
                w(111.0, 740.0, "BAKERY 4321-2026-01-05"),
                w(21.0, 723.0, "2026-01-05"), w(111.0, 723.0, "BALANCE CARRIED FORWARD----2026-01-05"), w(542.0, 723.0, "-"), w(626.0, 723.0, "1200.00"), w(735.0, 723.0, "1200.00"),
            ),
        ),
    )

    /** ملف PDF 1.4 صغير: خط Helvetica من الـ14 الأساسيين (مش مضمّن)، وكل كلمة `Tj` لوحدها بمكانها المطلق (`Tm`). */
    fun pdf(): ByteArray {
        val objects = mutableListOf<String>()
        val pageIds = pages.indices.map { 4 + it * 2 }
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        objects += "<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >>"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
        for ((i, page) in pages.withIndex()) {
            val stream = page.words.joinToString("\n") { "BT /F1 $FONT_SIZE Tf 1 0 0 1 ${num(it.x)} ${num(it.y)} Tm (${escape(it.text)}) Tj ET" }
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $WIDTH $HEIGHT] /Resources << /Font << /F1 3 0 R >> >> /Contents ${pageIds[i] + 1} 0 R >>"
            objects += "<< /Length ${stream.length} >>\nstream\n$stream\nendstream"
        }
        val out = StringBuilder("%PDF-1.4\n")
        val offsets = objects.mapIndexed { i, body ->
            val at = out.length
            out.append("${i + 1} 0 obj\n").append(body).append("\nendobj\n")
            at
        }
        val xref = out.length
        out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        for (o in offsets) out.append(o.toString().padStart(10, '0')).append(" 00000 n \n")
        out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        // ASCII بس ⇒ عدد الحروف = عدد البايتات، فالـoffsets صح
        check(out.all { it.code < 128 })
        return out.toString().encodeToByteArray()
    }

    private fun num(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString()

    private fun escape(text: String) = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    private fun key(r: ParsedRow) = listOf(r.date, r.amountMinor, r.direction, r.statedBalanceMinor, r.merchantName, r.description)

    /** الجهاز قرا الملف زي الكلمات اللي اتكتب بيها: نفس الصفحات · نفس الكلمات في مكانها · نفس العمليات. */
    fun assertReadsLikeTheWords(read: List<PdfPage>) {
        assertEquals(pages.map { it.pageNumber }, read.map { it.pageNumber }, "أرقام الصفحات")
        for ((expected, actual) in pages.zip(read)) {
            val missing = expected.words.filter { e ->
                actual.words.none { a -> a.text.trim() == e.text && abs(a.x - e.x) <= TOLERANCE && abs(a.y - e.y) <= TOLERANCE }
            }
            missing.firstOrNull()?.let { m ->
                val near = actual.words.minByOrNull { abs(it.y - m.y) + abs(it.x - m.x) }
                fail("صفحة ${expected.pageNumber}: ${missing.size} من ${expected.words.size} كلمة مش في مكانها — أولها $m · الأقرب ليها $near · أول اللي اتقرا ${actual.words.take(8)}")
            }
            assertEquals(expected.words.size, actual.words.size, "صفحة ${expected.pageNumber}: كلمات زيادة ${actual.words.map { it.text } - expected.words.map { it.text }.toSet()}")
        }
        val reference = parseQnbPdf(pages)
        val device = parseQnbPdf(read)
        assertEquals(3, reference.rows.size, "الكشف المخترع نفسه: 3 عمليات")
        assertTrue(device.errors.isEmpty(), device.errors.toString())
        assertEquals(reference.rows.map(::key), device.rows.map(::key), "نفس العمليات")
        assertEquals(reference.openingBalanceMinor to reference.closingBalanceMinor, device.openingBalanceMinor to device.closingBalanceMinor)
    }
}
