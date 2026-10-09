package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** عرض التواريخ جوه الجمل (DESIGN-SYSTEM «الخط والأرقام»): أرقام عربية شرقية في العربي، والسبت أول الأسبوع (OVERRIDES §76). */
class DateTextsTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test fun headerDateLikeThePrototype() {
        // النموذج: «الأربعاء، ٧ أكتوبر» يوم 2026-10-07
        assertEquals("الأربعاء، ٧ أكتوبر", weekdayDayMonth("2026-10-07"))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("الأربع، ٧ أكتوبر", weekdayDayMonth("2026-10-07"))
        Texts.language = Language.EN
        assertEquals("Wednesday, October 7", weekdayDayMonth("2026-10-07"))
    }

    @Test fun calendarGridStartsOnSaturday() {
        assertEquals(listOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة"), weekdayShortNamesFromSaturday())
        assertEquals(5, saturdayColumnOf("2026-10-01"), "أول أكتوبر ٢٠٢٦ خميس")
        assertEquals(0, saturdayColumnOf("2026-10-03"), "السبت أول عمود")
        assertEquals(6, saturdayColumnOf("2026-10-09"), "الجمعة آخر عمود")
    }

    @Test fun sentenceNumbersAreEasternInArabicOnly() {
        assertEquals("٢٠٢٦", sentenceNumber(2026))
        assertEquals("أكتوبر ٢٠٢٦", monthYear(2026, 10))
        Texts.language = Language.EN
        assertEquals("2026", sentenceNumber(2026))
        assertEquals("October 2026", monthYear(2026, 10))
    }

    @Test fun currencyNamesAndSymbols() {
        assertEquals("ر.س", currencySymbol(Currency.SAR))
        assertEquals("جنيه مصري", currencyName(Currency.EGP))
    }
}
