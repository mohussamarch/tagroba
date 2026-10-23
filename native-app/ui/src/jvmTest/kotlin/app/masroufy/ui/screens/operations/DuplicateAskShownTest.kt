package app.masroufy.ui.screens.operations

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * fix053 (فحص 0.5.3): `duplicateAskPending = true` في فايربيز وكارت «مكرر؟» مش ظاهر — كان جوه كارت الوجبة والعلامتين اللي بيتخبى
 * مع رجل تحويل بين البلدين. الشرط دلوقتي السؤال نفسه بس. كل البيانات مخترعة.
 */
class DuplicateAskShownTest {
    @Test fun anOpenDuplicateQuestionIsShown() {
        assertTrue(duplicateAskShown(Fx.tx("d1", merchant = "TEST CAFE").copy(duplicateAskPending = true)))
    }

    @Test fun answeredOrNeverAskedIsNotShown() {
        assertFalse(duplicateAskShown(Fx.tx("d2").copy(duplicateAskPending = false)), "«الاتنين صح» ما يرجعش")
        assertFalse(duplicateAskShown(Fx.tx("d3")), "never asked")
    }
}
