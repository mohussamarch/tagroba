package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Budget
import app.masroufy.core.CategoryBudget
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.Merchant
import app.masroufy.core.PersonAllocation
import app.masroufy.core.AllocationKind
import app.masroufy.core.ReviewState
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.normalizeText
import app.masroufy.data.DocumentCodecs
import app.masroufy.port.TransactionPatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * القراية من الذاكرة ([LocalMirror]) لازم تطلّع **نفس** اللي فايربيز بيطلّعه — نفس العناصر وبنفس الترتيب.
 * جهاز «أ» من غير مزامنة (كل قراية من السيرفر) وجهاز «ب» بمزامنة (كل قراية من الذاكرة)، على نفس الحساب، ونفس الأسئلة للاتنين.
 * البيانات فيها الحالات الصعبة: حدود المدى بالظبط · حقل مش موجود · أكتر من 30 معرّف · اسم بديل · ترتيب تنازلي بحد.
 */
@RunWith(AndroidJUnit4::class)
class MirrorParityTest {
    private fun txn(id: String, date: String, createdAt: String = "2026-09-01T00:00:00.000Z", order: Int = 0) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = order, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = 1_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = createdAt, updatedAt = createdAt,
    )

    @Test fun mirrorAnswersExactlyLikeFirestore() = runBlocking<Unit> {
        withTimeout(120_000) {
            val uid = "kt-" + java.util.UUID.randomUUID()
            val spaceA = FirestoreSpace.forUser(Emulator.firestore("parity-a"), uid)
            val a = FirestoreContainer(spaceA)
            // 40 عملية: أيام متكررة (الترتيب بالمعرّف جوه نفس اليوم) وحدود الشهر بالظبط
            a.transactions.saveMany((0 until 40).map { i ->
                txn("t-${(i * 7) % 40}".padEnd(6, 'x'), "2026-09-${(1 + i % 30).toString().padStart(2, '0')}", "2026-09-${(1 + i % 28).toString().padStart(2, '0')}T10:00:00.000Z", order = i % 3)
            } + txn("t-edge-start", "2026-08-28") + txn("t-edge-end", "2026-09-27") + txn("t-out", "2026-09-28"))
            a.merchants.saveMany(listOf(
                Merchant("m-2", "Cafe", normalizeText("Cafe")),
                Merchant("m-1", "Test Mart", normalizeText("Test Mart"), aliases = listOf(normalizeText("تست مارت"), normalizeText("TM"))),
                Merchant("m-3", "Other", normalizeText("Other"), aliases = listOf(normalizeText("TM"))),
            ))
            a.importBatches.save(ImportBatch("b-1", ImportSourceType.CSV_LEGACY, "h-1", "a.csv", "2026-09-01T00:00:00.000Z", ImportBatchState.COMMITTED, ImportCounts(1, 1, 0, 0, 0, 0)))
            a.importBatches.save(ImportBatch("b-2", ImportSourceType.CSV_LEGACY, "h-2", "b.csv", "2026-09-03T00:00:00.000Z", ImportBatchState.STAGED, ImportCounts(1, 1, 0, 0, 0, 0)))
            a.importBatches.save(ImportBatch("b-3", ImportSourceType.CSV_LEGACY, "h-1", "c.csv", "2026-09-02T00:00:00.000Z", ImportBatchState.COMMITTED, ImportCounts(1, 1, 0, 0, 0, 0)))
            a.sourceRecords.saveMany((0 until 12).map { i ->
                SourceRecord("sr-$i", if (i % 2 == 0) "b-1" else "b-3", if (i < 8) "acct-1" else "acct-2", null, "h$i", i, "raw", if (i % 3 == 0) null else "t-${i}xxx", MatchingState.NEW, "جديد")
            })
            a.allocations.saveMany(listOf(PersonAllocation("al-1", "t-1xxx", "p-1", AllocationKind.RECEIVABLE, 500, Currency.SAR)))
            a.budgets.save(Budget("2026-09", "2026-09", "2026-08-28", "2026-09-27", 800_000, 80, "x", "x"))
            a.budgets.saveCategoryBudget(CategoryBudget("cb-2", "2026-09", "c-2", 1_000, true, null))
            a.budgets.saveCategoryBudget(CategoryBudget("cb-1", "2026-09", "c-1", 2_000, true, null))
            a.budgets.saveCategoryBudget(CategoryBudget("cb-x", "2026-10", "c-1", 3_000, true, null))
            // تعديل جزئي: مسح حقل ⇒ الذاكرة لازم تشوف نفس الشكل
            a.transactions.update("t-0xxx", TransactionPatch(note = "ملاحظة", updatedAt = "2026-09-30T00:00:00.000Z"))

            val spaceB = FirestoreSpace.forUser(Emulator.firestore("parity-b"), uid)
            val b = FirestoreContainer(spaceB)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val sync = FirestoreSync(spaceB, DocumentCodecs.byGroup.keys.toList())
            sync.start(scope)
            sync.awaitComplete()

            /** نفس السؤال للاتنين — ولازم الذاكرة تكون فعلًا هي اللي جاوبت. */
            suspend fun <T> same(what: String, ask: suspend (FirestoreContainer) -> T) {
                assertTrue(spaceB.mirror?.isSynced("transactions") == true)
                val fromServer = ask(a)
                val fromMirror = ask(b)
                assertEquals(fromServer, fromMirror, "الذاكرة جاوبت غير فايربيز: $what")
            }
            same("مدى شهر بحدوده") { it.transactions.listByDateRange("2026-08-28", "2026-09-27") }
            same("مدى فاضي") { it.transactions.listByDateRange("2027-01-01", "2027-01-31") }
            same("اتعملت بعد تاريخ (ترتيب فايربيز)") { it.transactions.listCreatedAfter("2026-09-20T00:00:00.000Z") }
            same("35 معرّف (فوق حد الـ30)") { c -> c.transactions.findByIds((0 until 35).map { "t-$it".padEnd(6, 'x') } + "t-مش-موجود") }
            same("كل التجار (ترتيب المعرّف)") { it.merchants.listAll() }
            same("تاجر بالاسم") { it.merchants.findByNormalizedName("test mart") }
            same("تاجر بالاسم البديل (أول واحد بالمعرّف)") { it.merchants.findByNormalizedName("tm") }
            same("تاجر مش موجود") { it.merchants.findByNormalizedName("مفيش") }
            same("آخر دفعتين") { it.importBatches.listRecent(2) }
            same("دفعة بالبصمة (المحفوظة بس)") { it.importBatches.findByFileHash("h-1") }
            same("دفعة مرحلية بالبصمة") { it.importBatches.findByFileHash("h-2") }
            same("دفعة بمعرّفها") { it.importBatches.findById("b-2") }
            same("مصادر حساب") { it.sourceRecords.listByAccountIdentity("acct-1") }
            same("مصادر دفعة") { it.sourceRecords.listByBatch("b-3") }
            same("مصادر بمعرّفات عمليات (فيها حقل مش موجود)") { c -> c.sourceRecords.listByTransactionIds((0 until 12).map { "t-${it}xxx" }) }
            same("تخصيصات") { it.allocations.listByTransactionIds(listOf("t-1xxx", "t-2xxx")) }
            same("ميزانية الفترة") { it.budgets.findByPeriod("2026-09") }
            same("سقوف ميزانية") { it.budgets.listCategoryBudgets("2026-09") }
            same("محفظة مش موجودة") { it.wallets.findById("w-x") }

            // كتابة من «ب» ⇒ تبان في ذاكرته لحظتها، وبعد ما توصل السيرفر «أ» يشوف نفس الشكل
            b.transactions.update("t-0xxx", TransactionPatch(clearNote = true, updatedAt = "2026-10-01T00:00:00.000Z"))
            b.transactions.saveMany(listOf(txn("t-new", "2026-09-15")))
            b.transactions.deleteMany(listOf("t-1xxx"))
            val local = b.transactions.listByDateRange("2026-08-28", "2026-09-27")
            assertTrue(local.any { it.id == "t-new" } && local.none { it.id == "t-1xxx" } && local.first { it.id == "t-0xxx" }.note == null, "كتابة الجهاز نفسه لازم تبان لحظتها")
            for (attempt in 0 until 50) {
                if (a.transactions.listByDateRange("2026-08-28", "2026-09-27") == local) break
                delay(100)
            }
            same("بعد كتابة الجهاز نفسه") { it.transactions.listByDateRange("2026-08-28", "2026-09-27") }
            sync.stop()
            scope.cancel()
        }
    }
}
