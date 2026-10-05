package app.masroufy.device

import app.masroufy.core.PdfPage
import app.masroufy.core.PositionedWord
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
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
import platform.PDFKit.PDFSelection
import platform.UIKit.NSFontAttributeName
import platform.UIKit.UIFont
import kotlin.math.round

/**
 * كلمات صفحات الـPDF بأماكنها على الآيفون (PDFKit — من النظام نفسه، مفيش مكتبة) — نفس الشكل اللي القارئين بياخدوه،
 * ونفس طريقة التجميع بتاعة أندرويد ([PdfBoxPages]): حروف ⇒ قطع على نفس السطر بفراغ [GAP_SPLIT] × عرض المسافة.
 * **مكان الحرف من «تحديد» الحرف** (`selectionForRange`) مش من `characterBoundsAtIndex`: التاني مستطيل الحبر نفسه (كل حرف
 * على ارتفاع شكل — «Date» اتقرت «D te» في أول تجربة)، والأول مستطيل الخط: `x` = بداية الحرف (زي pdfjs وPdfBox) والعرض = تقدّم
 * الحرف وتحته = خط القاعدة + `descender` ⇒ خط القاعدة = تحته − `descender`. (اتقاس على ماك GitHub 2026-10-03: «A» عند 21.0
 * بالظبط، وتحت كل حروف السطر 795.16 = 797 − 1.84.)
 * ⚠️ **اتجرب على كشف مخترع بس** (`SyntheticStatement` على محاكي الآيفون في GitHub) — كشفين المالك ما اتجربوش عليه
 * (مفيش ماك هنا، والكشف الحقيقي ما بيطلعش على GitHub). لما يبقى فيه ماك: نفس مقارنة `RealPdfPagesTest`.
 */
@OptIn(ExperimentalForeignApi::class)
class PdfKitPages : PdfPagesPort {
    override suspend fun read(data: ByteArray, onProgress: ((page: Int, total: Int) -> Unit)?): List<PdfPage> = withContext(Dispatchers.IO) {
        // ملف مش PDF: `initWithData` بيرجّع nil، وكوتلن بتحوّله NullPointerException من الـconstructor (اتشاف على ماك GitHub)
        val doc = (if (data.isEmpty()) null else runCatching { PDFDocument(data = data.toNSData()) }.getOrNull()) ?: throw PdfReadError(uiText(TextKey.PDF_INVALID))
        if (doc.isLocked) throw PdfReadError(uiText(TextKey.PDF_PASSWORD))
        val total = doc.pageCount.toInt()
        // PDFKit بيقبل ملف بايظ أحيانًا ويرجّع مستند من غير صفحات — ده مش «كشف فاضي»
        if (total == 0) throw PdfReadError(uiText(TextKey.PDF_INVALID))
        (0 until total).map { i ->
            val page = doc.pageAtIndex(i.toULong()) ?: throw PdfReadError(uiText(TextKey.PDF_INVALID))
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
            val selection = page.selectionForRange(NSMakeRange(idx.toULong(), 1uL))
            val (x, bottom, width, height) = (selection?.boundsForPage(page) ?: page.characterBoundsAtIndex(idx.toLong()))
                .useContents { listOf(origin.x, origin.y, size.width, size.height) }
            if (width <= 0 && height <= 0) continue
            val (descender, space) = fonts.getOrPut(bottom to height) { fontMetrics(selection, height) }
            val unit = if (ch.isHighSurrogate() && idx + 1 < text.length) text.substring(idx, idx + 2) else ch.toString()
            out += Glyph(unit, x, x + width, bottom - descender, space)
        }
        return out
    }

    /** `descender` (بالسالب) وعرض المسافة من خط الحرف نفسه؛ لو مش متاح ⇒ نسب Helvetica من ارتفاع مستطيل الخط (= حجم الخط). */
    private fun fontMetrics(selection: PDFSelection?, height: Double): Pair<Double, Double> {
        val font = selection?.attributedString?.attribute(NSFontAttributeName, 0uL, null) as? UIFont
        return if (font != null) font.descender to 0.278 * font.pointSize else -0.23 * height to 0.278 * height
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
