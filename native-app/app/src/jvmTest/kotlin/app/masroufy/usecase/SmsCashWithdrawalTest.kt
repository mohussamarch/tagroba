package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.Language
import app.masroufy.core.Period
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsKind
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.Wallet
import app.masroufy.core.cashMovement
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.core.uiText
import app.masroufy.memory.MemoryAllocationRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * السحب من الصرّاف = نقل لمحفظة الكاش لوحده (§75-4 — `CashWithdrawalEffect`): تحويل داخلي مؤكد من البنك للكاش، البنك بينزل والكاش بيزيد،
 * ومش مصروف ولا «حركة فلوس». البلد اللي مالهاش محفظة كاش **واحدة** ⇒ بيستنى بسبب جديد. الإيداع الكاش والشراء بالكاش لسه بيستنوا
 * (أسئلتهم مفتوحة). كل الرسايل والأسامي مخترعة.
 */
class SmsCashWithdrawalTest {
    private val atm = "ATM Withdrawal\nCard: *7739\nAmount: SAR 500.00\nAt: TEST ATM RIYADH\nOn: 2026-10-07 10:00"
    private val egAgent = "تم سحب 500 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 700 جنيه. تاريخ العملية: 2026-10-07 10:00 رقم العملية: 900000613"
    private val october = Period("2026-10", "2026-10-01", "2026-10-31", 31)

    @AfterTest fun resetTexts() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test fun anAtmWithdrawalMovesToTheOnlyCashWallet() = runBlocking<Unit> {
        val desk = S2Desk()
        desk.receive("atm", atm, SENT_AT)
        val line = desk.load().ready.single()
        assertEquals(SmsKind.CASH_WITHDRAWAL, line.kind)
        assertNull(line.confirmReason, "مش مستني — بيتسجل لوحده")
        assertTrue(line.shape.clear)
        desk.screen.recordAll(emptyMap(), emptyList())

        val t = desk.all().single()
        assertEquals(EconomicKind.INTERNAL_TRANSFER, t.economicKind)
        assertTrue(t.economicKindConfirmed)
        assertEquals(ReviewState.CONFIRMED, t.reviewState)
        assertEquals(BANK.id, t.walletId)
        assertEquals(CASH.id, t.transferToWalletId)
        assertEquals(-50_000L, desk.balance(BANK))
        assertEquals(50_000L, desk.balance(CASH))

        val cash = LoadCashSummary(LoadCashSummaryDeps(desk.space.wallets, desk.space.txnStore, MemoryAllocationRepository(), desk.space.categories))
            .load(october, "2026-10-08")!!
        assertEquals(50_000L, cash.balanceMinor, "الكاش زاد")
        assertEquals(0L, cash.spentInPeriodMinor, "النقل مش صرف كاش")
        val totals = computePeriodTotals(desk.all(), emptyList())
        assertEquals(0L to 0L, totals.incomeMinor to totals.personalExpenseMinor, "مش دخل ولا مصروف")
        assertEquals(0L to 0L, cashMovement(desk.all()).let { it.inMinor to it.outMinor }, "ولا «حركة فلوس» (§58)")
    }

    @Test fun anEgyptWalletAgentWithdrawalMovesToTheEgyptCashWallet() = runBlocking<Unit> {
        val desk = S2Desk(wallets = listOf(S2_EG_CASH, S2_EG_BANK), parse = ::parseEgyptBankSms)
        desk.receive("vf", egAgent, SENT_AT)
        assertNull(desk.load(S2_EG_BANK).ready.single().confirmReason)
        desk.screen.recordAll(emptyMap(), emptyList())
        val t = desk.all().single()
        assertEquals(EconomicKind.INTERNAL_TRANSFER to S2_EG_CASH.id, t.economicKind to t.transferToWalletId)
        assertEquals(Currency.EGP, t.currency)
        assertEquals(-50_000L to 50_000L, desk.balance(S2_EG_BANK) to desk.balance(S2_EG_CASH))
    }

    @Test fun withoutExactlyOneCashWalletTheWithdrawalWaitsWithItsReason() = runBlocking<Unit> {
        val cash2 = Wallet("w-cash2", "نقد تاني", Currency.SAR, "cash", 0, "2026-01-01")
        for (wallets in listOf(listOf(BANK), listOf(CASH, cash2, BANK))) {
            val desk = S2Desk(wallets = wallets)
            desk.receive("atm", atm, SENT_AT)
            val texts = mutableListOf<String?>()
            for ((language, variant) in listOf(Language.AR to ArabicVariant.MSA, Language.AR to ArabicVariant.EGYPTIAN, Language.EN to ArabicVariant.MSA)) {
                Texts.language = language
                Texts.arabicVariant = variant
                val line = desk.load().ready.single()
                assertEquals(uiText(TextKey.SMS_WAIT_NO_CASH_WALLET), line.confirmReason, "$wallets $language $variant")
                assertTrue(!line.shape.clear)
                texts += line.confirmReason
            }
            assertEquals(3, texts.toSet().size, "تلات نسخ مختلفة")
            resetTexts()
            // المالك سجّلها بنفسه ⇒ بتتسجل زي ما هي (ما بنختارش محفظة كاش من عندنا)
            desk.recordAll()
            val t = desk.all().single()
            assertEquals(EconomicKind.UNCLASSIFIED, t.economicKind)
            assertNull(t.transferToWalletId)
        }
    }

    @Test fun cashDepositsAndPurchasesWithCashStillWait() = runBlocking<Unit> {
        val desk = S2Desk()
        desk.receive("dep", "Deposit ATM\nAmount: SAR 2,000.00\nAccount: **1188\nOn: 2026-10-07 09:12", SENT_AT)
        desk.receive("cb", "PoS Purchase & Cashback\nAmount: SAR 350.00\nAt: TEST HYPER\nOn: 2026-10-07 18:40", SENT_AT)
        val target = desk.target()
        assertEquals(CASH.id, target.cashWalletId, "فيه محفظة كاش واحدة")
        val ready = desk.screen.load(target).ready.associateBy { it.messageId }
        assertEquals(uiText(TextKey.SMS_WAIT_CASH_DEPOSIT), ready.getValue("dep").confirmReason)
        assertEquals(uiText(TextKey.SMS_WAIT_PURCHASE_CASH), ready.getValue("cb").confirmReason)
    }

    /**
     * التسجيل التلقائي في الخلفية: البلد اللي استيرادها فيه الأثر ⇒ السحب بيتسجل نقل لوحده؛ من غيره (`SmsSpace` في الاختبارات القديمة) ⇒
     * بيستنى بدل ما يتسجل صرف من البنك. ⚠️ بعد وضع التعلّم (§77-A — شريحة S1) أول رسالة من الشكل ده هتستنى مرة.
     */
    @Test fun inTheBackgroundOnlyALaneThatMovesCashRecordsWithdrawals() = runBlocking<Unit> {
        for (wired in listOf(true, false)) {
            val space = SmsSpace()
            val world = SmsWorld(listOf(space)).enable()
            world.receive(sms("atm", atm))
            val effects = if (wired) listOf(CashWithdrawalEffect(space.wallets), SmsFeeEffect(space.categories), RefundConfirmEffect()) else emptyList()
            val lane = SmsLane.of(space.spaceId, space.importDeps().copy(effects = effects), ManageSmsInbox(world.inbox, space.parse), space.wallets)
            val r = AutoRecordSms(AutoRecordSmsDeps(world.inbox, listOf(lane))).run()
            if (wired) {
                assertEquals(1, r.recorded)
                assertEquals(CASH.id, space.all().single().transferToWalletId)
            } else {
                assertEquals(listOf("atm"), r.waiting)
                assertTrue(space.all().isEmpty(), "ما اتسجلش صرف من البنك")
            }
            assertNotEquals(wired, r.waiting.contains("atm"))
        }
    }
}
