package app.masroufy.ui.screens.home

import app.masroufy.core.ArabicVariant
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * «اختر شكلك» (`LookSheet` جوه «ملفك»): ستة أشكال مؤقتة (الكاركتر النهائي لسه بيتصمم §73) · المختار بيظهر في الدواير ·
 * من غير اختيار ⇒ اللي في ملفك ⇒ الأول · رقم مش من الستة ما يكسرش الدايرة.
 */
class LookSheetTest {
    @AfterTest fun reset() {
        LookChoice.look = null
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test fun sixTemporaryLooksWithArabicLabels() {
        assertEquals(6, LOOK_COUNT)
        assertEquals("الشكل ٣", t(TextKey.LOOK_SHEET_ITEM, sentenceNumber(3)))
        assertEquals("أشكال مؤقتة — الكاركتر النهائي قيد التصميم.", t(TextKey.LOOK_SHEET_NOTE))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("أشكال مؤقتة — الكاركتر النهائي لسه بيتصمم.", t(TextKey.LOOK_SHEET_NOTE))
    }

    @Test fun theCirclesShowTheChosenLookThenTheProfileThenTheFirst() {
        assertEquals(1, avatarLook(null, null))
        assertEquals(4, avatarLook(null, 4), "من غير اختيار ⇒ اللي في ملفك")
        LookChoice.look = 2
        assertEquals(2, avatarLook(LookChoice.look, 4), "اختيار «اختر شكلك» بيغلب")
        assertEquals(1, avatarLook(9, null), "رقم برّه الستة ⇒ الأول")
    }
}
