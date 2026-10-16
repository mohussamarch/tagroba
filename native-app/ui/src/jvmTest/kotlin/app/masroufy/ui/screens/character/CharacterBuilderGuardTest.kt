package app.masroufy.ui.screens.character

import app.masroufy.core.CharacterLook
import app.masroufy.core.CharacterPart
import app.masroufy.core.normalized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** الخروج من البنّاء بتعديلات ما اتحفظتش + المسودة على الجهاز (طلب المالك 2026-10-16). */
class CharacterBuilderGuardTest {
    private class MemoryDraft : CharacterDraftStore {
        var value: String? = null
        override fun read() = value
        override fun write(value: String?) { this.value = value }
    }

    private val saved = startBuilder(CharacterLook.START, "male")

    @Test fun noChangesLeavesAtOnceAndChangesAskFirst() {
        assertFalse(isDirty(saved.look, saved.look))
        assertEquals(ExitStep.LEAVE, exitStep(isDirty(saved.look, saved.look)))
        val edited = pick(saved, CharacterPart.HAIR, 3)
        assertTrue(isDirty(edited.look, saved.look))
        assertEquals(ExitStep.CONFIRM, exitStep(isDirty(edited.look, saved.look)))
        // الرجوع لنفس الشكل = مفيش تعديلات
        assertFalse(isDirty(pick(edited, CharacterPart.HAIR, saved.look.hair).look, saved.look))
    }

    @Test fun onlySaveAndLeaveClearTheDraft() {
        assertTrue(clearsDraft(ExitChoice.SAVE))
        assertTrue(clearsDraft(ExitChoice.LEAVE))
        assertFalse(clearsDraft(ExitChoice.STAY))
    }

    @Test fun draftRoundTripsAndIsOfferedOnlyWhenDifferent() {
        val edited = pick(pick(saved, CharacterPart.HAIR, 3), CharacterPart.GLASSES, 2).look
        val store = MemoryDraft()
        store.write(encodeDraft(edited))
        val back = decodeDraft(store.read())
        assertEquals(edited.normalized(), back)
        assertEquals(back, draftToOffer(back, saved.look), "البرنامج اتقفل بتعديلات ⇒ «كمّل من آخر تعديل»")
        assertNull(draftToOffer(decodeDraft(encodeDraft(saved.look)), saved.look), "نفس المحفوظ ⇒ مفيش عرض")
        assertNull(decodeDraft(null))
        assertNull(decodeDraft("بايظ"))
        assertNull(decodeDraft("hair=x"))
    }
}
