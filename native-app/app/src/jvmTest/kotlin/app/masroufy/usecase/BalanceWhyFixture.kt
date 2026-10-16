package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.ReviewState
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryProjectLinkRepository
import app.masroufy.memory.MemoryRepairBackup
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.port.RepairBackupPort
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository

/**
 * عالم «افهم رصيدك» — **كل المبالغ والأسامي مخترعة** (مفيش بيانات من ملفات المالك). محفظة بنك ومحفظة كاش؛ كل عملية بتتسجل بمصدرها
 * (دفعة كشف · دفعة رسايل · من غير سجل = يدوي) زي ما الاستيراد بيكتبها.
 */
internal class LedgerWorld(
    val bank: Wallet = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 500_000, "2025-01-01"),
    val cash: Wallet = Wallet("w-cash", "النقد", Currency.SAR, "cash", 100_000, "2025-01-01"),
    txnStore: MemoryTransactionRepository = MemoryTransactionRepository(),
    sourceStore: MemorySourceRecordRepository = MemorySourceRecordRepository(),
    batchStore: MemoryImportBatchRepository = MemoryImportBatchRepository(),
    walletStore: MemoryWalletRepository? = null,
) {
    val txns = txnStore
    val sources = sourceStore
    val batches = batchStore
    val wallets = walletStore ?: MemoryWalletRepository(listOf(bank, cash))
    val allocations = MemoryAllocationRepository()
    val tags = MemoryTransactionTagRepository()
    val backup = MemoryRepairBackup()
    private var seq = 0

    fun txn(
        id: String, date: String, minor: Long, dir: Direction = Direction.OUT, wallet: String? = bank.id, to: String? = null,
        kind: EconomicKind = EconomicKind.PURCHASE, currency: Currency = Currency.SAR, createdAt: String = "2026-10-01T00:00:00.000Z",
    ) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = seq++, economicKind = kind, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = minor, currency = currency, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = createdAt, updatedAt = createdAt, walletId = wallet,
        transferToWalletId = to, rawMerchantName = "TEST SHOP $id",
    )

    /** العملية + سجل مصدرها في دفعة من النوع [from] (null = يدوي، من غير سجل). */
    suspend fun add(t: Transaction, from: ImportSourceType?) {
        txns.saveMany(listOf(t))
        if (from == null) return
        val batchId = "b-${from.wire}"
        if (batches.findById(batchId) == null) {
            batches.save(ImportBatch(batchId, from, "hash-$batchId", "$batchId.csv", "2026-10-01T00:00:00.000Z", ImportBatchState.COMMITTED, ImportCounts(0, 0, 0, 0, 0, 0)))
        }
        val ref = if (from == ImportSourceType.SMS) "SMS:${t.id}" else null
        sources.saveMany(listOf(SourceRecord("src-${t.id}", batchId, "acct", ref, "h-${t.id}", t.sourceOrder, "raw ${t.id}", t.id, MatchingState.NEW, "new")))
    }

    fun explain() = ExplainBalance(ExplainBalanceDeps(txns, sources, batches, wallets))

    suspend fun shown(asOf: String): Long? = LoadWithYouNow(wallets, txns, Currency.SAR).load(asOf).wallets.first { it.wallet.id == bank.id }.balanceMinor

    fun merger(port: RepairBackupPort? = backup) = mergerOf(txns, sources, batches, port, allocations, tags)
}

/** ساعة بتتقدم ثانية مع كل قراية — كل نسخة «قبل الإصلاح» ليها اسم لوحدها زي الجهاز. */
internal class TickingClock : app.masroufy.port.Clock {
    private var n = 0

    override fun nowIso(): String {
        val s = n++
        return "2026-10-15T0${s / 3600 % 10}:${(s / 60 % 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}.000Z"
    }
}

/** «راجع المكرر» على مستودعات موجودة — نفس اعتمادات التشغيل (الروابط كلها فاضية إلا اللي الاختبار بيمرره). */
internal fun mergerOf(
    txns: TransactionRepository,
    sources: SourceRecordRepository,
    batches: MemoryImportBatchRepository,
    port: RepairBackupPort?,
    allocations: MemoryAllocationRepository = MemoryAllocationRepository(),
    tags: MemoryTransactionTagRepository = MemoryTransactionTagRepository(),
    rematch: (suspend () -> List<app.masroufy.core.SmsStatementPair>)? = null,
    personLinks: app.masroufy.port.PersonLinkRepository? = null,
) = MergeDuplicates(
    MergeDuplicatesDeps(
        txns, sources, batches, MemoryUnitOfWork(listOfNotNull(txns as? app.masroufy.memory.Snapshotable, sources as? app.masroufy.memory.Snapshotable)), TickingClock(),
        port, allocations, MemorySettlementRepository(), MemoryObligationRepository(),
        RevertLinkDeps(
            MemoryProjectLinkRepository(), MemoryEventLinkRepository(), tags, MemoryRoscaEntryRepository(), MemoryInstallmentPaymentRepository(),
            MemoryInstallmentPlanRepository(), MemoryZakatPaymentRepository(), MemoryAssetLotRepository(), MemoryAssetSaleRepository(),
            personLinks = personLinks,
        ),
        rematch = rematch,
    ),
)
