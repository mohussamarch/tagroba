package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.TextKey
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * إجابة المالك 2026-10-15 (OVERRIDES §91-1) من أولها لآخرها: تحويل «من:/الى:» وولا محفظة عليها الأرقام ⇒ بيستنى ويسأل «الحساب ده بتاعك؟»
 * ⇒ «أيوه، ده حسابي» (المحفظة اللي اختارها) بيحفظ الأرقام عليها والمستني بيتسجل لوحده · «لأ» بيسجلها فلوس لحد تاني أو منه. كله مخترع.
 */
class AnswerSmsAccountTest {
    private suspend fun waitingWorld(): SmsWorld {
        val world = SmsWorld().enable()
        world.receive(sms("m1", TO_PERSON), sms("m2", FROM_PERSON))
        assertEquals(0, world.auto().run().recorded)
        return world
    }

    @Test fun theWaitingLineCarriesTheQuestionAndItsDigits() = runBlocking<Unit> {
        val world = waitingWorld()
        val space = world.spaces.single()
        val review = space.screen(world.memory).load(SmsReviewTarget(BANK.id, BANK.name))
        val asked = review.ready.filter { it.waitReason == TextKey.SMS_WAIT_UNKNOWN_ACCOUNT }
        assertEquals(setOf("m1", "m2"), asked.map { it.messageId }.toSet())
        assertEquals(setOf("1111"), asked.map { it.askLast4 }.toSet())
        assertEquals(setOf(BANK.id), asked.map { it.walletId }.toSet())
    }

    @Test fun yesSavesTheDigitsOnTheChosenWalletAndTheWaitingOnesRecordThemselves() = runBlocking<Unit> {
        val world = waitingWorld()
        val space = world.spaces.single()
        AnswerSmsAccount(space.wallets).mine(BANK.id, "1111")
        assertEquals("1111", space.wallets.findById(BANK.id)!!.accountLast4)
        assertEquals(2, world.auto().run().recorded)
        val all = space.all()
        assertEquals(setOf(BANK.id), all.map { it.walletId }.toSet())
        assertEquals(Direction.OUT to 50_000L, all.single { it.amountMinor == 50_000L }.let { it.observedDirection to it.amountMinor })
        assertEquals(Direction.IN, all.single { it.amountMinor == 100_000L }.observedDirection)
        assertEquals(emptyList(), world.queued())
        // رسالة جديدة لنفس الحساب بعد كده ⇒ بتتسجل على طول من غير سؤال
        world.receive(sms("m3", TO_PERSON.replace("500", "70")))
        assertEquals(1, world.auto().run().recorded)
    }

    @Test fun noRecordsThemAsMoneyToOrFromSomeoneElseAndRemembersIt() = runBlocking<Unit> {
        val world = waitingWorld()
        val space = world.spaces.single()
        AnswerSmsAccount(space.wallets).notMine(BANK.id, "1111")
        val wallet = space.wallets.findById(BANK.id)!!
        assertNull(wallet.accountLast4)
        assertEquals(listOf("1111"), wallet.notMineLast4)
        assertEquals(2, world.auto().run().recorded)
        val all = space.all()
        val out = all.single { it.amountMinor == 50_000L }
        val incoming = all.single { it.amountMinor == 100_000L }
        assertEquals(Direction.OUT to Direction.IN, out.observedDirection to incoming.observedDirection, "the direction the message says")
        assertEquals(setOf(BANK.id), all.map { it.walletId }.toSet())
        // مش تحويل بين محافظه — فلوس لشخص/من شخص (لسه بتتسأل عن نوعها)
        assertEquals(setOf(EconomicKind.UNCLASSIFIED), all.map { it.economicKind }.toSet())
        world.receive(sms("m3", FROM_PERSON.replace("1000", "30")))
        assertEquals(1, world.auto().run().recorded, "the answer is remembered — no second question")
    }

    @Test fun yesAfterNoWinsAndBadAnswersAreRefused() = runBlocking<Unit> {
        val world = waitingWorld()
        val space = world.spaces.single()
        val answer = AnswerSmsAccount(space.wallets)
        answer.notMine(BANK.id, "1111")
        answer.mine(BANK.id, "1111")
        assertEquals(emptyList(), space.wallets.findById(BANK.id)!!.notMineLast4)
        assertFailsWith<WalletError> { answer.mine(CASH.id, "2222") }
        assertFailsWith<WalletError> { answer.mine(BANK.id, "2222") }
        assertFailsWith<WalletError> { answer.notMine(BANK.id, "1111") }
        assertFailsWith<WalletError> { answer.mine(BANK.id, "12") }
    }
}
