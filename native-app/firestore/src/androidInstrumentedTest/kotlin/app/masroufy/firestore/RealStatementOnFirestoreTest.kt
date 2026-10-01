package app.masroufy.firestore

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.masroufy.core.Currency
import app.masroufy.core.ImportSourceType
import app.masroufy.core.SchemaId
import app.masroufy.core.Wallet
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.formatAmount
import app.masroufy.core.parseIsoDate
import app.masroufy.core.parseMoney
import app.masroufy.core.periodForDate
import app.masroufy.core.toDayNumber
import app.masroufy.data.DocumentCodecs
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.port.AllocationRepository
import app.masroufy.port.BudgetRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.MerchantRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.memory.SequentialIdGenerator
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * **كشف المالك الحقيقي على فايربيز** (محاكي) — على جهازه بس: الملف بيدخل الـAPK بتاع الاختبار من `files/` وقت البناء
 * (`build/ownerTestAssets`، مش في Git)، ولو مش موجود الاختبار بيتخطّى **بسطر في السجل** مش في صمت.
 * جهاز «أ» بيستورد الكشف بحالة الاستخدام نفسها ⇒ جهاز «ب» جديد بينزّل كل حاجة (التنزيل الأول) ⇒ الشاشات × كل الفترات
 * من النسخة المحلية ومن السيرفر ⇒ من غير نت. **السجل (`adb logcat -s MasroufyReal`) فيه أعداد وأوقات بس — مفيش مبلغ.**
 */
@RunWith(AndroidJUnit4::class)
class RealStatementOnFirestoreTest {
    private val tag = "MasroufyReal"
    private fun log(line: String) { Log.i(tag, line) }

    private inline fun <T> timed(label: String, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        log("$label: ${(System.nanoTime() - start) / 1_000_000} ms")
        return result
    }

    private fun ownerCsv(): String? = runCatching {
        InstrumentationRegistry.getInstrumentation().context.assets.open("owner/transactions_full.csv").use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    /** الشاشات التلاتة فترة فترة (يوم الراتب 28) ⇒ عدد العمليات اللي ظهرت + أبطأ رئيسية. */
    private suspend fun screens(c: FirestoreContainer, label: String): Int = screens(c.transactions, c.categories, c.allocations, c.budgets, c.merchants, label)

    private suspend fun screens(
        txns: TransactionRepository, categories: CategoryRepository, allocations: AllocationRepository, budgets: BudgetRepository, merchants: MerchantRepository, label: String,
    ): Int {
        val home = LoadHomeScreen(LoadHomeScreenDeps(txns, categories, allocations, budgets))
        val budget = LoadBudgetScreen(LoadBudgetScreenDeps(txns, categories, allocations, budgets))
        val list = LoadTransactionsScreen(LoadTransactionsScreenDeps(txns, categories, allocations, merchants))
        var period = periodForDate("2025-01-01", 28)
        var periods = 0
        var shown = 0
        var slowest = 0L
        val started = System.nanoTime()
        while (period.start <= "2026-09-04") {
            val t0 = System.nanoTime()
            home.load(LoadHomeScreenRequest(period, period.end, 28))
            budget.load(LoadBudgetScreenRequest(period, period.end, 28))
            shown += list.load(LoadTransactionsScreenRequest(period = period)).transactions.size
            slowest = maxOf(slowest, (System.nanoTime() - t0) / 1_000_000)
            periods++
            period = periodForDate(dayNumberToIso(toDayNumber(parseIsoDate(period.end)) + 1), 28)
        }
        log("$label — الشاشات التلاتة × $periods فترة: ${(System.nanoTime() - started) / 1_000_000} ms (أبطأ فترة $slowest ms)")
        return shown
    }

    @Test fun ownerStatementOnFirestore() = runBlocking<Unit> {
        val csv = ownerCsv()
        if (csv == null) log("⚠️ كشف المالك مش في الـAPK (files/transactions_full.csv مش موجود وقت البناء) — الاختبار اتخطّى")
        assumeTrue("كشف المالك مش موجود على الجهاز ده", csv != null)
        withTimeout(600_000) {
            val uid = "kt-real-" + java.util.UUID.randomUUID()
            val wallet = Wallet("w-bank", "البنك", Currency.SAR, "bank", parseMoney("4837.83"), "2025-01-01")

            // جهاز «أ»: الاستيراد كله على فايربيز (التصنيفات والتجار والقواعد كمان)
            val a = FirestoreContainer(FirestoreSpace.forUser(Emulator.firestore("real-a"), uid))
            a.wallets.save(wallet)
            val importer = ImportStatement(
                ImportStatementDeps(
                    a.transactions, a.sourceRecords, a.importBatches, a.merchants, a.categories, a.rules,
                    a.uow, SequentialIdGenerator(), FixedClock("2026-10-01T00:00:00.000Z"),
                ),
            )
            val request = ImportRequest("transactions_full.csv", csv!!, "alrajhi-main", ImportSourceType.CSV_LEGACY, wallet.id, SchemaId.LEGACY)
            val preview = timed("معاينة الكشف على فايربيز") { importer.preview(request) }
            assertEquals(1912, preview.counts.newCount)
            timed("حفظ 1,912 عملية + مصادرها على فايربيز (مستنيين السيرفر)") { importer.commit(request, preview) }

            // جهاز «ب» جديد: التنزيل الأول — بنسجّل التقدم مجموعة مجموعة (ده اللي شريط التقدم هيعرضه)
            val spaceB = FirestoreSpace.forUser(Emulator.firestore("real-b"), uid)
            val b = FirestoreContainer(spaceB)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val sync = FirestoreSync(spaceB, DocumentCodecs.byGroup.keys.toList())
            val syncStart = System.nanoTime()
            // خط زمني: «بعد كام مللي ثانية كام مجموعة خلصت» + إمتى العمليات (أتقل مجموعة) خلصت
            val timeline = mutableListOf<String>()
            var txnsAt = -1L
            val watch = scope.launch {
                sync.syncedGroups.collect { done ->
                    val ms = (System.nanoTime() - syncStart) / 1_000_000
                    timeline += "${done.size}@$ms"
                    if (txnsAt < 0 && "transactions" in done) txnsAt = ms
                }
            }
            sync.start(scope)
            sync.awaitComplete()
            log("التنزيل الأول (${sync.total} مجموعة): ${(System.nanoTime() - syncStart) / 1_000_000} ms — العمليات خلصت بعد $txnsAt ms")
            log("التقدم (مجموعات@مللي ثانية): ${timeline.joinToString(" ")}")
            watch.cancel()

            val rt = Runtime.getRuntime()
            System.gc()
            log("الذاكرة المستعملة بعد التنزيل: ${(rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)} MB")

            // الشاشات من الذاكرة (ب) ومن السيرفر (أ — من غير مزامنة)
            assertEquals(1912, screens(b, "من الذاكرة (بعد التزامن)"), "كل عملية في فترة واحدة بالظبط")
            assertEquals(1912, screens(a, "من السيرفر (المحاكي على نفس الكمبيوتر)"))
            // المقياس: نفس الشاشات بمستودعات الذاكرة العادية على نفس المحاكي — الفرق ده بس هو تكلفة التخزين
            val bare = screens(
                MemoryTransactionRepository(b.transactions.listByDateRange("0000", "9999")), MemoryCategoryRepository(b.categories.listAll()),
                MemoryAllocationRepository(), MemoryBudgetRepository(), MemoryMerchantRepository(b.merchants.listAll()), "مستودعات الذاكرة العادية (من غير فايربيز خالص)",
            )
            assertEquals(1912, bare)

            val reconciled = timed("مطابقة الرصيد من الذاكرة") { ReconcileBalance(ReconcileDeps(b.transactions, b.wallets)).run(wallet.id, "2026-09-04", 28) }
            assertEquals("2,707.25", formatAmount(reconciled.result.closingMinor))
            assertEquals(0, reconciled.result.mismatches.size)

            // «ب» من غير نت: نفس الشاشات بنفس العدد (الذاكرة ما بتتأثرش)
            spaceB.db.disableNetwork()
            assertEquals(1912, screens(b, "من غير نت"))
            spaceB.db.enableNetwork()

            sync.stop()
            scope.cancel()
        }
    }
}
