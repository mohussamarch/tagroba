package app.masroufy.ui.screens.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** r041b P2 (فحص المحاكي r041 — اللقطات 90 · 91 · re-02): رجوع الجهاز جوه «أول تشغيل» والمسودة على الجهاز. بيانات مخترعة. */
class OnboardingDraftTest {
    private val fresh = OnbState(OnbStep.NAME, fresh = true, home = "SA")

    @Test fun systemBackGoesToThePreviousCardAndNeverPastTheFirst() {
        assertNull(systemBack(fresh), "first card ⇒ leave the app, never home")
        val bank = fresh.copy(step = OnbStep.BANK, answers = OnbAnswers(countryCode = "SA", name = "سامي", gender = "male"))
        assertEquals(OnbStep.CASH, systemBack(bank)?.step)
        // «الكاش» و«جاهز» مالهمش سهم فوق — الجهاز برضه بيرجع كارت
        assertEquals(OnbStep.COUNTRY, systemBack(bank.copy(step = OnbStep.CASH))?.step)
        assertEquals(OnbStep.SOURCE, systemBack(bank.copy(step = OnbStep.READY))?.step)
        // من البلد للاسم: الإجابات زي ما هي والرد «أهلًا …» ظاهر
        val name = systemBack(bank.copy(step = OnbStep.COUNTRY))!!
        assertEquals(OnbStep.NAME, name.step)
        assertEquals("سامي", name.answers.name)
        assertEquals(OnbReply.NAMED, name.reply)
    }

    @Test fun draftRoundTripsEveryAnswer() {
        val s = OnbState(
            OnbStep.PAYDAY,
            OnbAnswers("EG", day = 25, dayLater = false, source = OnbSource.SMS, cashMinor = 123_450L,
                bank = OnbBank("cib", "1234", 9_900L), name = "منى\u001Fعلي", gender = "female"),
            reply = OnbReply.DAY, fresh = true, home = "SA", otherCountryMoney = true,
        )
        val back = decodeDraft(encodeDraft(s))!!
        assertEquals(s.copy(answers = s.answers.copy(name = "منى علي")), back)
        val bare = OnbState(OnbStep.COUNTRY, OnbAnswers(dayLater = true, bank = OnbBank("rajhi", null, null)))
        assertEquals(bare, decodeDraft(encodeDraft(bare)))
    }

    @Test fun brokenOrForeignDraftStartsOver() {
        assertNull(decodeDraft(null))
        assertNull(decodeDraft(""))
        assertNull(decodeDraft("d0\u001FNAME"))
        assertNull(decodeDraft(encodeDraft(fresh).replace("NAME", "LOOK")))
    }

    @Test fun draftWinsOnlyOverAnUntouchedState() {
        val draft = fresh.copy(step = OnbStep.BANK, answers = OnbAnswers(countryCode = "SA", name = "سامي"))
        assertSame(draft, resumeFrom(OnbState(OnbStep.NAME), draft))
        val rotated = OnbState(OnbStep.PAYDAY, OnbAnswers(name = "هالة"))
        assertSame(rotated, resumeFrom(rotated, draft), "state restored by the device itself wins")
        val none = OnbState(OnbStep.NAME)
        assertSame(none, resumeFrom(none, null))
    }
}
