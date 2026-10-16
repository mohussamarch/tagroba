package app.masroufy.ui.screens.onboarding

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Language
import app.masroufy.core.Texts
import app.masroufy.core.UserProfile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * «بماذا نناديك؟» أول كارت بعد الدخول (r041 — character-spec §4.1): الاسم لازم والنوع = الإجابة، والاتنين بيتكتبوا في الآخر بس
 * (`finishInput`). بيانات مخترعة.
 */
class OnboardingNameStateTest {
    @AfterTest fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val profile = UserProfile(
        displayName = null, salaryMinor = null, payday = 28, gender = null, supportsDependents = null,
        dependentKinds = null, hasCar = null, renter = null, domesticWorker = null, business = null, onboardedAt = null,
    )

    @Test fun bothNameAndGenderAreRequiredBeforeContinue() {
        val s = OnbState(OnbStep.NAME)
        val onlyGender = pickGender(s, GENDER_MALE)
        assertEquals("male", onlyGender.answers.gender, "صف النوع بيتعلّم حتى والاسم فاضي")
        assertNull(onlyGender.reply, "الاسم فاضي ⇒ مفيش «متابعة» (قرار المالك 2026-10-16)")
        assertNull(typeName(s, "   ").reply, "مسافات بس ⇒ مفيش اسم")
        assertNull(typeName(s, "سامي").reply, "اسم من غير نوع ⇒ مفيش «متابعة»")
        assertFalse(canSkip(s), "الاسم ما بيتخطاش")
        assertFalse(canBack(s), "مفيش رجوع للدخول")
    }

    @Test fun nameAndGenderGiveTheReplyAndEditingTheNameUpdatesIt() {
        val s = pickGender(typeName(OnbState(OnbStep.NAME), "  سامي "), GENDER_MALE)
        assertEquals(OnbReply.NAMED, s.reply)
        assertEquals("male", s.answers.gender)
        assertEquals("أهلًا سامي", replyText(OnbReply.NAMED, s.answers, 28).first)
        assertEquals("أهلًا سامح", replyText(OnbReply.NAMED, typeName(s, "سامح").answers, 28).first, "تعديل الاسم بعد الرد بيحدّثه")
        assertNull(typeName(s, "").reply, "الاسم اتمسح ⇒ «متابعة» تختفي")
        assertEquals(OnbStep.COUNTRY, next(s).step, "بعد الاسم: البلد")
        assertTrue(canBack(next(s)), "من البلد رجوع للاسم")
        val again = back(next(s))
        assertEquals(OnbStep.NAME, again.step)
        assertEquals(OnbReply.NAMED, again.reply, "رجع والاتنين موجودين ⇒ «متابعة» ظاهرة")
    }

    @Test fun anOlderAccountStartsWithItsSavedNameAndGender() {
        val s = prefillName(OnbState(OnbStep.NAME), "هالة", "female")
        assertEquals("هالة", s.answers.name)
        assertEquals(OnbReply.NAMED, s.reply)
        assertSame(s, prefillName(s, "غيره", "male"), "اللي اتكتب في الكارت ما يتغطّاش")
        assertNull(prefillName(OnbState(OnbStep.NAME), null, "other").answers.gender, "نوع مش معروف ⇒ مش متختار")
    }

    @Test fun theNameFieldStopsAt24Characters() {
        assertEquals(24, typedName("ا".repeat(40)).length)
        assertEquals("منى", typedName("منى"))
    }

    @Test fun nameAndGenderAreWrittenOnlyAtTheEnd() {
        val answers = OnbAnswers(name = "منى", gender = GENDER_FEMALE, day = 25)
        val input = finishInput(profile, answers)
        assertEquals("منى", input.profile.displayName)
        assertEquals("female", input.profile.gender)
        assertEquals(25, input.profile.payday)
        // ما اتجاوبش (حالة قديمة اترجعت بعد الكارت ده) ⇒ اللي في الملف زي ما هو
        val kept = finishInput(profile.copy(displayName = "هالة", gender = "female"), OnbAnswers())
        assertEquals("هالة", kept.profile.displayName)
        assertEquals("female", kept.profile.gender)
        assertNull(finishInput(profile, OnbAnswers(name = "  ")).profile.displayName)
    }

    @Test fun theTitleFollowsTheArabicVariant() {
        Texts.arabicVariant = ArabicVariant.MSA
        assertEquals("بماذا نناديك؟", app.masroufy.ui.text.t(stepTitle(OnbStep.NAME)))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("نناديك بإيه؟", app.masroufy.ui.text.t(stepTitle(OnbStep.NAME)))
        assertEquals("راجل", genderOptions().first().label)
    }
}
