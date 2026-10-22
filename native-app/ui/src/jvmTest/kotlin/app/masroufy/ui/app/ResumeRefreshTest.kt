package app.masroufy.ui.app

import kotlin.test.Test
import kotlin.test.assertEquals

/** 0.5.2: خصم «ادفع بعدين» اتسجل في الخلفية ⇒ الرجوع للتطبيق بيقول «البيانات اتغيرت» فاللوحة تتفحص (من غير إعادة فتح). */
class ResumeRefreshTest {
    @Test fun firstShowIsQuietAndEveryReturnSaysDataChanged() {
        val changes = DataChanges()
        val resume = ResumeRefresh(changes)
        resume.onVisible()
        assertEquals(0, changes.version, "أول فتح: الشاشات بتقرا لوحدها")
        resume.onVisible()
        assertEquals(1, changes.version)
        resume.onVisible()
        assertEquals(2, changes.version)
    }
}
