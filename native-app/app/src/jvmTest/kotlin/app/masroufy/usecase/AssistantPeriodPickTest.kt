package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.AskKey
import app.masroufy.core.AssistChoiceKind
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistSpeaker
import app.masroufy.core.AssistantReply
import app.masroufy.core.CardState
import app.masroufy.core.Currency
import app.masroufy.core.EID_ADHA_ID
import app.masroufy.core.EID_FITR_ID
import app.masroufy.core.Language
import app.masroufy.core.Texts
import app.masroufy.core.formatMoney
import app.masroufy.core.uiText
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * §82-1 (رد المالك 2026-10-10): «صرفت كام في رمضان؟» ⇒ كارت «من إمتى لإمتى؟» بيومين، والإجابة بعده من **نفس** حالة الاستخدام اللي بتجاوب أي
 * مدى (`LoadMoneySummary` · `LoadCategorySpend`). عالم المساعد المخترع (`AssistantWorld`): النهارده 2026-10-10.
 */
class AssistantPeriodPickTest {
    @BeforeTest fun egyptianTexts() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
    }

    private fun money(minor: Long) = formatMoney(minor, Currency.SAR)
    private fun List<AssistMessage>.periodCard() = last { it.choice == AssistChoiceKind.PICK_PERIOD }

    @Test fun ramadanAsksForTheFirstAndLastDayThenAnswersFromTheSameUseCase() = runBlocking<Unit> {
        val w = AssistantWorld()
        val asked = w.chat.send("صرفت كام في رمضان؟", w.ctx())
        val card = asked.messages.periodCard()
        assertTrue(card.pending)
        assertTrue("رمضان" in card.text, card.text)
        assertTrue(card.text.none { it.isDigit() }, "ولا رقم قبل ما تختار اليومين")
        assertIs<AssistantReply.PeriodPick>(card.reply)
        val answered = w.chat.pickPeriod(card.id, "2026-10-01", "2026-10-06", w.ctx())
        val expected = assertNotNull(w.money.load("2026-10-01", "2026-10-06").expenseMinor)
        assertEquals(35_000L, expected, "قهوة 25 + 25 + بقالة 300")
        val reply = answered.messages.last { it.from == AssistSpeaker.BOT }
        assertTrue(money(expected) in reply.text, reply.text)
        assertTrue("رمضان" in reply.text, reply.text)
        val closed = answered.messages.first { it.id == card.id }
        assertEquals(CardState.DONE to "2026-10-01|2026-10-06", closed.state to closed.picked)
        assertEquals("2026-10-01" to "2026-10-06", (closed.reply as AssistantReply.PeriodPick).picked)
        // ضغطة تانية على نفس الكارت ما بتجاوبش تاني
        val again = w.chat.pickPeriod(card.id, "2026-10-01", "2026-10-02", w.ctx())
        assertEquals(answered.messages.size, again.messages.size)
    }

    @Test fun aCategoryInAnOccasionUsesTheCategorySpend() = runBlocking<Unit> {
        val w = AssistantWorld()
        val which = w.chat.send("كم صرفت على القهوة في العيد", w.ctx()).messages.last { it.choice == AssistChoiceKind.PICK_OCCASION }
        val card = w.chat.pick(which.id, EID_FITR_ID, w.ctx()).messages.periodCard()
        val answered = w.chat.pickPeriod(card.id, "2026-09-29", "2026-10-03", w.ctx())
        val expected = assertNotNull(w.categorySpend.load("2026-09-29", "2026-10-03", setOf(w.coffeeId)).amountMinor)
        assertEquals(5_000L, expected)
        assertTrue(money(expected) in answered.messages.last { it.from == AssistSpeaker.BOT }.text)
    }

    @Test fun aBadRangeIsExplainedAndTheCardStaysOpen() = runBlocking<Unit> {
        val w = AssistantWorld()
        val card = w.chat.send("صرفت كام في رمضان", w.ctx()).messages.periodCard()
        val reversed = w.chat.pickPeriod(card.id, "2026-10-06", "2026-10-01", w.ctx())
        assertEquals(uiText(AskKey.CHAT_PERIOD_ORDER), reversed.messages.last().text)
        val future = w.chat.pickPeriod(card.id, "2026-10-01", "2026-10-20", w.ctx())
        assertEquals(uiText(AskKey.CHAT_PERIOD_FUTURE), future.messages.last().text)
        assertTrue(future.messages.first { it.id == card.id }.pending, "الكارت مستني لحد مدى صح")
    }

    /** §89-2 السعودية: «العيد» لوحده ⇒ «أنهي عيد؟» بزرارين، وبعدها كارت اليومين باسم العيد اللي اتختار والإجابة من نفس حالة الاستخدام. */
    @Test fun saudiPlainEidAsksWhichEidFirst() = runBlocking<Unit> {
        val w = AssistantWorld()
        val asked = w.chat.send("صرفت كام في العيد؟", w.ctx()).messages
        val which = asked.last()
        assertEquals(AssistChoiceKind.PICK_OCCASION, which.choice)
        assertTrue(asked.none { it.choice == AssistChoiceKind.PICK_PERIOD }, "مفيش كارت أيام قبل ما يختار العيد")
        assertEquals(listOf(EID_FITR_ID to "عيد الفطر", EID_ADHA_ID to "عيد الأضحى"), which.options.map { it.id to it.label })
        assertIs<AssistantReply.Choice>(which.reply)
        // اختيار مش من الاتنين ⇒ ولا حاجة
        assertTrue(w.chat.pick(which.id, "other", w.ctx()).messages.first { it.id == which.id }.pending)
        val chosen = w.chat.pick(which.id, EID_ADHA_ID, w.ctx())
        val closed = chosen.messages.first { it.id == which.id }
        assertEquals(CardState.DONE to EID_ADHA_ID, closed.state to closed.picked)
        val card = chosen.messages.periodCard()
        assertTrue("عيد الأضحى" in card.text, card.text)
        val answered = w.chat.pickPeriod(card.id, "2026-10-01", "2026-10-06", w.ctx())
        val reply = answered.messages.last { it.from == AssistSpeaker.BOT }
        assertTrue(money(35_000) in reply.text && "عيد الأضحى" in reply.text, reply.text)
    }

    /** §89-2 مصر: «العيد» لوحده ⇒ كارت اليومين على طول — ما بنخمّنش أنهي عيد. */
    @Test fun egyptianPlainEidGoesStraightToTheDays() = runBlocking<Unit> {
        val w = AssistantWorld()
        val egypt = AssistContext("2026-10-10T09:00:00.000Z", "2026-10-10", 12, w.space.copy(countryCode = "EG"))
        val asked = w.chat.send("صرفت كام في العيد؟", egypt).messages
        assertTrue(asked.none { it.choice == AssistChoiceKind.PICK_OCCASION })
        val card = asked.periodCard()
        assertTrue(card.pending && "العيد" in card.text, card.text)
        val answered = w.chat.pickPeriod(card.id, "2026-10-01", "2026-10-06", egypt)
        assertTrue(money(35_000) in answered.messages.last { it.from == AssistSpeaker.BOT }.text)
    }

    /** العيد بالاسم ⇒ زي ما هو (كارت اليومين على طول) في البلدين. */
    @Test fun namedEidsKeepTheirCard() = runBlocking<Unit> {
        val w = AssistantWorld()
        val egypt = AssistContext("2026-10-10T09:00:00.000Z", "2026-10-10", 12, w.space.copy(countryCode = "EG"))
        for (ctx in listOf(w.ctx(), egypt)) {
            for ((said, name) in listOf("صرفت كام في عيد الفطر" to "عيد الفطر", "صرفت كام في العيد الكبير" to "عيد الأضحى")) {
                val last = w.chat.send(said, ctx).messages.last()
                assertEquals(AssistChoiceKind.PICK_PERIOD, last.choice, said)
                assertTrue(name in last.text, last.text)
            }
        }
    }

    @Test fun otherMonthsAndOccasionsAskToo() = runBlocking<Unit> {
        val w = AssistantWorld()
        for (text in listOf("كم صرفت في شوال", "صرفت كام في ذي الحجة", "دخلي كام في رمضان", "how much did I spend in ramadan")) {
            val v = w.chat.send(text, w.ctx())
            val last = v.messages.last()
            assertEquals(AssistChoiceKind.PICK_PERIOD, last.choice, text)
        }
    }
}
