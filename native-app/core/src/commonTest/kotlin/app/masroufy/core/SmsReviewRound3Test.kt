package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * الجولة التالتة من مراجعة قارئ الرسايل — كل حالة كانت بتطلع غلط قبل التصليح (المراجع جرّبها). الجداول الكبيرة في
 * `SmsAdversarialCases` (لازم تتجاهل · ما تتسجلش · لازم تتسجل)، وهنا التفاصيل: المبلغ الأجنبي والاقتراح المحلي والسبب والتاريخ.
 * الرسايل مخترعة (TEST STORE · الكارت 6604 · الحساب 1188 · المبالغ مخترعة).
 */
class SmsReviewRound3Test {
    private fun saudi(body: String) = parseBankSms(smsMessage(body), 1)
    private fun egypt(body: String) = parseEgyptBankSms(smsMessage(body), 1)

    private fun ok(r: SmsParseResult, name: String): SmsRow = when (r) {
        is SmsParseResult.Ok -> r.row
        is SmsParseResult.Rejected -> throw AssertionError("$name: rejected «${r.reason}»")
    }

    private fun pending(r: SmsParseResult, name: String): SmsForeignPending = when (r) {
        is SmsParseResult.Ok -> throw AssertionError("$name: BOOKED ${r.row.amountMinor} — §75-12 says a foreign purchase waits")
        is SmsParseResult.Rejected -> r.foreign ?: throw AssertionError("$name: no pending details («${r.reason}»)")
    }

    private fun reason(r: SmsParseResult) = assertIs<SmsParseResult.Rejected>(r).reason
    private fun notPending(r: SmsParseResult, name: String) = assertNull(assertIs<SmsParseResult.Rejected>(r, name).foreign, "$name: waiting in both countries")

    private fun checkPending(r: SmsParseResult, name: String, code: String, minor: Long, local: Long?) {
        val p = pending(r, name)
        assertEquals(SmsForeignAmount(code, minor), p.foreign, "$name: foreign")
        assertEquals(local, p.localSuggestion, "$name: local suggestion")
        assertEquals(SMS_TX_DAY, p.date, "$name: date")
    }

    /** §75-12: رمز · اسم بالإنجليزي أو العربي · كود من غير كسور · الشكل «X (SAR …)» ⇒ مستنية، والمحلي اقتراح بس — في بلد واحد. */
    @Test fun foreignWrittenAnyWayWaitsWithItsLocalSuggestion() {
        val sa = listOf(
            Triple("Online Purchase\nAmount: \$23.40 (SAR 87.75)\nAt: TEST STORE\n2026-03-05 09:10", SmsForeignAmount("USD", 2340), 8775L),
            Triple("Purchase\nJPY 4500 (SAR 112.50)\nAt TEST STORE\n2026-03-05 09:10", SmsForeignAmount("JPY", 4500), 11250L),
            Triple("International Purchase\nCard *6604\nINR 2500 = SAR 112.50\nAt: TEST STORE\n2026-03-05 09:10", SmsForeignAmount("INR", 250000), 11250L),
            Triple("شراء دولي\nبطاقة: *6604\nمبلغ: 4500 ين ياباني\nما يعادل: 112.50 ر.س\nلدى: TEST STORE\nفي: 2026-03-05 09:10", SmsForeignAmount("JPY", 4500), 11250L),
            Triple("شراء دولي\nبطاقة: *6604\nمبلغ: 300 يوان\n(156.20 ريال)\nلدى: TEST STORE\nفي: 2026-03-05 09:10", SmsForeignAmount("CNY", 30000), 15620L),
            Triple("Purchase\n€19.99 (SAR 81.40)\nAt: TEST STORE\n2026-03-05 09:10", SmsForeignAmount("EUR", 1999), 8140L),
            Triple("Purchase\nAmount: 12.50 Swiss Francs (SAR 52.10)\nAt: TEST STORE\n2026-03-05 09:10", SmsForeignAmount("CHF", 1250), 5210L),
            Triple("شراء عبر الإنترنت\nمبلغ: 20 فرنك سويسري\nالمبلغ بالريال: 84.10 ر.س\nلدى: TEST STORE\nفي: 2026-03-05 09:10", SmsForeignAmount("CHF", 2000), 8410L),
            Triple("International Purchase\nAmount: 25.00 Euro (SAR 101.00)\nAt: TEST STORE\n2026-03-05 09:10", SmsForeignAmount("EUR", 2500), 10100L),
            Triple("Purchase\nAmount: 25.00 US Dollars (SAR 93.75)\nAt: TEST STORE\n2026-03-05 09:10", SmsForeignAmount("USD", 2500), 9375L),
            Triple("شراء دولي\nمبلغ: 300 يوان (155.00 ريال)\nلدى: TEST STORE\nفي: 2026-03-05 09:10", SmsForeignAmount("CNY", 30000), 15500L),
        )
        for ((body, foreign, local) in sa) {
            checkPending(saudi(body), body, foreign.currency, foreign.amountMinor, local)
            notPending(egypt(body), "EG lane: $body")
            assertFalse(SmsVocabulary.ignoreBeforeStorage(body), "device filter dropped: $body")
        }
        // عنوان «International» + كود جنب رقم صحيح؛ القوسين بعد اسم المحل مش مقابل ⇒ مفيش اقتراح (قاعدة 10)، والمحل من غير القوسين
        val tr = pending(saudi("International Purchase TRY 450 at TEST STORE (SAR 93.75) 2026-03-05"), "TRY title")
        assertEquals(SmsForeignAmount("TRY", 45000), tr.foreign)
        assertNull(tr.localSuggestion)
        assertEquals("TEST STORE", tr.merchantName)
        val eg = listOf(
            Triple("Your credit card 6604 was charged \$15.00 (EGP 720.00) at TEST STORE on 05/03/2026", SmsForeignAmount("USD", 1500), 72000L),
            Triple("Your credit card 6604 was charged £12.00 at TEST STORE on 05/03/2026. Equivalent EGP 750.00", SmsForeignAmount("GBP", 1200), 75000L),
            Triple("A Trx using card 6604 for 15.00 US Dollars (EGP 720.00) at TEST STORE on 05/03/2026", SmsForeignAmount("USD", 1500), 72000L),
            Triple("تم خصم 4,500 ين من بطاقتك 6604 عند TEST STORE يوم 05/03/2026 بما يعادل 1,450 جم", SmsForeignAmount("JPY", 4500), 145000L),
            Triple("تم خصم \$25.00 (EGP 1,250.00) من بطاقتك المنتهية بـ 6604 عند TEST STORE يوم 05/03/2026", SmsForeignAmount("USD", 2500), 125000L),
        )
        for ((body, foreign, local) in eg) {
            checkPending(egypt(body), body, foreign.currency, foreign.amountMinor, local)
            notPending(saudi(body), "SA lane: $body")
            assertFalse(SmsVocabulary.ignoreBeforeStorage(body), "device filter dropped: $body")
        }
    }

    /** كارت سعودي اتخصم بالجنيه والريال مقابله (دي 360 · «500.00 جم» بالعربي) ⇒ مستنية **في السعودية بس** ومعاها الجنيه. */
    @Test fun aSaudiCardChargedInEgpWaitsInTheSaudiLaneOnly() {
        val bodies = listOf(
            "International Online Purchase\nAmount: EGP 500.00 (SAR 37.50)\nCard: *6604 - VISA (Ecommerce)\nAt: TEST STORE\nOn: 05/03/2026 09:10" to 3750L,
            "Purchase of EGP 500.00 (SAR 37.50) at TEST STORE on 05/03/2026 with card 6604" to 3750L,
            "شراء دولي\nبطاقة: *6604\nمبلغ: 500.00 جم\n(38.50 ر.س)\nلدى: TEST STORE\n2026-03-05 09:10" to 3850L,
        )
        for ((body, local) in bodies) {
            checkPending(saudi(body), body, "EGP", 50000, local)
            notPending(egypt(body), "EG lane: $body")
        }
        // العكس: كارت مصري اتخصم بالريال والجنيه **مقابل** بين قوسين ⇒ مصر بس، والمبلغ الأجنبي هو الريال (مش الجنيه)
        val egyptianCard = "Your credit card ending with#6604 was charged for SAR 75.00 (EGP 980.00) at TEST STORE on 05/03/26 at 09:10"
        checkPending(egypt(egyptianCard), "EG card in SAR", "SAR", 7500, 98000)
        notPending(saudi(egyptianCard), "SA lane: EG card in SAR")
        // نفس الحالة بشكل سطور بعنوان «Purchase» (القارئ السعودي بيفهم اتجاهها) ⇒ برضه مصر بس
        val lines = "Purchase\nAmount: SAR 75.00 (EGP 980.00)\nAt: TEST STORE\n2026-03-05 09:10"
        checkPending(egypt(lines), "EG card in SAR (lines)", "SAR", 7500, 98000)
        notPending(saudi(lines), "SA lane: EG card in SAR (lines)")
    }

    /** المقابل المحلي = القوسين **بعد المبلغ الأجنبي** بس — مش رسوم ولا رصيد ولا حد (قاعدة 10). */
    @Test fun aFeeBalanceOrLimitInParenthesesIsNotTheLocalSuggestion() {
        val sa = listOf(
            "International Purchase\nAmount: USD 23.40\nInternational fee (SAR 2.20)\nAt: TEST STORE\n2026-03-05 09:10",
            "International Purchase\nAmount: USD 23.40\nAt: TEST STORE\nAvailable balance (SAR 4,100.00)\nOn: 05/03/2026 09:10",
            "International Purchase\nAmount: USD 23.40\nAt: TEST STORE\nFees (SAR 1.75)\nOn: 05/03/2026 09:10",
        )
        for (body in sa) checkPending(saudi(body), body, "USD", 2340, null)
        // الرسوم نفسها بعملة أجنبية ومعاها مقابلها ⇒ ده مقابل الرسوم مش الشراء
        checkPending(saudi("International Purchase\nAmount: USD 23.40\nFee: USD 2.20 (SAR 8.25)\nAt: TEST STORE\n2026-03-05 09:10"), "fee equivalent", "USD", 2340, null)
        checkPending(egypt("Your credit card ending with#6604 was charged for USD 15.00 at TEST STORE on 05/03/2026. Fees (EGP 25.00)"), "EG fees", "USD", 1500, null)
        val limit = "Your credit card ending with#6604 was charged for USD 14.90 at TEST STORE on 05/03/26 at 09:10. Available limit (EGP 38,500.00)."
        checkPending(egypt(limit), "EG limit", "USD", 1490, null)
    }

    /** عكس أو استقطاع لحاجة ليها اتجاه ⇒ «الاتجاه مش واضح». العناوين المعروفة بمعنى استرداد لسه استرداد داخل (§75-6). */
    @Test fun reversalsAndDeductionsOfSomethingAreUnclear() {
        val unclear = uiText(TextKey.SMS_DIRECTION_UNCLEAR)
        val titles = listOf("Refund Reversal", "عكس استرداد", "ATM Withdrawal Reversal", "عكس سحب صراف آلي", "Cashback Reversal",
            "Debit Reversal", "عكس حوالة واردة", "Salary Deduction", "Salary Reversal")
        for (t in titles) assertEquals(unclear, reason(saudi("$t\nAmount: SAR 500.00\nAt: TEST STORE\n2026-03-05 09:10")), t)
        for (t in listOf("Purchase Reversal", "عكس عملية", "Reverse Transaction", "حوالة عكسية")) {
            val row = ok(saudi("$t\nAmount: SAR 64.25\nFrom: TEST STORE\n2026-03-05 09:10"), t)
            assertEquals(IN, row.direction, t)
            assertEquals(SmsKind.REFUND, row.kind, t)
        }
    }

    /** مصر: شيك رجع ⇒ مش واضح (زي السعودية) · من حسابك لحسابك ⇒ مش واضح · شحن المحفظة نفسها ⇒ داخل مش فاتورة. */
    @Test fun egyptChequeOwnTransferAndWalletTopUp() {
        val unclear = uiText(TextKey.SMS_DIRECTION_UNCLEAR)
        assertEquals(unclear, reason(egypt("Cheque no. 4417 for EGP 5,000.00 deposited on 05/03/2026 was returned unpaid")))
        assertEquals(unclear, reason(egypt("تم تحويل مبلغ 500 جم من حسابك رقم 1188 إلى حسابك رقم 2277 يوم 05/03/2026")))
        val topUp = ok(egypt("تم شحن محفظتك فودافون كاش بمبلغ 500.00 جنيه بنجاح يوم 05/03/2026"), "top-up")
        assertEquals(IN, topUp.direction)
        assertEquals(SmsKind.OTHER, topUp.kind)
        assertEquals(unclear, reason(egypt("تم شحن محفظتك بمبلغ 500.00 جنيه من بطاقتك 6604 يوم 05/03/2026")))
        // شحن رصيد الموبايل **من** المحفظة لسه فاتورة طالعة
        val recharge = ok(egypt("تم شحن رصيد موبايلك ب 50 بنجاح وخصم 57 من محفظتك شاملة الضريبة يوم 05/03/2026"), "recharge")
        assertEquals(OUT, recharge.direction)
        assertEquals(SmsKind.BILL, recharge.kind)
    }

    /** الهجري: اللي بعد «هـ» بيتساب و«الموافق» بيكسب · سنة بعيدة عن الوصول ⇒ التاريخ مش واضح. */
    @Test fun hijriDatesAreNotGregorian() {
        val citizen = ok(saudi("إيداع\nحساب المواطن\nمبلغ: 1,200.00 ر.س\nبتاريخ: 1447/09/16هـ الموافق 2026-03-05"), "muwafiq")
        assertEquals(SMS_TX_DAY, citizen.date)
        assertEquals(120000L, citizen.amountMinor)
        val dateUnclear = uiText(TextKey.SMS_DATE_UNCLEAR)
        assertEquals(dateUnclear, reason(saudi("Purchase\nAmount: SAR 64.25\nAt: TEST STORE\nOn: 1447-09-16")))
        assertEquals(dateUnclear, reason(egypt("تم خصم 500 جم من بطاقتك 6604 عند TEST STORE يوم 1447/09/16")))
        assertEquals(SMS_TX_DAY, ok(saudi("شراء\nمبلغ: SAR 64.25\nلدى: TEST STORE\nفي: 16/09/1447هـ - 05/03/2026"), "dmy").date)
    }

    /** رسوم/ضريبة **بعد** المبلغ أو قبله · مرجع قصير لازق في الكود ⇒ مش مبلغ العملية (والمبلغ الحقيقي من غير عملة ⇒ العملة مش واضحة). */
    @Test fun feesTaxesAndShortReferencesAreNotTheAmount() {
        val unclear = uiText(TextKey.SMS_CURRENCY_UNCLEAR)
        assertEquals(unclear, reason(saudi("Outgoing Transfer\nAmount: 900.00\nSAR 5.75 fee\nTo: SAMI TESTER\n2026-03-05 09:10")))
        assertEquals(unclear, reason(saudi("Online Purchase\nAmount: 64.25\nTax: SAR 8.38\nAt: TEST STORE\n2026-03-05 09:10")))
        assertEquals(unclear, reason(saudi("Purchase\nAmount 64.25\nRef SR4821\nAt TEST STORE\n2026-03-05 09:10")))
        assertEquals(unclear, reason(saudi("Ref SR1234\nPurchase\nAmount 64.25\nAt TEST GROCER\n2026-03-05")))
        assertEquals(unclear, reason(saudi("Purchase\nSR1234\nAmount 64.25\nAt TEST GROCER\n2026-03-05")))
        // مرجع بمسافة (مش لازق) كان بيتسجل 48,213 ريال · ومرجع جنب مبلغ حقيقي بعملته ما بيبقاش «أكتر من مبلغ»
        assertEquals(unclear, reason(saudi("Purchase\nAmount 64.25\nRef SR 48213\nAt TEST STORE\n2026-03-05 09:10")))
        assertEquals(6425L, ok(saudi("Purchase\nAmount: SAR 64.25\nRef SR4821\nAt: TEST STORE\n2026-03-05 09:10"), "ref beside amount").amountMinor)
        // الكود اللازق في المبلغ لوحده لسه مبلغ (ملف المرجع «شراءSR25»)، والرسوم جنب مبلغ بعملته ما بتلخبطش
        assertEquals(2500L, ok(saudi("شراءSR25 2026-03-05"), "glued").amountMinor)
        assertEquals(90000L, ok(saudi("Outgoing Transfer\nAmount: SAR 900.00\nSAR 5.75 fee\nTo: SAMI TESTER\n2026-03-05 09:10"), "fee after").amountMinor)
        assertEquals(6425L, ok(saudi("Online Purchase\nAmount: SAR 64.25\nTax: SAR 8.38\nAt: TEST STORE\n2026-03-05 09:10"), "tax").amountMinor)
    }

    /** فاصل آلاف غريب · كسور أكتر من منزلتين قبل العملة ⇒ مش صالح. ملف المرجع «شراء 1.234 SAR» = 234.00 **بالظبط** وبس. */
    @Test fun oddSeparatorsAndExtraDecimalsBeforeTheCurrencyAreInvalid() {
        val invalid = uiText(TextKey.SMS_AMOUNT_INVALID)
        val bad = listOf("1'234.50 ر.س", "1’234.50 ر.س", "SAR 1'234.50", "12.345 SAR", "1,234.567 SAR", "64.255 ريال", "1.234.50 SAR")
        for (amount in bad) assertEquals(invalid, reason(saudi("شراء\nمبلغ: $amount\nلدى: TEST GROCER\n2026-03-05")), amount)
        assertEquals(invalid, reason(egypt("تم خصم 1'234.50 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 05/03/2026")))
        assertEquals(23400L, ok(saudi("شراء 1.234 SAR 2026-03-05"), "golden-locked").amountMinor)
        assertEquals(6425L, ok(saudi("شراء\nمبلغ: «64.25 SAR»\nلدى: TEST GROCER\n2026-03-05"), "quoted").amountMinor)
    }

    /** «Purchase Cancelled … Refund»: إلغاء ومعاه استرداد ⇒ مش صرف جديد (مش واضح) · «استرداد مبلغ عملية ملغاة» استرداد داخل. */
    @Test fun aCancelledPurchaseWithARefundIsNotANewExpense() {
        assertEquals(uiText(TextKey.SMS_DIRECTION_UNCLEAR), reason(saudi("Purchase Cancelled\nAmount: SAR 64.25\nAt: TEST GROCER\nRefund\n2026-03-05 09:10")))
        val refund = ok(saudi("استرداد مبلغ عملية ملغاة\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: 2026-03-05 09:10"), "refund")
        assertEquals(IN, refund.direction)
        assertEquals(SmsKind.REFUND, refund.kind)
    }

    /** الرصيد بعد آخر عملية في مصر ما بيتقريش مبلغ العملية. */
    @Test fun egyptBalanceAfterLastPurchaseIsNotTheAmount() {
        assertEquals(uiText(TextKey.SMS_AMOUNT_UNCLEAR), reason(egypt("Account 1188: EGP 4,100.00 after last purchase on 05/03/2026")))
    }
}
