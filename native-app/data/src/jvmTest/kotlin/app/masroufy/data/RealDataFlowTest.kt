package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.ImportSourceType
import app.masroufy.core.PdfPage
import app.masroufy.core.PositionedWord
import app.masroufy.core.SchemaId
import app.masroufy.core.Wallet
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.emptyBackupData
import app.masroufy.core.formatAmount
import app.masroufy.core.parseAlrajhiPdf
import app.masroufy.core.parseIsoDate
import app.masroufy.core.parseMoney
import app.masroufy.core.periodForDate
import app.masroufy.core.toDayNumber
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.FullBackup
import app.masroufy.usecase.ImportRequest
import app.masroufy.usecase.ImportStatement
import app.masroufy.usecase.ImportStatementDeps
import app.masroufy.usecase.LoadBudgetScreen
import app.masroufy.usecase.LoadBudgetScreenDeps
import app.masroufy.usecase.LoadBudgetScreenRequest
import app.masroufy.usecase.LoadHomeScreen
import app.masroufy.usecase.LoadHomeScreenDeps
import app.masroufy.usecase.LoadHomeScreenRequest
import app.masroufy.usecase.LoadTransactionsScreen
import app.masroufy.usecase.LoadTransactionsScreenDeps
import app.masroufy.usecase.LoadTransactionsScreenRequest
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
 * البرنامج كله على **كشف المالك الحقيقي** (20 شهر، 1,912 عملية) — على جهازه بس (`files/` مستبعد من Git).
 * بيستورد الـCSV، وبعدين نفس الكشف PDF (**لازم يطلع كله مكرر**)، ويفتح الرئيسية والميزانية والعمليات فترة فترة،
 * ويطابق الرصيد، ويحوّل كل حاجة لمستندات فايربيز ويرجّعها، ويعمل نسخة شاملة ويستعيدها.
 * **مفيش مبلغ بيتكتب في أي ملف:** التقرير (`data/build/real-data-report.txt`) فيه أعداد وأوقات بس.
 * الأرقام المستهدفة هي نفسها المكتوبة في CLAUDE.md.
 */
class RealDataFlowTest {
    private val files = File(System.getProperty("masroufy.ownerFiles") ?: error("مكان ملفات المالك مش متحدد"))
    private val report = StringBuilder()

    private inline fun <T> timed(label: String, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        report.appendLine("$label: ${(System.nanoTime() - start) / 1_000_000} ms")
        return result
    }

    private fun pdfPages(): List<PdfPage> = Json.parseToJsonElement(File(files, "alrajhi-pdf-pages.json").readText()).jsonArray.map { p ->
        PdfPage(
            p.jsonObject.getValue("pageNumber").jsonPrimitive.int,
            p.jsonObject.getValue("words").jsonArray.map { w ->
                val o = w.jsonObject
                PositionedWord(o.getValue("x").jsonPrimitive.double, o.getValue("y").jsonPrimitive.double, o.getValue("text").jsonPrimitive.content)
            },
        )
    }

    @Test fun wholeProgramOnTheRealStatement() = runBlocking<Unit> {
        val wallet = Wallet("w-bank", "البنك", Currency.SAR, "bank", parseMoney("4837.83"), "2025-01-01")
        val txns = MemoryTransactionRepository()
        val sources = MemorySourceRecordRepository()
        val batches = MemoryImportBatchRepository()
        val categories = MemoryCategoryRepository()
        val wallets = MemoryWalletRepository(listOf(wallet))
        val deps = ImportStatementDeps(
            txns, sources, batches, MemoryMerchantRepository(), categories, MemoryRuleRepository(),
            MemoryUnitOfWork(listOf(txns, sources, batches)), SequentialIdGenerator(), FixedClock("2026-09-30T00:00:00.000Z"),
        )
        val importer = ImportStatement(deps)
        val account = "alrajhi-main"

        // 1) الـCSV
        val csv = ImportRequest("transactions_full.csv", File(files, "transactions_full.csv").readText(Charsets.UTF_8), account, ImportSourceType.CSV_LEGACY, wallet.id, SchemaId.LEGACY)
        val csvPreview = timed("معاينة الـCSV (1,912 صف)") { importer.preview(csv) }
        assertEquals(1912, csvPreview.counts.total)
        assertEquals(1912, csvPreview.counts.newCount, "أول استيراد: كله جديد")
        timed("حفظ الـCSV") { importer.commit(csv, csvPreview) }
        assertEquals(1912, txns.all().size)

        // 2) نفس الكشف PDF بعد الـCSV ⇒ مفيش ولا عملية تتضاف (منع التكرار بمفتاح الرصيد — OVERRIDES §30)
        val rows = timed("قراية الـPDF (68 صفحة)") { parseAlrajhiPdf(pdfPages()).rows }
        val pdf = ImportRequest("statement.pdf", rows.joinToString("\n") { it.raw }, account, ImportSourceType.PDF_ALRAJHI, wallet.id, SchemaId.ALRAJHI_PDF, parsedRows = rows)
        val pdfPreview = timed("معاينة الـPDF قدام 1,912 موجودين") { importer.preview(pdf) }
        report.appendLine("الـPDF بعد الـCSV: جديد=${pdfPreview.counts.newCount} مكرر=${pdfPreview.counts.duplicates} متشابه=${pdfPreview.counts.similar} تعارض=${pdfPreview.counts.conflicts} غلط=${pdfPreview.counts.invalid}")
        assertEquals(0, pdfPreview.counts.newCount, "نفس الكشف PDF المفروض ما يضيفش ولا عملية")

        // 3) الشاشات فترة فترة (يوم الراتب 28)
        val home = LoadHomeScreen(LoadHomeScreenDeps(txns, categories, MemoryAllocationRepository(), MemoryBudgetRepository()))
        val budget = LoadBudgetScreen(LoadBudgetScreenDeps(txns, categories, MemoryAllocationRepository(), MemoryBudgetRepository()))
        val list = LoadTransactionsScreen(LoadTransactionsScreenDeps(txns, categories, MemoryAllocationRepository()))
        var period = periodForDate("2025-01-01", 28)
        var periods = 0
        var shown = 0
        var slowestHome = 0L
        val started = System.nanoTime()
        while (period.start <= "2026-09-04") {
            val t0 = System.nanoTime()
            home.load(LoadHomeScreenRequest(period, period.end, 28))
            slowestHome = maxOf(slowestHome, (System.nanoTime() - t0) / 1_000_000)
            budget.load(LoadBudgetScreenRequest(period, period.end, 28))
            shown += list.load(LoadTransactionsScreenRequest(period = period)).transactions.size
            periods++
            period = periodForDate(dayNumberToIso(toDayNumber(parseIsoDate(period.end)) + 1), 28)
        }
        report.appendLine("الشاشات التلاتة × $periods فترة: ${(System.nanoTime() - started) / 1_000_000} ms (أبطأ رئيسية ${slowestHome} ms، والرئيسية بتقرا التاريخ كمان)")
        assertEquals(1912, shown, "كل عملية لازم تظهر في فترة واحدة بالظبط")

        // 4) الرصيد
        val reconciled = timed("مطابقة الرصيد") { ReconcileBalance(ReconcileDeps(txns, wallets)).run(wallet.id, "2026-09-04", 28) }
        assertEquals("2,707.25", formatAmount(reconciled.result.closingMinor))
        assertEquals(0, reconciled.result.mismatches.size)

        // 5) مستندات فايربيز: كل عملية ومصدر ودفعة ⇒ مستند ⇒ كيان، من غير ما حاجة تتغير، ومفيش رقم حساب كامل يتخزن
        val longDigits = Regex("[0-9٠-٩۰-۹]{5,}")
        val stored = timed("تحويل 1,912 عملية + مصادرها لمستندات والعكس") {
            val t = txns.all().map { LedgerCodecs.transactions.toStore(it) }
            val s = sources.all().map { LedgerCodecs.sourceRecords.toStore(it) }
            val b = batches.all().map { LedgerCodecs.importBatches.toStore(it) }
            for (d in t) assertEquals(d, LedgerCodecs.transactions.toStore(LedgerCodecs.transactions.decode(d)))
            for (d in s) assertEquals(d, LedgerCodecs.sourceRecords.toStore(LedgerCodecs.sourceRecords.decode(d)))
            Triple(t, s, b)
        }
        val leaks = (stored.first + stored.second).sumOf { d -> d.entries.count { (k, v) -> v is String && k != "id" && !k.endsWith("Id") && longDigits.containsMatchIn(v) } }
        assertEquals(0, leaks, "نص فيه 5 أرقام ورا بعض أو أكتر هيتخزن")

        // 6) النسخة الشاملة: إنشاء ⇒ نص ⇒ خطة استعادة في حساب فاضي ⇒ استعادة
        val data = emptyBackupData().also {
            it.getValue("wallets") += ReferenceCodecs.wallets.toStore(wallet)
            it.getValue("transactions") += stored.first
            it.getValue("sourceRecords") += stored.second
            it.getValue("importBatches") += stored.third
        }
        val file = timed("إنشاء النسخة الشاملة") { FullBackup(MemoryFullBackup(data)).create("2026-09-30T00:00:00.000Z") }
        val text = file.toJsonText()
        report.appendLine("حجم النسخة الشاملة: ${text.length / 1024} KB")
        val target = FullBackup(MemoryFullBackup())
        val plan = timed("فحص النسخة وخطة الاستعادة") { target.plan(text) }
        assertEquals(1 + 1912 + stored.second.size + stored.third.size, plan.totalToAdd)
        val applied = timed("الاستعادة") { target.apply(plan.file) }
        assertEquals(plan.totalToAdd, applied.totalAdded)

        report.appendLine("مصادر العمليات: ${stored.second.size} · دفعات الاستيراد: ${stored.third.size}")
        File("build/real-data-report.txt").writeText(report.toString())
        assertTrue(report.isNotEmpty())
    }
}
