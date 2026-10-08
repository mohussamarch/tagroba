package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import app.masroufy.core.SmsKind.CASH_WITHDRAWAL
import app.masroufy.core.SmsKind.PURCHASE
import app.masroufy.core.SmsKind.REFUND
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * الجولة التانية من مراجعة قارئ الرسايل (الملاحظات اللي ما اتصلحتش في الجولة الأولى) — كل حالة كانت بتطلع غلط قبل التصليح.
 * الرسايل مخترعة (المحل NOVA GAMES / TEST GROCER · الكارت 4417/6604 · الأسماء والمبالغ مخترعة).
 */
class SmsReviewRound2Test {
    private fun saudi(body: String, at: String = SMS_RECEIVED_AT) = parseBankSms(smsMessage(body, receivedAt = at), 1)
    private fun egypt(body: String, at: String = SMS_RECEIVED_AT) = parseEgyptBankSms(smsMessage(body, receivedAt = at), 1)

    private fun ok(r: SmsParseResult, name: String): SmsRow = when (r) {
        is SmsParseResult.Ok -> r.row
        is SmsParseResult.Rejected -> throw AssertionError("$name: rejected «${r.reason}»")
    }

    private fun pending(r: SmsParseResult, name: String): SmsForeignPending =
        assertIs<SmsParseResult.Rejected>(r, name).foreign ?: throw AssertionError("$name: no pending details («${(r as SmsParseResult.Rejected).reason}»)")

    private fun reason(r: SmsParseResult) = assertIs<SmsParseResult.Rejected>(r).reason

    /** «120 ريال قطري» كانت 120 ريال سعودي متسجلة · الدينار الكويتي 3 كسور («12.345» كانت 12.34) · الين من غير كسور. */
    @Test fun otherRiyalsAndCurrencyDecimals() {
        val qar = pending(saudi("شراء دولي\nبطاقة:4417;مدى\nمبلغ:120 ريال قطري\nدولة:QA\nلدى:NOVA GAMES\nفي:2026-03-05 09:10"), "QAR")
        assertEquals(SmsForeignAmount("QAR", 12000), qar.foreign)
        assertEquals("NOVA GAMES", qar.merchantName)
        assertNull(qar.localSuggestion)
        val kwd = pending(saudi("شراء دولي\nبطاقة:4417;مدى\nمبلغ:KWD 12.345\nدولة:KW\nلدى:NOVA GAMES\nفي:2026-03-05 09:10"), "KWD")
        assertEquals(SmsForeignAmount("KWD", 12345), kwd.foreign)
        assertEquals(3, kwd.foreign.decimals)
        assertEquals(SmsForeignAmount("BHD", 7125), pending(saudi("VISA Purchase\nVia: *4417\nAmount: BHD 7.125\nFrom: NOVA GAMES\nAt: 2026-03-05 09:10"), "BHD").foreign)
        val egKwd = pending(egypt("Your credit card ending with#4417 was charged for KWD 12.345 at NOVA GAMES on 05/03/26 at 09:10. Card available limit is EGP 38,500.00."), "EG KWD")
        assertEquals(SmsForeignAmount("KWD", 12345), egKwd.foreign)
        // «ريال قطري» في رسالة مصرية مش علامة رسالة سعودية ⇒ بتستنى في مصر (وبس)
        val egQar = "تم خصم 120 ريال قطري من بطاقتك المنتهية بـ 4417 عند NOVA GAMES يوم 05/03/2026"
        assertEquals(SmsForeignAmount("QAR", 12000), pending(egypt(egQar), "EG QAR").foreign)
        assertNull((saudi(egQar) as SmsParseResult.Rejected).foreign)
        assertEquals(0, SmsForeignAmount("JPY", 4500).decimals)
        // «ريال» من غير بلد = ريال سعودي زي ما هو (ملف المرجع)
        assertEquals(12000L, ok(saudi("شراء\nبطاقة:4417;مدى\nمبلغ:120 ريال\nلدى:NOVA GAMES\nفي:2026-03-05 09:10"), "ريال").amountMinor)
        assertNull(parseForeignMinor("12.3456", "KWD"))
        assertNull(parseForeignMinor("64,25", "USD"))
    }

    /** المقابل بالجنيه بين قوسين = اقتراح بس، والدولار اللازق في الرقم («USD15.00») عملة أجنبية برضه. */
    @Test fun egyptianForeignPendingDetails() {
        val glued = pending(egypt("لقد تم رد USD15.00 على بطاقتكم الائتمانية المنتهية بـ# 4417 من NOVA GAMES"), "USD15.00")
        assertEquals(SmsForeignAmount("USD", 1500), glued.foreign)
        assertEquals(IN, glued.direction)
        assertEquals(REFUND, glued.kind)
        val gbp = pending(egypt("Your credit card ending with#4417 was charged for GBP12.00 at NOVA GAMES on 05/03/26 at 09:10. Card available limit is EGP 38,500.00."), "GBP12.00")
        assertEquals(SmsForeignAmount("GBP", 1200), gbp.foreign)
        val parens = pending(egypt("Your credit card ending with#4417 was charged for USD 14.90 (EGP 720.00) at NOVA GAMES on 05/03/26 at 09:10"), "parens")
        assertEquals(72000L, parens.localSuggestion)
    }

    /**
     * كارت مصري اتخصم بالريال (الرسالة فيها حد البطاقة بالجنيه) = عملية أجنبية في مصر، وكارت سعودي اتخصم بالجنيه (رصيده بالريال)
     * = عملية أجنبية في السعودية. وكل رسالة **مستنية في بلد واحد بس** (الصندوق بيتقري بقارئ كل بلد).
     */
    @Test fun aCardChargedInTheOtherCountrysCurrencyWaitsInOneCountryOnly() {
        val egyptianCard = "Your credit card ending with#4417 was charged for SAR 75.00 at NOVA GAMES on 05/03/26 at 09:10. Card available limit is EGP 38,500.00."
        assertEquals(SmsForeignAmount("SAR", 7500), pending(egypt(egyptianCard), "EG card in SAR").foreign)
        assertNull((saudi(egyptianCard) as SmsParseResult.Rejected).foreign)
        val saudiCard = "شراء دولي\nبطاقة:6604;مدى\nمبلغ:EGP 500.00\nدولة:EG\nلدى:NOVA GAMES\nرصيد: SAR 4,100.00\nفي:2026-03-05 09:10"
        assertEquals(SmsForeignAmount("EGP", 50000), pending(saudi(saudiCard), "SA card in EGP").foreign)
        assertNull((egypt(saudiCard) as SmsParseResult.Rejected).foreign)
        // الرسالة السعودية اللي فيها مقابل بالريال ما تستناش في مصر كمان
        val d360 = "International Online Purchase\nAmount: USD 23.40 (SAR 87.50)\nCard: *6604 - VISA (Ecommerce)\nAt: NOVA GAMES\nOn: 05/03/2026 09:10"
        assertEquals(8750L, pending(saudi(d360), "d360").localSuggestion)
        assertNull((egypt(d360) as SmsParseResult.Rejected).foreign)
    }

    /** «ATMOSPHERE LOUNGE» محل مش صرّاف — والصرّاف الحقيقي «NBE ATM0417» لسه سحب كاش (§75-4). */
    @Test fun atmNeedsAWordBoundary() {
        val lounge = ok(egypt("تم خصم 245.00 جم من بطاقة الائتمان رقم 4417 عند ATMOSPHERE LOUNGE يوم 03-05 الساعة 09:10"), "lounge")
        assertEquals(PURCHASE, lounge.kind)
        val arab = ok(egypt("A Trx using Card XXXX4417 from ATMOSPHERE LOUNGE for EGP 245.00 on 05/03/2026 at 09:10 GMT+2. Available balance is EGP 3,412.60."), "arab")
        assertEquals(PURCHASE, arab.kind)
        val atm = ok(egypt("تم خصم 500 EGP من بطاقة الخصم المباشر رقم4417 عندNBE ATM0417 يوم03-05 الساعة09:10 المتاح3,412.60EGP"), "nbe atm")
        assertEquals(CASH_WITHDRAWAL, atm.kind)
    }

    /** «from NADIA M. EXAMPLE.» كان بيتقص «NADIA M» — والاسم من غير حرف مختصر زي ما هو. */
    @Test fun englishCounterpartyNamesKeepTheirInitials() {
        fun party(body: String) = transferPartyOf(smsTransaction(ok(egypt(body), body), Currency.EGP))?.label
        assertEquals("NADIA M. EXAMPLE", party("Your account ending in 3355 has been credited with EGP 5,000.00 on 05/03/2026 from NADIA M. EXAMPLE. Ref: 7788 (IPN Inward Transfer)"))
        assertEquals("NADIA M. EXAMPLE", party("Instant transfer of 640 EGP received from NADIA M. EXAMPLE. Reference: 123456."))
        assertEquals("SAMI TESTER", party("Instant transfer of 640 EGP received from SAMI TESTER. Reference: 123456."))
    }

    /** الرقم من الناحيتين ⇒ أكتر من مبلغ · كود لازق في رقم مرجع مش مبلغ · فوق 100 مليون ⇒ مش صالح · الأرقام العربية بفواصلها صح. */
    @Test fun amountsNextToTheCurrency() {
        assertEquals(uiText(TextKey.SMS_MULTIPLE_AMOUNTS), reason(saudi("Purchase\nCard 6604 SAR 64.25\nAt TEST GROCER\n2026-03-05")))
        assertEquals(uiText(TextKey.SMS_CURRENCY_UNCLEAR), reason(saudi("Ref SR2026030512\nPurchase\nAmount 64.25\nAt TEST GROCER\n2026-03-05")))
        assertEquals(uiText(TextKey.SMS_AMOUNT_INVALID), reason(saudi("شراء\nمبلغ: SAR 200,000,000.00\nلدى: TEST GROCER\n2026-03-05")))
        assertEquals(uiText(TextKey.SMS_AMOUNT_INVALID), reason(saudi("شراء\nمبلغ: SAR 64,25\nلدى: TEST GROCER\n2026-03-05")))
        assertEquals(uiText(TextKey.SMS_AMOUNT_INVALID), reason(egypt("تم خصم 1 234.50 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 05/03/2026")))
        assertEquals(uiText(TextKey.SMS_MULTIPLE_AMOUNTS), reason(egypt("Purchase with debit card 6604 EGP 500.00 at TEST GROCER on 05/03/2026")))
        assertEquals(123450L, ok(saudi("شراء عبر نقاط البيع\nبطاقة: ٦٦٠٤\nمبلغ: ١٬٢٣٤٫٥٠ ر.س\nلدى: TEST GROCER\nفي: ٢٠٢٦-٠٣-٠٥ ٠٩:١٠"), "arabic").amountMinor)
        // الساعة والتاريخ جنب العملة مش رقم تاني
        assertEquals(12500L, ok(saudi("شراء\nلدى: TEST GROCER\nفي: 2026-03-05 09:10 125.00 SAR"), "time").amountMinor)
        // ملف المرجع بيقفل «شراء 1.234 SAR» = 234.00 (سؤال مفتوح للمالك — OVERRIDES §75.1)، ومصر من غير ملف مرجع ⇒ مش صالح
        assertEquals(23400L, ok(saudi("شراء 1.234 SAR 2026-03-05"), "golden-locked").amountMinor)
        assertEquals(uiText(TextKey.SMS_AMOUNT_INVALID), reason(egypt("تم خصم 1.234 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 05/03/2026")))
    }

    /** الرصيد مكتوب اسمه **بعد** المبلغ أو في السطر **اللي قبله** — مش المبلغ. ورسوم الحوالة مش المبلغ لو المبلغ نفسه من غير عملة. */
    @Test fun balanceOrFeeLabelledAfterOrAbove() {
        assertEquals(6425L, ok(saudi("Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05\nSAR 4,100.00 Available"), "after").amountMinor)
        assertEquals(uiText(TextKey.SMS_AMOUNT_UNCLEAR), reason(saudi("Deposit\nSAR 4,100.00 is your available balance\n2026-03-05")))
        assertEquals(uiText(TextKey.SMS_CURRENCY_UNCLEAR), reason(saudi("حوالة صادرة\nالمبلغ: 900.00\nرسوم:\nSAR 5.75\nالى: SAMI TESTER\n2026-03-05 09:10")))
        assertEquals(uiText(TextKey.SMS_AMOUNT_UNCLEAR), reason(saudi("Purchase 64.25 at TEST GROCER 2026-03-05 Bal SAR 4,100.00")))
        assertEquals(90000L, ok(saudi("Outgoing transfer\nAmount: SAR 900.00\nCharges: SAR 5.75\nTo: SAMI TESTER\n2026-03-05 09:10"), "charges").amountMinor)
        // الرصيد قبل المبلغ في نفس الرسالة (ملف المرجع) لسه بيتقري صح
        assertEquals(2500L, ok(saudi("خصم 25 SAR المتاح 1000 SAR 2026-03-05"), "golden").amountMinor)
    }

    /** التاريخ بسنة كاملة بعد الوصول بأكتر من يوم = عرض أو ميعاد سداد، مش عملية. يوم بعد الوصول مقبول (فرق التوقيت). */
    @Test fun fullDatesMustNotBeInTheFuture() {
        val body = { d: String -> "شراء\nمبلغ: SAR 64.25\nلدى: TEST GROCER\nفي: $d" }
        assertEquals(uiText(TextKey.SMS_DATE_UNCLEAR), reason(saudi(body("2027-03-05"))))
        assertEquals(uiText(TextKey.SMS_DATE_UNCLEAR), reason(saudi(body("31/03/2026"))))
        assertEquals("2026-03-06", ok(saudi(body("2026-03-06")), "+1 day").date)
        // أكتر من 60 يوم قبل الوصول: ملف المرجع بيقبله بتاريخه (سؤال مفتوح للمالك — OVERRIDES §75.1)
        assertEquals("2025-12-01", ok(saudi(body("2025-12-01")), "old").date)
        assertEquals(uiText(TextKey.SMS_DATE_UNCLEAR), reason(egypt("Your debit card 6604 was charged EGP 500.00 at TEST GROCER on Mar 25, 2026")))
        assertEquals(SMS_TX_DAY, ok(egypt("Your debit card 6604 was charged EGP 500.00 at TEST GROCER on Mar 5, 2026"), "month name").date)
    }

    /** يوم الوصول للأهلي السعودي بس لما **الشكل كله** موجود (عنوان معروف · «بـ<مبلغ> SAR» · آخر سطر الكارت). */
    @Test fun snbArrivalDayNeedsTheWholeShape() {
        assertEquals(SMS_TX_DAY, ok(saudi("شراء انترنت\nبـ64.25 SAR\nمن TEST GROCER\nمدى-ابل *6604"), "snb").date)
        assertEquals(SMS_TX_DAY, ok(saudi("تصحيح سحب نقدي\nمبلغ 64.25 SAR\nمدى *6604"), "snb correction").date)
        // عنوان مش معروف (من غير كلمة رمز ولا عرض) ⇒ ما ياخدش تاريخ النهارده
        assertEquals(uiText(TextKey.SMS_DATE_UNCLEAR), reason(saudi("رسالة من البنك\nشراء\nبـ64.25 SAR\nمن TEST GROCER\nمدى *6604")))
        assertEquals(uiText(TextKey.SMS_DATE_UNCLEAR), reason(saudi("شراء انترنت\nمن TEST GROCER 64.25 SAR\nمدى *6604")))
    }

    /** «تصحيح» (عنوان داخل) والنص فيه «تم خصم» ⇒ مش واضح · «شيك مرتجع» مش استرداد · «Purchase … was reversed» مش صرف. */
    @Test fun contradictoryDirections() {
        val unclear = uiText(TextKey.SMS_DIRECTION_UNCLEAR)
        assertEquals(unclear, reason(saudi("تصحيح\nتم خصم مبلغ 64.25 SAR من حسابك 1188\nفي: 2026-03-05")))
        assertEquals(unclear, reason(saudi("شيك مرتجع\nالمبلغ: SAR 5,000.00\nحساب: 1188\nفي: 2026-03-05")))
        assertEquals(unclear, reason(saudi("Purchase SAR 64.25 at TEST GROCER 2026-03-05 was reversed due to a technical error")))
        // «تصحيح» من غير خصم لسه استرداد داخل (§75-6)
        assertEquals(IN, ok(saudi("تصحيح\nمبلغ: SAR 64.25\nحساب: 1188\nفي: 2026-03-05"), "correction").direction)
        // «Purchase Reversal» (إس تي سي) لسه استرداد
        assertEquals(REFUND, ok(saudi("Purchase Reversal\nAmount: 64.25 SAR\nFrom: TEST GROCER\nAt: 2026-03-05 09:10"), "stc").kind)
        assertEquals(OUT, ok(saudi("Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\n2026-03-05"), "plain").direction)
    }
}
