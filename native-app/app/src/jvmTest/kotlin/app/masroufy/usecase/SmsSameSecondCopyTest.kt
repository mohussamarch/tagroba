package app.masroufy.usecase

import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OVERRIDES §120-10 · §121-12 (نفس الثانية، مش لازم نفس الجزء من الألف)
 * — §120-10 (قرار المالك 2026-11-15): رسالتين بنفس النص من نفس المرسل في نفس الوقت ⇒ التانية نسخة وبتتشال من غير تسجيل
 * (المرجع `SMS:` + بصمة المرسل والوقت والنص واحد). بيانات مخترعة (`SmsAutoFixture.kt`).
 */
class SmsSameSecondCopyTest {
    @Test fun identicalTextAtTheSameTimeIsRecordedOnceAndTheCopyLeavesTheInbox() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE), sms("m2", CAFE))
        val result = world.auto().run()
        assertEquals(1, world.spaces.single().all().size, "النسخة ما بتتسجلش")
        assertEquals(1, result.recorded)
        assertTrue(result.waiting.isEmpty(), "النسخة ما بتستناش تأكيد")
        assertTrue(world.queued().isEmpty(), "الاتنين اتشالوا من الصندوق")
    }

    @Test fun theSameTextAtAnotherSecondIsNotACopy() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE), sms("m2", CAFE, at = "2026-10-07T10:00:01Z"))
        world.auto().run()
        // وقت تاني ⇒ مرجع تاني: مش بتتشال كنسخة (بتتسجل أو تستنى حسب قاعدة «خصم تاني من نفس المحل»)
        assertTrue(world.spaces.single().all().size + world.queued().size == 2)
    }

    @Test fun identicalTextInTheSameSecondWithOtherMillisecondsIsACopy() = runBlocking<Unit> {
        // §121-12: نفس الثانية — مش لازم نفس الجزء من الألف (أي ترتيب في الصندوق)
        val world = SmsWorld().enable()
        world.receive(sms("m2", CAFE, at = "2026-10-07T10:00:00.870Z"), sms("m1", CAFE, at = "2026-10-07T10:00:00.120Z"))
        val result = world.auto().run()
        assertEquals(1, world.spaces.single().all().size, "النسخة ما بتتسجلش")
        assertEquals(1, result.recorded)
        assertTrue(result.waiting.isEmpty(), "النسخة ما بتستناش تأكيد")
        assertTrue(world.queued().isEmpty(), "الاتنين اتشالوا من الصندوق")
    }

    @Test fun theCopyTakesTheFirstArrivalTimeAndOthersStayAsTheyAre() {
        val first = QueuedSms("m1", "TESTBANK", "2026-10-07T10:00:00.120Z", CAFE)
        val copy = QueuedSms("m2", "TESTBANK", "2026-10-07T10:00:00.870Z", CAFE)
        val nextSecond = QueuedSms("m3", "TESTBANK", "2026-10-07T10:00:01.050Z", CAFE)
        val otherText = QueuedSms("m4", "TESTBANK", "2026-10-07T10:00:00.500Z", CAFE_TWIN)
        val otherSender = QueuedSms("m5", "OTHERBANK", "2026-10-07T10:00:00.600Z", CAFE)
        val state = SmsInboxState(enabled = true, permission = true, more = false, count = 5, senders = listOf("TESTBANK"),
            messages = listOf(copy, first, nextSecond, otherText, otherSender))
        val out = withSameSecondCopies(state).messages.associate { it.id to it.receivedAt }
        assertEquals(first.receivedAt, out["m2"], "النسخة بتاخد وقت الأولى ⇒ نفس المرجع")
        assertEquals(first.receivedAt, out["m1"], "الأولى زي ما هي (مراجع القديم ما بتتغيرش)")
        assertEquals(nextSecond.receivedAt, out["m3"], "ثانية تانية ⇒ مش نسخة")
        assertEquals(otherText.receivedAt, out["m4"])
        assertEquals(otherSender.receivedAt, out["m5"])
    }
}
