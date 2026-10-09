package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * الشريحة S3 — §77-D: العملية اللي **رجعت** (`SmsKind.RETURNED`) ورقمها المرجعي، وقواعد البحث عن الأصلية (`Reversals.kt`) — والاسترداد من
 * محل بيفضل استرداد (§75-6). كل الرسايل مخترعة (TEST STORE · الكارت 6604 · الحساب 1188 · المرجع 553317781 …).
 */
class SmsReturnedTest {
    private val at = "2026-10-07T10:00:00Z"

    private fun row(r: SmsParseResult, name: String): SmsRow = when (r) {
        is SmsParseResult.Ok -> r.row
        is SmsParseResult.Rejected -> throw AssertionError("$name: rejected «${r.reason}»")
    }

    private fun saudi(body: String) = row(parseBankSms(BankSmsMessage("TESTBANK", at, body), 1), body)
    private fun egypt(body: String) = row(parseEgyptBankSms(BankSmsMessage("TESTBANK", at, body), 1), body)

    @Test fun returnedOperationsAreReturnedAndTheReadingIsUnchanged() {
        val cases = listOf(
            saudi("حوالة واردة مرتجعة\nمبلغ:SAR 250.00\nمرجع:553317781\nفي:26-10-05 10:00"),
            saudi("حوالة مرتجعة\nمبلغ:SAR 250.00\nمرجع:553317781\nفي:26-10-05 10:00"),
            saudi("Purchase Reversal\nAmount: SAR 250.00\nFrom: TEST STORE\nRef: 553317781\n2026-10-05 09:10"),
            saudi("عكس عملية\nالى: ***6604; VISA\n250.00 SAR :المبلغ\nفي: TEST STORE\nبتاريخ: 2026-10-05 10:00:00"),
            egypt("تم رد مبلغ التحويل 250.00 جم لحسابكم رقم 1188 رقم مرجعي 553317781 يوم 05/10/2026"),
            egypt("IPN Transfer dated 02/10/2026 10:00 with EGP 250.00 returned with Ref# 553317781. For info call 19888"),
            egypt("The transaction on your credit card#6604 from TEST STORE with EGP 250.00 on 05/10/26 at 09:10 has been refunded."),
            egypt("لقد تم رد EGP250.00 على بطاقتكم الائتمانية المنتهية بـ# 6604 من TEST STORE"),
        )
        for (r in cases) {
            assertEquals(SmsKind.RETURNED, r.kind, r.raw)
            assertEquals(IN, r.direction, r.raw)
            assertEquals(25_000L, r.amountMinor, r.raw)
        }
        // بيت التمويل: «dated <يوم التحويل الأصلي>» ⇒ يوم الوصول (الجولة السادسة) — زي ما هو
        assertEquals("2026-10-07", cases[5].date)
    }

    @Test fun theWordingAloneDecides() {
        for (body in listOf("التحويل رجع", "الحوالة الصادرة مرتجعة", "تم رد مبلغ التحويل", "اترد المبلغ", "Transfer returned", "Reverse Transaction")) {
            assertEquals(SmsKind.RETURNED, refineSmsKind(body, SmsKind.TRANSFER_IN, IN), body)
            assertEquals(SmsKind.TRANSFER_OUT, refineSmsKind(body, SmsKind.TRANSFER_OUT, OUT), "$body: الصادر ما بيتغيرش")
        }
        // الراتب والسحب والإيداع وبين حساباتك وسداد الكارت ما بيبقوش «رجعت»
        for (kind in listOf(SmsKind.SALARY, SmsKind.CASH_DEPOSIT, SmsKind.OWN_TRANSFER, SmsKind.CARD_PAYMENT)) {
            assertEquals(kind, refineSmsKind("التحويل رجع", kind, IN))
        }
    }

    /** §75-6: الاسترداد من محل وكاش باك بيفضلوا استرداد (مش «رجعت»). */
    @Test fun merchantRefundsAndCashbackStayRefunds() {
        for (body in listOf("استرداد شراء\nبطاقة:6604;مدى\nمبلغ:SAR 250.00\nلدى:TEST STORE\nفي:26-10-05 10:00",
            "كاش باك\nبطاقة:6604;مدى\nمبلغ:SAR 12.40\nلدى:TEST STORE\nفي:26-10-05 09:10",
            "استرداد مبلغ شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10",
            "Notification: Refund\nTransaction: TEST STORE\nCard: ***6604\nAmount: 89.00 SAR\nDate: 2026-10-05 16:02")) {
            assertEquals(SmsKind.REFUND, saudi(body).kind, body)
        }
        for (body in listOf("استرداد مبلغ شراء", "كاش باك", "مرتجع", "إرجاع", "Refund")) assertEquals(SmsKind.REFUND, refineSmsKind(body, SmsKind.REFUND, IN), body)
    }

    @Test fun theBankReferenceIsReadAndOnlyItsLastFourMatter() {
        assertEquals("••••7781", saudi("شراء\nبطاقة:6604;مدى\nمبلغ:SAR 250.00\nلدى:TEST STORE\nفي:26-10-02 10:00\nمرجع:553317781").bankReference)
        assertEquals("••••7781", smsReferenceOf("IPN Transfer with EGP 250.00 deducted with Ref# 553317781. For info call 19888"))
        assertEquals("••••7781", smsReferenceOf("تم تنفيذ تحويل لحظي بمبلغ 250 جم برقم مرجعي 553317781 بتاريخ 05/10"))
        assertEquals("••••7781", smsReferenceOf("الرقم المرجعي: 553317781"))
        assertEquals("FT26A••••4567", smsReferenceOf("Reference: FT26A1234567"))
        assertEquals("*••••7781", smsReferenceOf("Ref. No. *553317781"))
        assertNull(smsReferenceOf("Refund 553317781"), "«Refund» مش لابل مرجع")
        assertNull(smsReferenceOf("Ref. Code: OK"), "القيمة لازم فيها رقم")
        assertNull(smsReferenceOf("مرجع: 12"), "أقل من 4")
        // الوصف المتخزن محجوب ⇒ نفس الذيل
        for (ref in listOf("553317781", "••••7781", "****7781", "*••••7781", "55-331-7781")) assertEquals("7781", referenceTail(ref), ref)
        assertEquals("4567", referenceTail("FT26A••••4567"))
        assertNull(referenceTail("a-12"))
        assertNull(referenceTail(null))
    }

    @Test fun theAnswerReferenceIsTheParserReference() {
        for ((body, parse) in listOf<Pair<String, (BankSmsMessage, Int) -> SmsParseResult>>(
            "شراء\nبطاقة:6604;مدى\nمبلغ:SAR 250.00\nلدى:TEST STORE\nفي:26-10-02 10:00" to ::parseBankSms,
            "IPN Transfer with EGP 250.00 deducted on 02/10/2026 10:00 from your AC ending with 1188 with Ref# 553317781. For info call 19888" to ::parseEgyptBankSms,
        )) {
            val message = BankSmsMessage("TESTBANK", at, body)
            val parsed = assertIs<SmsParseResult.Ok>(parse(message, 1)).row
            assertEquals(parsed.reference, smsSourceReference(message))
            assertEquals(parsed.description, smsSafeText(message))
        }
    }

    private fun txn(id: String, date: String, dir: Direction, amount: Long = 25_000, wallet: String = "w-bank") = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "2026-10-01T00:00:00Z", updatedAt = "2026-10-01T00:00:00Z", walletId = wallet,
    )

    /** §77-D: أصلية واحدة بنفس الذيل والمبلغ والمحفظة والاتجاه العكسي في الـ60 يوم ⇒ تتلغي؛ مربوطة أو مؤكدة ⇒ سؤال؛ غير كده ⇒ مش لاقيين. */
    @Test fun findingTheOriginal() {
        val ret = txn("r", "2026-10-05", IN)
        val buy = txn("o", "2026-10-02", OUT)
        fun c(t: Transaction, tail: String = "7781", linked: Boolean = false) = OriginalCandidate(t, setOf(tail), linked)
        assertEquals(ReversalMatch.Cancel(buy), matchReversal(ret, "7781", listOf(c(buy))))
        assertEquals(ReversalMatch.Check(buy), matchReversal(ret, "7781", listOf(c(buy, linked = true))))
        val confirmed = buy.copy(economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true)
        assertEquals(ReversalMatch.Check(confirmed), matchReversal(ret, "7781", listOf(c(confirmed))))
        fun miss(why: ReversalMiss) = ReversalMatch.NotFound(why)
        assertEquals(miss(ReversalMiss.NO_REFERENCE), matchReversal(ret, null, listOf(c(buy))))
        assertEquals(miss(ReversalMiss.NO_ORIGINAL), matchReversal(ret, "9999", listOf(c(buy))))
        assertEquals(miss(ReversalMiss.PARTIAL_AMOUNT), matchReversal(ret, "7781", listOf(c(buy.copy(amountMinor = 30_000)))))
        assertEquals(miss(ReversalMiss.SEVERAL), matchReversal(ret, "7781", listOf(c(buy), c(buy.copy(id = "o2")))))
        assertEquals(miss(ReversalMiss.NO_ORIGINAL), matchReversal(ret, "7781", listOf(c(buy.copy(occurredAt = "2026-08-05")))), "أقدم من 60 يوم")
        assertEquals(ReversalMatch.Cancel(buy.copy(occurredAt = "2026-08-06")), matchReversal(ret, "7781", listOf(c(buy.copy(occurredAt = "2026-08-06")))), "60 يوم بالظبط")
        assertEquals(miss(ReversalMiss.NO_ORIGINAL), matchReversal(ret, "7781", listOf(c(buy.copy(walletId = "w-other")))), "محفظة تانية")
        assertEquals(miss(ReversalMiss.NO_ORIGINAL), matchReversal(ret, "7781", listOf(c(txn("o3", "2026-10-02", IN)))), "نفس الاتجاه")
        assertEquals(miss(ReversalMiss.NO_ORIGINAL), matchReversal(ret, "7781", listOf(c(buy.copy(reversedById = "r0")))), "ملغية قبل كده")
        // المبلغ الأصلي من المصدر لو المالك عدّل المبلغ (§32)
        assertEquals(ReversalMatch.Cancel(buy.copy(amountMinor = 20_000, originalAmountMinor = 25_000)),
            matchReversal(ret, "7781", listOf(c(buy.copy(amountMinor = 20_000, originalAmountMinor = 25_000)))))
    }

    @Test fun cancelledPairIsAnInternalTransferBothWays() {
        val ret = cancelledReturn(txn("r", "2026-10-05", IN).copy(suggestedKind = EconomicKind.REFUND_RECEIVED), "o", "now")
        val buy = cancelledOriginal(txn("o", "2026-10-02", OUT), "r", "now")
        assertEquals(true, isCancelledPair(ret, buy))
        assertNull(ret.suggestedKind)
        val totals = computePeriodTotals(listOf(ret, buy), emptyList())
        assertEquals(PeriodTotals(0, 0, 0, 0), totals)
        assertEquals(CashMovement(0, 0), cashMovement(listOf(ret, buy)))
        val pending = pendingRefund(txn("r2", "2026-10-05", IN), "now")
        assertEquals(true, awaitsRefundAnswer(pending))
        assertEquals(0L, computePeriodTotals(listOf(pending), emptyList()).incomeMinor, "«استرداد» مقترح مش دخل")
        assertEquals(true, awaitsReversalAnswer(pendingReversalCheck(txn("r3", "2026-10-05", IN), "now")))
    }

    @Test fun writtenForeignAmountsUseTheCurrencyDecimals() {
        assertEquals(2340L, writtenForeignMinor("23.40", "USD"))
        assertEquals(12345L, writtenForeignMinor("12.345", "KWD"))
        assertEquals(4500L, writtenForeignMinor("4500", "JPY"))
        assertEquals(125_000L, writtenForeignMinor("1,250.00", "USD"))
        assertEquals(2000L, writtenForeignMinor("20", "USD"))
        assertNull(writtenForeignMinor("12.3456", "USD"), "كسور أكتر من العملة ⇒ مش مقروء بالظبط")
        assertNull(writtenForeignMinor("4500.5", "JPY"))
        assertNull(writtenForeignMinor("abc", "USD"))
        assertNull(writtenForeignMinor(null, "USD"))
        assertEquals(3, currencyDecimals("KWD"))
        assertEquals(0, currencyDecimals("JPY"))
    }
}
