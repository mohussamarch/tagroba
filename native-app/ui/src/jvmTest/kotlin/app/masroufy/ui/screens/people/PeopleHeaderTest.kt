package app.masroufy.ui.screens.people

import kotlin.test.Test
import kotlin.test.assertEquals

/** smoke 0.5.4 ملاحظة 4 — هيدر «الأشخاص» شفاف فوق المسرح ومصمت في «الكل» (الكروت ما تبانش تحت العنوان). */
class PeopleHeaderTest {
    @Test fun headerIsClearOnTheStageAndOpaqueInAll() {
        assertEquals(0f, headerFill(0f))
        assertEquals(1f, headerFill(1f), "«الكل» ⇒ مصمت")
        assertEquals(1f, headerFill(0.4f), "مصمت قبل ما الكروت توصل للعنوان")
        assertEquals(0.3f, headerFill(0.1f), 0.0001f)
    }
}
