package app.masroufy.device

import app.masroufy.core.PdfPage
import app.masroufy.core.PositionedWord
import app.masroufy.usecase.PdfPagesPort
import app.masroufy.usecase.PdfReadError
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSMakeRange
import platform.Foundation.attribute
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFPage
import platform.UIKit.NSFontAttributeName
import platform.UIKit.UIFont
import kotlin.math.round

/**
 * كلمات صفحات الـPDF بأماكنها على الآيفون (PDFKit — من النظام نفسه، مفيش مكتبة) — نفس الشكل اللي القارئين بياخدوه،
 * ونفس طريقة التجميع بتاعة أندرويد ([PdfBoxPages]): حروف ⇒ قطع على نفس السطر بفراغ [GAP_SPLIT] × عرض المسافة.
 * PDFKit بيدّي **مستطيل** كل حرف (مش خط القاعدة زي pdfjs وPdfBox) ⇒ خط القاعدة = تحت المستطيل − `descender` الخط.
 * ⚠️ **اتجرب على كشف مخترع بس** (`SyntheticStatement` على محاكي الآيفون في GitHub) — كشفين المالك ما اتجربوش عليه
 * (مفيش ماك هنا، والكشف الحقيقي ما بيطلعش على GitHub). لما يبقى فيه ماك: نفس مقارنة `RealPdfPagesTest`.
 */
@OptIn(ExperimentalForeignApi::class)
class PdfKitPages : PdfPagesPort {
    override suspend fun read(data: ByteArray, onProgress: ((page: Int, total: Int) -> Unit)?): List<PdfPage> = withContext(Dispatchers.IO) {
        val doc = (if (data.isEmpty()) null else PDFDocument(data = data.toNSData())) ?: throw PdfReadError("الملف ده مش PDF أو تالف")
        if (doc.isLocked) throw PdfReadError("الملف محمي بكلمة سر — افتحه واحفظه من غير كلمة سر وجرّب تاني")
        val total = doc.pageCount.toInt()
        // PDFKit بيقبل ملف بايظ أحيانًا ويرجّع مستند من غير صفحات — ده مش «كشف فاضي»
        if (total == 0) throw PdfReadError("الملف ده مش PDF أو تالف")
        (0 until total).map { i ->
            val page = doc.pageAtIndex(i.toULong()) ?: throw PdfReadError("الملف ده مش PDF أو تالف")
            val words = toWords(glyphsOf(page))
            onProgress?.invoke(i + 1, total)
            PdfPage(i + 1, words)
        }
    }

    /** حرف بمكانه: [x] الحافة الشمال · [baseline] خط القاعدة من تحت الصفحة · [space] عرض المسافة بخطه. */
    internal class Glyph(val text: String, val x: Double, val right: Double, val baseline: Double, val space: Double)

    private fun glyphsOf(page: PDFPage): List<Glyph> {
        val text = page.string ?: return emptyList()
        val count = minOf(page.numberOfCharacters.toInt(), text.length)
        // الخط بيتقري مرة لكل «تحت مستطيل» + ارتفاع (نفس السطر بنفس الخط) — مش لكل حرف
        val fonts = HashMap<Pair<Double, Double>, Pair<Double, Double>>()
        val out = ArrayList<Glyph>(count)
        for (idx in 0 until count) {
            val ch = text[idx]
            if (ch.isWhitespace() || ch.isLowSurrogate()) continue
            val (x, bottom, width, height) = page.characterBoundsAtIndex(idx.toLong()).useContents { listOf(origin.x, origin.y, size.width, size.height) }
            if (width <= 0 && height <= 0) continue
            val (descender, space) = fonts.getOrPut(bottom to height) { fontMetrics(page, idx, height) }
            val unit = if (ch.isHighSurrogate() && idx + 1 < text.length) text.substring(idx, idx + 2) else ch.toString()
            out += Glyph(unit, x, x + width, bottom - descender, space)
        }
        return out
    }

    /** `descender` (بالسالب) وعرض المسافة من خط الحرف نفسه؛ لو مش متاح ⇒ نسب Helvetica من الارتفاع. */
    private fun fontMetrics(page: PDFPage, idx: Int, height: Double): Pair<Double, Double> {
        val font = page.selectionForRange(NSMakeRange(idx.toULong(), 1uL))?.attributedString
            ?.attribute(NSFontAttributeName, 0uL, null) as? UIFont
        return if (font != null) font.descender to 0.278 * font.pointSize else -0.207 * height / 1.2 to 0.278 * height / 1.2
    }

    internal companion object {
        const val GAP_SPLIT = 1.5
        const val GAP_SPACE = 0.3

        fun toWords(glyphs: List<Glyph>): List<PositionedWord> {
            val lines = glyphs.groupBy { round(it.baseline * 2) / 2.0 }
            val out = mutableListOf<PositionedWord>()
            for ((_, line) in lines) {
                val sorted = line.sortedBy { it.x }
                var start = sorted.first()
                val text = StringBuilder(start.text)
                var prevEnd = start.right
                for (g in sorted.drop(1)) {
                    val space = g.space.takeIf { it > 0 } ?: 2.5
                    val gap = g.x - prevEnd
                    if (gap > GAP_SPLIT * space) {
                        out += PositionedWord(start.x, start.baseline, text.toString())
                        start = g
                        text.clear()
                    } else if (gap > GAP_SPACE * space) {
                        text.append(' ')
                    }
                    text.append(g.text)
                    prevEnd = g.right
                }
                out += PositionedWord(start.x, start.baseline, text.toString())
            }
            return out
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
