package app.masroufy.ui.screens.home

import app.masroufy.core.ProfileCheck
import app.masroufy.core.checkProfile
import app.masroufy.core.emptyProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * «كمّل ملفك»: سؤال واحد بالترتيب (السيارة ⇒ العائلة ⇒ السكن ⇒ العمل الخاص) للحقول اللي لسه فاضية بس، والإجابة ⇒ حقول يقبلها `checkProfile`.
 */
class ProfileQuestionModelTest {
    @Test fun oneQuestionAtATimeInOrder() {
        val p = emptyProfile()
        assertEquals(ProfileCard.CAR, nextProfileCard(null))
        assertEquals(ProfileCard.CAR, nextProfileCard(p))
        assertEquals(ProfileCard.FAMILY, nextProfileCard(p.copy(hasCar = false)))
        assertEquals(ProfileCard.HOME, nextProfileCard(p.copy(hasCar = true, supportsDependents = false)))
        assertEquals(ProfileCard.BIZ, nextProfileCard(p.copy(hasCar = true, supportsDependents = true, renter = false)))
        assertNull(nextProfileCard(p.copy(hasCar = true, supportsDependents = true, renter = false, business = false)), "خلص ⇒ الكارت بيختفي")
    }

    @Test fun answersBecomeProfileFieldsTheLogicAccepts() {
        val p = emptyProfile()
        assertEquals(true, applyAnswer(p, ProfileCard.CAR, "yes").hasCar)
        assertEquals(false, applyAnswer(p, ProfileCard.CAR, "no").hasCar)
        val spouse = applyAnswer(p, ProfileCard.FAMILY, "spouse")
        assertEquals(listOf("spouse", "children"), spouse.dependentKinds)
        val parents = applyAnswer(p, ProfileCard.FAMILY, "parents")
        assertEquals(listOf("parents"), parents.dependentKinds)
        val none = applyAnswer(p, ProfileCard.FAMILY, "no")
        assertEquals(false, none.supportsDependents)
        assertNull(none.dependentKinds)
        assertEquals(true, applyAnswer(p, ProfileCard.HOME, "rent").renter)
        assertEquals(false, applyAnswer(p, ProfileCard.HOME, "family").renter)
        assertEquals(true, applyAnswer(p, ProfileCard.BIZ, "yes").business)
        for (card in ProfileCard.entries) for (o in optionsOf(card)) {
            assertIs<ProfileCheck.Ok>(checkProfile(applyAnswer(p, card, o.id)), "$card/${o.id}")
        }
        assertEquals(p, applyAnswer(p, ProfileCard.CAR_TO_WORK, "yes"), "«تذهب بها للعمل» ليها حالة استخدام لوحدها")
    }
}
