package app.masroufy.wiring

import app.masroufy.usecase.SmsRunProgress
import kotlin.test.Test
import kotlin.test.assertEquals

/** smoke 0.5.4 ملاحظة 5 — تقدم «أكّد الأشكال» في مصر ما يعرضش رسايل السعودية المستنية في الصندوق. أرقام مخترعة. */
class SmsProgressScopeTest {
    @Test fun progressIsScopedToTheScreensCountryWithTheCountTakenOnceBefore() {
        // الصندوق فيه 620 مستنية: 619 سعودي + 1 مصري ⇒ «التانيين» 619 بيتحسبوا مرة قبل اللفة
        val others = othersInBox(boxCount = 620, mineCount = 1)
        assertEquals(619, others)
        assertEquals(SmsRunProgress(0, 0, 1), scopeProgress(SmsRunProgress(0, 0, 620), others), "أول تقدم = مصر بس")
        assertEquals(SmsRunProgress(1, 0, 0), scopeProgress(SmsRunProgress(1, 0, 619), others), "بعد تسجيل المصرية")
        assertEquals(0, scopeProgress(SmsRunProgress(5, 0, 600), others).queued, "ما ينزلش تحت الصفر لو اللفة سجلت من بلد تانية")
        assertEquals(0, othersInBox(3, 5))
    }
}
