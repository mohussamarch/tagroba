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
import kotlin.test.assertTrue

/**
 * قارئ كشف الراجحي PDF بكوتلن على **الكشف الحقيقي** (68 صفحة) — على جهاز المالك بس، ومفيش رقم بيتطبع.
 * الكلمات بإحداثياتها اتطلعت بنفس قراية التطبيق الحالي (`scripts/real/exportPdfPages.mjs` ⇒ `files/alrajhi-pdf-pages.json`)،
 * فالاختبار ده بيحكم على **القارئ نفسه**: لازم يطلّع نفس الـ1,912 صف اللي في ملف الـCSV، صف بصف، والرصيد يتقفل.
 */
class RealPdfStatementTest {
    private val files = File(System.getProperty("masroufy.ownerFiles") ?: error("مكان ملفات المالك مش متحدد"))

    private fun pages(): List<PdfPage> = Json.parseToJsonElement(File(files, "alrajhi-pdf-pages.json").readText()).jsonArray.map { p ->
        PdfPage(
            p.jsonObject.getValue("pageNumber").jsonPrimitive.int,
            p.jsonObject.getValue("words").jsonArray.map { w ->
                val o = w.jsonObject
                PositionedWord(o.getValue("x").jsonPrimitive.double, o.getValue("y").jsonPrimitive.double, o.getValue("text").jsonPrimitive.content)
            },
        )
    }

    @Test fun pdfMatchesTheCsvRowByRow() {
        val outcome = parseAlrajhiPdf(pages())
        assertEquals(68, outcome.pagesRead)
        assertTrue(outcome.errors.isEmpty(), "صفوف مرفوضة من الـPDF: ${outcome.errors.size}")
        assertEquals(1912, outcome.rows.size)

        val debit = sumMoney(outcome.rows.filter { it.direction == Direction.OUT }.map { it.amountMinor })
        val credit = sumMoney(outcome.rows.filter { it.direction == Direction.IN }.map { it.amountMinor })
        assertEquals("302,171.45", formatAmount(debit))
        assertEquals("300,040.87", formatAmount(credit))

        val movements = outcome.rows.mapIndexed { i, r ->
            LedgerMovement(r.date, i, if (r.direction == Direction.OUT) r.amountMinor else 0, if (r.direction == Direction.IN) r.amountMinor else 0, r.statedBalanceMinor, label = r.merchantName)
        }
        val chain = reconcileBalance(parseMoney("4837.83"), "2025-01-01", movements)
        assertEquals("2,707.25", formatAmount(chain.closingMinor))
        assertEquals("2026-09-04", chain.closingAt)
        assertEquals(0, chain.mismatches.size, "الرصيد لازم يطابق في كل سطر")

        // نفس صفوف الـCSV بالظبط (تاريخ · مبلغ · اتجاه · رصيد) — بالترتيب بعد الفرز، عشان ترتيب الصفحات مش شرط
        val csv = parseRows(parseCsv(File(files, "transactions_full.csv").readText(Charsets.UTF_8))).rows
        fun key(r: ParsedRow) = "${r.date}|${r.amountMinor}|${r.direction.wire}|${r.statedBalanceMinor}"
        val fromPdf = outcome.rows.map(::key).sorted()
        val fromCsv = csv.map(::key).sorted()
        val firstDiff = fromPdf.indices.firstOrNull { fromPdf[it] != fromCsv.getOrNull(it) }
        assertEquals(null, firstDiff, "أول صف مختلف بين الـPDF والـCSV (رقم الصف بس، من غير بيانات)")
    }
}
