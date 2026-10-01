package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.ImportSourceType
import app.masroufy.core.PdfPage
import app.masroufy.core.PositionedWord
import app.masroufy.core.SchemaId
import app.masroufy.core.Wallet
import app.masroufy.core.parseQnbPdf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.ImportRequest
import app.masroufy.usecase.ImportStatement
import app.masroufy.usecase.ImportStatementDeps
import app.masroufy.usecase.ReconcileBalance
import app.masroufy.usecase.ReconcileDeps
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
 * كشف QNB مصر الحقيقي من أوله لآخره — على جهاز المالك بس، ومفيش مبلغ بيتطبع أو يتكتب:
 * استيراد **بالجنيه** ⇒ مطابقة الرصيد لحد رصيد النهاية ⇒ نفس الكشف تاني ما يضيفش حاجة ⇒ ولا رقم كامل بيتخزن.
 */
class RealQnbImportTest {
    private val files = File(System.getProperty("masroufy.ownerFiles") ?: error("مكان ملفات المالك مش متحدد"))

    private fun pages(): List<PdfPage> = Json.parseToJsonElement(File(files, "qnb-pdf-pages.json").readText()).jsonArray.map { p ->
        PdfPage(
            p.jsonObject.getValue("pageNumber").jsonPrimitive.int,
            p.jsonObject.getValue("words").jsonArray.map { w ->
                val o = w.jsonObject
                PositionedWord(o.getValue("x").jsonPrimitive.double, o.getValue("y").jsonPrimitive.double, o.getValue("text").jsonPrimitive.content)
            },
        )
    }

    @Test fun importReconcileAndReimport() = runBlocking<Unit> {
        val outcome = parseQnbPdf(pages())
        val first = outcome.rows.first().date
        val last = outcome.rows.last().date
        val wallet = Wallet("w-qnb", "البنك", Currency.EGP, "bank", outcome.openingBalanceMinor!!, first)
        val txns = MemoryTransactionRepository()
        val sources = MemorySourceRecordRepository()
        val batches = MemoryImportBatchRepository()
        val importer = ImportStatement(
            ImportStatementDeps(
                txns, sources, batches, MemoryMerchantRepository(), MemoryCategoryRepository(), MemoryRuleRepository(),
                MemoryUnitOfWork(listOf(txns, sources, batches)), SequentialIdGenerator(), FixedClock("2026-10-01T00:00:00.000Z"),
            ),
        )
        val request = ImportRequest(
            "qnb.pdf", outcome.rows.joinToString("\n") { it.raw }, "qnb-main", ImportSourceType.PDF_QNB, wallet.id, SchemaId.QNB_PDF,
            parsedRows = outcome.rows, currency = Currency.EGP,
        )
        val preview = importer.preview(request)
        assertEquals(outcome.rows.size, preview.counts.newCount, "أول استيراد: كله جديد")
        importer.commit(request, preview)
        assertTrue(txns.all().isNotEmpty() && txns.all().all { it.currency == Currency.EGP }, "كل العمليات بالجنيه")

        val reconciled = ReconcileBalance(ReconcileDeps(txns, MemoryWalletRepository(listOf(wallet)))).run(wallet.id, last, 28)
        assertEquals(outcome.closingBalanceMinor, reconciled.result.closingMinor, "الرصيد اتقفل على رصيد نهاية الكشف")
        assertEquals(0, reconciled.result.mismatches.size, "كل سطر مطابق للرصيد المكتوب")

        assertEquals(0, importer.preview(request.copy(fileName = "qnb-again.pdf")).counts.newCount, "نفس الكشف تاني ما يضيفش حاجة")

        val longDigits = Regex("""\d{5,}""")
        val stored = txns.all().map { LedgerCodecs.transactions.toStore(it) } + sources.all().map { LedgerCodecs.sourceRecords.toStore(it) }
        val leaks = stored.sumOf { d -> d.entries.count { (k, v) -> v is String && k != "id" && !k.endsWith("Id") && longDigits.containsMatchIn(v) } }
        assertEquals(0, leaks, "رقم كامل هيتخزن")
    }
}
