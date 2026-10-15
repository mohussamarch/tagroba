package app.masroufy.ui.screens.onboarding

import app.masroufy.core.UiKey
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Language
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.UserProfile
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** رحلة أول تشغيل: الخطوات والتقدم والتخطي والرد والاختيارات المقفولة وما يروح للحفظ — دوال نقية. */
class OnboardingStateTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val saudi = Space(DEFAULT_SPACE_ID, "السعودية", "SA", Currency.SAR, "2025-01-01T00:00:00Z")
    private val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-05-12T09:00:00Z")
    private val profile = UserProfile(
        displayName = null, salaryMinor = null, payday = 28, gender = null, supportsDependents = null,
        dependentKinds = null, hasCar = null, renter = null, domesticWorker = null, business = null, onboardedAt = null,
    )

    @Test
    fun progressStartsAtOneOnTheCountryAndFillsWhenAnswered() {
        val country = OnbState(OnbStep.COUNTRY)
        assertEquals(OnbStep.COUNTRY, steps(country).first(), "أول كارت = البلد (الشكل خرج من الرحلة)")
        assertEquals(1, stepNumber(country))
        assertEquals(ONB_SEGMENTS, segmentCount(country))
        assertEquals(0, filledSegments(country))
        assertEquals(1, filledSegments(pickCountry(country, "SA")))
        assertEquals(ONB_SEGMENTS, filledSegments(OnbState(OnbStep.READY)))
        assertEquals(ONB_SEGMENTS, stepNumber(OnbState(OnbStep.READY)))
    }

    @Test
    fun savedStateFromOldBuildsComesBackSafely() {
        // النسخ القديمة كانت بتحفظ الترتيب: 0 = «اختر شكلك» ⇒ البلد · 1 = البلد · 4 = يوم الراتب
        assertEquals(OnbStep.COUNTRY, restoreStep(0))
        assertEquals(OnbStep.COUNTRY, restoreStep("LOOK"))
        assertEquals(OnbStep.COUNTRY, restoreStep(1))
        assertEquals(OnbStep.PAYDAY, restoreStep(4))
        assertEquals(OnbStep.READY, restoreStep("READY"))
        assertEquals(OnbStep.COUNTRY, restoreStep(null))
        assertEquals(OnbStep.COUNTRY, restoreStep(99))
        assertNull(restoreReply(0), "رد الشكل القديم ⇒ مفيش رد")
        assertEquals(OnbReply.COUNTRY_EG, restoreReply(2))
        assertEquals(OnbReply.SMS, restoreReply("SMS"))
        assertEquals(OnbSource.FILE, restoreSource(1))
        assertEquals(OnbSource.LATER, restoreSource("LATER"))
        assertNull(restoreSource(null))
    }

    @Test
    fun backAndSkipOnlyWhereThePrototypeAllowsThem() {
        assertFalse(canBack(OnbState(OnbStep.COUNTRY)))
        assertTrue(canBack(OnbState(OnbStep.PAYDAY)))
        assertFalse(canSkip(OnbState(OnbStep.COUNTRY)))
        assertTrue(canSkip(OnbState(OnbStep.PAYDAY)))
        assertFalse(canSkip(pickDay(OnbState(OnbStep.PAYDAY), 27)))
        assertEquals(OnbStep.PAYDAY, back(OnbState(OnbStep.SOURCE)).step)
        assertEquals(OnbStep.READY, next(OnbState(OnbStep.READY)).step)
    }

    @Test
    fun skippingPaydayKeepsTheSavedDayAndNeverInventsOne() {
        val s = skip(OnbState(OnbStep.PAYDAY))
        assertTrue(s.answers.dayLater)
        assertNull(s.answers.day)
        assertEquals(OnbReply.DAY_LATER, s.reply)
        assertEquals(28, finishInput(profile, s.answers).profile.payday)
        assertEquals(t(UiKey.ONB_R_DAY_LATER_L, sentenceNumber(28)), replyText(OnbReply.DAY_LATER, s.answers, 28).second)
        val picked = pickDay(OnbState(OnbStep.PAYDAY), 25)
        assertEquals(25, finishInput(profile, picked.answers).profile.payday)
        assertNull(finishInput(profile, picked.answers).cashMinor)
    }

    @Test
    fun egyptIsLockedWithItsReasonUntilItsSpaceExistsOrCanBeCreated() {
        val locked = countryOptions(listOf(saudi), canCreate = false)
        assertFalse(locked[0].off)
        assertTrue(locked[1].off)
        assertEquals(t(UiKey.ONB_COUNTRY_NOT_YET), locked[1].sub)
        assertFalse(countryOptions(listOf(saudi, egypt), canCreate = false)[1].off)
        assertFalse(countryOptions(listOf(saudi), canCreate = true)[1].off)
        assertEquals(OnbReply.COUNTRY_EG, pickCountry(OnbState(OnbStep.COUNTRY), "eg").reply)
    }

    /** «ادخل التطبيق» بمصر (رد المالك §88-8): بتتعمل من طريق «البلدان»، أو بترجع من الأرشيف، والفشل بيرجع فشل (مش السعودية في صمت). */
    @Test
    fun egyptIsCreatedOrBroughtBackThroughTheSpacesAdmin() = kotlinx.coroutines.runBlocking<Unit> {
        val calls = mutableListOf<String>()
        fun admin(archived: List<Space> = emptyList(), fail: Boolean = false) = object : app.masroufy.ui.screens.more.SpacesAdmin {
            override suspend fun create(countryCode: String): Space { calls += "create:$countryCode"; if (fail) error("مفيش نت"); return egypt }
            override suspend fun archive(spaceId: String): Space = error("مش متوقع")
            override suspend fun unarchive(spaceId: String): Space { calls += "back:$spaceId"; return egypt }
            override suspend fun archived(): List<Space> = archived
        }
        val eg = OnbAnswers(countryCode = "EG")
        assertEquals("eg", countrySpaceFor(eg, listOf(saudi), admin()).getOrNull())
        assertEquals("eg", countrySpaceFor(eg, listOf(saudi), admin(archived = listOf(egypt.copy(archived = true)))).getOrNull())
        assertEquals(listOf("create:EG", "back:eg"), calls)
        assertTrue(countrySpaceFor(eg, listOf(saudi), admin(fail = true)).isFailure)
        calls.clear()
        // مفتوحة أصلًا · اختار السعودية · ما اختارش · الإنشاء مش متاح ⇒ ولا نداء
        assertNull(countrySpaceFor(eg, listOf(saudi, egypt), admin()).getOrThrow())
        assertNull(countrySpaceFor(OnbAnswers(countryCode = "SA"), listOf(saudi), admin()).getOrThrow())
        assertNull(countrySpaceFor(OnbAnswers(), listOf(saudi), admin()).getOrThrow())
        assertNull(countrySpaceFor(eg, listOf(saudi), null).getOrThrow())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun bankSmsIsOffOnIphoneWithItsReason() {
        val ios = sourceOptions(smsReadable = false)
        assertTrue(ios[0].off)
        assertEquals(t(UiKey.ONB_SRC_SMS_IOS), ios[0].sub)
        assertFalse(sourceOptions(smsReadable = true)[0].off)
        assertEquals(t(UiKey.ONB_DONE_NEXT), nextLabel(OnbState(OnbStep.SOURCE)))
    }

    @Test
    fun switchesSpaceOnlyWhenTheChosenCountryIsOpenAndNotActive() {
        val eg = OnbAnswers(countryCode = "EG")
        assertEquals("eg", spaceToOpen(eg, listOf(saudi, egypt), DEFAULT_SPACE_ID))
        assertNull(spaceToOpen(eg, listOf(saudi), DEFAULT_SPACE_ID))
        assertNull(spaceToOpen(OnbAnswers(countryCode = "SA"), listOf(saudi, egypt), DEFAULT_SPACE_ID))
        assertNull(spaceToOpen(OnbAnswers(), listOf(saudi, egypt), DEFAULT_SPACE_ID))
    }

    @Test
    fun egyptianWordingDiffersFromSaudi() {
        val msa = stepSub(OnbStep.COUNTRY)
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        val eg = stepSub(OnbStep.COUNTRY)
        assertTrue(msa != eg, "Egyptian wording must come from the Egyptian table")
    }
}
