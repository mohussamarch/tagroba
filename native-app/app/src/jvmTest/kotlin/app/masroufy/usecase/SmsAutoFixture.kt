package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Currency
import app.masroufy.core.ImportBatch
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.SmsParseResult
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.Wallet
import app.masroufy.core.parseBankSms
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.yield

/**
 * عالم وهمي لرسايل البنك اللي بتتسجل لوحدها (OVERRIDES §72). **كل الرسايل والأسامي والمبالغ مخترعة**، بنفس أشكال الرسايل المخترعة
 * اللي في `golden/sms.json` (اختبارات المحلل) — مفيش رسالة بنك حقيقية.
 */
internal const val SENT_AT = "2026-10-07T10:00:00Z"

internal fun sms(id: String, body: String, at: String = SENT_AT, sender: String = "TESTBANK") = QueuedSms(id, sender, at, body)

internal val CAFE = "شراء\nبـSR 25\nلدى:TEST CAFE\n26/10/07"
internal val MART = "شراء\nبـSR 40\nلدى:TEST MART\n26/10/07"
internal val UNCLEAR = "رسالة\nبـSR 24\n26/10/07"
/** نفس اليوم والمبلغ والاتجاه بتوع [CAFE] من محل تاني ⇒ «شبه عملية موجودة» بعد ما [CAFE] يتسجل. */
internal val CAFE_TWIN = "شراء\nبـSR 25\nلدى:OTHER SHOP\n26/10/07"
internal val TO_PERSON = "حوالة داخلية صادرة\nمن:1111\nإلى:TEST PERSON\nبـSR 500\n26/10/07 09:35"
internal val FROM_PERSON = "حوالة محلية واردة\nمن:TEST PERSON\nبـSR 1000\nإلى:1111\n26/10/07"
internal val EG_CARD = "Your Debit Card **1234 had a Successful transaction of EGP 41.25 @TEST STORE,your available bal.EGP174.40"

/**
 * الجولة الرابعة: القارئ بيقراها (عنوان «Purchase» لوحده = القاعدة القديمة بالكلمات) بس **مش شكل معروف** ⇒ ما بتتسجلش لوحدها، بتستنى.
 * 30.00 ريال · TEST SHOP.
 */
internal val KEYWORD_ONLY = "Purchase\nبـSR 30\nلدى:TEST SHOP\n26/10/07"

/** نفس الفكرة في مصر: جملة خصم مش على قالب بنك معروف (500.00 جنيه). */
internal val EG_KEYWORD_ONLY = "تم خصم 500.00 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 07/10/2026"

/** شراء بعملة أجنبية (§75-12): القارئ بيرفضه ومعاه المبلغ الأجنبي ⇒ بيستنى، عمره ما بيتسجل لوحده. */
internal val FOREIGN = "شراء انترنت\nبطاقة:6604;مدى\nمبلغ:USD 20.00\nلدى:TEST SHOP\nفي:26-10-07 10:00"

internal val BANK = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01")
internal val CASH = Wallet("w-cash", "النقد", Currency.SAR, "cash", 0, "2026-01-01")
internal val BANK2 = Wallet("w-bank2", "بنك وهمي تاني", Currency.SAR, "bank", 0, "2026-01-01")

/** صندوق بيقع مرة واحدة وقت الشيل — زي الجهاز اللي بيقفل بعد الحفظ وقبل الشيل. */
internal class FlakyInbox(val real: MemorySmsInbox) : SmsInboxPort by real {
    var failAcks = 0

    override suspend fun acknowledge(ids: List<String>): SmsInboxState {
        if (failAcks > 0) {
            failAcks--
            throw IllegalStateException("crash after save, before acknowledge")
        }
        return real.acknowledge(ids)
    }
}

/** العمليات بتقع مرة واحدة جوه الحفظ — وحدة العمل لازم ترجّع كله. */
internal class FlakyTxns(val real: MemoryTransactionRepository) : TransactionRepository by real {
    var failSaves = 0

    override suspend fun saveMany(transactions: List<Transaction>) {
        if (failSaves > 0) {
            failSaves--
            throw IllegalStateException("crash inside the save")
        }
        real.saveMany(transactions)
    }
}

/** الحفظ بيستنى دوره (`yield`) — عشان تشغيلتين في نفس الوقت يتداخلوا فعلًا لو مفيش قفل. */
internal class SlowBatches(val real: MemoryImportBatchRepository) : ImportBatchRepository by real {
    override suspend fun save(batch: ImportBatch) {
        yield()
        real.save(batch)
    }
}

/** بلد واحدة بمستودعاتها. */
internal class SmsSpace(
    val spaceId: String = "sa",
    wallets: List<Wallet> = listOf(CASH, BANK),
    parties: List<TransferParty> = emptyList(),
    val parse: (BankSmsMessage, Int) -> SmsParseResult = ::parseBankSms,
) {
    val txnStore = MemoryTransactionRepository()
    val txns = FlakyTxns(txnStore)
    val sources = MemorySourceRecordRepository()
    val batchStore = MemoryImportBatchRepository()
    val batches = SlowBatches(batchStore)
    val wallets = MemoryWalletRepository(wallets)
    val parties = MemoryTransferPartyRepository(parties)
    val categories = MemoryCategoryRepository(listOf(Category("cat-food", null, "مطاعم", "utensils", "#111111", "#eeeeee", true, 1)))
    val rules = MemoryRuleRepository(listOf(ClassificationRule("rule-1", 1, "TEST CAFE", RuleMatchMode.CONTAINS, "cat-food", true)))
    val ids = SequentialIdGenerator()

    fun importDeps(effects: List<RecordEffect> = emptyList()) = ImportStatementDeps(
        txns = txns, sources = sources, batches = batches, merchants = MemoryMerchantRepository(), categories = categories, rules = rules,
        uow = MemoryUnitOfWork(listOf(txnStore, sources, batchStore, parties)), ids = ids, clock = FixedClock("2026-10-07T11:00:00.000Z"),
        transferParties = parties, effects = effects,
    )

    /** آثار رسايل البنك بتاعة S1 زي التشغيل الحقيقي: «حسابي التاني» بآخر 4 أرقام (§75-11) · «ده راتبك؟» (§75-2). */
    fun smsEffects(inbox: SmsInboxPort): List<RecordEffect> = listOf(OwnAccountByLast4Effect(wallets), SmsSalaryEffect(inbox, spaceId))

    fun lane(inbox: SmsInboxPort) = SmsLane.of(spaceId, importDeps(smsEffects(inbox)), ManageSmsInbox(inbox, parse), wallets)

    /** شاشة رسايل البنك للبلد دي — [learning] = §77-A (الشاشة الحقيقية بتدّيه؛ null = زي قبل وضع التعلّم). */
    fun screen(inbox: SmsInboxPort, learning: SmsLearning? = null) = ReviewSmsInbox(
        ReviewSmsInboxDeps(ManageSmsInbox(inbox, parse), ImportStatement(importDeps(smsEffects(inbox))), MemoryMerchantRepository(), categories, ids, learning = learning),
    )

    suspend fun all(): List<Transaction> = txnStore.listByDateRange("0000-01-01", "9999-12-31")
}

/**
 * [learnOnReceive] (§77-A «وضع التعلّم» — S1): الافتراضي إن المالك **أكّد قبل كده** رسالة بنفس شكل كل رسالة بتوصل (من نفس مرسلها في
 * كل بلد قارئها فاهمها) — عشان اختبارات المحافظ والقفل والتكرار تفضل عن اللي بتختبره. وضع التعلّم نفسه بيتختبر بـ`false`
 * (`SmsLearnModeTest` · `SmsSalaryAskTest` · `SmsRoutingTest`). الشكل اللي مش واضح عمره ما بيتعلّم في الحالتين.
 */
internal class SmsWorld(val spaces: List<SmsSpace> = listOf(SmsSpace()), private val learnOnReceive: Boolean = true) {
    val memory = MemorySmsInbox(emptyList(), available = true)
    val inbox = FlakyInbox(memory)

    /** المالك فعّل المرسلين دول (الافتراضي TESTBANK) — رسايل غيرهم ما بتتسجلش لوحدها (الجولة الرابعة). */
    suspend fun enable(vararg senders: String): SmsWorld = apply { memory.enable(if (senders.isEmpty()) listOf("TESTBANK") else senders.toList()) }

    fun receive(vararg messages: QueuedSms) = messages.forEach {
        memory.receive(it)
        if (learnOnReceive) learn(it)
    }

    /** §77-A: المالك أكّد قبل كده رسالة بنفس شكل كل واحدة من [messages] (من مرسلها) — في كل بلد قارئها فاهمها (`MemorySmsInbox.preLearn`). */
    fun learn(vararg messages: QueuedSms) = messages.forEach { m -> spaces.forEach { s -> memory.preLearn(s.spaceId, s.parse, m) } }

    /** كل تشغيلة بنسخة جديدة من حالة الاستخدام — زي عامل خلفية جديد. */
    fun auto() = AutoRecordSms(AutoRecordSmsDeps(inbox, spaces.map { it.lane(inbox) }))

    suspend fun queued(): List<String> = memory.sync().messages.map { it.id }
}
