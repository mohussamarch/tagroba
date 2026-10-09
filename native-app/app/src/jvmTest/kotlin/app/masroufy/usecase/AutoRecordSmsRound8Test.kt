package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.core.uiText
import app.masroufy.memory.MemoryMerchantRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * الجولة التامنة (OVERRIDES §72.5 — المراجعة العدائية الرابعة): رسايل **شكلها معروف** بس تسجيلها لوحدها في محفظة البنك كان غلط:
 * - **فلوس داخلة على كارت ائتمان** («Credit Card Credited» · «بطاقة ائتمانية تأكيد سداد» · «تم قيد مبلغ … لبطاقتك الائتمانية») كانت بتتسجل
 *   دخل لحساب البنك وتلغي خصم «Credit Card Payment» ⇒ الرصيد يزيد بالسداد كله (M01/M02: صافي 0 بدل −1,500). دلوقتي بتستنى — إلا لو المحفظة
 *   نفسها هي الكارت (أرقامها نفس أرقام الكارت).
 * - **إيداع كاش** (نقل من محفظة الكاش، §75-4 بالعكس) و**شراء ومعاه كاش** بيستنوا زي السحب.
 * - **حساب المالك التاني** بكل كتابات سطر الحساب («Account: **4417» · «IBAN: SA** **** 4417» …) بيستنى ومعاه سببه.
 * كل الرسايل والأرقام مخترعة.
 */
class AutoRecordSmsRound8Test {
    private val payment = "Credit Card Payment\nAmount: SAR 1,500.00\nCard: *4476\nFrom account: **1188\nOn: 2026-10-07 13:04"
    private val credited = "Credit Card Credited\nAmount: SAR 1,500.00\nCard: *4476\nOn: 2026-10-07 13:05"
    private val paymentAr = "بطاقة ائتمانية تسديد\nالمبلغ: 2,200.00 ر.س\nالبطاقة: *4476\nمن حساب: **1188\nفي: 2026-10-07 13:04"
    private val creditedAr = "بطاقة ائتمانية تأكيد سداد\nالمبلغ: 2,200.00 ر.س\nالبطاقة: *4476\nفي: 2026-10-07 13:05"
    private val merchantCredit = "Credit Card Credited\nAmount: SAR 89.00\nCard: *4476\nAt: MARJAN TOYS\nOn: 2026-10-07 16:30"
    private val atm = "سحب صراف آلي\nمبلغ: 600.00 ر.س\nبطاقة: *5093\nفي: 2026-10-07 15:10"
    private val deposit = "إيداع صراف آلي\nمبلغ: 600.00 ر.س\nحساب: **1188\nفي: 2026-10-07 15:40"
    private val depositEn = "Deposit ATM\nAmount: SAR 2,000.00\nAccount: **1188\nOn: 2026-10-07 09:12"
    private val cashback = "PoS Purchase & Cashback\nAmount: SAR 350.00\nAt: MARJAN HYPER\nOn: 2026-10-07 18:40"

    private fun screen(world: SmsWorld, space: SmsSpace) = ReviewSmsInbox(
        ReviewSmsInboxDeps(ManageSmsInbox(world.inbox, space.parse), ImportStatement(space.importDeps()), MemoryMerchantRepository(), space.categories, space.ids),
    )

    @Test fun aCardCreditNeverCancelsTheCardPaymentDebit() = runBlocking<Unit> {
        val bank = Wallet("w-sa-bank", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "1188")
        val space = SmsSpace(wallets = listOf(CASH, bank))
        val world = SmsWorld(listOf(space)).enable()
        world.receive(
            sms("pay", payment), sms("cred", credited), sms("payAr", paymentAr), sms("credAr", creditedAr), sms("refund", merchantCredit),
            sms("atm", atm), sms("dep", deposit), sms("depEn", depositEn), sms("cb", cashback),
        )

        val r = world.auto().run()
        assertEquals(2, r.recorded, "خصم سداد الكارت من الحساب بس")
        assertEquals(listOf("cred", "credAr", "refund", "atm", "dep", "depEn", "cb"), r.waiting)
        val all = space.all()
        assertEquals(listOf(Direction.OUT, Direction.OUT), all.map { it.observedDirection })
        assertEquals(-370_000L, all.sumOf { if (it.observedDirection == Direction.IN) it.amountMinor else -it.amountMinor }, "صافي −3,700 (مش صفر)")

        val ready = screen(world, space).load(SmsReviewTarget(bank.id, bank.name, accountLast4 = "1188")).ready.associateBy { it.messageId }
        for (id in listOf("cred", "credAr")) assertEquals(uiText(TextKey.SMS_WAIT_CARD_CREDIT), ready.getValue(id).confirmReason, id)
        assertEquals(uiText(TextKey.SMS_WAIT_REFUND), ready.getValue("refund").confirmReason)
        for (id in listOf("dep", "depEn")) assertEquals(uiText(TextKey.SMS_WAIT_CASH_DEPOSIT), ready.getValue(id).confirmReason, id)
        assertEquals(uiText(TextKey.SMS_WAIT_CASH_WITHDRAWAL), ready.getValue("atm").confirmReason)
        assertEquals(uiText(TextKey.SMS_WAIT_PURCHASE_CASH), ready.getValue("cb").confirmReason)
    }

    /** المحفظة **هي** الكارت (أرقامها نفس الكارت) ⇒ السداد الداخل عليها بيتسجل لوحده. */
    @Test fun aCardCreditRecordsItselfIntoTheCardsOwnWallet() = runBlocking<Unit> {
        val card = Wallet("w-card", "بطاقة وهمية", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "4476")
        val space = SmsSpace(wallets = listOf(CASH, card))
        val world = SmsWorld(listOf(space)).enable()
        world.receive(sms("cred", credited))
        val r = world.auto().run()
        assertEquals(1, r.recorded)
        assertEquals(listOf(150_000L to Direction.IN), space.all().map { it.amountMinor to it.observedDirection })
    }

    @Test fun egyptianCardCreditsCashWithdrawalsAndRefundsWait() = runBlocking<Unit> {
        val eg = Wallet("eg-bank", "بنك مصري وهمي", Currency.EGP, "bank", 0, "2026-01-01", accountLast4 = "2277")
        val space = SmsSpace("eg", wallets = listOf(eg), parse = ::parseEgyptBankSms)
        val world = SmsWorld(listOf(space)).enable()
        world.receive(
            sms("credit", "تم قيد مبلغ 2,500.00 جم لبطاقتك الائتمانية رقم 3318"),
            sms("atm", "تم خصم 2,000 جم من بطاقة الخصم المباشر رقم 3318 عند CASH WITHDRAWAL BANQUE SAMPLE يوم 10-07 الساعة 13:10"),
            sms("cashback", "تم اضافة تحويل لحظي لحسابكم رقم 2277 بمبلغ 60 جم من كاش باك فوري رقم مرجعي 900000563 يوم 10-07"),
            sms("buy", "تم خصم 850 جم من بطاقة الخصم المباشر رقم 3318 عند ZAHRA MART يوم 10/07 الساعة 14:05"),
        )
        val r = world.auto().run()
        assertEquals(1, r.recorded, "الشراء بس")
        assertEquals(listOf("credit", "atm", "cashback"), r.waiting)
        assertEquals(listOf("2026-10-07"), space.all().map { it.occurredAt }, "«يوم 10/07» شهر/يوم في جملة الأهلي")
        val ready = screen(world, space).load(SmsReviewTarget(eg.id, eg.name, Currency.EGP, accountLast4 = "2277")).ready.associateBy { it.messageId }
        assertEquals(uiText(TextKey.SMS_WAIT_CARD_CREDIT), ready.getValue("credit").confirmReason)
        assertEquals(uiText(TextKey.SMS_WAIT_CASH_WITHDRAWAL), ready.getValue("atm").confirmReason)
        assertEquals(uiText(TextKey.SMS_WAIT_REFUND), ready.getValue("cashback").confirmReason)
    }

    /** P-group: حساب المالك التاني بأي كتابة لسطر الحساب ⇒ بيستنى بدل ما يتسجل في محفظة البنك المربوط. */
    @Test fun everyAccountSpellingOfTheOwnersOtherAccountWaits() = runBlocking<Unit> {
        val bankA = Wallet("sa-bank", "بنك وهمي أ", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "6618")
        val bankB = Wallet("sa-bank2", "بنك وهمي ب", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "4417")
        val space = SmsSpace(wallets = listOf(CASH, bankA, bankB))
        val world = SmsWorld(listOf(space)).enable()
        world.auto().chooseWallet("sa", "TESTBANK", bankA.id)
        val bodies = listOf(
            "Debit Transfer Local\nAmount: SAR 900.00\nAccount: **4417\nTo: TAREQ PROBE\nOn: 2026-10-07 11:40",
            "حوالة صادرة محلية\nالمبلغ: 900.00 ر.س\nالحساب: **4417\nالى: طارق الاختباري\nفي: 2026-10-07 11:40",
            "حوالة صادرة محلية\nالمبلغ: 900.00 ر.س\nمن حساب: 4417*\nالى: طارق الاختباري\nفي: 2026-10-07 11:40",
            "Credit transfer Local\nAmount: SAR 900.00\nIBAN: SA** **** 4417\nFrom: TAREQ PROBE\nOn: 2026-10-07",
            "Debit Transfer Local\nAmount: SAR 900.00\nFrom: SA****4417\nTo: TAREQ PROBE\nOn: 2026-10-07 11:40",
            "PoS Purchase\nAmount: SAR 64.25\nCard: *9001\nAccount: **4417\nAt: WOMBAT PANTRY\nOn: 2026-10-07 18:22",
        )
        world.receive(*bodies.mapIndexed { i, b -> sms("p$i", b) }.toTypedArray(), sms("cafe", CAFE))
        val r = world.auto().run()
        assertEquals(1, r.recorded, "الرسالة اللي مفيهاش رقم حساب بس")
        assertEquals(bodies.indices.map { "p$it" }, r.waiting)
        assertTrue(space.all().all { it.walletId == bankA.id })
        val target = SmsReviewTarget(bankA.id, bankA.name, accountLast4 = "6618", otherAccountsLast4 = setOf("4417"))
        val ready = screen(world, space).load(target).ready
        assertTrue(ready.all { it.confirmReason == uiText(TextKey.SMS_WAIT_OTHER_ACCOUNT) }, ready.joinToString { "${it.messageId}: ${it.confirmReason}" })
    }
}
