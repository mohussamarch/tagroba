package app.masroufy.device

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.masroufy.core.ParsedRow
import app.masroufy.core.PdfPage
import app.masroufy.core.PositionedWord
import app.masroufy.core.hasArabic
import app.masroufy.core.parseAlrajhiPdf
import app.masroufy.core.parseQnbPdf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * قراية الـPDF على الموبايل (PdfBox) قدام قراية الكمبيوتر (pdfjs — نفس التطبيق الحالي) على **كشفين المالك الحقيقيين**:
 * القارئ لازم يطلّع **نفس الصفوف بالظبط** (التاريخ · المبلغ · الاتجاه · الرصيد) — KOTLIN_PLAN §2 «صفر فرق».
 * الملفات بتدخل APK الاختبار من `files/` وقت البناء (برا Git)؛ لو مش موجودة الاختبار بيتخطّى بسطر في السجل.
 * السجل (`adb logcat -s MasroufyPdf`) فيه أعداد وأوقات بس.
 */
@RunWith(AndroidJUnit4::class)
class RealPdfPagesTest {
    private val tag = "MasroufyPdf"
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    private fun asset(name: String): ByteArray? = runCatching { context.assets.open("owner/$name").use { it.readBytes() } }.getOrNull()

    private fun jsonPages(name: String): List<PdfPage> = Json.parseToJsonElement(asset(name)!!.toString(Charsets.UTF_8)).jsonArray.map { p ->
        PdfPage(
            p.jsonObject.getValue("pageNumber").jsonPrimitive.int,
            p.jsonObject.getValue("words").jsonArray.map { w ->
                val o = w.jsonObject
                PositionedWord(o.getValue("x").jsonPrimitive.double, o.getValue("y").jsonPrimitive.double, o.getValue("text").jsonPrimitive.content)
            },
        )
    }

    private fun key(r: ParsedRow) = listOf(r.date, r.amountMinor, r.direction, r.statedBalanceMinor)

    private fun arabicStats(pages: List<PdfPage>): String {
        val words = pages.flatMap { it.words }
        return "كلمات فيها عربي ${words.count { hasArabic(it.text) }} · كلمات فيها «?» ${words.count { '?' in it.text }} من ${words.size}"
    }

    private fun compare(pdf: String, json: String, parse: (List<PdfPage>) -> Pair<List<ParsedRow>, Int>) = runBlocking {
        val bytes = asset(pdf)
        if (bytes == null || asset(json) == null) Log.i(tag, "⚠️ $pdf مش في الـAPK — الاختبار اتخطّى")
        assumeTrue("كشف المالك مش موجود على الجهاز ده", bytes != null && asset(json) != null)
        val started = System.nanoTime()
        val device = PdfBoxPages(target).read(bytes!!)
        val ms = (System.nanoTime() - started) / 1_000_000
        val reference = jsonPages(json)
        val (deviceRows, deviceErrors) = parse(device)
        val (referenceRows, _) = parse(reference)
        Log.i(tag, "$pdf: ${device.size} صفحة في $ms ms · صفوف الموبايل ${deviceRows.size} · صفوف الكمبيوتر ${referenceRows.size} · مرفوض $deviceErrors")
        Log.i(tag, "$pdf — الموبايل: ${arabicStats(device)}")
        Log.i(tag, "$pdf — الكمبيوتر: ${arabicStats(reference)}")
        assertEquals(0, deviceErrors, "صفوف مرفوضة من قراية الموبايل")
        assertEquals(referenceRows.size, deviceRows.size, "نفس عدد العمليات")
        val firstDiff = deviceRows.indices.firstOrNull { key(deviceRows[it]) != key(referenceRows[it]) }
        assertTrue(firstDiff == null, "أول اختلاف عند العملية رقم ${firstDiff?.plus(1)} (${firstDiff?.let { deviceRows[it].raw }})")
    }

    @Test fun alrajhiMatchesTheComputerRowByRow() = compare("alrajhi-statement.pdf", "alrajhi-pdf-pages.json") { pages ->
        parseAlrajhiPdf(pages).let { it.rows to it.errors.size }
    }

    @Test fun qnbMatchesTheComputerRowByRow() = compare("qnb-statement.pdf", "qnb-pdf-pages.json") { pages ->
        parseQnbPdf(pages).let { it.rows to it.errors.size }
    }
}
