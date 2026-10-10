package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** رسالة خطأ بتتعرض للمستخدم بس لو طالعة من جداول النصوص — رسائل التخزين والشبكة لأ. */
class TextMatchTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test
    fun useCaseMessagesAreUiText() {
        assertTrue(isUiText(uiText(TextKey.REVIEW_CHANGED)))
        assertTrue(isUiText(uiText(TextKey.TXN_NOT_FOUND_ID, "t-42")))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertTrue(isUiText(uiText(TextKey.REVIEW_CHANGED)))
        Texts.language = Language.EN
        assertTrue(isUiText(uiText(TextKey.TXN_NOT_FOUND_ID, "t-42")))
    }

    @Test
    fun storageAndNetworkMessagesAreNot() {
        assertFalse(isUiText("PERMISSION_DENIED: Missing or insufficient permissions."))
        assertFalse(isUiText("Failed to get document because the client is offline."))
        assertFalse(isUiText("حالة تجهيز المراجع غير سليمة"))
        assertFalse(isUiText(""))
    }

    @Test
    fun placeholderOnlyTemplatesMatchNothing() {
        assertFalse(templateMatches("{0}", "anything"))
        assertFalse(templateMatches("{0}: {1}", "a: b"))
        assertTrue(templateMatches("عملية غير موجودة: {0}", "عملية غير موجودة: x"))
        assertFalse(templateMatches("عملية غير موجودة: {0}", "عملية: x"))
    }
}
