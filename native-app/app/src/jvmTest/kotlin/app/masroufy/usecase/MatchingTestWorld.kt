package app.masroufy.usecase

import app.masroufy.core.CROSS_SOURCE_WINDOW_DAYS
import app.masroufy.core.Currency
import app.masroufy.core.Id
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportSourceType
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.parseBankSms
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryProjectLinkRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.QueuedSms

/**
 * عالم وهمي للشريحة S4 (الكشف والرسالة نفس العملية — §75-10): بنك واحد «بنك وهمي» (رصيد افتتاحي 5,000.00 @ 2026-09-30)، صندوق رسايل
 * من «TESTBANK»، وكشف بالمخطط القديم (فيه عمود رصيد). **كل الرسايل والمحلات والمبالغ مخترعة.**
 */
internal const val MW_BANK = "w-bank"
internal const val MW_BANK_NAME = "بنك وهمي"
internal const val MW_HEADER = "التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل"

/** شراء من رسالة بنك بتاريخ [day] (yy/MM/dd) ومبلغ [amount] ريال. */
internal fun matchingPurchaseSms(amount: String, day: String, shop: String = "TEST CAFE") = "شراء\nبـSR $amount\nلدى:$shop\n$day"

/** الدفعة بتقع مرة واحدة قبل `committed` — زي انقطاع بعد كتابة العمليات وقبل العلامة. */
internal class MatchingCrashBatches(val real: MemoryImportBatchRepository) : ImportBatchRepository by real {
    var crash = false

    override suspend fun updateState(id: Id, state: ImportBatchState) {
        if (crash && state == ImportBatchState.COMMITTED) {
            crash = false
            throw IllegalStateException("crash before committed")
        }
        real.updateState(id, state)
    }
}

internal class MatchingWorld(window: Int? = CROSS_SOURCE_WINDOW_DAYS, rollback: Boolean = true, val effects: List<RecordEffect> = emptyList()) {
    val txns = MemoryTransactionRepository()
    val sources = MemorySourceRecordRepository()
    val batchStore = MemoryImportBatchRepository()
    val batches = MatchingCrashBatches(batchStore)
    val wallets = MemoryWalletRepository(listOf(Wallet(MW_BANK, MW_BANK_NAME, Currency.SAR, "bank", 500_000, "2026-09-30")))
    val parties = MemoryTransferPartyRepository()
    val ids = SequentialIdGenerator()
    val clock = FixedClock("2026-10-05T10:00:00.000Z")
    val inbox = MemorySmsInbox(emptyList(), available = true)

    val deps = ImportStatementDeps(
        txns = txns, sources = sources, batches = batches, merchants = MemoryMerchantRepository(), categories = MemoryCategoryRepository(), rules = MemoryRuleRepository(),
        uow = if (rollback) MemoryUnitOfWork(listOf(txns, sources, batchStore, parties)) else PassthroughUnitOfWork(), ids = ids, clock = clock,
        transferParties = parties, effects = effects, crossSourceWindowDays = window,
    )
    val importer = ImportStatement(deps)
    val review = ReviewSmsInbox(ReviewSmsInboxDeps(ManageSmsInbox(inbox, ::parseBankSms), importer, deps.merchants, deps.categories, ids, SmsLearning(inbox, "sa"), wallets))
    val target = SmsReviewTarget(MW_BANK, MW_BANK_NAME)
    private var smsCount = 0

    fun receive(body: String, at: String = "2026-10-01T09:00:00Z"): String {
        val id = "m-${++smsCount}"
        inbox.receive(QueuedSms(id, "TESTBANK", at, body))
        return id
    }

    /** «سجّل الكل» من شاشة الرسايل (تأكيد المالك). */
    suspend fun recordSms(): Int {
        review.load(target)
        return review.recordAll(emptyMap(), emptyList())
    }

    fun statement(vararg lines: String, file: String = "statement-oct.csv") =
        ImportRequest(file, (listOf(MW_HEADER) + lines).joinToString("\n"), MW_BANK_NAME, ImportSourceType.CSV_LEGACY, walletId = MW_BANK)

    suspend fun import(request: ImportRequest) = importer.commit(request, importer.preview(request))

    fun all(): List<Transaction> = txns.all()

    /** [extra] = تراجعات آثار تانية جنب `MergeUndo` (زي `SubscriptionChargeUndo`). */
    fun revert(extra: List<BatchUndo> = emptyList()) = RevertImportBatch(
        RevertDeps(
            txns, sources, batchStore, MemorySettlementRepository(), MemoryAllocationRepository(), MemoryObligationRepository(), MemoryUnitOfWork(listOf(txns, sources, batchStore)),
            RevertLinkDeps(
                MemoryProjectLinkRepository(), MemoryEventLinkRepository(), MemoryTransactionTagRepository(), MemoryRoscaEntryRepository(), MemoryInstallmentPaymentRepository(),
                MemoryInstallmentPlanRepository(), MemoryZakatPaymentRepository(), MemoryAssetLotRepository(), MemoryAssetSaleRepository(),
            ),
            undoers = listOf(MergeUndo(txns, clock)) + extra,
        ),
    )
}
