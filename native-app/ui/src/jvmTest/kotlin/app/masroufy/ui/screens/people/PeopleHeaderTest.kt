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

    /** fix055: آخر صف في «الكل» بيقف فوق مسافة القوايم (156) + شريط أزرار الجوال — مش لازق في «اسأل مصروفي». */
    @Test fun allListEndsAboveTheAskBarAndTheSystemBar() {
        val top = 24f; val view = 800f; val drum = 7 * 114f
        val plain = allListExtra(top, drum, view, navInset = 0f)
        val withBar = allListExtra(top, drum, view, navInset = 24f)
        assertEquals(24f, withBar - plain, 0.0001f, "شريط الجوال بيزوّد التمرير بطوله")
        // آخر الأسطوانة بعد التمرير كله = top + 82 + drum − extra ⇒ فوق الشريطين (14 + 70 + 6 + 44 = 134) + شريط الجوال
        val lastRowBottom = top + 82f + drum - withBar
        assertEquals(view - 156f - 24f, lastRowBottom, 0.0001f)
        assertEquals(0f, allListExtra(top, 100f, view, 24f), "قايمة قصيرة ما بتسكرولش زيادة")
    }
}
