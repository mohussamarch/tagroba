package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import app.masroufy.core.SmsRound8Cases.OCT8
import app.masroufy.core.SmsRound8Cases.OCT9
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الجولة التامنة (OVERRIDES §72.5 — المراجعة العدائية الرابعة للجولة السابعة): القارئين. كل اختبار = ملاحظة، برسايل المراجع نفسها
 * ([SmsRound8Cases] — مخترعة). فلتر الجهاز في `SmsSafetyRound8Test` (device) والانتظار في الصندوق في `AutoRecordSmsRound8Test` (app).
 */
class SmsRound8Test {
    private fun msg(body: String, at: String = OCT8) = BankSmsMessage("TESTBANK", at, body)

    private fun sa(body: String, at: String = OCT8) = parseBankSms(msg(body, at), 1)

    private fun eg(body: String, at: String = OCT8) = parseEgyptBankSms(msg(body, at), 1)

    private fun ok(r: SmsParseResult, what: String = "") = assertIs<SmsParseResult.Ok>(r, "$what $r").row

    private fun rejected(r: SmsParseResult, what: String = "") = assertIs<SmsParseResult.Rejected>(r, "$what $r")

    private fun waits(r: SmsParseResult, what: String) {
        assertTrue(r !is SmsParseResult.Ok || !r.row.shape.clear, "auto-recorded: $what ${(r as? SmsParseResult.Ok)?.row?.shape?.wire}")
    }

    @Test fun cardCreditsAreCardPaymentsAndACreditFromAMerchantIsARefund() {
        for (body in SmsRound8Cases.cardCredits) {
            val row = ok(sa(body, OCT9), body)
            assertEquals(IN to SmsKind.CARD_PAYMENT, row.direction to row.kind, body)
            assertEquals("4476", row.ownLast4, body) // رقم الكارت ⇒ الصندوق بيقارنه بأرقام المحفظة (`AutoRecordSmsRound8Test`)
        }
        assertEquals(OUT to SmsKind.CARD_PAYMENT, ok(sa(SmsRound8Cases.CARD_PAYMENT_DEBIT, OCT9)).let { it.direction to it.kind })
        val refund = ok(sa(SmsRound8Cases.CARD_REFUND, OCT9))
        assertEquals(SmsKind.REFUND, refund.kind)
        assertEquals("MARJAN TOYS", refund.merchantName)
        for (body in SmsRound8Cases.egyptCardCredits) {
            val row = ok(eg(body, OCT9), body)
            assertEquals(IN to SmsKind.CARD_PAYMENT, row.direction to row.kind, body)
            assertEquals(SmsShape.KnownShape("arabbank", "credit-card-credit"), row.shape)
        }
    }

    @Test fun nbeDatesAreMonthDayWithAnySeparator() {
        for ((body, at, day) in SmsRound8Cases.nbeDates) assertEquals(day, ok(eg(body, at), body).date, body)
        // مرسل تاني بـ«/» = يوم/شهر (العرف في مصر)، و«-» من غير الأهلي ملتبس لو القرايتين ممكنين
        assertEquals("2026-09-10", ok(eg("Purchase of EGP 850.00 at ZAHRA MART يوم 10/09 using card 3318", OCT9)).date)
    }

    @Test fun egyptianPartialDatesTheShapesAcceptAreReadNotTheArrivalDay() {
        for ((body, at, day) in SmsRound8Cases.egyptPartialDates) assertEquals(day, ok(eg(body, at), body).date, body)
        // «24/7» في اسم المحل لسه مش تاريخ (الرسالة من غير تاريخ = يوم الوصول)
        assertEquals("2026-10-08", ok(eg("Your Debit Card **4417 had a Successful transaction of EGP 41.25 @PHARMA 24/7,your available bal.EGP174.40")).date)
        // مبلغ بكسور شكله «يوم.شهر» مش تاريخ
        assertEquals("2026-10-08", ok(eg("Your Debit Card **4417 had a Successful transaction of EGP 10.08 @TEST STORE,your available bal.EGP 10.09")).date)
        // تاريخين مختلفين ⇒ الشكل المعروف بيستنى
        waits(eg("IPN transfer received with amount of EGP 400.00 from 4417 on 07.10 at 13:00. Ref# 900000614 06/10"), "two dates")
    }

    @Test fun anEgyptianTimeWithoutADateWaits() {
        for ((body, at) in SmsRound8Cases.timeOnly) waits(eg(body, at), body)
        // ونفس القالب بتاريخ لسه بيتسجل لوحده
        assertTrue(ok(eg(SmsRound8Cases.egyptPartialDates.first().first, "2026-10-09T06:00:00Z")).shape.clear)
    }

    @Test fun theOwnersOtherAccountIsReadFromEveryAccountSpelling() {
        for (body in SmsRound8Cases.ownAccount4417) {
            val row = ok(if ('\n' in body) sa(body) else eg(body), body)
            assertEquals("4417", row.ownLast4, body)
        }
        // الكارت لوحده لسه بيتقري، والطرف التاني («From: **4417» في الوارد) مش حسابك
        assertEquals("9001", ok(sa("PoS Purchase\nAmount: SAR 64.25\nCard: *9001\nAt: WOMBAT PANTRY\nOn: 2026-10-08 18:22")).ownLast4)
        assertNull(ok(sa("Credit transfer Local\nAmount: SAR 900.00\nFrom: **4417\nOn: 2026-10-08")).ownLast4)
    }

    @Test fun purchasesAbroadWaitEvenInLocalCurrency() {
        for (body in SmsRound8Cases.saudiAbroad) waits(sa(body), body)
        for (body in SmsRound8Cases.egyptAbroad) waits(eg(body), body)
        // المحلي لسه بيتسجل لوحده: مدينة البلد أو كودها · «.COM» · اسم من كلمة واحدة
        val local = listOf(
            "PoS Purchase\nAmount: SAR 75.00\nAt: SAMPLE MALL RIYADH SA\nOn: 2026-10-08 10:00",
            "PoS Purchase\nAmount: SAR 75.00\nAt: SAMPLE CAFE JEDDAH\nOn: 2026-10-08 10:00",
            "PoS Purchase\nAmount: SAR 75.00\nAt: SAMPLE.COM\nOn: 2026-10-08 10:00",
            "PoS Purchase\nAmount: SAR 75.00\nAt: AL-OTHAIM MARKETS\nOn: 2026-10-08 10:00",
            "شراء عبر نقاط البيع\nمبلغ: 180.00 ر.س\nلدى: متجر العينة الرياض\nفي: 2026-10-08 10:00",
        )
        for (body in local) assertTrue(ok(sa(body), body).shape.clear, body)
        assertTrue(ok(eg("Your credit card ending with #4417 was charged for EGP 640.00 at TEST MART CAIRO on 07/10/2026 at 20:00.")).shape.clear)
        assertTrue(foreignCountryTail("SAMPLE MALL DUBAI U.A.E", SAUDI_TAIL))
        assertFalse(foreignCountryTail("SAMPLE TRADING CO", SAUDI_TAIL))
    }

    @Test fun theAmountMustComeFromTheAmountSlot() {
        for (body in SmsRound8Cases.amountFromFreeSlot) {
            assertEquals(uiText(TextKey.SMS_AMOUNT_UNCLEAR), rejected(sa(body), body).reason, body)
        }
        // الرقم جوه اسم المحل أو رقم الفاتورة مش مبلغ تاني — المبلغ الحقيقي بيتقري
        assertEquals(23_000L, ok(sa("Bill Payment\nAmount: SAR 230.00\nBiller: SAMPLE WATER CO\nالفاتورة: SR4471\nOn: 2026-10-08 09:00")).amountMinor)
        assertEquals(3_700L, ok(sa("PoS Purchase\nAmount: SAR 37.00\nAt: محلات 5 ريال\nOn: 2026-10-08 18:22")).amountMinor)
        // البوابة لوحدها: المبلغ المقروء لازم = خانة المبلغ
        assertEquals(SmsShape.KeywordFallback, saudiShape("PoS Purchase\nAmount: SAR 37.00\nAt: TEST\nOn: 2026-10-08 18:22", OUT, 500))
        assertEquals(SmsShape.SamaTitle, saudiShape("PoS Purchase\nAmount: SAR 37.00\nAt: TEST\nOn: 2026-10-08 18:22", OUT, 3_700))
    }

    @Test fun titleRulesSeeTheTitleTheShapeGateSees() {
        // من غير همزة = بالهمزة (كان صرف «OTHER» من كلمة «سداد»)
        assertEquals(IN to SmsKind.CARD_PAYMENT, ok(sa(SmsRound8Cases.cardCredits[2])).let { it.direction to it.kind })
        // بالشدة + «حساب راتب» كان **راتب داخل** — دلوقتي سداد كارت صرف
        for (body in listOf(
            "بطاقة ائتمانيّة تسديد\nالمبلغ: 1,000.00 ر.س\nمن حساب: **6618 - حساب راتب\nفي: 2026-10-08 10:00",
            "بطاقة ائتمانية تسديد\nالمبلغ: 1,000.00 ر.س\nمن حساب: **6618 - حساب راتب\nفي: 2026-10-08 10:00",
        )) assertEquals(OUT to SmsKind.CARD_PAYMENT, ok(sa(body), body).let { it.direction to it.kind }, body)
    }

    @Test fun maskLettersAreNotAName() {
        for (body in listOf(
            "حوالة داخلية\nمن: XX6618\nالى: **4417\nمبلغ: 500.00 ر.س\nفي: 2026-10-08 11:40",
            "حوالة محلية\nمن: **6618\nالى: xx4417\nمبلغ: 500.00 ر.س\nفي: 2026-10-08 11:40",
        )) assertEquals(uiText(TextKey.SMS_DIRECTION_UNCLEAR), rejected(sa(body), body).reason, body)
        assertEquals(OUT, ok(sa("حوالة داخلية\nمن: **6618\nالى: خالد التجريبي\nمبلغ: 500.00 ر.س\nفي: 2026-10-08 11:40")).direction)
    }

    @Test fun aBidiMarkInsideANumberIsRejected() {
        val hidden = uiText(TextKey.SMS_HIDDEN_TEXT)
        for (body in SmsRound8Cases.bidiInsideNumber) {
            assertEquals(hidden, rejected(sa(body), body).reason, body)
            assertEquals(hidden, rejected(eg(body), body).reason, body)
        }
        // حوالين الرقم عادي
        assertTrue(ok(sa("PoS Purchase\nAmount: SAR ‏48.60‏\nAt: QUOLL BAKERY\nOn: 2026-10-07 18:22")).shape.clear)
        assertFalse(hasBidiInsideNumber("SAR 48.60‎, 2026-10-07"))
    }

    @Test fun aRefundOrStatusWordInTheSenderSlotIsNotAPlainTransfer() {
        for (body in SmsRound8Cases.refundInSender) assertEquals(SmsKind.REFUND, ok(eg(body), body).kind, body)
        for (body in SmsRound8Cases.statusInNameSlot) waits(eg(body), body)
        // نفس الأسامي من غير الكلمة لسه بتتسجل لوحدها
        assertTrue(ok(eg("Instant transfer of 300.00 EGP received from SAMI MODEL")).shape.clear)
        assertTrue(ok(eg("تم استلام مبلغ 500 جنيه من رقم 01001112255 المسجل باسم رنا التجريبية")).shape.clear)
    }

    @Test fun failureWordingsAreRejectedNotReady() {
        val declined = uiText(TextKey.SMS_DECLINED)
        for (body in SmsRound8Cases.failures) assertEquals(declined, rejected(eg(body), body).reason, body)
    }

    @Test fun piastresMakeTheAmountUnclear() {
        assertEquals(uiText(TextKey.SMS_AMOUNT_UNCLEAR), rejected(eg("تم تحويل ٥٠٠ جنيه و٥٠ قرش لرقم 01001112287 رصيدك الحالي 300 جنيه")).reason)
    }

    @Test fun cashAndCashbackKinds() {
        for (body in listOf("CASH WITHDRAWAL BANQUE SAMPLE", "ماكينة صراف بنك سامبل فرع الدقي", "BANQUE SAMPLE CASH ADVANCE")) {
            val row = ok(eg("تم خصم 2,000 جم من بطاقة الخصم المباشر رقم 3318 عند $body يوم 10-09 الساعة 13:10", OCT9), body)
            assertEquals(SmsKind.CASH_WITHDRAWAL, row.kind, body)
        }
        assertEquals(SmsKind.CASH_WITHDRAWAL, ok(sa("PoS Purchase\nAmount: SAR 500.00\nAt: CASH WITHDRAWAL SAMPLE\nOn: 2026-10-08 10:00")).kind)
        for (body in listOf(
            "PoS Purchase & Cashback\nAmount: SAR 350.00\nAt: MARJAN HYPER\nOn: 2026-10-09 18:40",
            "شراء ونقد عبر نقاط البيع\nمبلغ: 350.00 ر.س\nلدى: هايبر مرجان\nفي: 2026-10-09 18:40",
        )) assertEquals(SmsKind.PURCHASE_WITH_CASH, ok(sa(body, OCT9), body).kind, body)
        assertEquals(SmsKind.CASH_DEPOSIT, ok(sa("إيداع صراف آلي\nمبلغ: 2,000.00 ر.س\nحساب: **1188\nفي: 2026-10-09 09:12", OCT9)).kind)
    }

    @Test fun pointsAndScheduledTailsAreReadAndWaitReady() {
        val read = listOf(
            "تمت عملية شراء بمبلغ 48.50 ر.س لدى صيدلية سديم بتاريخ 2026-10-09 وسيتم إضافة 48 نقطة قطاف" to 4_850L,
            "Your Visa card *4476 was used for SAR 210.00 at LAYAN OPTICS on 09/10/2026. Bonus points will be credited at month end" to 21_000L,
            "Your mada card **5093 was used for SAR 48.50 at SADEEM PHARMACY on 09/10/2026, you will earn 48 points." to 4_850L,
        )
        for ((body, amount) in read) {
            val row = ok(sa(body, OCT9), body)
            assertEquals(amount to OUT, row.amountMinor to row.direction, body)
            assertFalse(row.shape.clear, body)
        }
        val scheduled = ok(eg("Scheduled transfer completed: EGP 1,500.00 credited to your account 2277 on 09/10/2026", OCT9))
        assertEquals(150_000L to IN, scheduled.amountMinor to scheduled.direction)
        // عرض النقاط لوحده لسه عرض
        assertEquals(uiText(TextKey.SMS_OFFER), rejected(sa("سيتم إضافة 500 نقطة لحسابك عند الدفع ببطاقتك مدى", OCT9)).reason)
    }

    @Test fun aBilingualMessageIsReadOnceAndWaits() {
        val body = "شراء عبر نقاط البيع\nمبلغ: 48.50 ر.س\nلدى: صيدلية سديم\nفي: 2026-10-09 10:41\nPoS Purchase\nAmount: SAR 48.50\nAt: SADEEM PHARMACY\nOn: 2026-10-09 10:41"
        val row = ok(sa(body, OCT9))
        assertEquals(4_850L, row.amountMinor)
        assertFalse(row.shape.clear)
        // مبلغين مختلفين بلغتين لسه «أكتر من مبلغ»
        assertEquals(uiText(TextKey.SMS_MULTIPLE_AMOUNTS), rejected(sa(body.replace("SAR 48.50", "SAR 84.50"), OCT9)).reason)
    }

    @Test fun anAmericanLookingDateNearArrivalWaits() {
        val s90 = ok(sa("PoS Purchase\nAmount: SAR 48.50\nAt: SADEEM PHARMACY\nOn: 10/09/2026 10:41", OCT9))
        assertEquals("2026-09-10", s90.date) // القراية زي ما هي (ملف المرجع)
        assertFalse(s90.shape.clear)
        assertTrue(ok(sa("PoS Purchase\nAmount: SAR 48.50\nAt: SADEEM PHARMACY\nOn: 05/10/2026 10:41", OCT9)).shape.clear)
    }

    @Test fun foreignDetailsTravelWithTheWaitingMessage() {
        fun pending(r: SmsParseResult) = assertNotNull(rejected(r).foreign, r.toString())
        fun unread(r: SmsParseResult) = assertNotNull(rejected(r).foreignUnread, r.toString())
        assertEquals(SmsForeignAmount("AED", 12_000), pending(sa("Purchase\nAmount: DHS 120.00\nAt: SAMPLE DUBAI MALL\nOn: 2026-10-09 19:20", OCT9)).foreign)
        assertEquals(SmsForeignAmount("JOD", 25_000), pending(sa("PoS Purchase\nAmount: JD 25.000\nAt: SAMPLE AMMAN CAFE\nOn: 2026-10-09", OCT9)).foreign)
        val jd = pending(sa("PoS Purchase\nAmount: JD 25.000 (SAR 132.40)\nAt: SAMPLE AMMAN CAFE\nOn: 2026-10-09", OCT9))
        assertEquals(13_240L, jd.localSuggestion)
        assertEquals("USD", unread(sa("PoS Purchase\nAmount: SAR 168.90\nCurrency: USD\nAt: SAMPLE STORE\nOn: 2026-10-09", OCT9)).currency)
        val dinar = unread(sa("شراء دولي\nمبلغ: 25.000 دينار\nلدى: مقهى العينة\nفي: 2026-10-09", OCT9))
        assertEquals(null to "25.000", dinar.currency to dinar.writtenAmount)
        // مصر: «دولار» لوحده (سؤال (س) — من غير كود) · «International» جوه جملة · المقابل بين قوسين في آخر الجملة
        val dollar = unread(eg("تم استلام مبلغ 200 دولار من رقم 01001112240 المسجل باسم سامي المثالي على رقم محفظتك 01009998877"))
        assertEquals(Triple(null, "200", IN), Triple(dollar.currency, dollar.writtenAmount, dollar.direction))
        assertEquals(SmsForeignAmount("USD", 4_000), pending(eg("You sent USD 40.00 to KARIM PROBE via InstaPay International on 08/10/2026")).foreign)
        assertEquals(64_050L, pending(eg("e& money: You paid GBP 9.99 to TEST MUSIC on 08/10/2026 (EGP 640.50)")).localSuggestion)
        // كارت سعودي اتخصم في مصر بالجنيه بس: بيستنى في السعودية، ومصر بتقول «البلد التانية»
        val s62 = "PoS Purchase\nAmount: EGP 1,250.00\nAt: ZAHRA MART\nOn: 2026-10-09 13:00"
        assertEquals(SmsForeignAmount("EGP", 125_000), pending(sa(s62, OCT9)).foreign)
        assertEquals(uiText(TextKey.SMS_OTHER_COUNTRY), rejected(eg(s62, OCT9)).reason)
    }
}
