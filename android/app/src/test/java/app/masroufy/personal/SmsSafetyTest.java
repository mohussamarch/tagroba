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
    @Test public void keepsLargeFinancialAmountsButHidesAccountNumbers() {
        String safe=SmsSafety.sanitize("حوالة واردة بمبلغ 15000.50 SAR في 2026-09-10 حساب 1234567890");
        assertTrue(safe.contains("15000.50")); assertFalse(safe.contains("1234567890"));
    }
}
