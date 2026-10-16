package app.masroufy.usecase

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * r05 (4a — شكوى المالك 2026-10-16: «اللي بدوس عليهم اختار حاجة مش بيحصل حالة ولا بيتشالو»). السبب: اختيار التصنيف على رسالة مستنية كان
 * بيتحفظ **تلميح بس** في الشاشة لحد «سجّل الكل». دلوقتي الاختيار بينادي `AutoRecordSms.confirm` بالتصنيف ⇒ الرسالة بتتسجل بيه (مؤكد) وبتتشال
 * من الصندوق، وشكلها بيتعلّم ⇒ تشغيلة بعدها بتسجّل باقي رسايل **نفس الشكل** (3c). كل الرسايل مخترعة (`SmsAutoFixture.kt`).
 */
class SmsWaitingChoiceTest {
    private val mart = "شراء\nبـSR 40\nلدى:TEST MART\n26/10/07"

    @Test fun pickingACategoryRecordsThatMessageWithItAndTheSameShapeFollows() = runBlocking<Unit> {
        val w = SmsWorld(learnOnReceive = false).enable()
        val space = w.spaces.single()
        w.receive(sms("m1", mart), sms("m2", mart.replace("SR 40", "SR 12")))
        assertEquals(listOf("m1", "m2"), w.auto().run().waiting.sorted(), "new shape ⇒ both wait")

        val ids = mutableListOf<String>()
        assertEquals(1, w.auto().confirm(listOf("m1"), categories = mapOf("m1" to "cat-food"), recordedIds = ids))
        val recorded = space.all().single()
        assertEquals(listOf(recorded.id), ids, "the screen gets the new transaction (party card · employer sheet)")
        assertEquals("cat-food" to true, recorded.categoryId to recorded.categoryConfirmed, "the owner's pick is saved, confirmed")
        assertEquals(4_000L, recorded.amountMinor)

        // نفس الشكل اتعلّم ⇒ الباقي بيتسجل لوحده في التشغيلة اللي بعدها (`SmsScreenExtras.confirm` بيشغّلها على طول)
        val swept = w.auto().run()
        assertEquals(1, swept.recorded)
        assertTrue(swept.waiting.isEmpty(), "nothing left waiting")
        assertEquals(setOf(4_000L, 1_200L), space.all().map { it.amountMinor }.toSet())
    }

    @Test fun confirmingOneMessageLeavesTheOthersOfAnotherShapeWaiting() = runBlocking<Unit> {
        val w = SmsWorld(learnOnReceive = false).enable()
        w.receive(sms("m1", mart), sms("c1", CAFE.replace("لدى:", "عند:")))
        w.auto().run()
        assertEquals(1, w.auto().confirm(listOf("m1")))
        assertEquals(listOf("c1"), w.auto().run().waiting, "another shape still waits for its own confirmation")
    }
}
