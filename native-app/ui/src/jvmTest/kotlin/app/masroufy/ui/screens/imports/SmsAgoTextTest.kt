package app.masroufy.ui.screens.imports

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Language
import app.masroufy.core.LastReadWhen
import app.masroufy.core.Texts
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 0.5.2: «قبل 3 دقيقة» ⇒ «قبل 3 دقائق» — الجمع بالعربي 1 · 2 · 3-10 · 11+ (فصحى ومصري). */
class SmsAgoTextTest {
    @AfterTest fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun min(n: Int) = whenText(LastReadWhen.MinutesAgo(n))

    private fun hours(n: Int) = whenText(LastReadWhen.HoursAgo(n))

    @Test fun msaPlurals() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
        assertEquals("قبل دقيقة", min(1))
        assertEquals("قبل دقيقتين", min(2))
        assertEquals("قبل 3 دقائق", min(3))
        assertEquals("قبل 10 دقائق", min(10))
        assertEquals("قبل 11 دقيقة", min(11))
        assertEquals("قبل ساعة", hours(1))
        assertEquals("قبل ساعتين", hours(2))
        assertEquals("قبل 5 ساعات", hours(5))
    }

    @Test fun egyptianPlurals() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("من دقيقة", min(1))
        assertEquals("من دقيقتين", min(2))
        assertEquals("من 4 دقايق", min(4))
        assertEquals("من 45 دقيقة", min(45))
        assertEquals("من 3 ساعات", hours(3))
    }
}
