package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.ImportSourceType
import app.masroufy.core.SchemaId
import app.masroufy.core.Wallet
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.formatAmount
import app.masroufy.core.parseIsoDate
import app.masroufy.core.periodForDate
import app.masroufy.core.toDayNumber
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
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
import app.masroufy.usecase.LoadHomeScreen
import app.masroufy.usecase.LoadHomeScreenDeps
import app.masroufy.usecase.LoadHomeScreenRequest
import app.masroufy.usecase.LoadTransactionsScreen
import app.masroufy.usecase.LoadTransactionsScreenDeps
import app.masroufy.usecase.LoadTransactionsScreenRequest
import app.masroufy.usecase.ReconcileBalance
import app.masroufy.usecase.ReconcileDeps
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * «أثر الداتا الكتير» (سؤال المالك 2026-09-30): كشف **وهمي** 10 سنين (20,000 عملية، ~10 أضعاف كشف المالك في السنة)
 * بنفس شكل كشف الراجحي CSV. بيقيس الاستيراد ومنع التكرار والشاشات والرصيد لما البيانات تكبر.
 * ⚠️ على مستودعات الذاكرة — ده قياس **منطق البرنامج**؛ وقت فايربيز نفسه اتقاس في `firebase-trial`.
 * الأوقات بتتكتب في `data/build/scale-report.txt` ومش بتتحط كشرط (الأجهزة بتختلف)؛ الشرط صحة الأرقام بس.
 */
class ScaleTest {
    private val report = StringBuilder()

    private inline fun <T> timed(label: String, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        report.appendLine("$label: ${(System.nanoTime() - start) / 1_000_000} ms")
        return result
    }

    private val categories = listOf("مطاعم", "بقالة", "مواصلات", "تحويلات", "فواتير", "ترفيه", "صحة", "تسوق")

    /** كشف وهمي: عملية كل ~4.4 ساعة، ومرتب أول كل شهر، والرصيد متسلسل صح. */
    private fun fakeStatement(count: Int, opening: Long, startDay: Int): Pair<String, Long> {
        val sb = StringBuilder("التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل\n")
        var balance = opening
        var seed = 7L
        fun next(): Long { seed = (seed * 6364136223846793005L + 1442695040888963407L); return (seed ushr 33) }
        for (i in 0 until count) {
            val date = dayNumberToIso(startDay + (i * 3650L / count).toInt()).replace('-', '/')
            val salary = i % 166 == 0
            val amount = if (salary) 1_500_000L else 500 + next() % 40_000
            val out = !salary
            balance += if (out) -amount else amount
            val money = "${amount / 100}.${(amount % 100).toString().padStart(2, '0')}"
            val bal = "${balance / 100}.${(kotlin.math.abs(balance) % 100).toString().padStart(2, '0')}"
            val cat = if (salary) "رواتب" else categories[(next() % categories.size).toInt()]
            sb.append("$date,${if (out) money else "0.00"},${if (out) "0.00" else money},$bal,متجر وهمي ${next() % 300},$cat,${if (salary) "إيداع" else "شراء"},\n")
        }
        return sb.toString() to balance
    }

    @Test fun tenYearsOfFakeData() = runBlocking<Unit> {
        val start = toDayNumber(parseIsoDate("2016-01-01"))
        // الرصيد الافتتاحي كبير بما يكفي عشان الرصيد ما يبقاش سالب (الأرقام وهمية)
        val opening = 500_000_000L
        val (csvText, closing) = fakeStatement(20_000, opening, start)
        val wallet = Wallet("w-fake", "بنك وهمي", Currency.SAR, "bank", opening, "2016-01-01")
        val txns = MemoryTransactionRepository()
        val sources = MemorySourceRecordRepository()
        val batches = MemoryImportBatchRepository()
        val categoryRepo = MemoryCategoryRepository()
        val importer = ImportStatement(
            ImportStatementDeps(
                txns, sources, batches, MemoryMerchantRepository(), categoryRepo, MemoryRuleRepository(),
                MemoryUnitOfWork(listOf(txns, sources, batches)), SequentialIdGenerator(), FixedClock("2026-09-30T00:00:00.000Z"),
            ),
        )
        val request = ImportRequest("fake-10y.csv", csvText, "fake-account", ImportSourceType.CSV_LEGACY, wallet.id, SchemaId.LEGACY)
        val preview = timed("معاينة 20,000 صف (أول مرة)") { importer.preview(request) }
        assertEquals(20_000, preview.counts.newCount, "counts=${preview.counts}")
        timed("حفظ 20,000 عملية") { importer.commit(request, preview) }

        // كشف تاني بيتداخل: آخر 2,000 صف موجودين + 500 جديد بعدهم
        val all = csvText.trimEnd('\n').split('\n')
        val (more, _) = fakeStatement(500, closing, start + 3651)
        val overlap = (listOf(all.first()) + all.takeLast(2_000) + more.trimEnd('\n').split('\n').drop(1)).joinToString("\n") + "\n"
        val second = ImportRequest("fake-overlap.csv", overlap, "fake-account", ImportSourceType.CSV_LEGACY, wallet.id, SchemaId.LEGACY)
        val secondPreview = timed("معاينة كشف متداخل (2,500 صف قدام 20,000 موجودين)") { importer.preview(second) }
        assertEquals(500, secondPreview.counts.newCount)
        assertEquals(2_000, secondPreview.counts.duplicates)

        val home = LoadHomeScreen(LoadHomeScreenDeps(txns, categoryRepo, MemoryAllocationRepository(), MemoryBudgetRepository()))
        val list = LoadTransactionsScreen(LoadTransactionsScreenDeps(txns, categoryRepo, MemoryAllocationRepository()))
        var period = periodForDate("2016-01-01", 28)
        var periods = 0
        var shown = 0
        var slowest = 0L
        val t = System.nanoTime()
        while (period.start <= "2025-12-31") {
            val t0 = System.nanoTime()
            home.load(LoadHomeScreenRequest(period, period.end, 28))
            slowest = maxOf(slowest, (System.nanoTime() - t0) / 1_000_000)
            shown += list.load(LoadTransactionsScreenRequest(period = period)).transactions.size
            periods++
            period = periodForDate(dayNumberToIso(toDayNumber(parseIsoDate(period.end)) + 1), 28)
        }
        report.appendLine("الرئيسية + العمليات × $periods فترة: ${(System.nanoTime() - t) / 1_000_000} ms (أبطأ رئيسية $slowest ms)")
        assertEquals(20_000, shown)

        val reconciled = timed("مطابقة رصيد 20,000 عملية") { ReconcileBalance(ReconcileDeps(txns, MemoryWalletRepository(listOf(wallet)))).run(wallet.id, "2025-12-31", 28) }
        /*
         * ⚠️ اكتشاف (2026-09-30): المطابقة بتقرا **60 فترة بس** من تاريخ الرصيد الافتتاحي (`MAX_PERIODS`، زي التطبيق الحالي)،
         * فبعد 5 سنين الرصيد اللي بيطلع **غلط من غير رسالة**. الاختبار بيثبّت السلوك الحالي لحد ما المالك يقرر.
         */
        assertEquals(60, reconciled.periodsRead)
        report.appendLine("⚠️ المطابقة قرت ${reconciled.periodsRead} فترة بس من $periods — الرصيد الناتج ${if (reconciled.result.closingMinor == closing) "صح" else "غلط"} ومفيش رسالة")
        assertEquals(0, reconciled.result.mismatches.size)
        File("build/scale-report.txt").writeText(report.toString())
    }
}
