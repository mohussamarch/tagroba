package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.MAX_SAFE_HALALAS
import app.masroufy.core.MoneyError
import app.masroufy.core.SmsKind
import app.masroufy.core.Wallet
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §75-12 (قرار المالك ✗): «شراء بعملة أجنبية ⇒ يتسجل ويسأل عن المبلغ بالعملة المحلية». السؤال على الرسالة المستنية، والإجابة بتسجّل عملية
 * واحدة بالمبلغ المحلي ومعاها الأجنبي — **ولا عملية بمبلغ مخترع قبل الإجابة** (قاعدة 10). كل الرسايل مخترعة.
 */
class ForeignSmsAskTest {
    private val usd = "شراء انترنت\nبطاقة:6604;مدى\nمبلغ:USD 23.40 (SAR 87.75)\nلدى:TEST SHOP\nفي:26-10-07 10:00"
    private val kwd = "شراء انترنت\nبطاقة:6604;مدى\nمبلغ:KWD 12.345\nلدى:TEST SHOP\nفي:26-10-07 10:00"
    private val currencyOnly = "PoS Purchase\nAmount: 25.00\nCurrency: USD\nAt: TEST SHOP\nOn: 2026-10-07"

    /** صندوق بيقع مرة واحدة وقت الشيل — الإجابة اتحفظت والرسالة فضلت. */
    private class AckFails(val real: SmsInboxPort) : SmsInboxPort by real {
        var fail = 0

        override suspend fun acknowledge(ids: List<String>): SmsInboxState {
            if (fail > 0) {
                fail--
                throw IllegalStateException("crash before acknowledge")
            }
            return real.acknowledge(ids)
        }
    }

    private fun asksOf(w: ReturnsWorld, inbox: SmsInboxPort = w.memory) = ForeignSmsAsks(AutoRecordSmsDeps(inbox, listOf(w.lane())))

    @Test fun aForeignPurchaseIsAQuestionNotATransaction() = runBlocking<Unit> {
        val w = ReturnsWorld().enable()
        w.memory.receive(sms("f1", usd))
        assertEquals(0, w.auto().run().recorded, "§75-12: ما بتتسجلش لوحدها بأي مبلغ")
        val ask = asksOf(w).list().single()
        assertEquals(Triple("USD", 2340L, 2), Triple(ask.currency, ask.foreignMinor, ask.decimals))
        assertEquals(8775L, ask.localSuggestion, "المكتوب بالريال اقتراح بس")
        assertEquals(Triple("sa", Direction.OUT, SmsKind.PURCHASE), Triple(ask.spaceId, ask.direction, ask.kind))
        assertEquals("2026-10-07", ask.date)
        assertEquals(emptyList(), w.all())
        val pending = w.asks(asksOf(w)).pending("2026-10-01", "2026-10-31")
        assertEquals(listOf(AskKind.FOREIGN_LOCAL_AMOUNT to "f1"), pending.map { it.kind to it.messageId })
        assertEquals(1, w.asks(asksOf(w)).pending("2026-10-08", "2026-12-31").size, "مستنية لحد ما تتجاوب مهما قدمت")
    }

    @Test fun answeringRecordsOneTransactionWithTheForeignAmount() = runBlocking<Unit> {
        val w = ReturnsWorld().enable()
        w.memory.receive(sms("f1", usd))
        val answer = asksOf(w).answerForeign("f1", 8_775)
        assertEquals(false, answer.alreadyRecorded)
        val t = w.all().single()
        assertEquals(answer.transactionId, t.id)
        assertEquals(Triple(8_775L, Currency.SAR, BANK.id), Triple(t.amountMinor, t.currency, t.walletId))
        assertEquals("USD" to 2340L, t.foreignCurrency to t.foreignAmountMinor)
        assertEquals(Direction.OUT, t.observedDirection)
        assertEquals(emptyList(), w.memory.sync().messages, "الرسالة اتشالت من الصندوق")
        assertEquals(emptyList(), asksOf(w).list())
        assertFailsWith<IllegalStateException> { asksOf(w).answerForeign("f1", 8_775) }
        // الرسالة نفسها وصلت تاني (الجهاز قراها تاني) ⇒ نفس المرجع ⇒ ما فيش عملية تانية
        w.memory.receive(sms("f1", usd))
        assertEquals(true, asksOf(w).answerForeign("f1", 8_775).alreadyRecorded)
        assertEquals(1, w.all().size)
        assertEquals(0, w.auto().run().recorded)
    }

    @Test fun aCrashBeforeTheMessageIsRemovedRecordsNothingTwice() = runBlocking<Unit> {
        val w = ReturnsWorld().enable()
        val inbox = AckFails(w.memory)
        w.memory.receive(sms("f1", usd))
        inbox.fail = 1
        assertFailsWith<IllegalStateException> { asksOf(w, inbox).answerForeign("f1", 8_775) }
        assertEquals(1, w.all().size, "اتحفظت")
        assertEquals(listOf("f1"), w.memory.sync().messages.map { it.id }, "والرسالة فضلت")
        val again = asksOf(w, inbox).answerForeign("f1", 9_000)
        assertEquals(true, again.alreadyRecorded, "حتى بمبلغ تاني: نفس الرسالة = نفس العملية")
        assertEquals(w.all().single().id, again.transactionId)
        assertEquals(8_775L, w.all().single().amountMinor)
        assertEquals(emptyList(), w.memory.sync().messages)
    }

    @Test fun theCurrencyDecimalsAreKept() = runBlocking<Unit> {
        val w = ReturnsWorld().enable()
        w.memory.receive(sms("k1", kwd))
        val ask = asksOf(w).list().single()
        assertEquals(Triple("KWD", 12345L, 3), Triple(ask.currency, ask.foreignMinor, ask.decimals))
        assertNull(ask.localSuggestion)
        asksOf(w).answerForeign("k1", 15_220)
        assertEquals("KWD" to 12345L, w.all().single().let { it.foreignCurrency to it.foreignAmountMinor })
    }

    /** الأجنبي اللي مبلغه مش مقروء بالظبط: العملة بتتخزن **لو معروفة بس**، والمبلغ الأجنبي لو اتقري بالظبط بس. */
    @Test fun anUnreadForeignAmountStoresOnlyWhatIsKnown() = runBlocking<Unit> {
        val w = ReturnsWorld().enable()
        w.memory.receive(sms("u1", currencyOnly))
        val ask = asksOf(w).list().single()
        assertEquals("USD" to null, ask.currency to ask.foreignMinor)
        asksOf(w).answerForeign("u1", 9_400)
        assertEquals("USD" to null, w.all().single().let { it.foreignCurrency to it.foreignAmountMinor })

        val eg = Wallet("eg-bank", "بنك مصري وهمي", Currency.EGP, "bank", 0, "2026-01-01")
        val w2 = ReturnsWorld(wallets = listOf(eg), parse = ::parseEgyptBankSms).enable()
        w2.memory.receive(sms("u2", "تم خصم 200 دولار من بطاقتك المنتهية بـ 6604 عند TEST SHOP يوم 07/10/2026"))
        val dollar = asksOf(w2).list().single()
        assertEquals(Triple(null, null, "200"), Triple(dollar.currency, dollar.foreignMinor, dollar.writtenAmount))
        asksOf(w2).answerForeign("u2", 980_000)
        val t = w2.all().single()
        assertEquals(Triple(980_000L, Currency.EGP, null), Triple(t.amountMinor, t.currency, t.foreignCurrency))
        assertNull(t.foreignAmountMinor)
    }

    @Test fun badAnswersAreRefused() = runBlocking<Unit> {
        val w = ReturnsWorld().enable()
        w.memory.receive(sms("f1", usd))
        assertFailsWith<IllegalArgumentException> { asksOf(w).answerForeign("f1", 0) }
        assertFailsWith<IllegalArgumentException> { asksOf(w).answerForeign("f1", -100) }
        assertFailsWith<MoneyError> { asksOf(w).answerForeign("f1", MAX_SAFE_HALALAS + 1) }
        assertFailsWith<IllegalStateException> { asksOf(w).answerForeign("nope", 100) }
        assertFailsWith<IllegalArgumentException> { asksOf(w).answerForeign("f1", 100, walletId = "no-such-wallet") }
        assertEquals(emptyList(), w.all())

        // بنكين ومن غير ربط ⇒ لازم يختار المحفظة
        val w2 = ReturnsWorld(wallets = listOf(CASH, BANK, BANK2)).enable()
        w2.memory.receive(sms("f2", usd))
        assertFailsWith<IllegalStateException> { asksOf(w2).answerForeign("f2", 8_775) }
        asksOf(w2).answerForeign("f2", 8_775, walletId = BANK2.id)
        assertEquals(BANK2.id, w2.all().single().walletId)
        assertTrue(w2.memory.sync().messages.isEmpty())
    }
}
