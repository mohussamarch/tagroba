package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.ImportSourceType
import app.masroufy.core.PdfPage
import app.masroufy.core.PositionedWord
import app.masroufy.core.SchemaId
import app.masroufy.core.Transaction
import app.masroufy.core.isTransferLike
import app.masroufy.core.parseQnbPdf
import app.masroufy.core.suspiciousTransferParties
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.ImportRequest
import app.masroufy.usecase.ImportStatement
import app.masroufy.usecase.ImportStatementDeps
import kotlinx.coroutines.runBlocking
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
 * «زون التحويلات» (§60) على **كشفين المالك الحقيقيين** — على جهازه بس. **مفيش اسم ولا مبلغ بيتطبع أو يتكتب**:
 * التقرير (`data/build/real-transfer-parties.txt`) أعداد بس.
 */
class RealTransferPartiesTest {
    private val files = File(System.getProperty("masroufy.ownerFiles") ?: error("مكان ملفات المالك مش متحدد"))
    private val report = StringBuilder()

    private suspend fun imported(request: ImportRequest): List<Transaction> {
        val txns = MemoryTransactionRepository()
        val sources = MemorySourceRecordRepository()
        val batches = MemoryImportBatchRepository()
        val importer = ImportStatement(
            ImportStatementDeps(
                txns, sources, batches, MemoryMerchantRepository(), MemoryCategoryRepository(), MemoryRuleRepository(),
                MemoryUnitOfWork(listOf(txns, sources, batches)), SequentialIdGenerator(), FixedClock("2026-10-03T00:00:00.000Z"),
            ),
        )
        importer.commit(request, importer.preview(request))
        return txns.all()
    }

    /** الطرف هو هو قبل التخزين وبعده (الرقم متقص في فايربيز). */
    private fun sameAfterStorage(list: List<Transaction>): Int =
        list.count { transferPartyOf(it)?.key != transferPartyOf(it.copy(rawDescription = it.rawDescription?.let(::sanitizeAccountNumbers)))?.key }

    @Test fun everyAlrajhiTransferHasItsParty() = runBlocking<Unit> {
        val all = imported(ImportRequest("t.csv", File(files, "transactions_full.csv").readText(), "a", ImportSourceType.CSV_LEGACY, "w-1", SchemaId.LEGACY))
        val transfers = all.filter(::isTransferLike)
        val parties = transfers.mapNotNull(::transferPartyOf)
        report.appendLine("الراجحي: تحويلات ${transfers.size} · ليها طرف ${parties.size} · أطراف مختلفة ${parties.map { it.key }.toSet().size} · أسئلة ${suspiciousTransferParties(all, emptySet()).size}")
        assertEquals(transfers.size, parties.size, "كل تحويل في كشف الراجحي ليه طرف")
        assertEquals(0, sameAfterStorage(transfers), "المفتاح ما بيتغيرش بعد قص الأرقام")
        assertTrue(parties.none { Regex("[0-9]{5,}").containsMatchIn(it.key + it.label) }, "مفيش رقم كامل في طرف")
        reportFile("real-transfer-parties.txt").writeText(report.toString())
    }

    @Test fun qnbTransfersWithoutAPartyAreOnlyTheOnesWhoseNameTheBankLost() = runBlocking<Unit> {
        val pages = Json.parseToJsonElement(File(files, "qnb-pdf-pages.json").readText()).jsonArray.map { p ->
            PdfPage(
                p.jsonObject.getValue("pageNumber").jsonPrimitive.int,
                p.jsonObject.getValue("words").jsonArray.map { w ->
                    val o = w.jsonObject
                    PositionedWord(o.getValue("x").jsonPrimitive.double, o.getValue("y").jsonPrimitive.double, o.getValue("text").jsonPrimitive.content)
                },
            )
        }
        val outcome = parseQnbPdf(pages)
        val all = imported(
            ImportRequest("q.pdf", outcome.rows.joinToString("\n") { it.raw }, "q", ImportSourceType.PDF_QNB, "w-q", SchemaId.QNB_PDF, parsedRows = outcome.rows, currency = Currency.EGP),
        )
        val transfers = all.filter(::isTransferLike)
        val missing = transfers.filter { transferPartyOf(it) == null }
        reportFile("real-transfer-parties-qnb.txt").writeText("QNB: تحويلات ${transfers.size} · ليها طرف ${transfers.size - missing.size}\n")
        assertTrue(transfers.isNotEmpty())
        // اللي من غير طرف: الاسم بعد IPN كله «?» (عربي ضاع من ملف البنك) — مش اسم لاتيني القارئ فوّته
        assertTrue(missing.all { Regex("IPN(?!\\s*TRANSFER)[?._ ]*[?]").containsMatchIn(it.rawDescription.orEmpty()) }, "تحويل اسمه موجود ومالوش طرف")
        assertEquals(0, sameAfterStorage(transfers))
    }
}
