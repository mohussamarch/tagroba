package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.withEstimatedKinds
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

    /**
     * P9: الكشف فيه نفس المبلغ في نفس اليوم ⇒ الإجابة **ما بتسجّلش** (كانت بتعدّي «شبه عملية موجودة» بالاختيار الصريح والمصروف بيتحسب
     * مرتين) — بترجع العملية الموجودة والرسالة بتفضل؛ «هي نفسها» بيشيل الرسالة · «لأ دي تانية» بيسجّلها.
     */
    @Test fun anAnswerThatLooksLikeAStatementLineAsksFirst() = runBlocking<Unit> {
        for (same in listOf(true, false)) {
            val w = ReturnsWorld().enable()
            w.importStatementLine("2026-10-07", 8_775, Direction.OUT, "FT26X0001")
            val statement = w.all().single()
            w.memory.receive(sms("f1", usd))
            val answer = asksOf(w).answerForeign("f1", 8_775)
            assertEquals(ForeignOutcome.LOOKS_LIKE_EXISTING to statement.id, answer.outcome to answer.transactionId)
            assertEquals(listOf(statement), w.all(), "ما اتسجلش حاجة")
            assertEquals(listOf("f1"), w.memory.sync().messages.map { it.id }, "والرسالة مستنية")
            if (same) {
                asksOf(w).keepExisting("f1")
                assertEquals(listOf(statement), w.all())
            } else {
                assertEquals(ForeignOutcome.RECORDED, asksOf(w).answerForeign("f1", 8_775, notTheSame = true).outcome)
                assertEquals(2, w.all().size)
            }
            assertEquals(emptyList(), w.memory.sync().messages)
            assertEquals(emptyList(), asksOf(w).list())
        }
    }

    /** P4: الأجنبي اللي **رجع** بيدخل §77-D بعد الإجابة: نوعه RETURNED ومرجعه معاه ⇒ «استرداد» مقترح وسؤال (مش داخل عادي). */
    @Test fun anAnsweredForeignReturnGoesThroughTheReturnRules() = runBlocking<Unit> {
        val w = ReturnsWorld().enable()
        w.memory.receive(sms("r1", "حوالة مرتجعة\nمبلغ:USD 23.40 (SAR 87.75)\nمرجع:$RETURN_REF\nفي:26-10-07 10:00"))
        val ask = asksOf(w).list().single()
        assertEquals(SmsKind.RETURNED to Direction.IN, ask.kind to ask.direction)
        asksOf(w).answerForeign("r1", 8_775)
        val t = w.all().single()
        assertEquals(EconomicKind.REFUND_RECEIVED to EconomicKind.UNCLASSIFIED, t.suggestedKind to t.economicKind)
        assertEquals("USD" to 2_340L, t.foreignCurrency to t.foreignAmountMinor)
        assertEquals(listOf(AskKind.REVERSAL_CHECK), w.asks(asksOf(w)).pending("2026-10-01", "2026-10-31").map { it.kind })
        assertEquals(0L, computePeriodTotals(withEstimatedKinds(w.all(), emptyMap()).transactions, emptyList()).incomeMinor)
    }

    /**
     * P4 (عكس من محل بعملة أجنبية): «Purchase Reversal» بالدولار = **استرداد** (§75-6) مش عملية رجعت (§77-D) ⇒ السؤال نوعه REFUND، والإجابة
     * بتعدّي على آثار وقت التسجيل بنوع REFUND و«المالك هو اللي سجّل» (أثر تأكيد الاسترداد بتاع الشريحة S2 بيشتغل عليها) — وما بتلغيش الشراء.
     */
    @Test fun aForeignMerchantReversalIsAskedAsARefundAndCancelsNothing() = runBlocking<Unit> {
        val seen = mutableListOf<Pair<SmsKind?, Boolean>>()
        val probe = object : RecordEffect {
            override suspend fun prepare(ctx: RecordContext) {
                for (line in ctx.lines) seen += line.sms?.kind to ctx.byOwner
            }
        }
        val w = ReturnsWorld(before = listOf(probe)).enable()
        w.confirmOnScreen("buy" to purchaseWithRef(day = "05", amount = "87.75"))
        seen.clear()
        w.memory.receive(sms("f2", "Purchase Reversal\nAmount: USD 23.40 (SAR 87.75)\nFrom: TEST STORE\nRef: $RETURN_REF\n2026-10-07 09:10"))
        val ask = asksOf(w).list().single()
        assertEquals(SmsKind.REFUND to Direction.IN, ask.kind to ask.direction)
        assertEquals(ForeignOutcome.RECORDED, asksOf(w).answerForeign("f2", 8_775).outcome)
        assertEquals(listOf<Pair<SmsKind?, Boolean>>(SmsKind.REFUND to true), seen)
        assertNull(w.all().single { it.observedDirection == Direction.OUT }.reversedById, "الشراء ما اتلغاش")
        val back = w.all().single { it.observedDirection == Direction.IN }
        assertEquals(Triple(null, "USD", 2_340L), Triple(back.reversalOfId, back.foreignCurrency, back.foreignAmountMinor))
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
