package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.PendingAsk
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryUnitOfWork
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * مراجعة S1 — الأسئلة: «ده حسابك التاني؟» لطرف **باسم** آخره زي حسابك (بدل ما يتأكد تحويل داخلي لوحده) · «ده راتبك؟» لازم يترد قبل
 * التسجيل · بلد الرسالة اللي ولا قارئ فهمها. كل الأسامي والأرقام مخترعة.
 */
class SmsAsksReviewTest {
    private val a = Wallet("w-a", "بنك وهمي أ", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "1111")
    private val b = Wallet("w-b", "بنك وهمي ب", Currency.SAR, "bank", 0, "2026-01-01", accountLast4 = "2222")
    private val salary = "راتب\nبـSR 9,500\n26/10/01"
    private val nextSalary = "راتب\nبـSR 9,750\n26/10/06"

    private fun asks(space: SmsSpace) = OwnAccountAskSource(space.spaceId, space.txnStore, space.sources, space.wallets, space.parties)

    /** الشكوى ٥: تحويل لـ«SAMPLE PERSON» وحسابه آخره 2222 (زي محفظة ب بالصدفة) كان بيبقى «تحويل داخلي» مؤكد والمصروف بيختفي. */
    @Test fun aNamedPartyWithTheDigitsOfYourOtherAccountIsAskedOnceNotConfirmed() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, a, b))
        val w = SmsWorld(listOf(space)).enable()
        w.auto().chooseWallet("sa", "TESTBANK", a.id)
        w.receive(
            sms("t1", "حوالة داخلية صادرة\nمن:1111\nإلى:SAMPLE PERSON\nإلى:2222\nبـSR 500\n26/10/07 09:35"),
            sms("t2", "حوالة داخلية صادرة\nمن:1111\nإلى:SAMPLE PERSON\nإلى:2222\nبـSR 120\n26/10/06 18:10"),
        )
        assertEquals(2, w.auto().run().recorded)
        val sent = space.all()
        assertTrue(sent.all { it.economicKind == EconomicKind.UNCLASSIFIED && !it.economicKindConfirmed }, "مش متأكد لوحده: $sent")
        val pending = asks(space).pending("2026-10-01", "2026-10-31")
        val latest = sent.single { it.amountMinor == 50_000L }
        assertEquals(listOf(PendingAsk(AskKind.OWN_ACCOUNT_CHECK, "sa", transactionId = latest.id, date = "2026-10-07")), pending, "سؤال واحد للطرف")

        // الرد «أيوه حسابي» = قرار الزون (§60): العمليتين تحويل داخلي، وما بيتسألش تاني
        val zone = ManageTransfers(ManageTransfersDeps(space.txnStore, space.parties, MemoryPersonRepository(), MemoryUnitOfWork(listOf(space.txnStore, space.parties)), FixedClock("2026-10-08T09:00:00.000Z")))
        assertEquals(2, zone.markOwnAccount(transferPartyOf(latest)!!))
        assertTrue(space.all().all { it.economicKind == EconomicKind.INTERNAL_TRANSFER && it.economicKindConfirmed })
        assertTrue(asks(space).pending("2026-10-01", "2026-10-31").isEmpty())
    }

    /** محفظة واحدة بس بأرقام تكفي للسؤال (زي الأثر): التحويل من محفظة مكتوبش أرقامها لطرف باسم آخره زي محفظة ب ⇒ بيتسأل برضه. */
    @Test fun oneWalletWithDigitsIsEnoughForTheQuestion() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, BANK, b))
        val w = SmsWorld(listOf(space)).enable()
        w.auto().chooseWallet("sa", "TESTBANK", BANK.id)
        w.receive(sms("t1", "حوالة داخلية صادرة\nمن:1111\nإلى:SAMPLE PERSON\nإلى:2222\nبـSR 500\n26/10/07 09:35"))
        assertEquals(1, w.auto().run().recorded)
        val sent = space.all().single()
        assertEquals(BANK.id to false, sent.walletId to sent.economicKindConfirmed)
        assertEquals(listOf(PendingAsk(AskKind.OWN_ACCOUNT_CHECK, "sa", transactionId = sent.id, date = "2026-10-07")), asks(space).pending("2026-10-01", "2026-10-31"))
    }

    /** الطرف أرقام بس · «بين حساباتك» ⇒ زي الأول: تحويل داخلي لوحده ومن غير سؤال. */
    @Test fun aDigitsOnlyPartyOrBetweenYourAccountsIsStillConfirmedWithoutAQuestion() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, a, b))
        val w = SmsWorld(listOf(space)).enable()
        w.auto().chooseWallet("sa", "TESTBANK", a.id)
        w.receive(sms("own", "Debit Transfer Internal\nAmount: SAR 300.00\nTo: **2222\nFrom: **1111\nOn: 2026-10-07 11:15"))
        assertEquals(1, w.auto().run().recorded)
        val t = space.all().single()
        assertEquals(Triple(EconomicKind.INTERNAL_TRANSFER, true, ReviewState.CONFIRMED), Triple(t.economicKind, t.economicKindConfirmed, t.reviewState))
        assertTrue(asks(space).pending("2026-10-01", "2026-10-31").isEmpty())
    }

    /** الشكوى ٩: «سجّل» من غير رد على «ده راتبك؟» كان بيسجلها «مش متصنف» والسؤال يرجع للرسالة الجاية — والنص بيقول «أكّد مرة إنه راتبك». */
    @Test fun aSalaryQuestionMustBeAnsweredBeforeTheMessageIsRecorded() = runBlocking<Unit> {
        val w = SmsWorld(listOf(SmsSpace()), learnOnReceive = false).enable()
        val space = w.spaces.single()
        w.receive(sms("s1", salary))
        assertEquals(0, w.auto().confirm(listOf("s1")), "من غير رد ما بتتسجلش")
        assertEquals(listOf(PendingAsk(AskKind.IS_SALARY, "sa", messageId = "s1", date = "2026-10-01")), SmsAskSource(w.auto()).pending("2026-10-01", "2026-10-31"))
        assertEquals(1, w.auto().confirm(listOf("s1"), isSalary = true), "الرد جه مع التأكيد")
        val first = space.all().single()
        assertEquals(EconomicKind.SALARY to true, first.economicKind to first.economicKindConfirmed)
        w.receive(sms("s2", nextSalary))
        assertEquals(1, w.auto().run().recorded, "بعد الرد مرة ⇒ لوحده")
        assertTrue(space.all().all { it.economicKind == EconomicKind.SALARY && it.economicKindConfirmed })
    }

    /** نفس القاعدة في الشاشة: «سجّل الكل» بيسيب رسالة «ده راتبك؟» لحد الرد (`answerSalary`)، وبعده بتتسجل «راتب». */
    @Test fun theScreenLeavesAnUnansweredSalaryAndRecordsItAfterTheAnswer() = runBlocking<Unit> {
        val w = SmsWorld(listOf(SmsSpace()), learnOnReceive = false).enable()
        val space = w.spaces.single()
        w.receive(sms("s1", salary), sms("c1", CAFE))
        val screen = space.screen(w.inbox)
        val target = SmsReviewTarget(BANK.id, BANK.name)
        assertEquals(TextKey.SMS_WAIT_IS_SALARY, screen.load(target).ready.single { it.messageId == "s1" }.waitReason)
        assertEquals(1, screen.recordAll(emptyMap(), emptyList()), "المحل بس")
        assertEquals(listOf("s1"), w.queued(), "الراتب فضل مستني بسؤاله")
        assertTrue(screen.answerSalary("s1", true))
        assertNull(screen.load(target).ready.single().question)
        assertEquals(1, screen.recordAll(emptyMap(), emptyList()))
        assertEquals(EconomicKind.SALARY to true, space.all().single { it.amountMinor == 950_000L }.let { it.economicKind to it.economicKindConfirmed })
        assertEquals(false, screen.answerSalary("gone", true), "رسالة مش في الصندوق")
    }

    /** الشكوى ١١: الرسالة اللي ولا قارئ فهمها كانت بتتحسب على أول بلد ⇒ رسالة مصرية مش مقروءة بتتعد في السعودية. */
    @Test fun anUnreadMessageBelongsToTheSpaceOfItsSenderOrToNone() = runBlocking<Unit> {
        val egBank = Wallet("w-eg", "بنك مصري وهمي", Currency.EGP, "bank", 0, "2026-01-01")
        val eg = SmsSpace("eg", listOf(egBank), parse = ::parseEgyptBankSms)
        val w = SmsWorld(listOf(SmsSpace(), eg)).enable("TESTBANK", "EGBANK")
        w.auto().chooseWallet("eg", "EGBANK", egBank.id)
        w.receive(sms("u-eg", "رسالة من البنك بدون مبلغ واضح", sender = "EGBANK"), sms("u-any", UNCLEAR))
        val spaces = SmsAskSource(w.auto()).pending("2026-10-01", "2026-10-31").associate { it.messageId.orEmpty() to it.spaceId }
        assertEquals(mapOf("u-eg" to "eg", "u-any" to ""), spaces, "مربوط ببلد ⇒ بلده · مش مربوط وفيه بلدين ⇒ مش معروفة")
    }
}
