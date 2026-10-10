package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Texts
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * «اختر شكلك» — **نسخة واحدة في الإعدادات** («ملفك» ⇒ `more/LookSheet.kt` — رد المالك OVERRIDES §82-6؛ نسخة الرئيسية اتشالت):
 * ستة أشكال مؤقتة (الكاركتر النهائي لسه بيتصمم §73) · الدواير بتعرض اللي في ملفك ⇒ الأول · رقم مش من الستة ما يكسرش الدايرة.
 */
class LookSheetTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test fun sixTemporaryLooksWithArabicLabels() {
        assertEquals(6, LOOK_COUNT)
        assertEquals("الشكل 3", t(UiKey.LOOK_N, sentenceNumber(3)))
        assertEquals("أشكال مؤقتة — الكاركتر النهائي قيد التصميم.", t(UiKey.LOOK_NOTE))
    }

    @Test fun theCirclesShowTheProfileLookThenTheFirst() {
        assertEquals(1, avatarLook(null))
        assertEquals(4, avatarLook(4), "اللي في ملفك")
        assertEquals(1, avatarLook(9), "رقم برّه الستة ⇒ الأول")
    }
}
