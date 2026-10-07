package app.masroufy.device

import app.masroufy.core.Currency
import app.masroufy.core.Wallet
import app.masroufy.core.parseBankSms
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAlertInbox
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryAlertReceipts
import app.masroufy.memory.MemoryAlertSettings
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryUsualHours
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.DeviceNotifier
import app.masroufy.port.SmsInboxPort
import app.masroufy.usecase.AlertEngineDeps
import app.masroufy.usecase.AutoRecordSms
import app.masroufy.usecase.AutoRecordSmsDeps
import app.masroufy.usecase.BackgroundCycleDeps
import app.masroufy.usecase.ImportStatementDeps
import app.masroufy.usecase.ManageSmsInbox
import app.masroufy.usecase.RunAlertEngine
import app.masroufy.usecase.RunBackgroundCycle
import app.masroufy.usecase.SmsLane

/**
 * التجميع اللي التطبيق الجاي هيعمله في `Application.onCreate` (ARCHITECTURE §31.29) — هنا بمستودعات الذاكرة ومحفظة بنك **وهمية**.
 * الصندوق والإشعارات والعامل هم بتوع أندرويد الحقيقيين.
 */
class TestBackgroundGraph(inbox: SmsInboxPort, notifier: DeviceNotifier) : BackgroundGraph {
    val txns = MemoryTransactionRepository()
    private val sources = MemorySourceRecordRepository()
    private val batches = MemoryImportBatchRepository()
    private val parties = MemoryTransferPartyRepository()
    private val importDeps = ImportStatementDeps(
        txns = txns, sources = sources, batches = batches, merchants = MemoryMerchantRepository(), categories = MemoryCategoryRepository(),
        rules = MemoryRuleRepository(), uow = MemoryUnitOfWork(listOf(txns, sources, batches, parties)), ids = SequentialIdGenerator(),
        clock = FixedClock("2026-10-08T00:00:00.000Z"), transferParties = parties,
    )
    private val wallets = MemoryWalletRepository(listOf(Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01")))
    val auto = AutoRecordSms(AutoRecordSmsDeps(inbox, listOf(SmsLane.of("sa", importDeps, ManageSmsInbox(inbox, ::parseBankSms), wallets))))
    val alertInbox = MemoryAlertInbox()
    private val engine = RunAlertEngine(
        AlertEngineDeps(MemoryAlertSettings(), MemoryAlertInteractions(), MemoryUsualHours(), MemoryAlertReceipts(), alertInbox, FixedClock("2026-10-08T00:00:00.000Z")),
    )
    private val cycle = RunBackgroundCycle(BackgroundCycleDeps(auto, { emptyList() }, engine, notifier))

    @Volatile var runs = 0

    override suspend fun cycle(): RunBackgroundCycle {
        runs++
        return cycle
    }
}
