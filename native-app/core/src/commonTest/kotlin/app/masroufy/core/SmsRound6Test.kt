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
 * الجولة السادسة (OVERRIDES §72.3 — مراجعة عدائية تانية للجولة الخامسة): الخانات الحرة جوه «الشكل المعروف» (سطر التحذير · المحل ·
 * الاسم · ذيل الكارت والحساب · ذيل فودافون كاش · «.+») بقت **محصورة** بدل ما تتفحص بقايمة كلمات بس. ولا رسالة من رسايل المراجع
 * ([SmsRound6Cases] · [SmsRound6Cases2]) بتتسجل لوحدها في أي بلد. وجنبها القراية الصح اللي المراجع لقاها غلط. كل الرسايل مخترعة.
 */
class SmsRound6Test {
    private val readers = listOf("SA" to ::parseBankSms, "EG" to ::parseEgyptBankSms)

    private fun msg(body: String, at: String) = BankSmsMessage("TESTBANK", at, body)

    private fun sa(body: String, at: String = MARCH) = parseBankSms(msg(body, at), 1)

    private fun eg(body: String, at: String = MARCH) = parseEgyptBankSms(msg(body, at), 1)

    private val all = SmsRound6Cases.mustNotAutoRecord + SmsRound6Cases2.mustNotAutoRecord + SmsRound6Cases2.mustReject

    @Test fun noAdversarialMessageIsRecordedAutomaticallyInAnyCountry() {
        val leaks = mutableListOf<String>()
        for ((name, at, body) in all) {
            for ((country, parse) in readers) {
                val r = parse(msg(body, at), 1)
                if (r is SmsParseResult.Ok && r.row.shape.clear) leaks += "$name [$country] AUTO ${r.row.amountMinor} ${r.row.direction} ${r.row.date} ${r.row.shape.wire}"
            }
        }
        assertTrue(leaks.isEmpty(), leaks.joinToString("\n"))
        assertTrue(all.size >= 110, "cases: ${all.size}")
    }

    @Test fun inconsistentTotalsHiddenStatesAndLostCreditsAreRejected() {
        val byName = all.associate { it.first to it }
        val names = SmsRound6Cases.mustReject + SmsRound6Cases2.mustReject.map { it.first }
        for (name in names) {
            val (_, at, body) = byName.getValue(name)
            for ((country, parse) in readers) assertIs<SmsParseResult.Rejected>(parse(msg(body, at), 1), "$name [$country]: $body")
        }
    }

    @Test fun aConsistentTotalIsStillTheAmountAndRecordsItself() {
        // إس تي سي #90 داخل البلد: الإجمالي = المبلغ + الضريبة + الرسوم ⇒ هو المخصوم فعلًا (87.50 + 0.86 + 5.75 = 94.11)
        val row = assertIs<SmsParseResult.Ok>(
            sa("Online Purchase\nVia: *8063,Visa\nAmount: 87.50 SAR\nFrom: NAJM BOOKS\nVAT: 0.86 SAR\nFees: 5.75 SAR\nTotal due amount: 94.11 SAR\nCountry: SA\nAt: 2026-03-05 10:00"),
        ).row
        assertEquals(9411L, row.amountMinor)
        assertTrue(row.shape.clear, row.shape.wire)
        // من غير رسوم: الإجمالي = المبلغ ⇒ نفس القيمة
        assertEquals(25000L, assertIs<SmsParseResult.Ok>(sa("Online Purchase\nAmount: SAR 250.00\nAt: NAJM BOOKS\nTotal due amount: SAR 250.00\nOn: 2026-03-05 11:05")).row.amountMinor)
    }

    @Test fun theGoldenDotAmountKeepsItsReadingButWaits() {
        // ملف المرجع: «1.234 SAR» = 234.00 (سؤال (ز) مفتوح) — القراية زي ما هي، بس ما بتتسجلش لوحدها
        val row = assertIs<SmsParseResult.Ok>(sa("شراء عبر نقاط البيع\nبطاقة: 8063*;مدى\nمبلغ: 1.250 SAR\nلدى: WADI FURNITURE\nفي: 2026-03-05 11:42")).row
        assertEquals(25000L, row.amountMinor)
        assertTrue(!row.shape.clear)
        // المبلغ في سطر العنوان (الإنماء «شراء انترنت <مبلغ>») — باقي السطور معروفة، فالعلامة نفسها هي اللي بتخليها تستنى
        val title = assertIs<SmsParseResult.Ok>(sa("شراء انترنت 1.250 SAR\nلدى: WADI FURNITURE\nفي: 2026-03-05 11:42")).row
        assertEquals(25000L, title.amountMinor)
        assertTrue(!title.shape.clear, title.shape.wire)
        assertTrue(assertIs<SmsParseResult.Ok>(sa("شراء انترنت 250.00 SAR\nلدى: WADI FURNITURE\nفي: 2026-03-05 11:42")).row.shape.clear)
    }

    @Test fun yearlessVodafoneDatesAreReadFromTheirSlot() {
        val byName = SmsRound6Cases2.mustNotAutoRecord.associate { it.first to it }
        for ((name, day) in SmsRound6Cases2.partialDates) {
            val (_, at, body) = byName.getValue(name)
            assertEquals(day, assertIs<SmsParseResult.Ok>(eg(body, at), name).row.date, name)
        }
        // نفس الشكل والساعة بعد التاريخ (كان مقروء صح قبل كده)
        val b13 = eg("تم سحب 300 ج بنجاح. رصيد حسابك في فودافون كاش الحالي 700 جنيه. تاريخ العملية: 07/10 23:50 رقم العملية: 900000613", OCTOBER)
        assertEquals("2026-10-07", assertIs<SmsParseResult.Ok>(b13).row.date)
    }

    @Test fun aReturnedIpnTransferIsBookedOnTheDayTheMoneyCameBack() {
        for ((body, at, day) in listOf(
            Triple("IPN transfer dated 03/03/2026 with EGP 2,000.00 returned with ref# 55120. For info call 19123", MARCH, "2026-03-05"),
            Triple("IPN transfer dated 14/02/2026 with EGP 3,250.00 returned with ref# 55770. For info call 19123", MARCH, "2026-03-05"),
            Triple("IPN transfer dated 01/10/2026 with EGP 800.00 returned with Ref# 900000380. For info call 19888", OCTOBER, "2026-10-08"),
        )) {
            val row = assertIs<SmsParseResult.Ok>(eg(body, at), body).row
            assertEquals(day, row.date, body)
            assertEquals(IN, row.direction)
            assertEquals(SmsShape.KnownShape("kfh", "ipn-returned"), row.shape)
        }
    }

    @Test fun directionWordsWithoutHamzaAndReferencesAreReadRight() {
        val eg07 = assertIs<SmsParseResult.Ok>(eg("ايداع تحويل لحظي IPN بمبلغ 2,750.00 جم بحسابك رقم 7392 من KARIM SAMPLE مرجع 99120")).row
        assertEquals(IN, eg07.direction)
        assertEquals(275000L, eg07.amountMinor)
        assertEquals(SmsShape.KnownShape("banquemisr", "transfer-in-instapay"), eg07.shape)
        assertEquals(IN, assertIs<SmsParseResult.Ok>(eg("ايداع تحويل لحظي IPN بمبلغ 850 جنيه بحسابك رقم 5521 من سيف التجريبي مرجع 900000376", OCTOBER)).row.direction)
        val b21 = "يرجي العلم انه تم تنفيذ تحويل لحظي بمبلغ 1,300 جم الي حسابك المنتهي ب 4410 من وليد التجريبي برقم مرجعي 900000621 بتاريخ 08/10/2026 13:20"
        assertEquals(IN, assertIs<SmsParseResult.Ok>(eg(b21, OCTOBER)).row.direction)
        val b14 = assertIs<SmsParseResult.Ok>(eg("2026-10-08 13:41: Received EGP 275.00 from 01000007614 to Mobile Account Number 01000009900. Ref: SR4471", OCTOBER)).row
        assertEquals(27500L, b14.amountMinor)
        assertEquals(IN, b14.direction)
    }

    @Test fun foreignPurchasesInMoreCurrenciesWaitWithTheForeignAmount() {
        val cases = listOf(
            Triple("SA", "شراء دولي\nبطاقة: 8063;مدى\nمبلغ: 1,500.00 بات\nدولة: TH\nلدى: SAMPLE BANGKOK\nفي: 26-03-05 10:00", SmsForeignAmount("THB", 150000)),
            Triple("SA", "شراء إنترنت\nمبلغ: 45,000 وون\nلدى: SAMPLE SEOUL\nفي: 2026-03-05 10:00", SmsForeignAmount("KRW", 45000)),
            Triple("SA", "شراء إنترنت\nمبلغ: 850.00 بيزو مكسيكي\nلدى: SAMPLE CANCUN\nفي: 2026-03-05 10:00", SmsForeignAmount("MXN", 85000)),
            Triple("SA", "Online Purchase\nAmount: 12,500 Ft\nAt: SAMPLE BUDAPEST\nOn: 2026-03-05 10:00", SmsForeignAmount("HUF", 1250000)),
            Triple("SA", "شراء دولي\nبطاقة: 8063;مدى\nمبلغ: 9.50 ريال عُماني\nدولة: OM\nلدى: SAMPLE MUSCAT\nفي: 26-03-05 10:00", SmsForeignAmount("OMR", 9500)),
            Triple("EG", "تم خصم 1,500.00 بات من بطاقة الائتمان رقم 7392 عند SAMPLE BANGKOK يوم 03-05", SmsForeignAmount("THB", 150000)),
            Triple("EG", "Your credit card ending with #7392 was charged for 150,000 L.L at SAMPLE BEIRUT on 05/03/26", SmsForeignAmount("LBP", 15000000)),
            Triple("EG", "تم خصم 9,000.00 جنيه سوداني من بطاقة الائتمان رقم 7392 عند SAMPLE KHARTOUM يوم 03-05", SmsForeignAmount("SDG", 900000)),
        )
        for ((country, body, expected) in cases) {
            val own = if (country == "SA") sa(body) else eg(body)
            val pending = assertNotNull(assertIs<SmsParseResult.Rejected>(own, body).foreign, "[$country] no pending: $body")
            assertEquals(expected, pending.foreign, body)
            assertEquals(OUT, pending.direction, body)
            val other = if (country == "SA") eg(body) else sa(body)
            assertTrue(other is SmsParseResult.Rejected && other.foreign == null, "other lane: $other — $body")
        }
        // «كرونة» لوحدها (سويدية؟ نرويجية؟) ⇒ أجنبي من غير كود · «19,99 €» فاصلة عشرية (اختيار الجولة التانية: ما بتتقريش) ⇒ بتستنى
        // من غير تفاصيل (ما بنخمّنش) — بس ما بتتسجلش في أي بلد
        for (body in listOf("شراء إنترنت\nمبلغ: 320.00 كرونة\nلدى: SAMPLE OSLO\nفي: 2026-03-05 10:00", "Online Purchase\nAmount: 19,99 €\nAt: SAMPLE BERLIN\nOn: 2026-03-05 10:00")) {
            for (r in listOf(sa(body), eg(body))) assertNull(assertIs<SmsParseResult.Rejected>(r, body).foreign, body)
        }
        // رمز عملة (₾) جوه خانة المحل أو جنب المقابل
        val s60 = assertNotNull(assertIs<SmsParseResult.Rejected>(sa("Online Purchase\nAmount: SAR 73.15\nAt: TBILISI MARKET 52.00 ₾\n2026-10-08 09:15", OCTOBER)).foreign)
        assertEquals(SmsForeignAmount("GEL", 5200), s60.foreign)
        val s61 = assertNotNull(assertIs<SmsParseResult.Rejected>(sa("Online Purchase\nAmount: 52.00 ₾ (SAR 73.15)\nAt: ZEPHYR KIOSK\n2026-10-08 09:15", OCTOBER)).foreign)
        assertEquals(SmsForeignAmount("GEL", 5200), s61.foreign)
        assertEquals(7315L, s61.localSuggestion)
        // كارت محفظة اتخصم بالدولار («You paid» فعل صرف)
        val x01 = assertNotNull(assertIs<SmsParseResult.Rejected>(eg("You paid USD 12.99 to TEST STREAMING using your Vodafone Cash card on 08/10/2026. Equivalent EGP 640.50", OCTOBER)).foreign)
        assertEquals(SmsForeignAmount("USD", 1299), x01.foreign)
        assertEquals(64050L, x01.localSuggestion)
    }

    @Test fun oneTimeCodesInNewWordingsAreSensitive() {
        for (body in listOf(
            "رقم التعريف المؤقت 662190 لعملية شراء بمبلغ 230.00 ر.س لدى NAJM BOOKS. لا تشاركه",
            "Online Purchase\nAmount: SAR 649.00\nAt: NAJM ELECTRONICS\nApproval PIN: 553901\nOn: 2026-03-05 11:05",
            "شراء إنترنت\nمبلغ: 649.00 ر.س\nلدى: NAJM ELECTRONICS\nرقم التعريف المؤقت: 553901\nفي: 2026-03-05 11:05",
            "mada Pay activation number 662190 for card *8063. A SAR 1.00 test charge applies",
            "Secure code for EGP 1,180.00 at SAMPLE MALL: 662190. Valid 5 minutes",
            "رقم التعريف المؤقت 662190 لإتمام دفع 1,180.00 جم لـ SAMPLE MALL",
        )) {
            assertEquals(TextKey.SMS_SENSITIVE, SmsVocabulary.ignoreReason(body), body)
            assertTrue(SmsVocabulary.ignoreBeforeStorage(body), body)
        }
        // «Approval code» لسه رقم موافقة في رسالة شراء حقيقية (مش رمز)
        assertTrue(SmsVocabulary.ignoreReason("Purchase\nAmount: SAR 64.25\nAt: TEST GROCER\nApproval code: 482913\n2026-03-05 09:10") == null)
    }

    @Test fun aSecurityWarningWithoutACodeIsNotAnOtp() {
        for (body in listOf(
            "PoS Purchase\nAmount: SAR 73.15\nAt: ZEPHYR KIOSK\n2026-10-08 09:15\nNever share your OTP with anyone",
            "شراء عبر نقاط البيع\nمبلغ: 73.15 ر.س\nلدى: كشك زفير\nفي: 2026-10-08 09:15\nلا تشارك رمز التحقق مع أي شخص",
            "تم خصم 412.60 جم من بطاقة الخصم المباشر رقم 4417 عند كشك زفير يوم 10-08 الساعة 09:15. لا تشارك الرقم السري مع أحد",
            "تم تحويل 3,000 جنيه بنجاح إلى حساب ينتهي بـ 9921 عبر انستاباي يوم 08/10/2026. لا تشارك الرقم السري الخاص بالتطبيق",
        )) {
            assertTrue(SmsVocabulary.ignoreReason(body) != TextKey.SMS_SENSITIVE, body)
            assertTrue(!SmsVocabulary.ignoreBeforeStorage(body), body)
        }
        // تحت عنوان موحّد بسطر تحذير معروف ⇒ بتتسجل لوحدها
        val s63 = assertIs<SmsParseResult.Ok>(sa("PoS Purchase\nAmount: SAR 73.15\nAt: ZEPHYR KIOSK\n2026-10-08 09:15\nNever share your OTP with anyone", OCTOBER)).row
        assertTrue(s63.shape.clear, s63.shape.wire)
        // التحذير نفسه فيه رمز ⇒ رمز
        assertEquals(TextKey.SMS_SENSITIVE, SmsVocabulary.ignoreReason("Online Purchase\nAmount: SAR 87.40\nDo not share the code 731905 with anyone\n2026-03-05"))
    }

    /**
     * **الحصر نفسه** بيمسك حالات قايمة الكلمات ما تعرفهاش (كل رسالة هنا مفيهاش ولا كلمة من [SHAPE_DOUBT] — مخترعة): جملة زيادة في اسم
     * المحل · اسم أطول من 8 كلمات · تحذير مش من القايمة · ذيل كارت أو حساب مش معروف · سطر بنك مش اسم بنك · تحية فيها كلام مش اسم ·
     * ذيل فودافون أو اسم مصري فيه حرف جر. لازم تتقري وتستنى، مش تتسجل لوحدها.
     */
    @Test fun theStructuralLimitsCatchStatesTheWordListDoesNotKnow() {
        val saudi = listOf(
            "PoS Purchase\nAmount: SAR 400.00\nAt: QASR HOTEL (ROOM DEPOSIT)\nOn: 2026-03-05 11:00",
            "شراء عبر نقاط البيع\nمبلغ: 250.00 ر.س\nلدى: WADI FUEL - قيمة تقديرية\nفي: 2026-03-05 07:40",
            "PoS Purchase\nAmount: SAR 400.00\nAt: QASR HOTEL FRONT DESK FINAL BILL WILL FOLLOW AT CHECKOUT\nOn: 2026-03-05 11:00",
            "شراء عبر نقاط البيع\nمبلغ: 250.00 ر.س\nلدى: WADI FUEL\nفي: 2026-03-05 07:40\nللاستفسار: المبلغ تقديري",
            "PoS Purchase\nAmount: SAR 400.00\nCard: *8063 - HOTEL HOLD\nAt: QASR HOTEL\nOn: 2026-03-05 11:00",
            "Debit Transfer Local\nAmount: SAR 1,875.00\nFrom account: 5519 - DORMANT\nTo: SAMIR DEMO\n2026-03-05 09:15",
            "Received transfer\nAmount: SAR 1,875.00\nFrom: SAMIR DEMO\nBank transfer under investigation\n2026-03-05 09:15",
            "هلا سامر الحوالة تحت التدقيق\nتم ايداع الراتب\nالمبلغ: 9,500.00 ر.س\nفي: 2026-03-05",
            "Incoming Transfer: Riyad Bank - Compliance Desk\nAmount: SAR 1,875.00\nFrom: *7719\nat: 2026-03-05 11:20",
            // حرف مخفي أو شكل عرض في كلمة عادية (مش كلمة حارس): بيتشال وبيتقري، بس الرسالة ما بتتسجلش لوحدها
            "PoS Purchase\nAmount: SAR 87.40\nAt: TEST BAK​ERY\n2026-03-05 11:20",
            "شراء عبر نقاط البيع\nمبلغ: 87.40 ر.س\nلدى: ﻣﺤﻠ TEST\nفي: 2026-03-05 11:20",
        )
        for (body in saudi) {
            assertTrue(!hasShapeDoubt(body), "the word list must not be what catches it: $body")
            val row = assertIs<SmsParseResult.Ok>(sa(body), body).row
            assertTrue(!row.shape.clear, "${row.shape.wire}: $body")
        }
        val egypt = listOf(
            "تم تحويل 300 جنيه لرقم 01000007433 مصاريف الخدمة 1 جنيه رصيد حسابك في فودافون كاش الحالي 699. المستلم هيستلم الفلوس من الفرع",
            "تم اضافة تحويل لحظي لحسابكم بمبلغ 1,500 جم من كريم عن طريق البنك رقم مرجعي 900000371",
            "Your credit card ending with#4417 was charged for EGP 412.60 at ZEPHYR KIOSK (ROOM 12 FOLIO) on 08/10/26 at 09:15",
        )
        for (body in egypt) {
            assertTrue(!hasShapeDoubt(body), "the word list must not be what catches it: $body")
            val row = assertIs<SmsParseResult.Ok>(eg(body, OCTOBER), body).row
            assertTrue(!row.shape.clear, "${row.shape.wire}: $body")
        }
        // نفس الرسايل من غير الزيادة ⇒ لسه بتتسجل لوحدها (الاختبار مش فاضي)
        for (body in listOf(
            "Incoming Transfer: Riyad Bank\nAmount: SAR 1,875.00\nFrom: *7719\nat: 2026-03-05 11:20",
            "PoS Purchase\nAmount: SAR 87.40\nAt: TEST BAKERY\n2026-03-05 11:20",
        )) {
            assertTrue(assertIs<SmsParseResult.Ok>(sa(body), body).row.shape.clear, body)
        }
        val plainEg = eg("Your credit card ending with#4417 was charged for EGP 412.60 at ZEPHYR KIOSK on 08/10/26 at 09:15", OCTOBER)
        assertTrue(assertIs<SmsParseResult.Ok>(plainEg).row.shape.clear)
    }

    /** كلمات الحالة بضماير متصلة وبالألف والياء بأي كتابة (الجولة السادسة — الحد كان بيمنعها)، من غير ما تمسك أسامي عادية. */
    @Test fun theDoubtWordsCoverAttachedPronounsAndUnifiedLetters() {
        for (text in listOf("تم استرجاعها", "تم تعليقها", "وتم ارجاعه للمرسل", "وتم إلغاؤها", "تم الغاءها", "تم الغاؤها", "أُلغيت", "عملية معكوسة", "مستردة", "اتعكست")) {
            assertTrue(hasShapeDoubt(text), text)
        }
        for (text in listOf("مطعم المعلقة الذهبية", "شركة التأمين", "الضمان الاجتماعي", "هيثم", "المرجع")) assertTrue(!hasShapeDoubt(text), text)
    }
}
