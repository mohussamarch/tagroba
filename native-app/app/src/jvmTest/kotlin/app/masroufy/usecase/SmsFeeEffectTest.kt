package app.masroufy.usecase

import app.masroufy.core.BankFeeCategory
import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.MatchingState
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsParseResult
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.core.walletBalancesOn
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryProjectLinkRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.port.QueuedSms
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal const val S2_MARCH = "2026-03-05T09:00:00Z"

/** حوالة بالإجمالي المستحق = 1,000.00 + رسوم 5.75 + ضريبة 0.86 (شكل إس تي سي). كل الأسامي والأرقام مخترعة. */
internal const val S2_SA_TOTAL_DUE = "حوالة صادرة\nمبلغ: SAR 1,000.00\nرسوم: SAR 5.75\nضريبة القيمة المضافة: SAR 0.86\nإجمالي المبلغ المستحق: SAR 1,006.61\nإلى: TEST PERSON\nفي: 2026-03-05 09:10"

/** حوالة محلية على شكل الراجحي: «الرسوم» سطر لوحدها برّه الـ1,000. */
internal const val S2_SA_FEE_ON_TOP = "حوالة محلية صادرة\nمصرف:ANB\nمن:1111\nمبلغ:SAR 1000\nالى:TEST PERSON\nالرسوم:SAR 5.75\n26/03/05 09:10"

/** فودافون كاش: «مصاريف الخدمة 1 جنيه» برّه الـ300. */
internal const val S2_VF_SEND = "تم تحويل 300 جنيه لرقم 01000001212 مصاريف الخدمة 1 جنيه رصيد حسابك فى فودافون كاش الحالي 699.\nتاريخ العملية: 09:10 26-03-05\nرقم العملية: 900000001"

internal val S2_EG_BANK = Wallet("w-eg-wallet", "محفظة مصرية وهمية", Currency.EGP, "bank", 0, "2026-01-01")
internal val S2_EG_CASH = Wallet("w-eg-cash", "كاش مصر", Currency.EGP, "cash", 0, "2026-01-01")

/**
 * مكتب الشريحة S2: بلد واحدة (مستودعات `SmsSpace`) + شاشة رسايل البنك بالاستيراد **ومعاه آثار التسجيل** (§77-B · §75-4 · §75-6) بالترتيب
 * اللي هيتوصّل بيه في التطبيق. التسجيل هنا **بضغطة المالك** («سجّل الكل») — مش التسجيل التلقائي (وضع التعلّم §77-A بيغيّره، شريحة S1).
 */
internal class S2Desk(
    wallets: List<Wallet> = listOf(CASH, BANK),
    val parse: (BankSmsMessage, Int) -> SmsParseResult = ::parseBankSms,
    val withEffects: Boolean = true,
    extraCategories: List<Category> = emptyList(),
    /** آثار قبل آثار S2 (عقد الترتيب — زي المبلغ المحلي للشراء الأجنبي في شريحة تانية). */
    before: List<RecordEffect> = emptyList(),
) {
    val space = SmsSpace(wallets = wallets, parse = parse)
    val inbox = MemorySmsInbox(emptyList(), available = true)

    /** نفس ذاكرة المحلات للشاشة («افتكره») وللاستيراد (التصنيف المتفتكر). */
    val merchants = MemoryMerchantRepository()
    val effects: List<RecordEffect> = before +
        if (withEffects) listOf(CashWithdrawalEffect(space.wallets), SmsFeeEffect(space.categories), RefundConfirmEffect()) else emptyList()
    val screen = ReviewSmsInbox(
        ReviewSmsInboxDeps(ManageSmsInbox(inbox, parse), ImportStatement(space.importDeps().copy(merchants = merchants, effects = effects)), merchants, space.categories, space.ids),
    )

    init {
        runBlocking { extraCategories.forEach { space.categories.save(it) } }
    }

    fun receive(id: String, body: String, at: String = S2_MARCH) = inbox.receive(QueuedSms(id, "TESTBANK", at, body))

    /** الهدف زي ما `AutoRecordSms.targetFor` بيبنيه: نفس قاعدة `cashWalletFor`، ومن غير الأثر مفيش محفظة كاش. */
    suspend fun target(wallet: Wallet = BANK) =
        SmsReviewTarget(wallet.id, wallet.name, wallet.currency, cashWalletId = if (withEffects) cashWalletFor(wallet, space.wallets.listAll())?.id else null)

    suspend fun load(wallet: Wallet = BANK) = screen.load(target(wallet))

    /** «سجّل الكل» = تأكيد المالك. */
    suspend fun recordAll(wallet: Wallet = BANK, includeSimilar: List<Int> = emptyList()): Int {
        load(wallet)
        return screen.recordAll(emptyMap(), includeSimilar)
    }

    suspend fun all(): List<Transaction> = space.all()

    suspend fun balance(wallet: Wallet): Long? = walletBalancesOn(space.wallets.listAll(), all(), "2026-12-31")[wallet.id]

    fun revert() = RevertImportBatch(
        RevertDeps(
            space.txnStore, space.sources, space.batchStore, MemorySettlementRepository(), MemoryAllocationRepository(), MemoryObligationRepository(),
            MemoryUnitOfWork(listOf(space.txnStore, space.sources, space.batchStore)),
            RevertLinkDeps(
                MemoryProjectLinkRepository(), MemoryEventLinkRepository(), MemoryTransactionTagRepository(), MemoryRoscaEntryRepository(),
                MemoryInstallmentPaymentRepository(), MemoryInstallmentPlanRepository(), MemoryZakatPaymentRepository(), MemoryAssetLotRepository(),
                MemoryAssetSaleRepository(),
            ),
        ),
    )
}

/**
 * الرسوم = عملية لوحدها «رسوم بنكية» جنب العملية الأصلية (§77-B — `SmsFeeEffect`): عمليتين في نفس الدفعة، الرصيد مظبوط، والتكرار
 * والتراجع شغالين على الاتنين. كل الرسايل مخترعة.
 */
class SmsFeeEffectTest {
    @Test fun aTransferWithTotalDueBecomesTheTransferAndASeparateBankFee() = runBlocking<Unit> {
        val desk = S2Desk()
        desk.receive("t1", S2_SA_TOTAL_DUE)
        assertEquals(1, desk.recordAll())
        val all = desk.all()
        assertEquals(2, all.size, "عمليتين بالظبط")
        val fee = all.single { it.economicKind == EconomicKind.FEE }
        val main = all.single { it.id != fee.id }
        assertEquals(100_000L, main.amountMinor, "الأصلية من غير الرسوم")
        assertEquals(100_661L, main.originalAmountMinor, "مبلغ الرسالة محفوظ لمنع التكرار (§32)")
        assertEquals(661L, fee.amountMinor, "5.75 رسوم + 0.86 ضريبة")
        assertTrue(fee.economicKindConfirmed)
        assertEquals(ReviewState.CONFIRMED, fee.reviewState)
        assertEquals(Direction.OUT, fee.observedDirection)
        assertEquals(BankFeeCategory.ID, fee.categoryId)
        assertEquals(BankFeeCategory.NAME, desk.space.categories.listAll().single { it.id == fee.categoryId }.name)
        assertEquals(listOf(main.occurredAt, main.walletId, main.currency), listOf(fee.occurredAt, fee.walletId, fee.currency))
        assertNull(transferPartyOf(fee), "الرسوم مش تحويل في زون التحويلات")
        assertNotNull(transferPartyOf(main), "والحوالة نفسها لسه تحويل")
        assertEquals(-100_661L, desk.balance(BANK), "الرصيد = المخصوم فعلًا")

        // نفس معاملة النوع زي الأول: الأصلية = اللي كان هيتسجل من غير الأثر بالظبط، ما عدا المبلغ
        val legacy = S2Desk(withEffects = false).run { receive("t1", S2_SA_TOTAL_DUE); recordAll(); all().single() }
        assertEquals(100_661L, legacy.amountMinor, "من غير الأثر: عملية واحدة بالإجمالي (زي التطبيق الحالي)")
        assertEquals(legacy, main.copy(id = legacy.id, amountMinor = 100_661L, originalAmountMinor = null))

        // سجل مصدر الرسوم في نفس الدفعة، مرجعه = مرجع الرسالة + #fee
        val records = desk.space.sources.listByTransactionIds(listOf(main.id, fee.id)).associateBy { it.transactionId }
        val mainRef = records.getValue(main.id).sourceReference!!
        assertEquals(mainRef + "#fee", records.getValue(fee.id).sourceReference)
        assertEquals(records.getValue(main.id).batchId, records.getValue(fee.id).batchId)
        assertEquals(MatchingState.NEW, records.getValue(fee.id).matchingState)
        assertEquals(uiText(TextKey.SMS_FEE_SOURCE_REASON), records.getValue(fee.id).reason)
    }

    @Test fun aFeeOnTopKeepsTheTransferAmount() = runBlocking<Unit> {
        val desk = S2Desk()
        desk.receive("t1", S2_SA_FEE_ON_TOP)
        desk.recordAll()
        val (main, fee) = desk.all().partition { it.economicKind != EconomicKind.FEE }.let { it.first.single() to it.second.single() }
        assertEquals(100_000L, main.amountMinor)
        assertNull(main.originalAmountMinor)
        assertEquals(575L, fee.amountMinor)
        assertEquals(-100_575L, desk.balance(BANK))
    }

    @Test fun vodafoneCashServiceFeeKeepsTheWalletRight() = runBlocking<Unit> {
        val desk = S2Desk(wallets = listOf(S2_EG_CASH, S2_EG_BANK), parse = ::parseEgyptBankSms)
        desk.receive("vf", S2_VF_SEND)
        assertEquals(1, desk.recordAll(S2_EG_BANK))
        assertEquals(listOf(100L, 30_000L), desk.all().map { it.amountMinor }.sorted())
        assertTrue(desk.all().all { it.currency == Currency.EGP })
        assertEquals(-30_100L, desk.balance(S2_EG_BANK))
    }

    @Test fun readingTheSameMessageAgainIsADuplicateAndAddsNoSecondFee() = runBlocking<Unit> {
        for (body in listOf(S2_SA_TOTAL_DUE, S2_SA_FEE_ON_TOP)) {
            val desk = S2Desk()
            desk.receive("t1", body)
            desk.recordAll()
            desk.receive("t1", body)
            val view = desk.load()
            assertEquals(listOf("t1"), view.duplicates.map { it.messageId }, "مكررة — مش تعارض")
            assertTrue(view.ready.isEmpty() && view.similar.isEmpty())
            assertEquals(0, desk.screen.recordAll(emptyMap(), emptyList()))
            assertEquals(2, desk.all().size, "ولا رسوم تانية")
        }
    }

    @Test fun aSimilarLineThatWasNotChosenWritesNoFee() = runBlocking<Unit> {
        val desk = S2Desk()
        desk.receive("t1", S2_SA_FEE_ON_TOP)
        desk.recordAll()
        // نفس اليوم والمبلغ والاتجاه من رسالة تانية ⇒ «شبه عملية موجودة» · وجنبها شراء جديد
        desk.receive("t2", "حوالة محلية صادرة\nمصرف:ANB\nمن:1111\nمبلغ:SAR 1000\nالى:TEST OTHER\nالرسوم:SAR 5.75\n26/03/05 11:30")
        desk.receive("c1", "شراء\nبـSR 40\nلدى:TEST MART\n26/03/05")
        val similar = desk.load().similar.single()
        assertEquals("t2", similar.messageId)
        assertEquals(1, desk.screen.recordAll(emptyMap(), emptyList()), "الجديد بس")
        assertEquals(3, desk.all().size, "الشراء اتضاف — ولا رسوم من السطر اللي ما اتختارش")
        assertEquals(1, desk.all().count { it.economicKind == EconomicKind.FEE })
        // لما المالك يختاره ⇒ الأصلية ورسومها
        desk.recordAll(includeSimilar = listOf(similar.lineNumber))
        assertEquals(5, desk.all().size)
        assertEquals(2, desk.all().count { it.economicKind == EconomicKind.FEE })
    }

    @Test fun revertingTheBatchDeletesTheTransferAndItsFee() = runBlocking<Unit> {
        val desk = S2Desk()
        desk.receive("t1", S2_SA_TOTAL_DUE)
        desk.recordAll()
        val batch = desk.space.batchStore.listRecent(10).single()
        // مراجعة S2: عدد الدفعة فيه عملية الرسوم (كان 1 والتراجع بيمسح 2 — والسجل بيعرض «1 عملية»)
        assertEquals(2, batch.counts.imported, "الحوالة ورسومها")
        val plan = desk.revert().execute(batch.id)
        assertEquals(2 to 2, plan.toDelete.size to plan.expectedCount)
        assertTrue(desk.all().isEmpty())
        assertTrue(desk.space.sources.listByBatch(batch.id).isEmpty())
    }

    @Test fun theFeeIsNeverWrittenWithoutItsTransfer() = runBlocking<Unit> {
        val desk = S2Desk()
        desk.receive("t1", S2_SA_TOTAL_DUE)
        desk.space.txns.failSaves = 1
        assertFailsWith<IllegalStateException> { desk.recordAll() }
        assertTrue(desk.all().isEmpty(), "وحدة العمل رجّعت كله")
        assertTrue(desk.space.sources.listByAccountIdentity(BANK.name).isEmpty() && desk.space.batchStore.listRecent(10).isEmpty())
        assertEquals(1, desk.recordAll(), "الرسالة فضلت في الصندوق واتسجلت المرة الجاية")
        assertEquals(2, desk.all().size)
    }

    @Test fun aFeeOnlyMessageIsABankFee() = runBlocking<Unit> {
        val desk = S2Desk()
        desk.receive("f1", "Debit fees\nReason: Card replacement fee\nAmount: SAR 15.00\nFrom: STC Bank wallet\nDate: 2026-03-05 09:10:44")
        desk.recordAll()
        val t = desk.all().single()
        assertEquals(EconomicKind.FEE, t.economicKind)
        assertTrue(t.economicKindConfirmed && t.categoryConfirmed)
        assertEquals(ReviewState.CONFIRMED, t.reviewState)
        assertEquals(BankFeeCategory.ID, t.categoryId)
        assertEquals(BankFeeCategory.NAME, t.sourceOperationType)
        assertEquals(1_500L, t.amountMinor)
    }

    @Test fun theOwnersBankFeeCategoryIsReusedAndOursIsCreatedOnce() = runBlocking<Unit> {
        val mine = Category("c-my-fees", null, "رسوم بنكية", "receipt", "#222222", "#dddddd", true, 5)
        val desk = S2Desk(extraCategories = listOf(mine))
        desk.receive("t1", S2_SA_TOTAL_DUE)
        desk.recordAll()
        assertEquals("c-my-fees", desk.all().single { it.economicKind == EconomicKind.FEE }.categoryId, "تصنيف المالك بنفس الاسم")
        assertTrue(desk.space.categories.listAll().none { it.id == BankFeeCategory.ID }, "ما اتعملش تصنيف تاني")

        val fresh = S2Desk()
        fresh.receive("t1", S2_SA_TOTAL_DUE)
        fresh.receive("t2", S2_SA_FEE_ON_TOP, "2026-03-05T10:00:00Z")
        fresh.recordAll()
        fresh.receive("t3", "حوالة محلية صادرة\nمصرف:ANB\nمن:1111\nمبلغ:SAR 250\nالى:TEST PERSON\nالرسوم:SAR 2.00\n26/03/05 12:00")
        fresh.recordAll()
        assertEquals(1, fresh.space.categories.listAll().count { it.name == BankFeeCategory.NAME }, "اتعمل مرة واحدة")
        assertEquals(3, fresh.all().count { it.categoryId == BankFeeCategory.ID })
    }
}
