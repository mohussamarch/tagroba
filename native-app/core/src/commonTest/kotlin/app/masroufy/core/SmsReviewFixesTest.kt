package app.masroufy.core

import app.masroufy.core.Direction.IN
import app.masroufy.core.Direction.OUT
import app.masroufy.core.SmsKind.PURCHASE
import app.masroufy.core.SmsKind.REFUND
import app.masroufy.core.SmsKind.SALARY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * أخطاء مراجعة جلسة 33 على قارئ الرسايل — كل حالة كانت بتطلع غلط قبل التصليح. الرسايل مخترعة (المحل NOVA GAMES / LUMEN CAFE /
 * PHARMA 24/7 · الكارت 4417 · المبالغ مخترعة).
 */
class SmsReviewFixesTest {
    private fun ok(r: SmsParseResult, name: String): SmsRow = when (r) {
        is SmsParseResult.Ok -> r.row
        is SmsParseResult.Rejected -> throw AssertionError("$name: rejected «${r.reason}»")
    }

    private fun saudi(body: String, at: String = SMS_RECEIVED_AT) = parseBankSms(smsMessage(body, receivedAt = at), 1)
    private fun egypt(body: String, at: String = SMS_RECEIVED_AT) = parseEgyptBankSms(smsMessage(body, receivedAt = at), 1)

    /** §75-12 لأي عملة: «TRY 450.00» كانت «المبلغ مش واضح» من غير المبلغ الأجنبي (والفلتر بيرميها). الين من غير كسور (4500 = 4500). */
    @Test fun anyIsoForeignCurrencyWaitsForTheLocalAmount() {
        for ((code, written, minor) in listOf(Triple("TRY", "450.00", 45000L), Triple("CHF", "30.00", 3000L), Triple("JPY", "4500", 4500L), Triple("INR", "1500.00", 150000L))) {
            val r = assertIs<SmsParseResult.Rejected>(saudi("شراء دولي\nبطاقة:4417;مدى\nمبلغ:$code $written\nدولة:XX\nلدى:NOVA GAMES\nفي:2026-03-05 09:10"), code)
            assertEquals(uiText(TextKey.SMS_FOREIGN_CURRENCY), r.reason, code)
            assertEquals(SmsForeignPending(SMS_TX_DAY, SmsForeignAmount(code, minor), OUT, "NOVA GAMES", PURCHASE, ownLast4 = "4417"), r.foreign, code)
        }
        val stc = assertIs<SmsParseResult.Rejected>(saudi("VISA Purchase\nVia: *4417\nAmount: 450.00 TRY\nFrom: NOVA GAMES\nAt: 2026-03-05 09:10"))
        assertEquals(SmsForeignAmount("TRY", 45000), stc.foreign?.foreign)
        val cib = assertIs<SmsParseResult.Rejected>(
            egypt("Your credit card ending with#4417 was charged for TRY 300.00 at NOVA GAMES on 05/03/26 at 09:10. Card available limit is EGP 38,500.00."),
        )
        assertEquals(uiText(TextKey.SMS_NOT_EGP), cib.reason)
        assertEquals(SmsForeignPending(SMS_TX_DAY, SmsForeignAmount("TRY", 30000), OUT, "NOVA GAMES", PURCHASE, ownLast4 = "4417"), cib.foreign)
    }

    /** اسم محل فيه كود عملة جنب رقم صحيح («TOP 10») مش عملة أجنبية — الكود حروف كبيرة وجنبه كسور أو بعد كلمة مبلغ. */
    @Test fun merchantNamesThatLookLikeCurrencyCodesStayLocal() {
        val row = ok(saudi("شراء\nبطاقة:4417;مدى\nمبلغ:SAR 64.25\nلدى:TOP 10 MARKET\nفي:2026-03-05 09:10"), "TOP 10")
        assertEquals(6425L, row.amountMinor)
        assertTrue(row.kind == PURCHASE)
        assertEquals("TOP 10 MARKET", row.merchantName)
        assertTrue(isoMoneyIn("please try 3 times, ALL 4 KIDS").isEmpty())
    }

    /** الأهلي المصري بالأرقام العربية: «١٬٢٥٠» كانت بتتقري 250 و«٢٥٠٫٥٠» 50 — في صمت. */
    @Test fun arabicIndicSeparatorsInEgyptianAmounts() {
        val thousands = ok(egypt("تم خصم ١٬٢٥٠ جم من بطاقة الائتمان رقم ٤٤١٧ عند LUMEN CAFE يوم ٠٣-٠٥ الساعة ٠٩:١٠ المتاح ٣٨٬٠٠٠ جم"), "١٬٢٥٠")
        assertEquals(125000L, thousands.amountMinor)
        assertEquals(SMS_TX_DAY, thousands.date)
        val decimals = ok(egypt("تم خصم ٢٥٠٫٥٠ جم من بطاقة الائتمان رقم ٤٤١٧ عند LUMEN CAFE يوم ٠٣-٠٥ الساعة ٠٩:١٠"), "٢٥٠٫٥٠")
        assertEquals(25050L, decimals.amountMinor)
    }

    /** «PHARMA 24/7» في اسم المحل مش تاريخ: كانت 24 يوليو في مصر، و«التاريخ مش واضح» في الأهلي السعودي. */
    @Test fun twentyFourSevenInAMerchantNameIsNotADate() {
        val cib = ok(egypt("لقد تم رد EGP415.50 على بطاقتكم الائتمانية المنتهية بـ# 4417 من PHARMA 24/7", "2026-08-10T09:00:00Z"), "cib 24/7")
        assertEquals("2026-08-10", cib.date)
        assertEquals(SmsKind.RETURNED, cib.kind) // §77-D: «تم رد» = عملية رجعت
        assertEquals("PHARMA 24/7", cib.merchantName)
        val snb = ok(saudi("شراء انترنت\nبـ19.90 SAR\nمن PHARMA 24/7\nمدى-ابل *4417"), "snb 24/7")
        assertEquals(SMS_TX_DAY, snb.date)
        assertEquals("PHARMA 24/7", snb.merchantName)
        // يوم/شهر في مكانه لسه بيتقري (QNB «on 29/07 at» · بيت التمويل «on 05/03 09:10»)
        assertEquals("2026-03-05", ok(egypt("IPN Transfer with EGP 900.00 deducted on 05/03 09:10 from your AC ending with 188 with Ref# 77"), "kfh").date)
    }

    /** الرسالة اللي مفيهاش تاريخ = يوم الوصول **بتوقيت القاهرة**، مش أول 10 حروف من وقت جرينتش. */
    @Test fun egyptianDatelessMessagesUseTheCairoDay() {
        val salary = "عميلنا العزيز لقد تم تحويل مبلغ EGP18,750.00 على حسابكم لدينا من جهة العمل"
        val row = ok(egypt(salary, "2026-03-04T23:30:00Z"), "salary 01:30 Cairo")
        assertEquals("2026-03-05", row.date)
        assertEquals(SALARY, row.kind)
        assertEquals("2026-07-01", cairoDayOf("2026-06-30T21:30:00Z"), "summer time +3")
        assertEquals("2026-01-15", cairoDayOf("2026-01-15T21:30:00Z"), "winter +2")
        assertEquals("2020-06-30", cairoDayOf("2020-06-30T21:30:00Z"), "no summer time before 2023")
        assertEquals("2026-10-29", cairoDayOf("2026-10-29T21:30:00Z"), "summer time ended 23:00 standard on the last Thursday")
        assertEquals("2026-04-23", cairoDayOf("2026-04-23T21:30:00Z"), "Thursday before summer time: still +2")
        assertEquals("2026-04-25", cairoDayOf("2026-04-24T21:30:00Z"), "summer time starts on the last Friday of April")
        assertEquals("2026-01-18", cairoDayOf("2026-01-18T02:24:00.000+02:00"))
    }

    /** فعل خصم صريح + كلمة وارد (نقط · «non-refundable» · محل اسمه DEPOSIT · ماكينة إيداع) ⇒ ما يتسجلش دخل. */
    @Test fun egyptianDebitIsNeverBookedAsIncome() {
        val shapes = listOf(
            "تم خصم 500.00 جم من بطاقتك المنتهية بـ 6604 عند TEST GROCER يوم 05/03/2026 وتم إضافة 50 نقطة لرصيد نقاطك",
            "Your debit card 6604 was charged EGP 500.00 at TEST GROCER on 05/03/2026, you received 50 points",
            "Your debit card 6604 was charged EGP 500.00 at TEST AIRLINE on 05/03/2026, non-refundable fare",
            "Your debit card 6604 was charged EGP 500.00 at TEST HOTEL DEPOSIT on 05/03/2026",
            "تم سحب 2,000 جم من حسابك من ماكينة إيداع وسحب يوم 05/03/2026",
            "تم خصم 500.00 جم من بطاقتك عند TEST GROCER يوم 05/03/2026 وتم إضافة 50 نقطة لبطاقتك",
        )
        for (body in shapes) {
            val r = egypt(body)
            assertTrue(r !is SmsParseResult.Ok || r.row.direction == OUT, "booked as income: $body")
        }
        assertEquals(OUT, ok(egypt(shapes[2]), "non-refundable").direction)
        assertEquals(uiText(TextKey.SMS_DIRECTION_UNCLEAR), assertIs<SmsParseResult.Rejected>(egypt(shapes[0])).reason)
    }

    /** رسايل مصرية من غير تاريخ مش عملية (رمز · عرض · مرفوضة · معلقة · طلب) كانت بتتسجل بتاريخ النهارده. */
    @Test fun undatedEgyptianNonTransactionsAreNotBooked() {
        val guarded = listOf(
            "Your one-time PIN for purchase of EGP 500.00 at TEST GROCER is 482913",
            "Your CIB verification PIN for the purchase of EGP 500.00 at TEST GROCER using credit card 6604 is 4829",
            "Use code 482913 to confirm the purchase of EGP 500.00 at TEST GROCER using your debit card 6604",
            "كود التحقق 482913 لتأكيد عملية purchase بمبلغ 500 جم عند TEST GROCER",
            "Your purchase of EGP 500.00 at TEST GROCER using debit card 6604 was rejected",
            "Transaction of EGP 500.00 on your credit card ending 6604 at TEST HOTEL is pending",
            "تم خصم 500 جم من حسابك يوم 05/03/2026 - عملية معلقة لحين التسوية",
            "Shop now with your credit card and pay over 12 months with 0% interest on purchases above EGP 5,000",
            "Get EGP 50 cashback when you pay with your debit card at TEST GROCER",
            "Your refund request of EGP 64.25 from TEST GROCER is under review",
            "تم استلام طلبك لاسترداد 64.25 جم من TEST GROCER",
            "تم استلام طلب سحب 300.00 جنيه من محفظتك",
        )
        val guards = listOf(TextKey.SMS_OFFER, TextKey.SMS_SENSITIVE, TextKey.SMS_DECLINED, TextKey.SMS_NOT_TRANSACTION).map { uiText(it) }
        // الجولة السابعة: «تم خصم … - عملية معلقة لحين التسوية» فيها فعل خصم خلص ومبلغ ⇒ فلتر الجهاز بيحفظها (§72: الضياع مش مقبول)
        // والقارئ بيرفضها بالحارس ⇒ بتستنى، ما بتتسجلش
        val storedButRejected = setOf("تم خصم 500 جم من حسابك يوم 05/03/2026 - عملية معلقة لحين التسوية")
        for (body in guarded) {
            val r = assertIs<SmsParseResult.Rejected>(egypt(body), body)
            assertTrue(r.reason in guards, "$body: rejected by accident («${r.reason}»), not by a guard")
            assertTrue(SmsVocabulary.ignoreBeforeStorage(body) != (body in storedButRejected), "device filter: $body")
        }
        // من غير حارس: رسالة من غير تاريخ ومن غير عبارة «عملية خلصت» ما بتاخدش يوم الوصول
        val promo = assertIs<SmsParseResult.Rejected>(egypt("Use your debit card at TEST GROCER and save EGP 50"))
        assertEquals(uiText(TextKey.SMS_DATE_UNCLEAR), promo.reason)
        // «كود العملية» (أورانج كاش) رقم عملية مش رمز
        val orange = ok(egypt("تم استلام مبلغ 900.00 جنيه من رقم 01100000777 رصيدك الحالي 4,100.00 جنيه كود العملية 123456789012"), "orange")
        assertEquals(90000L, orange.amountMinor)
        assertEquals(IN, orange.direction)
    }
}
