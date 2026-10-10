package app.masroufy.usecase

import app.masroufy.core.AskKey
import app.masroufy.core.AssistConversation
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistMessageKind
import app.masroufy.core.AssistSpeaker
import app.masroufy.core.CardState
import app.masroufy.core.CardType
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.MainWalletSource
import app.masroufy.core.ObligationKind
import app.masroufy.core.uiText
import app.masroufy.core.weekStartSaturday
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ردود المالك §79.2 اللي المحرك ما كانش بيعملها (بيانات مخترعة — `AssistantWorld`): الدخل والسلفة والتحويل بكروت تأكيد زي المصروف (٧) ·
 * التعديل بالكتابة بيفضّل نوع الكارت · عملية شبهها النهارده (٦) · السجل بيتمسح بعد ٣ شهور (٣) · الأسبوع بيبدأ السبت (٤).
 */
class AssistantMoneyCardsTest {
    private val w = AssistantWorld()
    private val ctx = w.ctx()
    private val main = MainSpendingWallets(w.stores.settings, w.wallets, w.space.id, w.clock)

    private suspend fun all() = w.txns.listByDateRange("2000-01-01", "2100-01-01")
    private fun lastCard(v: ChatView): AssistMessage = v.messages.last { it.kind == AssistMessageKind.TXN_CARD }

    @Test fun incomeAsksWhichWalletWithoutTouchingTheMainWalletThenSavesAsIncome() = runBlocking<Unit> {
        val pick = w.chat.send("قبضت 5000", ctx).messages.last()
        assertEquals(AssistMessageKind.WALLET_PICK, pick.kind)
        assertEquals(uiText(AskKey.CHAT_WHICH_WALLET), pick.text)
        val card = lastCard(w.chat.pick(pick.id, w.bank.id, ctx))
        assertNull(main.get(), "محفظة الدخل ما بقتش «الأساسية» — دي للصرف بس")
        assertEquals(CardType.INCOME, card.card!!.cardType)
        assertEquals("+", card.card!!.cardType.sign)
        assertEquals(uiText(AskKey.CHAT_CARD_INCOME), card.text)

        w.chat.confirm(card.id, ctx)
        val saved = all().single { it.amountMinor == 500_000L }
        assertEquals(Direction.IN, saved.observedDirection)
        assertEquals(EconomicKind.FREELANCE, saved.economicKind, "دخل من غير اسم ⇒ «عمل حر» مش راتب")
    }

    @Test fun salaryNamedOnAWalletGoesStraightToTheCard() = runBlocking<Unit> {
        val card = lastCard(w.chat.send("جاني راتب 10000 على الراجحي", ctx))
        assertEquals(EconomicKind.SALARY, card.card!!.kind)
        assertEquals(w.bank.id, card.card!!.walletId)
    }

    @Test fun lentMoneyShowsUnderOwedToYouAndBorrowedUnderYouOwe() = runBlocking<Unit> {
        val lent = lastCard(w.chat.send("سلفت أحمد 200 كاش", ctx))
        assertEquals(CardType.LENT, lent.card!!.cardType)
        assertEquals("−", lent.card!!.cardType.sign)
        assertEquals("p-ahmed", lent.card!!.personId)
        assertEquals(uiText(AskKey.CHAT_CARD_LENT, "أحمد"), lent.text)
        w.chat.confirm(lent.id, ctx)
        assertEquals(listOf(ObligationKind.RECEIVABLE to 20_000L), w.obligations.listByPerson("p-ahmed").map { it.kind to it.originalMinor })
        assertEquals(EconomicKind.LOAN_GRANTED, all().single { it.amountMinor == 20_000L }.economicKind)

        val borrowed = lastCard(w.chat.send("استلفت من خالد 300 كاش", ctx))
        assertEquals(CardType.BORROWED, borrowed.card!!.cardType)
        assertEquals("+", borrowed.card!!.cardType.sign)
        w.chat.confirm(borrowed.id, ctx)
        assertEquals(listOf(ObligationKind.LOAN_PAYABLE to 30_000L), w.obligations.listByPerson("p-khaled").map { it.kind to it.originalMinor })
    }

    @Test fun aLoanWithoutAKnownPersonAsksForTheName() = runBlocking<Unit> {
        val v = w.chat.send("سلفت 200", ctx)
        assertTrue(v.messages.none { it.kind == AssistMessageKind.TXN_CARD })
        assertEquals(uiText(AskKey.CHAT_NEEDS_PERSON), v.messages.last().text)
    }

    @Test fun movesBetweenOwnWalletsHaveNoSignAndSaveAsInternalTransfer() = runBlocking<Unit> {
        val move = lastCard(w.chat.send("حولت 500 من الكاش للبنك", ctx))
        assertEquals(CardType.MOVE, move.card!!.cardType)
        assertEquals("", move.card!!.cardType.sign)
        assertEquals(w.cash.id to w.bank.id, move.card!!.walletId to move.card!!.toWalletId)
        w.chat.confirm(move.id, ctx)
        val saved = all().single { it.amountMinor == 50_000L }
        assertEquals(EconomicKind.INTERNAL_TRANSFER, saved.economicKind)

        val atm = lastCard(w.chat.send("سحبت 300 من الصراف", ctx))
        assertEquals(w.bank.id to w.cash.id, atm.card!!.walletId to atm.card!!.toWalletId, "السحب من البنك للكاش")
    }

    @Test fun editingByTypingKeepsTheCardType() = runBlocking<Unit> {
        val lent = lastCard(w.chat.send("سلفت أحمد 200 كاش", ctx))
        val v = w.chat.send("لا خليها 300", ctx)
        assertEquals(CardState.DROPPED, v.messages.first { it.id == lent.id }.state)
        val edited = lastCard(v)
        assertEquals(CardType.LENT, edited.card!!.cardType)
        assertEquals(30_000L, edited.card!!.amountMinor)
        assertEquals("p-ahmed", edited.card!!.personId)
        w.chat.confirm(edited.id, ctx) // كارت مستني + كلام جديد = تعديل له ⇒ نخلّصه الأول

        val move = lastCard(w.chat.send("حولت 500 من الكاش للبنك", ctx))
        val m2 = lastCard(w.chat.send("خليها 700", ctx))
        assertEquals(CardType.MOVE, m2.card!!.cardType)
        assertEquals(70_000L to w.bank.id, m2.card!!.amountMinor to m2.card!!.toWalletId)
        assertTrue(move.id != m2.id)
    }

    @Test fun aSameDayLookalikeSaysSoOnTheCard() = runBlocking<Unit> {
        main.set(w.bank.id, MainWalletSource.CHAT)
        w.txns.saveMany(listOf(w.txn("t-sms", w.today, 2_500)))
        val card = lastCard(w.chat.send("قهوة 25", ctx))
        assertEquals("t-sms", card.card!!.similarTransactionId)
        assertTrue(card.text.startsWith(uiText(AskKey.CHAT_SIMILAR_TODAY)), card.text)
    }

    @Test fun historyDeletesItselfAfterThreeMonths() = runBlocking<Unit> {
        fun conv(id: String, at: String) = AssistConversation(id, w.space.id, at, at, "سؤال", "رد", 2)
        w.stores.conversations.save(conv("c-old", "2026-07-09T10:00:00.000Z"))
        w.stores.messages.save(AssistMessage("c-old-0000-m", "c-old", "2026-07-09T10:00:00.000Z", AssistSpeaker.ME, AssistMessageKind.TEXT, "سؤال"))
        w.stores.conversations.save(conv("c-recent", "2026-07-11T10:00:00.000Z"))
        w.chat.open(ctx)
        assertEquals(listOf("c-recent"), w.stores.conversations.listAll().map { it.id }, "أقدم من ٣ شهور اتمسحت والأحدث فضلت")
        assertTrue(w.stores.messages.listByConversation("c-old").isEmpty())
    }

    @Test fun theAssistantsWeekStartsOnSaturday() {
        assertEquals("2026-10-10", weekStartSaturday("2026-10-10"), "السبت نفسه")
        assertEquals("2026-10-10", weekStartSaturday("2026-10-16"), "الجمعة اللي بعده")
        assertEquals("2026-10-03", weekStartSaturday("2026-10-09"))
    }
}
