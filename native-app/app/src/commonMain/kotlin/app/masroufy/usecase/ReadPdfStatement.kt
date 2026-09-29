package app.masroufy.usecase

import app.masroufy.core.ParsedRow
import app.masroufy.core.PdfPage
import app.masroufy.core.RowError
import app.masroufy.core.parseAlrajhiPdf

/**
 * ReadPdfStatement — نقل `readPdfStatement.ts`: كشف PDF ⇒ صفوف موحّدة **من نفس نوع صفوف الـCSV**، فمنع التكرار
 * والتصنيف والحفظ بيمشوا زي ما هما. مفيش خط استيراد موازي للـPDF عن قصد: خطين = قاعدتين لمنع التكرار.
 */

/** الملف مش PDF، أو اتقرا ومفيهوش عمليات — الرسالة بلغة المستخدم. */
class PdfReadError(message: String) : IllegalStateException(message)

/**
 * استخراج كلمات الصفحات بأماكنها — PdfBox على أندرويد و PDFKit على الآيفون (KOTLIN_PLAN §2، `expect/actual`).
 * بيرمي `PdfReadError` لو الملف مش PDF أو تالف أو محمي.
 */
interface PdfPagesPort {
    suspend fun read(data: ByteArray, onProgress: ((page: Int, total: Int) -> Unit)? = null): List<PdfPage>
}

data class PdfStatementResult(
    val rows: List<ParsedRow>,
    val errors: List<RowError>,
    val pagesRead: Int,
    /**
     * نص قانوني بتتحسب منه بصمة الملف — مش بايتات الـPDF: نفس الكشف لو اتصدّر تاني بيطلع ببايتات مختلفة
     * (وقت التوليد جوه الملف). البصمة على **محتوى العمليات** بتمسك التكرار الحقيقي.
     */
    val content: String,
)

class ReadPdfStatement(private val pages: PdfPagesPort) {
    suspend fun read(data: ByteArray, onProgress: ((page: Int, total: Int) -> Unit)? = null): PdfStatementResult {
        val outcome = parseAlrajhiPdf(pages.read(data, onProgress))
        if (outcome.rows.isEmpty()) {
            // الفشل بيتقال بسببه — «مفيش عمليات» لوحدها بتخلي المستخدم تايه
            throw PdfReadError(
                "قرينا ${outcome.pagesRead} صفحة بس ملقيناش أي عملية. " +
                    "القارئ متظبط على كشف حساب مصرف الراجحي — لو ده كشف بنك تاني، " +
                    "مش هينفع دلوقتي.",
            )
        }
        return PdfStatementResult(outcome.rows, outcome.errors, outcome.pagesRead, canonicalContent(outcome.rows))
    }

    /** سطر لكل عملية بالحقول اللي بتعرّفها. */
    private fun canonicalContent(rows: List<ParsedRow>): String =
        rows.joinToString("\n") { "${it.date}|${it.amountMinor}|${it.direction.wire}|${it.statedBalanceMinor ?: ""}" }
}
