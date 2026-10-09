package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import app.masroufy.core.SmsRound7Cases.OCT8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * الجولة السابعة (OVERRIDES §72.4 — المراجعة العدائية التالتة للجولة السادسة): الخانة الحرة بقت **كلمات اسم بس** (`SmsSlotWords.kt`) ·
 * اللابل من غير «:» محصور · كل لابل مرة واحدة · عنوان دي 360 = اسم بنك · علامات قلب الاتجاه بتترفض · الإجمالي المستحق على الداخل ·
 * الأهلي من غير تاريخ وفيه ساعة · التاريخ الأقدم من 60 يوم · محل برّه البلد. ولا رسالة من رسايل المراجع ([SmsRound7Cases] ·
 * [SmsRound7Cases2]) بتتسجل لوحدها في أي بلد. كل الرسايل مخترعة.
 */
class SmsRound7Test {
    private val readers = listOf("SA" to ::parseBankSms, "EG" to ::parseEgyptBankSms)

    private fun msg(body: String, at: String = OCT8) = BankSmsMessage("TESTBANK", at, body)

    private fun sa(body: String, at: String = OCT8) = parseBankSms(msg(body, at), 1)

    private fun eg(body: String, at: String = OCT8) = parseEgyptBankSms(msg(body, at), 1)

    private val all = SmsRound7Cases.mustNotAutoRecord + SmsRound7Cases2.mustNotAutoRecord
    private val byName = all.associate { it.first to it }

    @Test fun noAdversarialMessageIsRecordedAutomaticallyInAnyCountry() {
        val leaks = mutableListOf<String>()
        for ((name, at, body) in all) {
            for ((country, parse) in readers) {
                val r = parse(msg(body, at), 1)
                if (r is SmsParseResult.Ok && r.row.shape.clear) leaks += "$name [$country] AUTO ${r.row.amountMinor} ${r.row.direction} ${r.row.date} ${r.row.shape.wire}"
            }
        }
        assertTrue(leaks.isEmpty(), leaks.joinToString("\n"))
        assertTrue(all.size >= 100, "cases: ${all.size}")
    }

    @Test fun notCompletedHiddenAndInconsistentMessagesAreRejectedNotReady() {
        fun check(names: List<String>, country: String, parse: (BankSmsMessage, Int) -> SmsParseResult) {
            for (name in names) {
                val (_, at, body) = byName.getValue(name)
                assertIs<SmsParseResult.Rejected>(parse(msg(body, at), 1), "$name [$country]: $body")
            }
        }
        check(SmsRound7Cases.saudiMustReject + SmsRound7Cases2.saudiMustReject, "SA", ::parseBankSms)
        check(SmsRound7Cases2.egyptMustReject, "EG", ::parseEgyptBankSms)
        // علامة قلب الاتجاه: السبب صريح
        val hidden = uiText(TextKey.SMS_HIDDEN_TEXT)
        assertEquals(hidden, (sa(byName.getValue("J01").third) as SmsParseResult.Rejected).reason)
        assertEquals(hidden, (eg(byName.getValue("EG07").third) as SmsParseResult.Rejected).reason)
    }

    /** المراجع قال إن دول سليمين — لازم يفضلوا بيتسجلوا لوحدهم (مش أي حاجة بقت تستنى). */
    @Test fun controlsStillRecordThemselves() {
        val clear = listOf(
            "Incoming Transfer: Al Rajhi Bank\nAmount: SAR 500.00\nFrom: RAYAN EXAMPLE\nOn: 2026-10-07 11:40",
            "PoS Purchase\nAmount: SAR 48.60\nAt: ⁨QUOLL BAKERY⁩\nOn: 2026-10-07 18:22", // العزل (isolates) مش بيقلب حاجة
            "PoS Purchase\nAmount: SAR 48.‏60\nAt: QUOLL BAKERY\nOn: 2026-10-07 18:22", // RLM جوه الرقم
            // أسامي ومحلات فيها كلمات قريبة من كلمات الحالة (مش منها)
            "PoS Purchase\nAmount: SAR 48.60\nAt: THE BODY SHOP\nOn: 2026-10-07 18:22",
            "PoS Purchase\nAmount: SAR 48.60\nAt: NEXT RIYADH PARK\nOn: 2026-10-07 18:22",
            "PoS Purchase\nAmount: SAR 48.60\nAt: DAYS INN OLAYA\nOn: 2026-10-07 18:22",
            "شراء عبر نقاط البيع\nمبلغ: 48.60 ر.س\nلدى: تموينات الوعل\nفي: 2026-10-07 18:22",
            "شراء عبر نقاط البيع\nمبلغ: 48.60 ر.س\nلدى: SAMPLE MALL RIYADH SA\nفي: 2026-10-07 18:22",
            "حوالة واردة\nالمبلغ: 980.00 ر.س\nمن: هيثم يوسف التجريبي\nفي: 2026-10-07",
            "حوالة صادرة محلية\nالمبلغ: 980.00 ر.س\nالى: سيف تامر التجريبي\nفي: 2026-10-07",
            "Bill Payment\nAmount: SAR 230.00\nBiller: SAMPLE POWER CO\nService: Electricity bill\nOn: 2026-10-07 09:00",
            "سداد فاتورة\nمبلغ: 230.00 ر.س\nالجهة: شركة الكهرباء التجريبية\nالفاتورة: 30012345678\nفي: 2026-10-07",
            "ايداع دعم حكومي - حساب المواطن\nمبلغ: 1,200.00 ر.س\nحساب المواطن\nفي: 2026-10-07",
            // نفس اللابل مرتين: اسم + رقم حساب (الراجحي #19) — مش عمليتين
            "حوالة داخلية صادرة\nمن:**3307\nمبلغ:SAR 1,250.00\nالى:خالد عمر المختبر\nالى:**7719\nفي:26-10-07 11:40",
        )
        for (body in clear) {
            val row = assertIs<SmsParseResult.Ok>(sa(body), body).row
            assertTrue(row.shape.clear, "${row.shape.wire}: $body")
        }
        val egClear = listOf(
            "لقد تم رد EGP75.50 على بطاقتكم الائتمانية المنتهية بـ# 4417 من PROBE BOOKS",
            "تم استلام مبلغ 900 جنيه من رقم 01000007301 المسجل باسم هيثم سيف التجريبي",
            "Your account ending in 4417 has been credited with EGP 3,200.00 on 08/10/2026 from NOHA PROBE.",
            "تم خصم 350.00 جم من بطاقة الائتمان رقم 5172 عند QUOLL BAKERY CAIRO EG يوم 10-07 الساعة 18:22",
        )
        for (body in egClear) {
            val row = assertIs<SmsParseResult.Ok>(eg(body), body).row
            assertTrue(row.shape.clear, "${row.shape.wire}: $body")
        }
    }

    /**
     * كل حصر **لوحده** — رسايل مفيهاش ولا كلمة حالة ولا كلمة «مش اسم» (قايمة الكلمات مش هي اللي بتمسكها): لو الحصر اتشال الرسالة
     * كانت هتتسجل لوحدها (التحوير بيتأكد).
     */
    @Test fun eachStructuralLimitHoldsOnItsOwn() {
        // ملاحظة «دعم حكومي» مش من القايمة
        waits(sa("ايداع دعم حكومي - حساب المواطن\nمبلغ: 1,200.00 ر.س\nدفعة تجريبية للنظام\nفي: 2026-10-07"))
        // بعد نقطتين عنوان دي 360: اسم مش بنك
        waits(sa("Incoming Transfer: Sunrise Holdings\nAmount: SAR 500.00\nFrom: RAYAN EXAMPLE\nOn: 2026-10-07 11:40"))
        // «لقد تم رد … من <المحل>» وبعده جملة عربي مفيهاش كلمة من القايمة
        waits(eg("لقد تم رد EGP260.00 على بطاقتكم الائتمانية المنتهية بـ# 4417 من PROBE STORE ونشكركم لاختياركم بنكنا"))
        // رقم مرجع لازق فيه حروف بعد الأرقام («RTN» = returned)
        waits(eg("تم اضافة تحويل لحظي لحسابكم بمبلغ 1,500 جم من ليان التجريبية رقم مرجعي 778812RTN يوم 10-07"))
        // الأصل بتاعهم بيتسجل لوحده
        assertTrue(assertIs<SmsParseResult.Ok>(eg("لقد تم رد EGP260.00 على بطاقتكم الائتمانية المنتهية بـ# 4417 من PROBE STORE")).row.shape.clear)
        assertTrue(assertIs<SmsParseResult.Ok>(eg("تم اضافة تحويل لحظي لحسابكم بمبلغ 1,500 جم من ليان التجريبية رقم مرجعي 778812 يوم 10-07")).row.shape.clear)
    }

    private fun waits(r: SmsParseResult) {
        assertTrue(r !is SmsParseResult.Ok || !r.row.shape.clear, "recorded: ${(r as SmsParseResult.Ok).row.raw}")
    }

    @Test fun aTotalDueLineOnAnIncomingTitleIsNeverTheAmount() {
        // الداخل: الإجمالي = المبلغ نفسه بس ⇒ المبلغ (من غير رسوم)
        assertEquals(100_000L, assertIs<SmsParseResult.Ok>(sa("Received transfer\nAmount: SAR 1,000.00\nTotal due amount: SAR 1,000.00\nFrom: RAYAN EXAMPLE\nOn: 2026-10-07 11:40")).row.amountMinor)
        // الصادر: لسه الإجمالي = المبلغ + الرسوم (إس تي سي)
        assertEquals(100_500L, assertIs<SmsParseResult.Ok>(sa("Online Purchase\nAmount: SAR 1,000.00\nFees: SAR 5.00\nTotal due amount: SAR 1,005.00\nAt: QUOLL BAKERY\nOn: 2026-10-07 11:40")).row.amountMinor)
    }

    @Test fun oldAndTimeOnlyDatesKeepTheirReadingButWait() {
        // القراية زي ما هي (ملف المرجع — سؤال (و) مفتوح)، بس ما بتتسجلش لوحدها
        assertEquals("2026-07-10", assertIs<SmsParseResult.Ok>(sa(byName.getValue("D09").third)).row.date)
        assertEquals("2025-11-02", assertIs<SmsParseResult.Ok>(sa(byName.getValue("D10").third)).row.date)
        val h07 = assertIs<SmsParseResult.Ok>(sa(byName.getValue("H07").third, SmsRound7Cases.AFTER_MIDNIGHT)).row
        assertEquals("2026-10-08", h07.date)
        assertFalse(h07.shape.clear)
        // نفس رسالة الأهلي من غير ساعة ⇒ لسه بتتسجل لوحدها بيوم الوصول
        assertTrue(assertIs<SmsParseResult.Ok>(sa("شراء نقاط بيع\nبـ48.60 SAR\nمن QUOLL BAKERY\nمدى *3906", SmsRound7Cases.AFTER_MIDNIGHT)).row.shape.clear)
    }

    @Test fun approvalCodesAreNotOneTimeCodesButNewCodeWordingsAre() {
        val approval = listOf("SR14", "SR27", "M02", "M03", "M04").map { byName.getValue(it).third } + listOf(
            "تمت عملية شراء بمبلغ 212.00 ر.س لدى SAFA OPTICS ببطاقة مدى *7739 في 2026-10-08 10:12، رمز الموافقة 553120",
            "Purchase SAR 48.50 at BUSTAN CAFE card *7739 2026-10-08 11:42 Appr Code 553120",
        )
        for (body in approval) assertFalse(isSensitiveText(guardText(normalizeSmsBody(body))), body)
        val codes = listOf(
            byName.getValue("SN08").third, byName.getValue("SN07").third,
            "شراء إنترنت\nمبلغ: 349.00 ر.س\nلدى: SAFA OPTICS\nلإتمام العملية أدخل: 551204",
            "Your mada Pay registration code for card *7739 is 551204. A SAR 1.00 verification charge may apply",
            "Your transaction PIN for InstaPay transfer of EGP 3,000.00 to MARIO SAMPLE is 553320",
        )
        for (body in codes) assertTrue(isSensitiveText(guardText(normalizeSmsBody(body))) && hasFreeCode(guardText(normalizeSmsBody(body))), body)
    }

    @Test fun foreignPurchasesWaitWithTheRightForeignAmount() {
        fun pending(r: SmsParseResult) = assertNotNull(assertIs<SmsParseResult.Rejected>(r).foreign, r.toString())
        // «Rp 150.000» = 150,000 روبية (كانت 150.00)
        val sf02 = pending(sa("Online Purchase\nAmount: Rp 150.000\nAt: SAMPLE BALI SHOP\nOn: 2026-10-08 10:00\nTotal due amount: SAR 37.80"))
        assertEquals(SmsForeignAmount("IDR", 15_000_000), sf02.foreign)
        assertEquals(3780L, sf02.localSuggestion)
        // اسم العملة بالعربي في قالب الأهلي المصري ⇒ «يوم 10-08» شهر-يوم والمبلغ الأجنبي معاها
        val ef02 = pending(eg("تم خصم 35.00 يورو من بطاقة الائتمان رقم 3390 عند SAMPLE ROMA يوم 10-08 الساعة 15:00"))
        assertEquals(SmsForeignAmount("EUR", 3500), ef02.foreign)
        assertEquals("2026-10-08", ef02.date)
        assertEquals(OUT, ef02.direction)
        val ef07 = pending(eg("تم خصم 1,250.00 ليرة تركية من بطاقة الائتمان رقم 3390 عند SAMPLE ISTANBUL يوم 10-08 الساعة 15:00"))
        assertEquals(SmsForeignAmount("TRY", 125_000), ef07.foreign)
        // كارت مصري اتخصم بالريال: مصر بس (السعودية بترفضها — قالب بنك مصري)
        val ef08 = byName.getValue("EF08").third
        assertEquals(SmsForeignAmount("SAR", 30_000), pending(eg(ef08)).foreign)
        assertEquals(uiText(TextKey.SMS_OTHER_COUNTRY), (sa(ef08) as SmsParseResult.Rejected).reason)
    }

    @Test fun aVodafoneRechargeWithoutACurrencyWordIsReadFromItsTemplate() {
        val row = assertIs<SmsParseResult.Ok>(
            eg("تم شحن رصيد موبايلك ب 30 بنجاح وخصم 34.20 من محفظتك شاملة الضريبة. رصيد حسابك في فودافون كاش الحالي 465.80"),
        ).row
        assertEquals(3420L, row.amountMinor)
        assertEquals(OUT, row.direction)
        assertEquals(SmsShape.KnownShape("vodafone-cash", "recharge-ar"), row.shape)
    }

    /** صيغ محافظ مصرية شائعة كانت بتستنى بسبب غلط («الاتجاه مش واضح» · «المبلغ مش واضح») — دلوقتي بتتقري (وبتستنى بسببها الصح). */
    @Test fun commonWalletWordingsAreRead() {
        val cases = listOf(
            Triple("وصلتك 450 ج.م من 01000007301 على محفظتك", 45_000L, IN),
            Triple("You sent EGP 820.00 to ZIAD PROBE via InstaPay on 08/10/2026", 82_000L, OUT),
            Triple("e& money: Transfer of EGP 500.00 to 01000007309 successful", 50_000L, OUT),
            Triple("WE Pay: تم دفع فاتورة الانترنت بمبلغ 187.50 جنيه بنجاح", 18_750L, OUT),
            Triple("Fawry: Your payment of EGP 312.40 for SAMPLE INTERNET completed successfully", 31_240L, OUT),
            Triple("أورانج كاش: دفعت 95 جنيه لـ PROBE KIOSK", 9_500L, OUT),
            Triple("e& money: Cash in of EGP 800.00 at PROBE AGENT successful", 80_000L, IN),
            Triple("valU: تم سداد القسط بمبلغ 550 جنيه بنجاح", 55_000L, OUT),
        )
        for ((body, amount, direction) in cases) {
            val row = assertIs<SmsParseResult.Ok>(eg(body), body).row
            assertEquals(amount, row.amountMinor, body)
            assertEquals(direction, row.direction, body)
            assertFalse(row.shape.clear, "${row.shape.wire}: $body") // مش قالب معروف ⇒ بتستنى تأكيد المالك
        }
    }

    @Test fun slotWordsAreClosedClassNotNames() {
        for (name in listOf("هيثم يوسف", "سيف الدين", "تامر التجريبي", "تموينات الوعل", "THE BODY SHOP", "NEXT", "DAYS INN", "UNDER ARMOUR", "SAMPLE.COM", "AL-OTHAIM")) {
            assertTrue(slotWordsOk(name), name)
        }
        for (phrase in listOf(
            "wasn't completed", "on queue", "sent for approval", "card check", "new bill issued", "OKAPI FUEL 14 ESTIMATED", "INCREMENTAL AUTH",
            "محتاج موافقتك", "هيوصلك خلال ساعة", "سترجع", "واتحجزت", "بمجرد استلام المبلغ", "مبلغ تقديري", "غير متاح للصرف", "يضاف المبلغ",
        )) {
            assertFalse(slotWordsOk(phrase), phrase)
        }
        assertTrue(hasShapeDoubt("رقم مرجعي 778812REVERSED"), "كلمة الحالة اللازقة في رقم")
    }

    @Test fun aMerchantCountryOtherThanTheReadersIsAbroad() {
        assertTrue(foreignCountryTail("SAMPLE MALL DUBAI AE", SAUDI_TAIL))
        assertTrue(foreignCountryTail("SAMPLE.COM LONDON GBR", SAUDI_TAIL))
        assertTrue(foreignCountryTail("SAMPLE CLOUD USA", EGYPT_TAIL))
        assertFalse(foreignCountryTail("SAMPLE MALL RIYADH SA", SAUDI_TAIL))
        assertFalse(foreignCountryTail("QUOLL BAKERY CAIRO EG", EGYPT_TAIL))
        assertFalse(foreignCountryTail("SAMPLE POWER CO", SAUDI_TAIL), "CO = شركة مش كولومبيا")
        assertFalse(foreignCountryTail("US", SAUDI_TAIL), "اسم من كلمة واحدة")
        assertFalse(foreignCountryTail("Sample Store us", SAUDI_TAIL), "حروف صغيرة مش كود")
        assertTrue(foreignCountryTail("SAMPLE MALL CAIRO EG", SAUDI_TAIL))
    }

    @Test fun refundsAndCashWithdrawalsKeepTheirKindForTheWaitingStep() {
        // القارئ بيعلّم النوع (الشكل معروف) — الانتظار نفسه في `ReviewSmsInbox` (§75-6 · §75-4)
        val refund = assertIs<SmsParseResult.Ok>(sa("استرداد شراء\nبطاقة: 7739*;مدى\nمبلغ: 89.00 ر.س\nمن: SAFA OPTICS\nفي: 26-10-08 16:02")).row
        assertEquals(SmsKind.REFUND, refund.kind)
        assertEquals(IN, refund.direction)
        val atm = assertIs<SmsParseResult.Ok>(sa("ATM Withdrawal\nCard: *7739\nAmount: SAR 500.00\nAt: OLAYA ATM RIYADH\nOn: 2026-10-08 10:00")).row
        assertEquals(SmsKind.CASH_WITHDRAWAL, atm.kind)
    }
}
