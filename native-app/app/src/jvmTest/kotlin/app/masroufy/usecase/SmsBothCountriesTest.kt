package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Wallet
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OVERRIDES §112-5 (قرار المالك 2026-10-23): البرنامج بيقرا رسايل البلدين دايمًا، وكل رسالة بتروح لبلد بنكها مهما كانت البلد المختارة —
 * وفي البلد التانية **ما بتظهرش** «ما اتفهمتش» ولا بتتعد. كل الرسايل والأسامي مخترعة.
 */
class SmsBothCountriesTest {
    private val eg = Wallet("eg-bank", "بنك مصري وهمي", Currency.EGP, "bank", 0, "2026-01-01")
    private val saudi = "PoS Purchase\nAmount: SAR 50.00\nAt: TEST SHOP\nOn: 2026-10-07 10:00"
    private val egyptian = "تم خصم 50.00 جم من بطاقتك عند TEST SHOP في 07/10/2026"

    private fun world(): Triple<SmsWorld, SmsSpace, SmsSpace> {
        val sa = SmsSpace("sa", elsewhere = listOf(::parseEgyptBankSms))
        val egypt = SmsSpace("eg", wallets = listOf(eg), parse = ::parseEgyptBankSms, elsewhere = listOf(::parseBankSms))
        return Triple(SmsWorld(listOf(sa, egypt)), sa, egypt)
    }

    private suspend fun SmsSpace.shownProblems(w: SmsWorld, target: SmsReviewTarget) = screen(w.inbox).load(target).let { it.failed.map { f -> f.messageId + ":" + f.reason } + it.ready.map { r -> r.messageId } }

    private val egTarget = SmsReviewTarget(eg.id, eg.name, Currency.EGP)
    private val saTarget = SmsReviewTarget(BANK.id, BANK.name, Currency.SAR)

    @Test fun aSaudiMessageWhileEgyptIsActiveIsRecordedInSaudiAndNotListedInEgypt() = runBlocking<Unit> {
        val (w, sa, egypt) = world()
        w.enable()
        w.receive(sms("sa1", saudi))
        // «القديم»: قبل أي تسجيل، شاشة مصر ما بتعرضهاش «ما اتفهمتش»
        assertEquals(emptyList(), egypt.shownProblems(w, egTarget))
        val run = w.auto().run()
        assertTrue("sa1" !in run.waiting, "مش مستنية في أي بلد: ${run.waiting}")
        assertEquals(listOf(5_000L), sa.all().map { it.amountMinor }, "اتسجلت في السعودية")
        assertTrue(egypt.all().isEmpty())
        assertEquals(emptyList(), egypt.shownProblems(w, egTarget))
    }

    @Test fun anEgyptianMessageWhileSaudiIsActiveIsRecordedInEgyptAndNotListedInSaudi() = runBlocking<Unit> {
        val (w, sa, egypt) = world()
        w.enable()
        w.receive(sms("eg1", egyptian))
        assertEquals(emptyList(), sa.shownProblems(w, saTarget))
        w.auto().run()
        // في مصر: اتسجلت أو مستنية المالك هناك (مرسل مصري جديد) — المهم إنها في مصر مش في السعودية
        assertTrue(egypt.all().map { it.amountMinor } == listOf(5_000L) || "eg1" in egypt.shownProblems(w, egTarget), "راحت مصر")
        assertTrue(sa.all().isEmpty())
        assertEquals(emptyList(), sa.shownProblems(w, saTarget))
    }
}
