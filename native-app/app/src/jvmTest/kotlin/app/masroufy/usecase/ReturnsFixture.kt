package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Category
import app.masroufy.core.Direction
import app.masroufy.core.ImportSourceType
import app.masroufy.core.ParsedRow
import app.masroufy.core.SchemaId
import app.masroufy.core.SmsParseResult
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.addMoney
import app.masroufy.core.parseBankSms
import app.masroufy.core.subtractMoney
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
import kotlinx.coroutines.runBlocking
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository

/**
 * عالم وهمي للشريحة S3 (العملية اللي رجعت §77-D · المبلغ المحلي للأجنبي §75-12): مستودعات في الذاكرة + نفس خط رسايل البنك (الشاشة والخلفية)
 * ومعاه آثار الشريحة. **كل الرسايل والأسامي والأرقام مخترعة** (TEST STORE · الكارت 6604 · المرجع 553317781 …).
 */
internal const val RETURN_REF = "553317781"

/** شراء الراجحي ومعاه سطر مرجع (شكل معروف) — 250.00 ريال يوم 2026-10-02. */
internal fun purchaseWithRef(day: String = "02", amount: String = "250.00", ref: String = RETURN_REF) =
    "شراء\nبطاقة:6604;مدى\nمبلغ:SAR $amount\nلدى:TEST STORE\nفي:26-10-$day 10:00\nمرجع:$ref"

/**
 * «حوالة مرتجعة» (عملية رجعت §77-D — كلمات بس، فبتستنى وبتتسجل من الشاشة) ومعاها المرجع — 250.00 ريال يوم 2026-10-05.
 * (أول نسخة كانت «Purchase Reversal» — ده عكس من محل وبقى استرداد بيستنى §75-6: [merchantReversal].)
 */
internal fun reversalWithRef(amount: String = "250.00", ref: String? = RETURN_REF, day: String = "05") =
    "حوالة مرتجعة\nمبلغ:SAR $amount\n" + (ref?.let { "مرجع:$it\n" } ?: "") + "في:26-10-$day 10:00"

/** «Purchase Reversal» (إس تي سي — شكل معروف): عكس من محل = استرداد (§75-6) بيستنى، مش عملية رجعت. */
internal fun merchantReversal(ref: String = RETURN_REF) = "Purchase Reversal\nAmount: SAR 250.00\nFrom: TEST STORE\nRef: $ref\n2026-10-05 09:10"

/** بيت التمويل مصر (أشكال معروفة ⇒ بتتسجل لوحدها): حوالة صادرة ومرجعها، ورجوعها بنفس المرجع (يوم الوصول). */
internal fun kfhOut(ref: String = RETURN_REF, amount: String = "250.00") =
    "IPN Transfer with EGP $amount deducted on 02/10 10:00 from your AC ending with 188 with Ref# $ref. For info call 19533"

internal fun kfhReturned(ref: String = RETURN_REF, amount: String = "250.00") =
    "IPN Transfer dated 02/10 10:00 with EGP $amount returned with Ref# $ref. For info call 19533"

internal val EG_BANK = Wallet("eg-bank", "بنك مصري وهمي", app.masroufy.core.Currency.EGP, "bank", 0, "2026-01-01")

/** العمليات بتقع مرة واحدة في التعديل — زي التطبيق اللي وقع بعد الحفظ وقبل ما الأصلية تتعلّم. */
internal class FlakyUpdates(private val real: TransactionRepository) : TransactionRepository by real {
    var failUpdates = 0

    /** كام تعديل يعدّي قبل ما [failUpdates] يبدأ (الوقوع بين كتابتين). */
    var passBeforeFail = 0

    override suspend fun update(id: String, patch: TransactionPatch) {
        if (passBeforeFail > 0) {
            passBeforeFail--
            real.update(id, patch)
            return
        }
        if (failUpdates > 0) {
            failUpdates--
            throw IllegalStateException("crash after commit")
        }
        real.update(id, patch)
    }
}

internal class ReturnsWorld(
    wallets: List<Wallet> = listOf(CASH, BANK),
    val parse: (BankSmsMessage, Int) -> SmsParseResult = ::parseBankSms,
    /** آثار قبل آثار الشريحة (زي «حسابي التاني» بآخر 4 — الشريحة S1 — اللي بيأكد النوع). */
    before: List<RecordEffect> = emptyList(),
) {
    val txns = MemoryTransactionRepository()
    val effectTxns = FlakyUpdates(txns)
    val sources = MemorySourceRecordRepository()
    val batches = MemoryImportBatchRepository()
    val wallets = MemoryWalletRepository(wallets)
    val parties = MemoryTransferPartyRepository()
    val categories = MemoryCategoryRepository(listOf(Category("cat-shop", null, "تسوق", "bag", "#111111", "#eeeeee", true, 1)))
    val ids = SequentialIdGenerator()
    val clock = FixedClock("2026-10-07T11:00:00.000Z")
    val allocations = MemoryAllocationRepository()
    val obligations = MemoryObligationRepository()
    val settlements = MemorySettlementRepository()
    val projectLinks = MemoryProjectLinkRepository()
    val eventLinks = MemoryEventLinkRepository()
    val tags = MemoryTransactionTagRepository()
    val revertLinks = RevertLinkDeps(
        projectLinks, eventLinks, tags, MemoryRoscaEntryRepository(), MemoryInstallmentPaymentRepository(), MemoryInstallmentPlanRepository(),
        MemoryZakatPaymentRepository(), MemoryAssetLotRepository(), MemoryAssetSaleRepository(),
    )
    val links = ReversalLinkDeps(allocations, obligations, settlements, revertLinks)
    val memory = MemorySmsInbox(emptyList(), available = true)
    val effects: List<RecordEffect> = before + listOf(ReturnedSmsEffect(ReturnedSmsDeps(effectTxns, sources, links)), ForeignSmsEffect())

    fun importDeps(withEffects: Boolean = true) = ImportStatementDeps(
        txns = txns, sources = sources, batches = batches, merchants = MemoryMerchantRepository(), categories = categories, rules = MemoryRuleRepository(),
        uow = MemoryUnitOfWork(listOf(txns, sources, batches, parties)), ids = ids, clock = clock, transferParties = parties,
        effects = if (withEffects) effects else emptyList(),
    )

    fun lane() = SmsLane.of("sa", importDeps().let { it.copy(effects = s1Effects(wallets, memory, "sa") + it.effects) }, ManageSmsInbox(memory, parse), wallets)

    /** S1 (§77-A): شكل كل رسالة في الصندوق اتأكد قبل كده — اختبارات S3 مش عن وضع التعلّم. */
    fun auto(): AutoRecordSms {
        runBlocking { memory.learnQueued("sa", parse) }
        return AutoRecordSms(AutoRecordSmsDeps(memory, listOf(lane())))
    }

    fun screen() = ReviewSmsInbox(ReviewSmsInboxDeps(ManageSmsInbox(memory, parse), ImportStatement(importDeps()), MemoryMerchantRepository(), categories, ids, SmsLearning(memory, "sa"), wallets))

    fun refunds() = RefundAsks(RefundAsksDeps(txns, sources, links, clock, MemoryUnitOfWork(listOf(txns))))

    /** من غير وحدة عمل وبالمستودع اللي بيقع ([effectTxns]) — زي فايربيز (وحدة العمل هناك بتمرّر بس). */
    fun refundsWithoutUnitOfWork() = RefundAsks(RefundAsksDeps(effectTxns, sources, links, clock))

    fun repair() = RepairReversals(RepairReversalsDeps(txns, clock))

    fun asks(foreign: ForeignSmsAsks? = null) = ReturnsAskSource("sa", txns, foreign)

    fun revert() = RevertImportBatch(
        RevertDeps(txns, sources, batches, settlements, allocations, obligations, MemoryUnitOfWork(listOf(txns, sources, batches)), revertLinks, listOf(ReversalUndo(txns, clock))),
    )

    suspend fun enable() = apply { memory.enable(listOf("TESTBANK")) }

    /** الرسايل وصلت و«سجّل الكل» من الشاشة (تأكيد المالك) ⇒ عدد اللي اتسجل. */
    suspend fun confirmOnScreen(vararg bodies: Pair<String, String>, at: String = SENT_AT, wallet: Wallet = BANK): Int {
        for ((id, body) in bodies) memory.receive(sms(id, body, at))
        val screen = screen()
        screen.load(SmsReviewTarget(wallet.id, wallet.name, wallet.currency))
        return screen.recordAll(emptyMap(), emptyList())
    }

    /** سطر كشف (مش رسالة) بمرجعه — الأصلية من الكشف (`SourceRecord.sourceReference`). */
    suspend fun importStatementLine(date: String, amount: Long, direction: Direction, reference: String) {
        val row = ParsedRow(1, date, amount, direction, "TEST STORE", reference, "statement", "TEST STORE", "$date|$amount|$reference")
        val request = ImportRequest("statement.csv", "statement-$reference-$date", BANK.name, ImportSourceType.CSV_PREVIEW, BANK.id, SchemaId.PREVIEW, listOf(row))
        val importer = ImportStatement(importDeps())
        importer.commit(request, importer.preview(request))
    }

    suspend fun all(): List<Transaction> = txns.listByDateRange("0000-01-01", "9999-12-31")

    suspend fun one(id: String): Transaction = txns.findByIds(listOf(id)).single()

    /** أثر المحفظة: الداخل − الخارج (حقيقة بنكية بالاتجاه). */
    suspend fun walletNet(walletId: String = BANK.id): Long = all().filter { it.walletId == walletId }.fold(0L) { acc, t ->
        if (t.observedDirection == Direction.IN) addMoney(acc, t.amountMinor) else subtractMoney(acc, t.amountMinor)
    }
}

/** آثار الشريحة S1 اللي `SmsLane.of` بيطلبها (§75-2 · §75-11). */
internal fun s1Effects(wallets: app.masroufy.port.WalletRepository, inbox: MemorySmsInbox, spaceId: String): List<RecordEffect> =
    listOf(OwnAccountByLast4Effect(wallets), SmsSalaryEffect(inbox, spaceId))

/** S1 (§77-A): المالك أكّد قبل كده شكل كل رسالة موجودة في الصندوق (من مرسلها في [spaceId]). */
internal suspend fun MemorySmsInbox.learnQueued(spaceId: String, parse: app.masroufy.port.BankSmsParser) {
    preLearn(spaceId, parse, *sync().messages.toTypedArray())
}
