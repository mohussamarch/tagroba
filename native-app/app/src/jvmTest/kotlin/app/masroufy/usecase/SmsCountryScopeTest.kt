package app.masroufy.usecase

import app.masroufy.core.missedSms
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.port.QueuedSms
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * fix053 — بلاغ المالك (فحص 0.5.3 على المحاكي): وإنت في مصر «رسائل البنك» كانت بتعرض «مستنية تأكيدك 618» و«ظاهر 200 من 618» وكارت
 * «فاتتك» عن رسايل السعودية. الصفحة كانت بتتفلتر، بس العدّ ([InboxView.count]) والرسايل ([InboxView.messages]) كانوا للصندوق كله.
 * كل الرسايل والمبالغ مخترعة.
 */
class SmsCountryScopeTest {
    private fun saudi(i: Int) = QueuedSms(
        "sa$i", "TESTBANK", "2026-09-${(1 + i % 28).toString().padStart(2, '0')}T10:${(i % 60).toString().padStart(2, '0')}:00Z",
        "PoS Purchase\nAmount: SAR ${10 + i}.00\nAt: TEST SHOP\nOn: 2026-09-${(1 + i % 28).toString().padStart(2, '0')} 10:00",
    )

    private fun egyptian(i: Int) = QueuedSms(
        "eg$i", "TESTEGBANK", "2026-10-0${1 + i}T09:00:00Z", "تم خصم ${20 + i}.00 جم من بطاقتك عند TEST SHOP في 0${1 + i}/10/2026",
    )

    /** رسالة رمز من البنك السعودي — مش مفهومة في أي بلد، بس مرسلها سعودي ⇒ ما تظهرش «ما اتفهمتش» في مصر. */
    private val saudiOtp = QueuedSms("sa-otp", "TESTBANK", "2026-09-30T11:00:00Z", "Your OTP is 1234. Do not share it.")

    private fun egypt(box: MemorySmsInbox) = ManageSmsInbox(box, ::parseEgyptBankSms, listOf(::parseBankSms))

    private fun saudiArabia(box: MemorySmsInbox) = ManageSmsInbox(box, ::parseBankSms, listOf(::parseEgyptBankSms))

    @Test fun egyptShowsNothingWhenOnlySaudiMessagesWait() = runBlocking<Unit> {
        val box = MemorySmsInbox((0 until 618).map(::saudi) + saudiOtp, window = 200)
        val eg = egypt(box).refresh()
        assertEquals(0, eg.count, "no Saudi message is counted in Egypt")
        assertTrue(eg.messages.isEmpty() && eg.items.isEmpty(), "no Saudi message is listed in Egypt")
        // كارت «فاتتك»: بنفس مسح الشاشة (`SmsGraph.missedWhileAway`)
        assertEquals(0, missedSms(null, eg.messages.map { 0L }, eg.count).n, "no away card in Egypt")
        val sa = saudiArabia(box).refresh()
        assertEquals(619, sa.count, "Saudi still sees all its messages")
        assertEquals(200, sa.messages.size)
    }

    @Test fun egyptCountsOnlyItsOwnMessagesBehindTheSaudiPage() = runBlocking<Unit> {
        val box = MemorySmsInbox((0 until 3).map(::egyptian) + (0 until 450).map(::saudi), window = 200)
        val eg = egypt(box)
        val view = eg.refresh()
        assertEquals(3, view.count, "only the 3 Egyptian messages count")
        assertTrue(view.items.all { it.sender == "TESTEGBANK" })
        assertTrue(eg.isOtherCountrySender("testbank"), "the Saudi sender is decided once")
        val sa = saudiArabia(box).refresh()
        assertEquals(450, sa.count, "Egyptian messages are not counted in Saudi")
        assertTrue(sa.items.none { it.sender == "TESTEGBANK" })
    }

    @Test fun oneCountryOnlyKeepsTheWholeInbox() = runBlocking<Unit> {
        val box = MemorySmsInbox((0 until 250).map(::saudi) + saudiOtp, window = 200)
        val view = ManageSmsInbox(box, ::parseBankSms).refresh()
        assertEquals(251, view.count)
        assertEquals(200, view.messages.size)
    }
}
