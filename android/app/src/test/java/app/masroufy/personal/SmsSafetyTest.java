package app.masroufy.personal;
import org.junit.Test;
import static org.junit.Assert.*;

public class SmsSafetyTest {
    @Test public void excludesCodesAndPromotionsBeforeStorage() {
        assertNull(SmsSafety.sanitize("OTP 123456 purchase amount 25 SAR"));
        assertNull(SmsSafety.sanitize("رمز التحقق 123456 شراء بمبلغ 25 SAR"));
        assertNull(SmsSafety.sanitize("عرض شراء بمبلغ 25 SAR"));
        assertNull(SmsSafety.sanitize("شراء مرفوض بمبلغ 25 SAR"));
        assertNull(SmsSafety.sanitize("رسالة شخصية"));
    }
    @Test public void redactsCardAndArabicDigitsWhilePreservingSmallAmountAndDate() {
        String safe=SmsSafety.sanitize("شراء بمبلغ ٢٥٫٥٠ SAR في 2026-09-10 بطاقة ١٢٣٤٥٦٧٨١٢٣٤٥٦٧٨");
        assertNotNull(safe);assertFalse(safe.contains("12345678"));assertTrue(safe.contains("25٫50"));assertTrue(safe.contains("2026-09-10"));
    }
    @Test public void keysAreStableAndSeparateSenderAndTime() {
        assertEquals(SmsSafety.key("BANK",1,"text"),SmsSafety.key("bank",1,"text"));
        assertNotEquals(SmsSafety.key("BANK",1,"text"),SmsSafety.key("bank",2,"text"));
        assertNotEquals(SmsSafety.key("BANK",1,"text"),SmsSafety.key("OTHER",1,"text"));
    }
    // 2026-09-19: الفلتر القديم كان بيرمي كل رسايل الراجحي بصمت لأن العملة «SR» — شكلها بأرقام وأسماء وهمية
    @Test public void keepsAlRajhiSrMessagesThatTheOldFilterDropped() {
        String pos = "شراء PoS\nعبر1111;مدى-سامسونج باي\nبـSR 24\nلـTEST STORE\n26/9/18 09:35";
        assertEquals(pos, SmsSafety.sanitize(pos));
        String big = SmsSafety.sanitize("شراء PoS\nعبر1111;مدى\nبـSR 12500\nلـTEST STORE\n26/9/18 09:35");
        assertNotNull(big); assertTrue(big.contains("SR 12500"));
        assertNotNull(SmsSafety.sanitize("دفع\nعبر:1111;مدى\nمن2222\nبـSR 50\nلـTEST WALLET\n16:47 17/9/26"));
        assertNotNull(SmsSafety.sanitize("شراء انترنت\nبطاقة:1111;مدى\nمبلغ:USD 5.30\nلدى:TEST AI\nفي:26/9/18 10:15"));
        String iban = SmsSafety.sanitize("حوالة واردة\nمن حساب SA0380000000608010167519\nبـSR 100\n26/9/18 09:35");
        assertNotNull(iban); assertFalse(iban.contains("608010167519")); assertTrue(iban.contains("SR 100"));
        assertNull(SmsSafety.sanitize("رمز التحقق 123456 لعملية شراء بـSR 20"));
    }
    @Test public void keepsLargeFinancialAmountsButHidesAccountNumbers() {
        String safe=SmsSafety.sanitize("حوالة واردة بمبلغ 15000.50 SAR في 2026-09-10 حساب 1234567890");
        assertTrue(safe.contains("15000.50")); assertFalse(safe.contains("1234567890"));
    }
}
