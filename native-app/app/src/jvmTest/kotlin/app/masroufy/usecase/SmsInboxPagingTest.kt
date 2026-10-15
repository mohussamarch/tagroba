package app.masroufy.usecase

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * مراجعة 0.3 (مهم): الصندوق بيعرض أحدث 200 رسالة مستنية بس — لو كلها مستنية تأكيد شكل، الرسايل الأقدم اللي شكلها اتعلّم ما كانتش بتتقري
 * خالص (لا في قراية التاريخ ولا في التكملة في الخلفية). دلوقتي التشغيلة بتقلّب الصفحات، والتقرير بيقول كام رسالة لسه ورا الجزء المعروض.
 */
class SmsInboxPagingTest {
    private val older = (1..3).map { HistoryTxn("in$it", day("2026-01-01", it), 50_000L + it, true, "TEST PAYER", 0) }
    private val newer = (1..201).map { HistoryTxn("buy$it", day("2026-02-01", it / 3), 1_000L + it, false, "TEST SHOP", 0) }

    private suspend fun world(digits: Boolean = true): HistoryWorld {
        val w = if (digits) HistoryWorld(window = 200, space = digitsSpace()) else HistoryWorld(window = 200)
        w.memory.enable(listOf("TESTBANK"))
        // شكل الحوالة الواردة اتأكد قبل كده — شكل الشراء لأ (كل الـ201 شراء مستنية تأكيد)
        assertEquals(1, w.memory.preLearn(w.space.spaceId, w.space.parse, older.first().sms))
        (older + newer).forEach { w.memory.receive(it.sms) }
        return w
    }

    @Test fun olderLearnedMessagesBehind201WaitingOnesAreStillRecorded() = runBlocking<Unit> {
        val w = world()
        assertEquals(204 to 200, w.memory.sync().let { it.count to it.messages.size }, "the three learned ones are behind a full waiting page")
        val run = AutoRecordSms(w.deps()).run()
        assertEquals(3, run.recorded, "the older recordable messages behind the full waiting page are recorded")
        assertEquals(3, w.space.all().size)
        val box = w.memory.sync()
        assertEquals(201, box.count, "only the purchases still wait")
        assertEquals(200, box.messages.size, "back on the newest page after the run")
        assertEquals("buy201", box.messages.last().id)
        assertEquals(200, run.waiting.size)
    }

    @Test fun withoutTheAccountDigitsTheLearnedTransfersWaitAndAsk() = runBlocking<Unit> {
        val w = world(digits = false)
        assertEquals(0, AutoRecordSms(w.deps()).run().recorded, "OVERRIDES §91-1: «إلى:1111» مش لأي محفظة ⇒ «الحساب ده بتاعك؟»")
        assertEquals(emptyList(), w.space.all())
        assertEquals(204, w.memory.sync().count)
    }

    @Test fun historyReportSaysHowManyAreBehindTheShownPage() = runBlocking<Unit> {
        val w = world()
        val report = w.history().confirmShapes(emptyList())
        assertEquals(3, report.recorded, "the history drain reaches them too")
        assertEquals(201, report.queued)
        assertEquals(1, report.unread, "«1 not read yet» — the oldest purchase is behind the shown page")
    }
}
