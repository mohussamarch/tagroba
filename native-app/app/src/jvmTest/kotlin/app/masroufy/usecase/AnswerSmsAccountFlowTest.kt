package app.masroufy.usecase

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * r041 A3: المرسل **ما اتعلّمش** شكل الرسالة (§77-A) ⇒ الرد على «الحساب ده بتاعك؟» (أيوه أو لأ) كان بيحفظ الأرقام والرسالة ترجع تستنى
 * «أول رسالة بهذا الشكل» فالمالك يدوس مرة تانية. دلوقتي الرد على الرسالة نفسها = تأكيد ليها: ضغطة واحدة ⇒ عملية واحدة ومفيش مستني،
 * والرسالة الجاية بنفس الشكل والأرقام بتتسجل من غير سؤال. كله مخترع.
 */
class AnswerSmsAccountFlowTest {
    private suspend fun unlearnedWorld(): SmsWorld {
        val world = SmsWorld(learnOnReceive = false).enable()
        world.receive(sms("m1", TO_PERSON))
        assertEquals(0, world.auto().run().recorded)
        assertEquals(listOf("m1"), world.queued())
        return world
    }

    @Test fun noOnceRecordsTheMessageAndTheNextOneOfTheSameShape() = runBlocking<Unit> {
        val world = unlearnedWorld()
        val space = world.spaces.single()
        val result = AnswerSmsAccount(space.wallets).answerAndRecord(world.auto(), BANK.id, "1111", mine = false, messageId = "m1")
        assertEquals(1, result.recorded)
        assertEquals(emptyList(), result.waiting)
        assertEquals(emptyList(), world.queued())
        assertEquals(1, space.all().size)
        assertTrue("1111" in space.wallets.findById(BANK.id)!!.notMineLast4)
        world.receive(sms("m2", TO_PERSON.replace("500", "70")))
        assertEquals(1, world.auto().run().recorded, "same digits, same shape ⇒ no question")
        assertEquals(emptyList(), world.queued())
    }

    @Test fun yesOnceRecordsTheMessageInTheChosenWallet() = runBlocking<Unit> {
        val world = unlearnedWorld()
        val space = world.spaces.single()
        val result = AnswerSmsAccount(space.wallets).answerAndRecord(world.auto(), BANK.id, "1111", mine = true, messageId = "m1")
        assertEquals(1, result.recorded)
        assertEquals(emptyList(), world.queued())
        assertEquals(setOf(BANK.id), space.all().map { it.walletId }.toSet())
        assertEquals("1111", space.wallets.findById(BANK.id)!!.accountLast4)
        world.receive(sms("m2", TO_PERSON.replace("500", "70")))
        assertEquals(1, world.auto().run().recorded)
    }

    @Test fun withoutTheMessageIdTheOldBehaviourStays() = runBlocking<Unit> {
        val world = unlearnedWorld()
        val space = world.spaces.single()
        val result = AnswerSmsAccount(space.wallets).answerAndRecord(world.auto(), BANK.id, "1111", mine = false, messageId = null)
        assertEquals(0, result.recorded, "an unlearned shape still waits when no message was confirmed")
        assertEquals(listOf("m1"), world.queued())
    }
}
