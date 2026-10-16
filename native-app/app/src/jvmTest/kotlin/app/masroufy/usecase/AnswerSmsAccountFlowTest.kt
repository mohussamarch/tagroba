package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.MatchingState
import app.masroufy.core.ReviewState
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
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
    /**
     * مراجعة r041 A3-1: «أيوه» بينقل السطر لمحفظة فيها عملية من رسالة تانية بنفس المبلغ واليوم والاتجاه ⇒ السطر بقى «شبيه». الرد عن أرقام الحساب مش
     * اختيار للشبيه ⇒ ما بيتسجلش (كان بيتسجل عملية تانية بنفس الـ500 والرصيد يبقى غلط) وبيفضل مستني اختيار المالك الصريح.
     */
    @Test fun aYesThatMakesTheLineSimilarDoesNotRecordItTwice() = runBlocking<Unit> {
        val world = SmsWorld(listOf(SmsSpace(wallets = listOf(CASH, BANK, BANK2))), learnOnReceive = false).enable()
        val space = world.spaces.single()
        world.auto().chooseWallet("sa", "TESTBANK", BANK.id)
        val earlier = Transaction(
            id = "t-earlier", occurredAt = "2026-10-07", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
            economicKindConfirmed = false, observedDirection = Direction.OUT, amountMinor = 50_000, currency = Currency.SAR, categoryConfirmed = false,
            excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "2026-10-07T08:00:00Z",
            updatedAt = "2026-10-07T08:00:00Z", walletId = BANK2.id,
        )
        space.txnStore.saveMany(listOf(earlier))
        space.sources.saveMany(listOf(SourceRecord("r-earlier", "b-earlier", BANK2.name, "SMS:earlier", "h", 1, "line", earlier.id, MatchingState.NEW, "")))
        world.receive(sms("m1", TO_PERSON))
        assertEquals(0, world.auto().run().recorded)
        val result = AnswerSmsAccount(space.wallets).answerAndRecord(world.auto(), BANK2.id, "1111", mine = true, messageId = "m1")
        assertEquals(0, result.recorded)
        assertEquals(listOf(earlier), space.all(), "مفيش 500 تانية")
        assertEquals(listOf("m1"), world.queued(), "الشبيه مستني اختيار المالك")
        assertEquals("1111", space.wallets.findById(BANK2.id)!!.accountLast4, "الرد نفسه اتحفظ")
    }
}
