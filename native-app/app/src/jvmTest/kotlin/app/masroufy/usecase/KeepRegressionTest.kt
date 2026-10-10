package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Category
import app.masroufy.core.ClassifiableRow
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportSourceType
import app.masroufy.core.InstallmentKind
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.ReviewState
import app.masroufy.core.SchemaId
import app.masroufy.core.SmsParseResult
import app.masroufy.core.Transaction
import app.masroufy.core.buildPeriod
import app.masroufy.core.computeExpenseBreakdown
import app.masroufy.core.parseBankSms
import app.masroufy.core.smsRowsJson
import app.masroufy.core.suspiciousTransferParties
import app.masroufy.core.toParsedRow
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * قرارات المالك اللي **ما بتغيّرش** حاجة — اختبار عشان ما تتغيرش بالغلط (§75-13 · §75-14 · §56 مع §75-8). كل الأسامي والمبالغ مخترعة.
 * الشغل كله بقرار المالك الحالي (`EstimatePolicy.OWNER_2026_10`) — الافتراضي.
 */
class KeepRegressionTest {
    private val clock = FixedClock("2026-10-07T11:00:00.000Z")
    private val sep = buildPeriod(2026, 9, 28)
    private var seq = 0

    private fun txn(date: String, minor: Long, dir: Direction, op: String? = null, desc: String? = null, categoryId: String? = null, source: String? = null) =
        Transaction(
            id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.UNCLASSIFIED,
            economicKindConfirmed = false, observedDirection = dir, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false,
            excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
            rawDescription = desc, sourceOperationType = op, categoryId = categoryId, sourceCategory = source,
        )

    private suspend fun home(txns: MemoryTransactionRepository, categories: MemoryCategoryRepository = MemoryCategoryRepository()) =
        LoadHomeScreen(LoadHomeScreenDeps(txns, categories, MemoryAllocationRepository(), MemoryBudgetRepository()))
            .load(LoadHomeScreenRequest(sep, "2026-10-08", 28, includeHistory = false))

    // ─── §75-13: «5 تحويلات في الشهر» = الشهر الميلادي، مهما كان يوم الراتب ───

    private fun transfer(date: String) = txn(date, 10_000, Direction.OUT, "عملية تحويل داخلية", "W-/TOACCT/11112222333344445TOSAMI:x")

    @Test fun suspiciousTransfersCountByCalendarMonth() = runBlocking<Unit> {
        // 5 بين 28 سبتمبر و2 أكتوبر: كلهم في فترة راتب واحدة (من 28) بس 3 في سبتمبر و2 في أكتوبر ⇒ مفيش سؤال
        val split = listOf("2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02").map(::transfer)
        assertEquals(emptyList(), suspiciousTransferParties(split, emptySet()))
        // 5 جوه أكتوبر ⇒ سؤال واحد على الشهر الميلادي
        val october = listOf("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09").map(::transfer)
        assertEquals(listOf("2026-10"), suspiciousTransferParties(october, emptySet()).map { it.month })
        // نفس الكلام من «زون التحويلات» — الزون ما بياخدش يوم راتب أصلًا
        suspend fun zone(rows: List<Transaction>) = ManageTransfers(
            ManageTransfersDeps(MemoryTransactionRepository(rows), MemoryTransferPartyRepository(), MemoryPersonRepository(), MemoryUnitOfWork(emptyList()), clock),
        ).zone().questions
        assertEquals(emptyList(), zone(split))
        assertEquals(listOf("SAMI#4445"), zone(october).map { it.party.key })
    }

    // ─── §75-14: شراء الذهب صرف لحد ما يضيفه للأصول بنفسه ───

    /** رسالة شراء مخترعة بنفس شكل رسايل الاختبار (`golden/sms.json`): 2,500.00 ريال من «TEST GOLD SHOP». */
    private val goldSms = "شراء\nبـSR 2500\nلدى:TEST GOLD SHOP\n26/10/07"

    private suspend fun recordGoldSms(txns: MemoryTransactionRepository): Transaction {
        val row = assertIs<SmsParseResult.Ok>(parseBankSms(BankSmsMessage("TESTBANK", "2026-10-07T10:00:00Z", goldSms), 1)).row
        val sources = MemorySourceRecordRepository()
        val batches = MemoryImportBatchRepository()
        val importer = ImportStatement(
            ImportStatementDeps(
                txns, sources, batches, MemoryMerchantRepository(), MemoryCategoryRepository(), MemoryRuleRepository(),
                MemoryUnitOfWork(listOf(txns, sources, batches)), SequentialIdGenerator(), clock,
            ),
        )
        val request = ImportRequest(
            fileName = "bank-sms.json", content = smsRowsJson(listOf(row)), accountIdentity = "TESTBANK", sourceType = ImportSourceType.SMS,
            schema = SchemaId.SMS, parsedRows = listOf(row.toParsedRow()), smsRows = mapOf(row.lineNumber to row),
        )
        importer.commit(request, importer.preview(request))
        return txns.listByDateRange(sep.start, sep.end).single()
    }

    @Test fun goldFromAnSmsIsSpendingUntilItIsAnAsset() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val gold = recordGoldSms(txns)
        assertEquals("TEST GOLD SHOP", gold.rawMerchantName)
        assertEquals(250_000, home(txns).expenseMinor, "شراء من محل ذهب من غير تصنيف «ذهب» ⇒ مصروف")

        // إضافته للأصول بنفسه (شراء مربوط بالعملية) ما بيغيّرش نوع العملية ولا المصروف
        val manage = ManageAssets(ManageAssetsDeps(MemoryAssetRepository(), MemoryAssetLotRepository(), MemoryAssetSaleRepository(), MemoryAssetPriceRepository(), SequentialIdGenerator(), clock))
        val asset = manage.addAsset(NewAsset("ذهب وهمي", "gold"))
        val lot = manage.recordPurchase(PurchaseInput(asset.id, "2026-10-07", QUANTITY_SCALE, 250_000, transactionId = gold.id))
        assertEquals(gold.id, lot.transactionId)
        assertEquals(gold, txns.findByIds(listOf(gold.id)).single(), "العملية زي ما هي بالظبط")
        assertEquals(250_000, home(txns).expenseMinor)
    }

    @Test fun statementGoldCategoryIsStillNotSpending() = runBlocking<Unit> {
        // مسار مجاميع القبول في CLAUDE.md (عمود التصنيف في الكشف)
        val breakdown = computeExpenseBreakdown(listOf(ClassifiableRow(300_000, 0, "ذهب"), ClassifiableRow(40_000, 0, "مطاعم وقهوة")))
        assertEquals(300_000, breakdown.nonExpenseMinor)
        assertEquals(40_000, breakdown.realExpenseMinor)
        // ونفس السطر في التطبيق: التصنيف «ذهب» ⇒ شراء أصل، مش مصروف
        val categories = MemoryCategoryRepository(listOf(Category("c-gold", null, "ذهب", "i", "#000", "#fff", true, 1)))
        val row = txn("2026-10-01", 300_000, Direction.OUT, categoryId = "c-gold", source = "ذهب")
        val shown = home(MemoryTransactionRepository(listOf(row)), categories)
        assertEquals(0, shown.expenseMinor)
        assertEquals(300_000, shown.cash.outMinor, "الفلوس خرجت فعلًا")
    }

    // ─── §56 مع §75-8: القسط المربوط مصروف تحت «المستحقات» حتى والداخل المجهول مستني ───

    @Test fun aLinkedInstallmentIsSpendingWhileUnknownIncomingWaits() = runBlocking<Unit> {
        val installment = txn("2026-10-01", 100_000, Direction.OUT)
        val incoming = txn("2026-10-02", 70_000, Direction.IN)
        val txns = MemoryTransactionRepository(listOf(installment, incoming))
        val categories = MemoryCategoryRepository()
        val payments = MemoryInstallmentPaymentRepository()
        val plans = ManageInstallments(
            ManageInstallmentsDeps(
                MemoryInstallmentPlanRepository(), payments, MemoryRoscaEntryRepository(), MemoryDebtTermsRepository(), MemoryObligationRepository(), txns,
                MemoryUnitOfWork(listOf(payments, txns)), SequentialIdGenerator(), clock, categories,
            ),
        )
        val plan = plans.save(
            InstallmentInput(
                name = "تقسيط وهمي", provider = "متجر وهمي", kind = InstallmentKind.PURCHASE_PLAN, currency = Currency.SAR,
                principalMinor = 600_000, totalMinor = 600_000, installmentMinor = 100_000, firstDueAt = "2026-10-01",
            ),
        )
        plans.link(plan.id, installment.id)
        val shown = home(txns, categories)
        assertEquals(100_000, shown.expenseMinor, "القسط مصروف (§56)")
        assertEquals(0, shown.incomeMinor)
        assertEquals(listOf(incoming.id), shown.pendingIncomingIds, "والـ700 الداخلة مستنية برّه الدخل (§75-1)")
        assertTrue(shown.distribution.isNotEmpty(), "تحت تصنيف «المستحقات»")
    }
}
