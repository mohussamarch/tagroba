package app.masroufy.usecase

import app.masroufy.core.ImportSourceType
import app.masroufy.core.ParsedRow
import app.masroufy.core.PdfPage
import app.masroufy.core.RowError
import app.masroufy.core.SchemaId
import app.masroufy.core.TextKey
import app.masroufy.core.parseAlrajhiPdf
import app.masroufy.core.parseQnbPdf
import app.masroufy.core.uiText

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
    /** الكشف اتقرا بأنهي قارئ — بيتبعت للاستيراد عشان دفعة الاستيراد تعرف مصدرها. */
    val schema: SchemaId = SchemaId.ALRAJHI_PDF,
    val sourceType: ImportSourceType = ImportSourceType.PDF_ALRAJHI,
)

class ReadPdfStatement(private val pages: PdfPagesPort) {
    suspend fun read(data: ByteArray, onProgress: ((page: Int, total: Int) -> Unit)? = null): PdfStatementResult {
        val read = pages.read(data, onProgress)
        val alrajhi = parseAlrajhiPdf(read)
        if (alrajhi.rows.isNotEmpty()) return PdfStatementResult(alrajhi.rows, alrajhi.errors, alrajhi.pagesRead, canonicalContent(alrajhi.rows))
        // مش الراجحي ⇒ QNB مصر (§40.3) — كل قارئ بيرفض شكل التاني، فمفيش كشف بيتقري بالغلط
        val qnb = parseQnbPdf(read)
        if (qnb.rows.isNotEmpty()) {
            return PdfStatementResult(qnb.rows, qnb.errors, qnb.pagesRead, canonicalContent(qnb.rows), SchemaId.QNB_PDF, ImportSourceType.PDF_QNB)
        }
        // الفشل بيتقال بسببه — «مفيش عمليات» لوحدها بتخلي المستخدم تايه
        throw PdfReadError(uiText(TextKey.PDF_NO_TRANSACTIONS, "${alrajhi.pagesRead}"))
    }

    /** سطر لكل عملية بالحقول اللي بتعرّفها. */
    private fun canonicalContent(rows: List<ParsedRow>): String =
        rows.joinToString("\n") { "${it.date}|${it.amountMinor}|${it.direction.wire}|${it.statedBalanceMinor ?: ""}" }
}
