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
import kotlin.test.assertTrue

/** كارتين البداية للحساب الجديد (OVERRIDES §81 — «معاك كام كاش؟» ثم «عندك حساب بنكي؟» بـ«إضافة/تخطي») كحالة نقية. أرقام مخترعة. */
class OnboardingMoneyStateTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val profile = UserProfile(
        displayName = null, salaryMinor = null, payday = 28, gender = null, supportsDependents = null,
        dependentKinds = null, hasCar = null, renter = null, domesticWorker = null, business = null, onboardedAt = null,
    )
    private val fresh = OnbState(OnbStep.LOOK, fresh = true, home = "SA")

    @Test fun onlyANewAccountGetsTheTwoCardsRightAfterTheCountry() {
        assertEquals(listOf(OnbStep.LOOK, OnbStep.COUNTRY, OnbStep.CASH, OnbStep.BANK, OnbStep.PAYDAY, OnbStep.SOURCE, OnbStep.READY), steps(fresh))
        assertTrue(OnbStep.CASH !in steps(OnbState(OnbStep.LOOK)), "حساب قديم ما بيتسألش")
        val country = pickCountry(next(fresh), "SA")
        assertEquals(OnbStep.CASH, next(country).step, "بعد البلد على طول: الكاش")
        assertEquals(OnbStep.BANK, next(addCash(next(country), 10_000)).step, "وبعد الكاش: البنك")
        assertEquals(8, segmentCount(fresh), "الدخول + 7 كروت")
        assertEquals(8, filledSegments(fresh.copy(step = OnbStep.READY)))
        assertEquals(4, stepNumber(fresh.copy(step = OnbStep.CASH)))
    }

    @Test fun anotherCountryHidesTheCards() {
        val egypt = pickCountry(next(fresh), "EG")
        assertFalse(money(egypt))
        assertEquals(OnbStep.PAYDAY, next(egypt).step)
        assertTrue(money(pickCountry(egypt, "SA")), "رجع لبلد الحساب ⇒ الكارتين رجعوا")
    }

    @Test fun addOrSkipAndBackFromTheBankOnly() {
        val cash = fresh.copy(step = OnbStep.CASH)
        assertTrue(canSkip(cash))
        assertFalse(canBack(cash), "قبل الكاش البلد — والبلد ما بيرجعش")
        val skipped = skip(cash)
        assertEquals(OnbReply.CASH_SKIP, skipped.reply)
        assertNull(skipped.answers.cashMinor)
        assertEquals(OnbReply.CASH, addCash(cash, 0).reply, "صفر مسموح — كتبه بنفسه")
        val bank = fresh.copy(step = OnbStep.BANK)
        assertTrue(canBack(bank) && canSkip(bank))
        assertEquals(OnbStep.CASH, back(bank).step)
        assertEquals(OnbStep.BANK, back(fresh.copy(step = OnbStep.PAYDAY)).step)
        assertEquals(OnbReply.BANK_SKIP, skip(bank).reply)
        val added = addBank(bank, OnbBank("sa-al-rajhi-bank", "0042", 250_000))
        assertEquals(OnbReply.BANK, added.reply)
        assertEquals("مصرف الراجحي •••• 0042", bankLine(added.answers.bank!!))
    }

    @Test fun finishCarriesCashAndBankOnlyWhenTheCardsWereShown() {
        val answers = OnbAnswers(countryCode = "SA", cashMinor = 75_050, bank = OnbBank("sa-al-rajhi-bank", null, 250_000))
        val input = finishInput(profile, answers, withMoney = true)
        assertEquals(75_050, input.cashMinor)
        val bank = input.bank!!
        assertEquals("sa-al-rajhi-bank", bank.bankId)
        assertEquals("bank", bank.kind)
        assertEquals("مصرف الراجحي", bank.name)
        assertEquals(250_000, bank.openingMinor)
        assertNull(bank.openingAt, "التاريخ = النهارده من `ManageWallets`")
        val without = finishInput(profile, answers, withMoney = false)
        assertNull(without.cashMinor)
        assertNull(without.bank)
    }
}
