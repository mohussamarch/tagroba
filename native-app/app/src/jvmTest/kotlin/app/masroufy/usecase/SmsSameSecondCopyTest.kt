package app.masroufy.usecase

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OVERRIDES §120-10 (قرار المالك 2026-11-15): رسالتين بنفس النص من نفس المرسل في نفس الوقت ⇒ التانية نسخة وبتتشال من غير تسجيل
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
}
