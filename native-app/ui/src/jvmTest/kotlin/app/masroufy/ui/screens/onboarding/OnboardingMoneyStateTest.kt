package app.masroufy.ui.screens.onboarding

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Language
import app.masroufy.core.Texts
import app.masroufy.core.UserProfile
import app.masroufy.usecase.OnboardingResult
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
    private val fresh = OnbState(OnbStep.COUNTRY, fresh = true, home = "SA")

    @Test fun onlyANewAccountGetsTheTwoCardsRightAfterTheCountry() {
        assertEquals(listOf(OnbStep.NAME, OnbStep.COUNTRY, OnbStep.CASH, OnbStep.BANK, OnbStep.PAYDAY, OnbStep.SOURCE, OnbStep.READY), steps(fresh))
        assertTrue(OnbStep.CASH !in steps(OnbState(OnbStep.COUNTRY)), "حساب قديم ما بيتسألش")
        val country = pickCountry(fresh, "SA")
        assertEquals(OnbStep.CASH, next(country).step, "بعد البلد على طول: الكاش")
        assertEquals(OnbStep.BANK, next(addCash(next(country), 10_000)).step, "وبعد الكاش: البنك")
        // الاسم أول كارت (r041) ⇒ «1 من 7» على الاسم و«7 من 7» على جاهز — الدخول مش جزء
        assertEquals(7, segmentCount(fresh), "7 كروت — الدخول مش جزء")
        assertEquals(1, stepNumber(fresh.copy(step = OnbStep.NAME)), "أول كارت = 1")
        assertEquals(2, stepNumber(fresh), "البلد = 2")
        assertEquals(1, filledSegments(fresh), "الاسم خلص")
        assertEquals(7, filledSegments(fresh.copy(step = OnbStep.READY)))
        assertEquals(3, stepNumber(fresh.copy(step = OnbStep.CASH)))
        assertEquals(7, stepNumber(fresh.copy(step = OnbStep.READY)), "جاهز = 7 من 7")
    }

    @Test fun anotherCountryHidesTheCards() {
        val egypt = pickCountry(fresh, "EG")
        assertFalse(money(egypt))
        assertEquals(OnbStep.PAYDAY, next(egypt).step)
        assertTrue(money(pickCountry(egypt, "SA")), "رجع لبلد الحساب ⇒ الكارتين رجعوا")
    }

    /** جولة 0.3: §81 رحلة الحساب الجديد لأي بلد — مصر بنفس الكارتين (بالجنيه وبنوك مصر) لما الفلوس تقدر تتكتب في محافظها. */
    @Test fun egyptGetsTheSameTwoCardsWhenItsMoneyCanGoThere() = runBlocking<Unit> {
        val start = fresh.copy(otherCountryMoney = true)
        val egypt = pickCountry(start, "EG")
        assertTrue(money(egypt) && moneyElsewhere(egypt))
        assertEquals(OnbStep.CASH, next(egypt).step, "بعد البلد: الكاش — مش «المحافظ»")
        assertEquals("EG", moneyCountry(egypt, "SA"), "عملة وبنوك مصر")
        assertFalse(moneyElsewhere(pickCountry(egypt, "SA")), "السعودية ⇒ في `OnboardAccount.finish` زي الأول")
        assertEquals("SA", moneyCountry(start, "SA"))

        val answers = OnbAnswers(countryCode = "EG", cashMinor = 300_000, bank = OnbBank("eg-national-bank-of-egypt", "0042", 1_250_000))
        var got: Triple<String, Long?, String?>? = null
        val sink = CountryMoney { id, cash, bank -> got = Triple(id, cash, bank?.bankId); OnboardingResult.Ok }
        assertEquals(OnboardingResult.Ok, moneyInto("eg", profile, answers, sink))
        assertEquals(Triple("eg", 300_000L, "eg-national-bank-of-egypt"), got, "الكاش والبنك رايحين لمحافظ مصر")
        assertIs<OnboardingResult.Failed>(moneyInto(null, profile, answers, sink), "البلد ما اتعملتش ⇒ نفضل على «جاهز»")
        assertIs<OnboardingResult.Failed>(moneyInto("eg", profile, answers, null))
        assertIs<OnboardingResult.Failed>(moneyInto("eg", profile, answers) { _, _, _ -> error("انقطع") }, "عطل ⇒ رسالة مش وقوع")
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
