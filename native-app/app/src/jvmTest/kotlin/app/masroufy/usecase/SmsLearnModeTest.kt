package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Currency
import app.masroufy.core.SmsParseResult
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.core.smsConfirmCandidate
import app.masroufy.core.uiText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * §77-A «وضع التعلّم» (قرار المالك 2026-10-09): رسالة البنك بتتسجل لوحدها **بس بعد ما المالك يأكد أول رسالة من نفس الشكل من نفس المرسل في
 * نفس البلد**. أي شكل جديد (أو شكل اتغيّرت فيه كلمة) بيستنى تأكيد مرة. كل الرسايل مخترعة (`SmsAutoFixture.kt`).
 */
class SmsLearnModeTest {
    private fun world(spaces: List<SmsSpace> = listOf(SmsSpace())) = SmsWorld(spaces, learnOnReceive = false)

    private fun keyOf(body: String, parse: (BankSmsMessage, Int) -> SmsParseResult = ::parseBankSms) =
        (parse(BankSmsMessage("TESTBANK", SENT_AT, body), 1) as SmsParseResult.Ok).row.learnKey!!

    @Test fun theFirstClearMessageWaitsThenRecordAllLearnsItsLayoutAndTheNextOneRecordsItself() = runBlocking<Unit> {
        val w = world().enable()
        val space = w.spaces.single()
        w.receive(sms("m1", CAFE))
        val first = w.auto().run()
        assertEquals(0, first.recorded, "أول رسالة من الشكل ده")
        assertEquals(listOf("m1") to listOf("m1"), first.waiting to first.newShape)
        assertTrue(first.unknownShape.isEmpty())
        assertNotNull(smsConfirmCandidate(w.auto().waiting().messageIds), "بتتحسب في «رسايل محتاجة تأكيدك»")

        val screen = space.screen(w.inbox, SmsLearning(w.inbox, "sa"))
        val line = screen.load(SmsReviewTarget(BANK.id, BANK.name)).ready.single()
        assertEquals(uiText(TextKey.SMS_WAIT_NEW_SHAPE) to TextKey.SMS_WAIT_NEW_SHAPE, line.confirmReason to line.waitReason)
        assertEquals(1, screen.recordAll(emptyMap(), emptyList()), "ضغطة المالك = التأكيد")
        assertEquals(mapOf("testbank" to setOf(keyOf(CAFE))), w.memory.learnedShapes("sa"), "بصمة الشكل اتحفظت (مش النص)")

        w.receive(sms("m2", MART)) // نفس الشكل، محل ومبلغ تانيين
        assertEquals(1, w.auto().run().recorded)
        assertEquals(setOf(2_500L, 4_000L), space.all().map { it.amountMinor }.toSet())
    }

    @Test fun aLearnedLayoutIsPerSenderAndPerCountry() = runBlocking<Unit> {
        val egBank = Wallet("w-eg", "بنك مصري وهمي", Currency.EGP, "bank", 0, "2026-01-01")
        val eg = SmsSpace("eg", listOf(egBank), parse = ::parseEgyptBankSms)
        val w = world(listOf(SmsSpace(), eg)).enable("TESTBANK", "OTHERBANK")
        w.memory.learnShapes("sa", "TESTBANK", setOf(keyOf(CAFE), keyOf(EG_CARD, ::parseEgyptBankSms)))
        w.receive(sms("x1", CAFE), sms("y1", MART, sender = "OTHERBANK"), sms("x2", EG_CARD))
        val r = w.auto().run()
        assertEquals(1, r.recorded, "نفس المرسل في نفس البلد بس")
        assertEquals(listOf("y1", "x2"), r.newShape, "مرسل تاني · نفس المرسل في مصر")
        assertTrue(eg.all().isEmpty())
    }

    @Test fun oneChangedFixedWordWaitsOnceAndThenRecordsItself() = runBlocking<Unit> {
        val w = world().enable()
        w.learn(sms("seed", CAFE))
        val other = "شراء\nبـSR 33\nعند:TEST CAFE\n26/10/07" // «لدى» ⇒ «عند»
        w.receive(sms("m1", other))
        assertEquals(listOf("m1"), w.auto().run().newShape)
        assertEquals(1, w.auto().confirm(listOf("m1")))
        w.receive(sms("m2", "شراء\nبـSR 12\nعند:TEST MART\n26/10/06"))
        assertEquals(1, w.auto().run().recorded, "الشكل الجديد اتعلّم مرة")
    }

    @Test fun afterLearningARefundAndACardCreditStillWaitForTheirOwnReason() = runBlocking<Unit> {
        val w = world().enable()
        val space = w.spaces.single()
        val refund = "استرداد شراء\nبطاقة: 7739*;مدى\nمبلغ: 89.00 ر.س\nمن: SAFA OPTICS\nفي: 26-10-07 16:02"
        val credited = "Credit Card Credited\nAmount: SAR 1,500.00\nCard: *4476\nOn: 2026-10-07 13:05"
        w.receive(sms("r1", refund), sms("c1", credited))
        assertEquals(2, w.auto().confirm(listOf("r1", "c1")), "المالك أكّدهم ⇒ شكلهم اتعلّم")
        w.receive(sms("r2", refund.replace("89.00", "12.00")), sms("c2", credited.replace("1,500.00", "700.00")))
        val r = w.auto().run()
        assertEquals(0, r.recorded)
        assertEquals(listOf("r2", "c2"), r.waiting)
        assertTrue(r.newShape.isEmpty(), "مش شكل جديد — سببهم هما")
        val ready = space.screen(w.inbox, SmsLearning(w.inbox, "sa")).load(SmsReviewTarget(BANK.id, BANK.name)).ready.associateBy { it.messageId }
        assertEquals(TextKey.SMS_WAIT_REFUND, ready.getValue("r2").waitReason)
        assertEquals(TextKey.SMS_WAIT_CARD_CREDIT, ready.getValue("c2").waitReason)
    }

    @Test fun dismissingADuplicateAndAKeywordOnlyMessageTeachNothing() = runBlocking<Unit> {
        val w = world().enable()
        val space = w.spaces.single()
        val target = SmsReviewTarget(BANK.id, BANK.name)
        val screen = space.screen(w.inbox, SmsLearning(w.inbox, "sa"))
        w.receive(sms("m1", CAFE))
        screen.load(target)
        screen.dismiss(listOf("m1"), target)
        assertTrue(w.memory.learnedShapes("sa").isEmpty(), "شيل ⇒ ولا تعلّم")

        // نفس الرسالة اتسجلت قبل كده من شاشة من غير تعلّم ⇒ رجعت «مكررة»
        w.receive(sms("m2", MART))
        space.screen(w.inbox).apply { load(target) }.recordAll(emptyMap(), emptyList())
        w.receive(sms("m2-again", MART))
        screen.load(target)
        assertEquals(0, screen.recordAll(emptyMap(), emptyList()))
        assertTrue(w.memory.learnedShapes("sa").isEmpty(), "المكرر ⇒ ولا تعلّم")

        w.receive(sms("k1", KEYWORD_ONLY))
        screen.load(target)
        assertEquals(1, screen.recordAll(emptyMap(), emptyList()))
        assertTrue(w.memory.learnedShapes("sa").isEmpty(), "الكلمات العامة مالهاش شكل يتعلّم")
    }

    @Test fun forgettingASendersLayoutsMakesItsNextMessageWaitAgain() = runBlocking<Unit> {
        val w = world().enable()
        w.learn(sms("seed", CAFE))
        w.receive(sms("m1", CAFE))
        assertEquals(1, w.auto().run().recorded)
        w.auto().forgetLayouts("sa", "TestBank")
        w.receive(sms("m2", MART))
        assertEquals(listOf("m2"), w.auto().run().newShape)
    }

    @Test fun confirmingOneMessageLetsTheNextCycleRecordTheOthersOfTheSameLayout() = runBlocking<Unit> {
        val w = world().enable()
        w.receive(sms("m1", CAFE), sms("m2", MART), sms("m3", "شراء\nبـSR 7\nلدى:TEST BAKERY\n26/10/06"))
        assertEquals(listOf("m1", "m2", "m3"), w.auto().run().newShape)
        assertEquals(1, w.auto().confirm(listOf("m1")))
        val next = w.auto().run()
        assertEquals(2, next.recorded, "اللي كانوا مستنيين بنفس الشكل")
        assertTrue(next.waiting.isEmpty())
    }

    @Test fun aKnownShapeFromTheResearchStillWaitsTheFirstTimeInEgyptToo() = runBlocking<Unit> {
        val egBank = Wallet("w-eg", "بنك مصري وهمي", Currency.EGP, "bank", 0, "2026-01-01")
        val eg = SmsSpace("eg", listOf(egBank), parse = ::parseEgyptBankSms)
        val w = world(listOf(SmsSpace(), eg)).enable()
        w.receive(sms("e1", EG_CARD))
        assertEquals(listOf("e1"), w.auto().run().newShape)
        assertEquals(1, w.auto().confirm(listOf("e1")))
        w.receive(sms("e2", EG_CARD.replace("41.25", "60.00")))
        assertEquals(1, w.auto().run().recorded)
        assertEquals(setOf(4_125L, 6_000L), eg.all().map { it.amountMinor }.toSet())
    }
}
