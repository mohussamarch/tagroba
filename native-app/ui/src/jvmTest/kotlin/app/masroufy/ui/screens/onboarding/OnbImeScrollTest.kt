package app.masroufy.ui.screens.onboarding

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * r041 A9 — خطوة البنك: الشريط بقى ثابت برّه التمرير (بيتجرب على المحاكي)، والصفحة ترجع لأولها لما الكيبورد يتقفل بس — مش وهو بيتفتح
 * ولا من غير ما يتغير.
 */
class OnbImeScrollTest {
    @Test fun scrollsBackOnlyWhenTheKeyboardCloses() {
        assertTrue(scrollBackOnImeClose(wasOpen = true, open = false))
        assertFalse(scrollBackOnImeClose(wasOpen = false, open = true), "opening ⇒ «أضف البنك» comes into view instead")
        assertFalse(scrollBackOnImeClose(wasOpen = false, open = false), "first frame")
        assertFalse(scrollBackOnImeClose(wasOpen = true, open = true))
    }
}
