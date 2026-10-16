package app.masroufy.usecase

import app.masroufy.memory.MemorySmsInbox
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.TimeSource

/**
 * مراجعة دمج 0.5.1 (A-F2): «اقرأ اللي فات» بيسجّل لحد 3 دقايق — اللفة اللي حد الوقت قطعها كانت بتتفتكر **كاملة** (`SmsRunMemo.mark`)،
 * فأول فتح للشاشة يقف عند أول صفحة ما فيهاش جديد والصفحات الأقدم اللي ما اتلفتش تستنى تشغيلة الخلفية (ساعات). **كل الرسايل مخترعة.**
 */
class SmsDeadlineMemoTest {
    private fun known(i: Int) = sms("k$i", "شراء\nبـSR ${money(2_000L + i * 7L)}\nلدى:TEST CAFE\n${smsDay(day("2026-09-01", i % 20))}", "${day("2026-09-01", i % 20)}T09:00:00Z")

    // رسايل مش مفهومة (عمرها ما بتتعلّم) — أحدث من الباقي، فبتملا أول صفحة لوحدها
    private fun unclear(i: Int) = sms("u$i", "رسالة\nبـSR ${100 + i}\n26/10/07", "${day("2026-10-01", i % 6)}T10:00:00Z")

    @Test fun aWalkCutByTheDeadlineIsFinishedByTheNextScreenOpen() = runBlocking<Unit> {
        val inbox = MemorySmsInbox(available = true, window = PAGE)
        inbox.enable(listOf("TESTBANK"))
        val space = SmsSpace()
        val messages = (0 until KNOWN).map(::known) + (0 until UNCLEAR_COUNT).map(::unclear)
        for (m in messages) {
            inbox.receive(m)
            inbox.preLearn(space.spaceId, space.parse, m)
        }
        val auto = AutoRecordSms(AutoRecordSmsDeps(inbox, listOf(space.lane(inbox))))
        val memo = SmsRunMemo()

        val cut = auto.record(memo = memo, deadline = TimeSource.Monotonic.markNow())
        assertEquals(0, cut?.recorded, "حد الوقت عدّى بعد أول صفحة (كلها مش مفهومة) ⇒ ولا رسالة اتسجلت لسه")

        val screen = auto.runNew(memo)
        assertEquals(KNOWN, screen.recorded, "فتح الشاشة بيكمّل الصفحات الأقدم اللي ما اتلفتش")
        assertEquals(KNOWN, space.all().size)
    }

    private companion object {
        const val PAGE = 200
        const val KNOWN = 60
        const val UNCLEAR_COUNT = 210
    }
}
