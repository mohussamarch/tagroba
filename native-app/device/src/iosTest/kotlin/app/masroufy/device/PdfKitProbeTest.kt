package app.masroufy.device

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSMakeRange
import platform.Foundation.attribute
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.UIKit.NSFontAttributeName
import platform.UIKit.UIFont
import kotlin.test.Test

/**
 * **تشخيص مؤقت** (بيطبع بس، ما بيفشلش): PDFKit بيرجّع إيه بالظبط لكل حرف وكل سطر في الكشف المخترع —
 * عشان طريقة حساب خط القاعدة وبداية الكلمة تتبني على أرقام حقيقية من ماك، مش تخمين. يتشال بعد ما القارئ يعدّي.
 */
@OptIn(ExperimentalForeignApi::class)
class PdfKitProbeTest {
    @Test fun printWhatPdfKitReturns() {
        val doc = PDFDocument(data = SyntheticStatement.pdf().toNSData())
        val page = doc.pageAtIndex(0u)!!
        val text = page.string ?: ""
        println("PROBE string(0..160)=" + text.take(160).replace("\n", "⏎").replace("\r", "↵").replace("\t", "⇥"))
        println("PROBE numberOfCharacters=${page.numberOfCharacters} length=${text.length}")
        for (i in 0 until minOf(14, text.length)) {
            val r = page.characterBoundsAtIndex(i.toLong()).useContents { "x=${origin.x} y=${origin.y} w=${size.width} h=${size.height}" }
            val sel = page.selectionForRange(NSMakeRange(i.toULong(), 1uL))
            val sb = sel?.boundsForPage(page)?.useContents { "x=${origin.x} y=${origin.y} w=${size.width} h=${size.height}" }
            val font = sel?.attributedString?.attribute(NSFontAttributeName, 0uL, null) as? UIFont
            println("PROBE [$i] '${text[i]}' char{$r} sel{$sb} font=${font?.fontName} pt=${font?.pointSize} asc=${font?.ascender} desc=${font?.descender}")
        }
        val all = page.selectionForRect(page.boundsForBox(kPDFDisplayBoxMediaBox))
        val lines = all?.selectionsByLine().orEmpty()
        println("PROBE lines=${lines.size}")
        for (l in lines.take(8)) {
            val s = l as platform.PDFKit.PDFSelection
            val b = s.boundsForPage(page).useContents { "x=${origin.x} y=${origin.y} w=${size.width} h=${size.height}" }
            println("PROBE line '${s.string}' {$b}")
        }
    }
}
