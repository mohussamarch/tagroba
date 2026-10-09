package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import app.masroufy.core.SmsRound5Cases.MARCH
import app.masroufy.core.SmsRound5Cases.OCTOBER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الجولة الخامسة (OVERRIDES §72 — «المفهومة بتتسجل لوحدها، والباقي يستنى»؛ ضياع عملية مش مقبول؛ تسجيل غلط أسوأ حاجة): كل رسايل
 * المراجعة العدائية للجولة الرابعة ([SmsRound5Cases] · [SmsRound5Cases2]) **ما بتتسجلش لوحدها في أي بلد**، واللي متناقضة بتترفض
 * (مش تستنى «جاهزة» بمبلغ واتجاه غلط). وجنبها: القراية الصح للحاجات اللي المراجع لقاها بتتقري غلط. كل الرسايل مخترعة.
 */
class SmsRound5Test {
    private val readers = listOf("SA" to ::parseBankSms, "EG" to ::parseEgyptBankSms)

    private fun msg(body: String, at: String) = BankSmsMessage("TESTBANK", at, body)

    private fun sa(body: String, at: String = MARCH) = parseBankSms(msg(body, at), 1)

    private fun eg(body: String, at: String = MARCH) = parseEgyptBankSms(msg(body, at), 1)

    @Test fun noAdversarialMessageIsRecordedAutomaticallyInAnyCountry() {
        val leaks = mutableListOf<String>()
        for ((name, at, body) in SmsRound5Cases.mustNotAutoRecord + SmsRound5Cases2.mustNotAutoRecord) {
            for ((country, parse) in readers) {
                val r = parse(msg(body, at), 1)
                if (r is SmsParseResult.Ok && r.row.shape.clear) leaks += "$name [$country] AUTO ${r.row.amountMinor} ${r.row.direction} ${r.row.date} ${r.row.shape.wire}"
                if (r is SmsParseResult.Ok && name !in SmsRound5Cases2.mayWaitReady) leaks += "$name [$country] waits READY ${r.row.amountMinor} ${r.row.direction}"
            }
        }
        assertTrue(leaks.isEmpty(), leaks.joinToString("\n"))
        assertTrue(SmsRound5Cases.mustNotAutoRecord.size + SmsRound5Cases2.mustNotAutoRecord.size >= 140)
    }

    @Test fun completedKnownShapesStillRecordThemselves() {
        // نفس الأشكال من غير الكلام الزيادة ⇒ لسه بتتسجل لوحدها (القايمة البيضا ما قفلتش الطبيعي)
        val clear = listOf(
            sa("Bill Payment\nAmount: SAR 230.40\nBiller: QAMAR TELECOM\n2026-03-05 10:42"),
            sa("سداد فاتورة\nالمبلغ: 230.40 ر.س\nالمفوتر: QAMAR TELECOM\nالحالة: تمت\n2026-03-05 10:42"),
            sa("شراء\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\nفي: 2026-03-05 11:20\nللاعتراض على العملية اتصل 8001110000"),
            sa("Incoming Transfer: Riyad Bank\nAmount: SAR 1,250.00\nFrom: *7719\nIBAN: *3307\nat: 2026-03-05 11:20"),
            eg("تم خصم 1,250.00 جم من بطاقة الخصم المباشر رقم 5208 عند NOVA MART يوم 03-05 الساعة 09:40 المتاح 3,000.00 جم للمزيد اتصل ب 19623"),
            eg("Your credit card ending with#7788 was charged for EGP 640.00 at SR TOYS on 05/03/26 at 19:30."),
            eg("تم خصم 185.00 جم من بطاقة الخصم المباشر رقم 4455 عند مطعم المعلقة الذهبية يوم 03-05 الساعة 14:30 المتاح 2,115.00 جم"),
        )
        for (r in clear) assertTrue(assertIs<SmsParseResult.Ok>(r).row.shape.clear, r.toString())
    }

    @Test fun walletFeeIsNeverTheAmountAndAReceiveIsNeverReadAsAFee() {
        val vf21 = assertIs<SmsParseResult.Ok>(eg("تم تحويل 300 جنيه لرقم 01000001212 مصاريف الخدمة وخصم 1.50 جنيه من محفظتك. رصيد حسابك في فودافون كاش الحالي 98.50", OCTOBER))
        assertEquals(30000L, vf21.row.amountMinor)
        assertEquals(OUT, vf21.row.direction)
        // «وخصم 10 جنيه من محفظتك» في استلام = مصاريف ⇒ مبلغين ⇒ بتستنى (كانت بتتسجل 10 جنيه دخل)
        assertIs<SmsParseResult.Rejected>(eg("تم استلام مبلغ 2,000 جنيه من رقم 01500006677 رصيدك الحالي 2,390 جنيه وخصم 10 جنيه من محفظتك رسوم استلام رقم العملية 900000124", OCTOBER))
        val b11 = assertIs<SmsParseResult.Ok>(eg("تم استلام مبلغ 750.00 جنيه من رقم 01000000431 المسجل باسم TEST PERSON وخصم 7.50 من محفظتك"))
        assertEquals(75000L, b11.row.amountMinor)
        // الشحن نفسه لسه بياخد المخصوم (الجولة التالتة)
        val recharge = assertIs<SmsParseResult.Ok>(eg("تم شحن رصيد موبايلك ب 50 بنجاح وخصم 57 من محفظتك شاملة الضريبة يوم 05/03/2026"))
        assertEquals(5700L, recharge.row.amountMinor)
    }

    @Test fun egyptAmountMustComeFromTheTemplateSlot() {
        // «TEST LIMIT» قبل المبلغ خلت 300 تتساب والضريبة 42 تبقى المبلغ ⇒ مبلغ القارئ ≠ خانة القالب ⇒ تستنى
        val ab01 = eg("A Trx using Card XXXX6604 from TEST LIMIT FITNESS for EGP 300.00 on 08/10/2026 at 10:00 GMT+2 incl. VAT EGP 42.00. Available balance is EGP 2,000.00.", OCTOBER)
        if (ab01 is SmsParseResult.Ok) assertTrue(!ab01.row.shape.clear)
        assertEquals(SmsShape.KeywordFallback, egyptShape("تم تحويل 500.00 جنيه لرقم 01200009876 رصيدك الحالي 1,200 جنيه", OUT, 150))
        assertEquals(SmsShape.KnownShape("eg-wallet", "send"), egyptShape("تم تحويل 500.00 جنيه لرقم 01200009876 رصيدك الحالي 1,200 جنيه", OUT, 50000))
    }

    @Test fun dayMonthFromAnotherSenderIsNotGuessed() {
        assertIs<SmsParseResult.Rejected>(eg("تم استلام 300 جنيه من رقم 01000001358 رصيدك الحالي 500 جنيه يوم 08-10 الساعة 14:00", "2026-10-08T09:30:00Z"))
        // الأهلي المصري لسه شهر-يوم
        val nbe = assertIs<SmsParseResult.Ok>(eg("تم خصم 300.00 جم من بطاقة الخصم المباشر رقم 4455 عند TEST SHOP يوم 10-08 الساعة 12:40", OCTOBER))
        assertEquals("2026-10-08", nbe.row.date)
    }

    @Test fun currencyWordsInsideMerchantNamesAreNotForeign() {
        for (body in listOf(
            "تم خصم 85.50 جم من بطاقة الخصم المباشر رقم 3344 عند صيدلية يوروفارم يوم 10-08 الساعة 20:00 المتاح 1,500.00 جم",
            "تم خصم 1,250.00 جم من بطاقة الخصم المباشر رقم 5208 عند ريال للعطور يوم 10-08 الساعة 09:40",
            "Your Debit Card **5208 had a Successful transaction of EGP 1,250.00 @SR TECH,your available bal.EGP 3,000.00",
            "Your credit card ending with#7788 was charged for E£ 230.00 at TEST BAKERY on 08/10/26 at 08:30.",
        )) {
            val row = assertIs<SmsParseResult.Ok>(eg(body, OCTOBER), body).row
            assertEquals(OUT, row.direction)
            assertTrue(row.shape.clear, body)
        }
        assertEquals(23000L, (eg("Your credit card ending with#7788 was charged for E£ 230.00 at TEST BAKERY on 08/10/26 at 08:30.", OCTOBER) as SmsParseResult.Ok).row.amountMinor)
    }

    @Test fun foreignPurchasesWaitWithTheForeignAmount() {
        // §75-12: «جنيه إسترليني» في رسالة سعودية = إسترليني (كان «الجنيه = رسالة مصرية» ⇒ من غير تفاصيل)
        val gbp = assertNotNull(assertIs<SmsParseResult.Rejected>(sa("شراء دولي\nمبلغ: 25.00 جنيه إسترليني\nلدى: ORBIT BOOKS\nفي: 2026-03-05 10:42")).foreign)
        assertEquals(SmsForeignAmount("GBP", 2500), gbp.foreign)
        assertNull((eg("شراء دولي\nمبلغ: 25.00 جنيه إسترليني\nلدى: ORBIT BOOKS\nفي: 2026-03-05 10:42") as SmsParseResult.Rejected).foreign, "واحدة بس")
        // كارت مصري اتخصم بالريال على قالب الأهلي
        for (amount in listOf("300.00 SAR", "300.00 ريال سعودي")) {
            val body = "تم خصم $amount من بطاقة الخصم المباشر رقم 5208 عند MAKKAH DATES يوم 03-05 الساعة 09:40"
            val pending = assertNotNull(assertIs<SmsParseResult.Rejected>(eg(body)).foreign, body)
            assertEquals(SmsForeignAmount("SAR", 30000), pending.foreign)
            assertEquals(OUT, pending.direction)
        }
        // اختصار أو رمز ⇒ أجنبي بكوده
        val kd = assertNotNull(assertIs<SmsParseResult.Rejected>(sa("Online Purchase\nAmount: KD 12.500\nAt: ORBIT BOOKS\n2026-03-05 10:42")).foreign)
        assertEquals(SmsForeignAmount("KWD", 12500), kd.foreign)
        val qr = assertNotNull(assertIs<SmsParseResult.Rejected>(sa("PoS International Purchase\nAmount: 61.50 QR (SAR 63.35)\nAt: TEST SOUK\n2026-03-05 11:20")).foreign)
        assertEquals(SmsForeignAmount("QAR", 6150), qr.foreign)
        assertEquals(6335L, qr.localSuggestion)
        val won = assertNotNull(assertIs<SmsParseResult.Rejected>(sa("Online Purchase\nAmount: ₩45,000\nAt: ORBIT BOOKS\n2026-03-05 10:42")).foreign)
        assertEquals(SmsForeignAmount("KRW", 45000), won.foreign)
        // «لدى» محل في رسالة مصرية (كان المحل فاضي في المستني)
        val lada = assertNotNull(assertIs<SmsParseResult.Rejected>(eg("تم خصم 30.00 USD من بطاقة الائتمان رقم 5208 لدى ORBIT BOOKS يوم 03-05")).foreign)
        assertEquals("ORBIT BOOKS", lada.merchantName)
    }

    @Test fun directionFixesAndRequestsAreNotReadyRows() {
        // «تم استلام … لرقم محفظتك» = استلام (كانت بتتقري صرف)
        assertEquals(IN, assertIs<SmsParseResult.Ok>(eg("تم استلام 500 جنيه من رقم 01000001357 لرقم محفظتك 01000009900 رصيدك الحالي 900 جنيه", OCTOBER)).row.direction)
        // «Refund initiated by merchant\nAmount: …» — المحل ما بيعديش السطر
        assertEquals("", saudiMerchantOf("Credit Card Refund\nRefund initiated by merchant\nAmount: SAR 245.60\nOn: 2026-03-05 10:42", SmsKind.REFUND))
    }

    @Test fun invisibleFormatCharactersAreRemovedBeforeTheGuards() {
        assertEquals("مرفوضة", normalizeSmsBody("مرف​وضة"))
        assertEquals(uiText(TextKey.SMS_DECLINED), (sa("PoS Purchase\nAmount: SAR 87.40\nAt: TEST BAKERY\n2026-03-05 11:20\nDecl‌ined") as SmsParseResult.Rejected).reason)
        assertEquals(uiText(TextKey.SMS_SENSITIVE), (sa("شراء انترنت\nOT­P 731905\nمبلغ: SAR 87.40\nلدى: TEST BAKERY\n2026-03-05 11:20") as SmsParseResult.Rejected).reason)
    }

    @Test fun oneTimeCodesInOtherWordingsAreSensitive() {
        for (body in listOf(
            "رمز لمرة واحدة 731905", "Verification No. 731905", "Verification number 731905", "Use 731905 to confirm this payment",
            "رقمك السري المؤقت 731905", "731905 is your code. Do not share it", "لإتمام العملية أدخل الرمز المرسل 731905",
            "الكود بتاعك 4829 عشان تأكد دفع 300 جنيه لـ TEST SHOP. ماتديهوش لحد",
        )) {
            assertEquals(TextKey.SMS_SENSITIVE, SmsVocabulary.ignoreReason(body), body)
        }
        // مش رمز: رقم العملية · كود العملية · المرجع
        for (body in listOf("PoS Purchase\nAmount: SAR 87.40\nTransaction code: 731905", "charged for EGP 87.40. Ref code: 73190", "كود العملية 48213")) {
            assertTrue(SmsVocabulary.ignoreReason(body) != TextKey.SMS_SENSITIVE, body)
        }
        assertTrue(SmsVocabulary.ignoreReason("Bill Payment one time\nAmount: SAR 2457.50\nOn: 2026-03-05 10:42") == null, "عنوان «لمرة واحدة» مش رمز")
    }
}
