package app.masroufy.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * قارئ كشف QNB مصر على **كشف المالك الحقيقي** — على جهازه بس (`files/qnb-pdf-pages.json` من `scripts/real/exportPdfPages.mjs`)،
 * ومفيش ولا مبلغ بيتطبع أو يتكتب. الحكم: كل سطر عملية اتقري · سلسلة الرصيد ماشية عملية عملية من رصيد البداية للنهاية ·
 * ولا رقم من 5 أرقام أو أكتر طالع من القارئ (قرار المالك: القارئ هو اللي يقص).
 */
class RealQnbPdfTest {
    private val files = File(System.getProperty("masroufy.ownerFiles") ?: error("مكان ملفات المالك مش متحدد"))

    private fun pages(name: String = "qnb-pdf-pages.json"): List<PdfPage> = Json.parseToJsonElement(File(files, name).readText()).jsonArray.map { p ->
        PdfPage(
            p.jsonObject.getValue("pageNumber").jsonPrimitive.int,
            p.jsonObject.getValue("words").jsonArray.map { w ->
                val o = w.jsonObject
                PositionedWord(o.getValue("x").jsonPrimitive.double, o.getValue("y").jsonPrimitive.double, o.getValue("text").jsonPrimitive.content)
            },
        )
    }

    @Test fun everyRowReadAndTheBalanceChainCloses() {
        val pages = pages()
        val outcome = parseQnbPdf(pages)
        assertTrue(outcome.errors.isEmpty(), "صفوف مرفوضة: ${outcome.errors.map { it.raw + " " + it.field }}")
        // عدد سطور العمليات من الكشف نفسه (تاريخ في أول عمود) من غير سطرين «BALANCE … FORWARD»
        val dateLines = pages.sumOf { p -> p.words.filter { it.x < 100 && Regex("""\d{4}-\d{2}-\d{2}""").matches(it.text.trim()) }.map { it.y }.distinct().size }
        val forwardLines = pages.sumOf { p -> p.words.count { it.text.contains("BALANCE BROUGHT FORWARD") || it.text.contains("BALANCE CARRIED FORWARD") } }
        assertEquals(dateLines - forwardLines, outcome.rows.size, "كل سطر عملية اتقري")

        var balance = assertNotNull(outcome.openingBalanceMinor, "رصيد البداية")
        for ((i, r) in outcome.rows.withIndex()) {
            balance = if (r.direction == Direction.IN) addMoney(balance, r.amountMinor) else subtractMoney(balance, r.amountMinor)
            assertEquals(r.statedBalanceMinor, balance, "سلسلة الرصيد اتكسرت عند العملية رقم ${i + 1} (${r.raw})")
        }
        assertEquals(outcome.closingBalanceMinor, balance, "رصيد النهاية")

        val longDigits = Regex("""\d{5,}""")
        for (r in outcome.rows) {
            for (text in listOf(r.description, r.raw, r.merchantName, r.sourceOperationType ?: "")) assertTrue(!longDigits.containsMatchIn(text), "رقم طويل طالع من القارئ في ${r.raw}")
        }
        val cards = outcome.rows.filter { it.sourceOperationType == "CARD PURCHASE" }
        assertTrue(cards.isNotEmpty() && cards.all { it.merchantName.isNotEmpty() }, "كل شراء بالكارت ليه اسم تاجر: ${cards.count { it.merchantName.isEmpty() }} من غير اسم")
        // كل قارئ بيرفض كشف البنك التاني (الاستيراد بيجرب الراجحي الأول وبعدين QNB)
        assertTrue(parseAlrajhiPdf(pages).rows.isEmpty(), "قارئ الراجحي ما يقراش كشف QNB")
        if (File(files, "alrajhi-pdf-pages.json").isFile) assertTrue(parseQnbPdf(pages("alrajhi-pdf-pages.json")).rows.isEmpty(), "قارئ QNB ما يقراش كشف الراجحي")
        File("build/real-qnb-report.txt").writeText(
            "عمليات: ${outcome.rows.size} · صفحات: ${outcome.pagesRead} · أنواع: ${outcome.rows.groupingBy { it.sourceOperationType }.eachCount()}\n",
        )
    }
}
