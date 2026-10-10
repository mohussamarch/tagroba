package app.masroufy.wiring

import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.countryPack
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseCsv
import app.masroufy.core.parseIsoDate
import app.masroufy.core.periodForDate
import app.masroufy.core.toDayNumber
import app.masroufy.port.BankSmsParser
import app.masroufy.port.BankSmsPort
import app.masroufy.port.BankSmsRead
import app.masroufy.ui.screens.imports.CsvTable
import app.masroufy.ui.screens.imports.ImportsDeps
import app.masroufy.ui.screens.imports.SmsDeps
import app.masroufy.ui.screens.imports.SmsRange
import app.masroufy.ui.screens.imports.SmsRangeKind
import app.masroufy.usecase.EditTransaction
import app.masroufy.usecase.EditTransactionDeps
import app.masroufy.usecase.ImportStatement
import app.masroufy.usecase.ManageCategories
import app.masroufy.usecase.ManageCategoriesDeps
import app.masroufy.usecase.ReadBankSms
import app.masroufy.usecase.ReadPdfStatement
import app.masroufy.usecase.ResumeStagedBatch
import app.masroufy.usecase.ResumeStagedBatchDeps
import app.masroufy.usecase.RevertDeps
import app.masroufy.usecase.RevertImportBatch
import app.masroufy.usecase.RevertLinkDeps

/**
 * «الاستيراد» — رسايل البنك (`AutoRecordSms` · `ReviewSmsInbox` · `ManageSmsInbox` في [SmsGraph]) · `ReadBankSms` · `ReadPdfStatement` ·
 * `ImportStatement` (`importDeps` — نفس خط الكشف والرسايل) · `RevertImportBatch` · `ResumeStagedBatch` · `EditTransaction` · `ManageCategories`.
 * **الملف ده بتاع المنطقة بس.** الصندوق والقارئين من الجهاز ([DeviceEnv.smsInbox] · [DeviceEnv.bankSms] · [DeviceEnv.pdfPages]).
 */
class ImportsGraph(private val c: AreaContext) : ImportsDeps {
    private val r = c.repos
    private val env = c.env

    /** قارئ رسايل البلد (حزمتها) — مصر والسعودية ليهم قارئ؛ بلد من غير قارئ ⇒ مفيش رسايل ولا لصق. */
    private val parse: BankSmsParser? = countryPack(c.space.countryCode).smsReader?.let { reader -> reader::parse }

    override val importer = ImportStatement(importDeps(r, env))

    // ⚠️ `spaceLegs` (فك زوج التحويل لنفسك قبل مسح رجله) محتاج `SpaceTransferWriter` — مش في `SpaceRepositories` لسه
    override val batches = RevertImportBatch(
        RevertDeps(
            r.transactions, r.sourceRecords, r.importBatches, r.settlements, r.allocations, r.obligations, r.uow,
            RevertLinkDeps(
                r.projectLinks, r.eventLinks, r.transactionTags, r.roscaEntries, r.installmentPayments, r.installmentPlans,
                r.zakatPayments, r.assetLots, r.assetSales,
            ),
        ),
    )

    override val staged = ResumeStagedBatch(ResumeStagedBatchDeps(r.transactions, r.sourceRecords, r.importBatches))

    override val transactions = EditTransaction(
        EditTransactionDeps(r.transactions, r.categories, r.tags, r.transactionTags, r.uow, env.ids, env.clock, allocations = r.allocations, settlements = r.settlements),
    )

    override val categories = ManageCategories(ManageCategoriesDeps(r.categories, env.ids))

    override val readSms: ReadBankSms? = parse?.let { p -> ReadBankSms(env.bankSms ?: NoBankSms, p) }

    override val pdf: ReadPdfStatement? = env.pdfPages?.let(::ReadPdfStatement)

    override val sms: SmsDeps? = env.smsInbox?.takeIf { it.available }?.let { inbox ->
        parse?.let { p -> SmsGraph(c, inbox, p, transactions, batches) }
    }

    override suspend fun wallets() = c.shell.addOptions().wallets

    override fun csvTable(content: String, rows: Int): CsvTable? {
        val doc = runCatching { parseCsv(content) }.getOrNull() ?: return null
        if (doc.header.isEmpty()) return null
        return CsvTable(doc.header, doc.rows.take(rows).map { it.cells }, doc.rows.size)
    }

    override suspend fun smsRanges(): List<SmsRange> {
        val today = env.today()
        val day = toDayNumber(parseIsoDate(today))
        val payday = runCatching { c.shell.profile.load().payday }.getOrNull() ?: DEFAULT_PAYDAY
        return listOf(
            SmsRange(SmsRangeKind.MONTH, periodForDate(today, payday).start, today),
            SmsRange(SmsRangeKind.WEEK, dayNumberToIso(day - 6), today),
            SmsRange(SmsRangeKind.DAYS30, dayNumberToIso(day - 29), today),
        )
    }
}

/** جهاز ما بيقراش رسايل فترة (الآيفون · الاختبار) — اللصق بيفضل شغال. */
private object NoBankSms : BankSmsPort {
    override val available = false

    override suspend fun read(from: String, to: String, senders: List<String>): BankSmsRead = BankSmsRead(emptyList(), truncated = false)
}
