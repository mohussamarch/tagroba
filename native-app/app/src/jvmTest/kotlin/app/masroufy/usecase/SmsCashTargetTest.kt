package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.SmsKind
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.core.uiText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * مراجعة الشريحة S2 (§75-4): قاعدة الانتظار في الخلفية والأثر كانوا بشرطين مختلفين — محفظة كاش بعملة تانية، أو المحفظة نفسها هي الكاش،
 * كانوا بيخلّوا السحب «جاهز» في الخلفية والأثر يسيبه، فيتسجل **صرف من البنك لوحده**. وسلفة كارت الائتمان والسحب من صرّاف برّه كانوا
 * بيتنقلوا للكاش. دلوقتي قاعدة واحدة (`cashWalletFor` + `movesToCash`) للهدف والأثر. كل الرسايل والأسامي والأرقام مخترعة.
 */
class SmsCashTargetTest {
    private val atm = "ATM Withdrawal\nCard: *7739\nAmount: SAR 500.00\nAt: TEST ATM RIYADH\nOn: 2026-10-07 10:00"
    private val egAgent = "تم سحب 500 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 700 جنيه. تاريخ العملية: 2026-10-07 10:00 رقم العملية: 900000613"
    private val cardAdvance = "Your credit card ending with #4417 was charged for EGP 2,000.00 at CASH WITHDRAWAL on 07/10/2026"
    private val abroad = "سحب صراف آلي دولي\nمبلغ: SAR 500.00\nبطاقة: *7739\nفي: 2026-10-07 10:00"
    private val usdCash = Wallet("w-usd-cash", "نقد بالدولار", Currency.USD, "cash", 0, "2026-01-01")
    private fun s2Effects(space: SmsSpace): List<RecordEffect> = listOf(CashWithdrawalEffect(space.wallets), SmsFeeEffect(space.categories), RefundConfirmEffect())

    private fun laneOf(space: SmsSpace, world: SmsWorld, wired: Boolean = true) =
        SmsLane.of(space.spaceId, space.importDeps().copy(effects = if (wired) s2Effects(space) else emptyList()), ManageSmsInbox(world.inbox, space.parse), space.wallets)

    @Test fun aCashWalletInAnotherCurrencyIsNotWhereTheWithdrawalGoes() = runBlocking<Unit> {
        // الخلفية: كانت بتسجله «غير محدد» صرف 500 من البنك (المصروف الشخصي 500)
        val space = SmsSpace(wallets = listOf(usdCash, BANK))
        val world = SmsWorld(listOf(space)).enable()
        world.receive(sms("atm", atm))
        val auto = AutoRecordSms(AutoRecordSmsDeps(world.inbox, listOf(laneOf(space, world))))
        val r = auto.run()
        assertEquals(0 to listOf("atm"), r.recorded to r.waiting)
        assertTrue(space.all().isEmpty(), "ما اتسجلش صرف من البنك")
        assertNull(auto.targetFor("sa", BANK.id)!!.cashWalletId)

        // الشاشة: سببه صحيح، والمالك لو سجّله بيتسجل زي ما هو (ما بنحطش ريال في محفظة دولار)
        val desk = S2Desk(wallets = listOf(usdCash, BANK))
        desk.receive("atm", atm, SENT_AT)
        assertEquals(uiText(TextKey.SMS_WAIT_CASH_WITHDRAWAL), desk.load().ready.single().confirmReason)
        desk.screen.recordAll(emptyMap(), emptyList())
        val t = desk.all().single()
        assertEquals(EconomicKind.UNCLASSIFIED to null, t.economicKind to t.transferToWalletId)
        assertEquals(0L, desk.balance(usdCash))

        // حتى لو هدف اتبنى غلط (محفظة كاش بعملة تانية) ⇒ الأثر نفسه ما بينقلش
        val wrong = S2Desk(wallets = listOf(usdCash, BANK))
        wrong.receive("atm", atm, SENT_AT)
        wrong.screen.load(SmsReviewTarget(BANK.id, BANK.name, cashWalletId = usdCash.id))
        wrong.screen.recordAll(emptyMap(), emptyList())
        assertNull(wrong.all().single().transferToWalletId)
    }

    @Test fun aWalletThatIsItselfTheCashWalletDoesNotMoveToItself() = runBlocking<Unit> {
        // مصر: المحفظة الوحيدة نوعها كاش والمرسل مربوط بيها ⇒ كان السحب من الوكيل بيتسجل «غير محدد» فيها لوحده
        val vf = Wallet("w-vf", "محفظة كاش وهمية", Currency.EGP, "cash", 0, "2026-01-01")
        val space = SmsSpace(spaceId = "eg", wallets = listOf(vf), parse = ::parseEgyptBankSms)
        val world = SmsWorld(listOf(space)).enable()
        world.receive(sms("vf", egAgent))
        val auto = AutoRecordSms(AutoRecordSmsDeps(world.inbox, listOf(laneOf(space, world))))
        auto.chooseWallet("eg", "TESTBANK", vf.id)
        val r = auto.run()
        assertEquals(0 to listOf("vf"), r.recorded to r.waiting)
        assertTrue(space.all().isEmpty())
        assertNull(auto.targetFor("eg", vf.id)!!.cashWalletId, "مش بينقل لنفسه")
    }

    @Test fun creditCardCashAndAtmsAbroadWaitAndAreNeverMoved() = runBlocking<Unit> {
        // سلفة نقدية من كارت ائتمان (شكل معروف): كانت بتتسجل لوحدها نقل 2,000 من الحساب للكاش — والحساب ما اتحركش
        val desk = S2Desk(wallets = listOf(S2_EG_CASH, S2_EG_BANK), parse = ::parseEgyptBankSms)
        desk.receive("adv", cardAdvance, SENT_AT)
        val line = desk.load(S2_EG_BANK).ready.single()
        assertEquals(SmsKind.CASH_WITHDRAWAL, line.kind)
        assertEquals(uiText(TextKey.SMS_WAIT_CASH_ADVANCE), line.confirmReason)
        assertFalse(line.shape.clear)
        desk.screen.recordAll(emptyMap(), emptyList())
        assertNull(desk.all().single().transferToWalletId, "المالك أكّد ⇒ بيتسجل زي ما هو، مش نقل")
        assertEquals(0L, desk.balance(S2_EG_CASH))

        // في الخلفية: بيستنى
        val space = SmsSpace(spaceId = "eg", wallets = listOf(S2_EG_CASH, S2_EG_BANK), parse = ::parseEgyptBankSms)
        val world = SmsWorld(listOf(space)).enable()
        world.receive(sms("adv", cardAdvance))
        val r = AutoRecordSms(AutoRecordSmsDeps(world.inbox, listOf(laneOf(space, world)))).run()
        assertEquals(listOf("adv"), r.waiting)
        assertTrue(space.all().isEmpty())

        // صرّاف برّه البلد بالريال بس: لما المالك يأكده ما بيتحطش ريال في محفظة النقد (اللي في الإيد عملة تانية)
        val sa = S2Desk()
        sa.receive("abroad", abroad, SENT_AT)
        sa.load()
        sa.screen.recordAll(emptyMap(), emptyList())
        val t = sa.all().single()
        assertEquals(EconomicKind.UNCLASSIFIED to null, t.economicKind to t.transferToWalletId, "صرف لسه ما اتحددش نوعه")
        assertEquals(0L to -50_000L, sa.balance(CASH) to sa.balance(BANK))
    }

    @Test fun theScreenGetsTheSameTargetAsTheBackground() = runBlocking<Unit> {
        val space = SmsSpace()
        val world = SmsWorld(listOf(space)).enable()
        world.receive(sms("atm", atm))
        val lane = laneOf(space, world)
        val auto = AutoRecordSms(AutoRecordSmsDeps(world.inbox, listOf(lane)))
        val target = auto.targetFor("sa", BANK.id)!!
        assertEquals(SmsReviewTarget(BANK.id, BANK.name, Currency.SAR, null, emptySet(), CASH.id), target)
        assertNull(auto.targetFor("sa", "w-missing"))
        assertNull(AutoRecordSms(AutoRecordSmsDeps(world.inbox, listOf(laneOf(space, world, wired = false)))).targetFor("sa", BANK.id)!!.cashWalletId)

        // الشاشة بالهدف ده: السحب جاهز من غير سبب انتظار — زي الخلفية بالظبط
        assertNull(lane.review.load(target).ready.single().confirmReason)
        // هدف الشاشة اتبنى من غير محفظة الكاش: السبب بيقول إمتى بيتنقل (صحيح)، و«سجّل الكل» بينقله فعلًا لإن فيه محفظة كاش واحدة
        val line = lane.review.load(SmsReviewTarget(BANK.id, BANK.name)).ready.single()
        assertEquals(uiText(TextKey.SMS_WAIT_CASH_WITHDRAWAL), line.confirmReason)
        lane.review.recordAll(emptyMap(), emptyList())
        assertEquals(CASH.id, space.all().single().transferToWalletId)
    }
}
