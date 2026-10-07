package app.masroufy.device

import app.masroufy.core.parseQnbPdf
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * الملف المخترع نفسه سليم — وبيتكتب في `device/build/synthetic-qnb.pdf` عشان يتقري بـpdfjs (قارئ التطبيق الحالي) على الكمبيوتر:
 * `node scripts/real/exportPdfPages.mjs native-app/device/build/synthetic-qnb.pdf <مجلد files>/synthetic-pages.json`
 */
class SyntheticPdfFileTest {
    @Test fun pdfIsWellFormedAndTheWordsMakeThreeTransactions() {
        val bytes = SyntheticStatement.pdf()
        val text = bytes.decodeToString()
        val xref = text.substringAfterLast("startxref\n").substringBefore('\n').toInt()
        assertTrue(text.startsWith("xref", xref), "startxref بيشاور على جدول xref")
        // كل مدخل في الجدول بيشاور على «n 0 obj» بتاعه بالظبط
        val entries = text.substring(xref).lines().drop(3).takeWhile { it.endsWith(" n ") }
        assertEquals(1 + 2 * SyntheticStatement.pages.size + 2, entries.size)
        for ((i, e) in entries.withIndex()) assertTrue(text.startsWith("${i + 1} 0 obj", e.take(10).toInt()), "الكائن ${i + 1}")
        assertEquals(3, parseQnbPdf(SyntheticStatement.pages).rows.size)
        File(System.getProperty("user.dir"), "build/synthetic-qnb.pdf").apply { parentFile.mkdirs() }.writeBytes(bytes)
    }
}
