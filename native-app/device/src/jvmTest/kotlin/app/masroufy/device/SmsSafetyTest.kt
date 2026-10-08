package app.masroufy.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** نفس حالات `SmsSafetyTest.java` في التطبيق الحالي حرف بحرف — أرقام وأسماء وهمية. */
class SmsSafetyTest {
    @Test fun excludesCodesAndPromotionsBeforeStorage() {
        assertNull(SmsSafety.sanitize("OTP 123456 purchase amount 25 SAR"))
        assertNull(SmsSafety.sanitize("رمز التحقق 123456 شراء بمبلغ 25 SAR"))
        assertNull(SmsSafety.sanitize("عرض شراء بمبلغ 25 SAR"))
        assertNull(SmsSafety.sanitize("شراء مرفوض بمبلغ 25 SAR"))
        assertNull(SmsSafety.sanitize("رسالة شخصية"))
    }

    @Test fun redactsCardAndArabicDigitsWhilePreservingSmallAmountAndDate() {
        val safe = SmsSafety.sanitize("شراء بمبلغ ٢٥٫٥٠ SAR في 2026-09-10 بطاقة ١٢٣٤٥٦٧٨١٢٣٤٥٦٧٨")
        assertNotNull(safe)
        assertFalse(safe.contains("12345678"))
        assertTrue(safe.contains("25٫50"))
        assertTrue(safe.contains("2026-09-10"))
    }

    @Test fun keysAreStableAndSeparateSenderAndTime() {
        assertEquals(SmsSafety.key("BANK", 1, "text"), SmsSafety.key("bank", 1, "text"))
        assertNotEquals(SmsSafety.key("BANK", 1, "text"), SmsSafety.key("bank", 2, "text"))
        assertNotEquals(SmsSafety.key("BANK", 1, "text"), SmsSafety.key("OTHER", 1, "text"))
    }

    @Test fun keepsAlRajhiSrMessagesThatTheOldFilterDropped() {
        val pos = "شراء PoS\nعبر1111;مدى-سامسونج باي\nبـSR 24\nلـTEST STORE\n26/9/18 09:35"
        assertEquals(pos, SmsSafety.sanitize(pos))
        val big = SmsSafety.sanitize("شراء PoS\nعبر1111;مدى\nبـSR 12500\nلـTEST STORE\n26/9/18 09:35")
        assertNotNull(big)
        assertTrue(big.contains("SR 12500"))
        assertNotNull(SmsSafety.sanitize("دفع\nعبر:1111;مدى\nمن2222\nبـSR 50\nلـTEST WALLET\n16:47 17/9/26"))
        assertNotNull(SmsSafety.sanitize("شراء انترنت\nبطاقة:1111;مدى\nمبلغ:USD 5.30\nلدى:TEST AI\nفي:26/9/18 10:15"))
        val iban = SmsSafety.sanitize("حوالة واردة\nمن حساب SA0380000000608010167519\nبـSR 100\n26/9/18 09:35")
        assertNotNull(iban)
        assertFalse(iban.contains("608010167519"))
        assertTrue(iban.contains("SR 100"))
        assertNull(SmsSafety.sanitize("رمز التحقق 123456 لعملية شراء بـSR 20"))
    }

    @Test fun dropsTheOnlinePurchaseCodeMessage() {
        assertNull(SmsSafety.sanitize("ننصح بعدم مشاركة الرمز لحمايتك من الاحتيال\nالرمز:111111\nبطاقة:*1111\nمبلغ:SAR 35.62\nلدى:TEST INSURANCE CO\nفي:18:56 26/09/16"))
        assertNull(SmsSafety.sanitize("الرمز:222222 لعملية شراء انترنت بـSR 10"))
        val online = "شراء انترنت بـSR 35.62\nعبر1111;مدى\nمن2222\nلـTEST INSURANCE\n18:57 16/9/26"
        assertEquals(online, SmsSafety.sanitize(online))
    }

    @Test fun keepsLargeFinancialAmountsButHidesAccountNumbers() {
        val safe = SmsSafety.sanitize("حوالة واردة بمبلغ 15000.50 SAR في 2026-09-10 حساب 1234567890")!!
        assertTrue(safe.contains("15000.50"))
        assertFalse(safe.contains("1234567890"))
    }

    // جلسة 32 — أشكال البحث (رسايل مخترعة): الجنيه بكل كتاباته وكلمات حركة جديدة، والمبلغ الكبير بالجنيه ما بيتقصش
    @Test fun keepsResearchedShapesThatTheOldWordListDropped() {
        val kept = listOf(
            "تم خصم 64.25 جم من بطاقة الائتمان رقم 6604 عند TEST GROCER يوم 03-05 الساعة 09:10",
            "تم سحب 300.00 ج بنجاح. رصيد حسابك في فودافون  كاش الحالي 4,100.00 جنيه.",
            "You have successfully recharged 50.00 LE to the balance of 01000000777",
            "تم استلام تحويل لحظي بمبلغ 900.00 ج.م من سامي التجريبي",
            "عكس عملية\nالى: ***6604; VISA\n12.40 SAR :المبلغ\nفي: TEST GROCER\nبتاريخ: 2026-03-05 09:10:44",
            "مشتريات نقاط البيع\nبطاقة: **6604;مدى\nمبلغ: 64.25 SAR\nلدى: TEST GROCER\nفي: 05/03/2026 09:10",
            "عملية انترنت\nب: 64.25 SAR\nمن:TEST GROCER\nفي:2026-03-05 09:10:44",
            "Debit fees\nReason: Card replacement fee\nAmount: SAR 15.00\nDate: 2026-03-05 09:10:44",
        )
        for (body in kept) assertNotNull(SmsSafety.sanitize(body), body)
        val big = SmsSafety.sanitize("تم استلام مبلغ 12500 جنيه من رقم 01000000777 المسجل بإسم سامي التجريبي")!!
        assertTrue(big.contains("12500 جنيه"), big)
        assertFalse(big.contains("01000000777"), "رقم الموبايل بيتقص لآخر 4")
        assertTrue(big.contains("••••0777"))
    }

    @Test fun dropsHoldsRequestsAndPurchaseLikeCodes() {
        assertNull(SmsSafety.sanitize("تفويض عبر الانترنت\nبطاقة:6604;مدى\nمبلغ:SAR 64.25\nلدى:TEST GROCER\nفي:26-03-05 09:10"))
        assertNull(SmsSafety.sanitize("رمز شراء أونلاين 482913\nللبطاقة *6604\nبـ 64.25 SAR\nمن TEST GROCER\nفي 09:10 05/03/2026"))
        assertNull(SmsSafety.sanitize("Insufficient balance-Online Purchase\nCard:6604;VISA-VISA\nAmount:64.25SR\nAt:TEST GROCER\n2026-03-05 09:10:44"))
        assertNull(SmsSafety.sanitize("تم طلب سحب مبلغ 300.00 جنيه من حساب فودافون كاش. للتأكيد اطلب #1*9* وادخل الرقم السري."))
        assertNull(SmsSafety.sanitize("لقد تم تقسيط مبلغ 1,200.00 جم من TEST GROCER على بطاقتكم الائتمانية"))
    }

    // QNB مصر (OVERRIDES §40.3): العملة EGP والرسالة إنجليزي
    @Test fun keepsQnbEgyptMessages() {
        val sent = SmsSafety.sanitize("IPN transfer sent with amount of EGP 300.00 from 1234 on 30/07 at 05:48 AM. Ref# aaaa1111.")
        assertNotNull(sent)
        assertTrue(sent.contains("EGP 300.00"))
        // رسالة الكارت مفيهاش «purchase» ولا «transfer» — كانت بتترمي في صمت
        val card = SmsSafety.sanitize("Your Debit Card **1234 had a Successful transaction of EGP 41.25 @sample-store.com,your available bal.EGP174.40")
        assertNotNull(card)
        assertTrue(card.contains("EGP 41.25"))
    }
}
