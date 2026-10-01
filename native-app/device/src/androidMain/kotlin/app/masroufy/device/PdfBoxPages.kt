package app.masroufy.device

import android.content.Context
import app.masroufy.core.PdfPage
import app.masroufy.core.PositionedWord
import app.masroufy.usecase.PdfPagesPort
import app.masroufy.usecase.PdfReadError
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * كلمات صفحات الـPDF بأماكنها على أندرويد (PdfBox-Android) — نفس الشكل اللي القارئين (`parseAlrajhiPdf` · `parseQnbPdf`) بياخدوه.
 * **المرجع = قراية التطبيق الحالي** (pdfjs على الويب، `scripts/real/exportPdfPages.mjs`): القطعة = نص متصل على نفس السطر،
 * و`x` = حافتها الشمال و`y` = ارتفاعها من تحت الصفحة. PdfBox بيدّي حروف منفردة، فبنجمّعها بنفس المنطق:
 * فراغ أكبر من [GAP_SPLIT] × عرض المسافة = قطعة جديدة، وأصغر من كده بس أكبر من [GAP_SPACE] × عرضها = مسافة جوه نفس القطعة.
 * ⚠️ اتقاس على كشفين المالك (الراجحي وQNB) بالمقارنة صف بصف مع قراية pdfjs — `RealPdfPagesTest`.
 */
class PdfBoxPages(private val context: Context) : PdfPagesPort {
    override suspend fun read(data: ByteArray, onProgress: ((page: Int, total: Int) -> Unit)?): List<PdfPage> = withContext(Dispatchers.Default) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val doc = try {
            PDDocument.load(data)
        } catch (_: InvalidPasswordException) {
            throw PdfReadError("الملف محمي بكلمة سر — افتحه واحفظه من غير كلمة سر وجرّب تاني")
        } catch (e: Exception) {
            throw PdfReadError("الملف ده مش PDF أو تالف")
        }
        doc.use { pdf ->
            (1..pdf.numberOfPages).map { n ->
                val collector = Collector()
                collector.startPage = n
                collector.endPage = n
                collector.getText(pdf)
                onProgress?.invoke(n, pdf.numberOfPages)
                PdfPage(n, toWords(collector.positions))
            }
        }
    }

    /** بيلم كل حرف بمكانه — من غير ما يكتب نص. */
    private class Collector : PDFTextStripper() {
        val positions = mutableListOf<TextPosition>()

        init {
            sortByPosition = false
        }

        override fun processTextPosition(text: TextPosition) {
            positions += text
        }
    }

    internal companion object {
        const val GAP_SPLIT = 1.5
        const val GAP_SPACE = 0.3

        /** الحروف ⇒ قطع: نفس السطر (نفس خط القاعدة تقريبًا) ومتجاورة. */
        fun toWords(chars: List<TextPosition>): List<PositionedWord> {
            val lines = chars.filter { it.unicode.isNotBlank() }.groupBy { Math.round(it.yDirAdj * 2) / 2.0 }
            val out = mutableListOf<PositionedWord>()
            for ((_, line) in lines) {
                val sorted = line.sortedBy { it.xDirAdj }
                var start = sorted.first()
                val text = StringBuilder(start.unicode)
                var prevEnd = start.xDirAdj + start.widthDirAdj
                for (c in sorted.drop(1)) {
                    val space = c.widthOfSpace.takeIf { it > 0 } ?: (c.widthDirAdj.takeIf { it > 0 } ?: 2.5f)
                    val gap = c.xDirAdj - prevEnd
                    if (gap > GAP_SPLIT * space) {
                        out += word(start, text)
                        start = c
                        text.clear()
                    } else if (gap > GAP_SPACE * space) {
                        text.append(' ')
                    }
                    text.append(c.unicode)
                    prevEnd = c.xDirAdj + c.widthDirAdj
                }
                out += word(start, text)
            }
            return out
        }

        private fun word(first: TextPosition, text: CharSequence) =
            PositionedWord(first.xDirAdj.toDouble(), (first.pageHeight - first.yDirAdj).toDouble(), text.toString())
    }
}
