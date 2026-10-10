package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.UiKey
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.EgyptInsuredCategory
import app.masroufy.core.EosEnd
import app.masroufy.core.EosPart
import app.masroufy.core.Language
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.usecase.RetirementCalculator
import app.masroufy.usecase.RetirementCalculatorDeps
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «حاسبة التقاعد» (`RetirementCalculator` ⇒ `RetirementSaudi` · `RetirementEgypt`): الخانات ⇒ `RetirementRequest`، والنتيجة ⇒ البطاقة البطلة
 * وسطري المكافأة و«كم تدّخر شهريًا». كل رقم من حالة الاستخدام؛ الناقص «غير متاح» بسببه، مش صفر.
 */
class RetirementPresenterTest {
    private val today = "2026-10-07"
    private val calc = RetirementCalculator(RetirementCalculatorDeps(MemoryIncomeSourceRepository()))

    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val saudi = mapOf(
        SaudiFields.BIRTH to "1990-04-12", SaudiFields.SO_FAR to "103", SaudiFields.BASIC to "10000", SaudiFields.HOUSING to "2500",
        SaudiFields.JOB to "2021-02-01", SaudiFields.EOS_WAGE to "12500", SaudiFields.WANT to "15000", SaudiFields.YEARS to "20", SaudiFields.HAVE to "40000",
    )

    @Test
    fun saudiNewSystemShowsThePensionTheAwardAndTheMonthlySaving() = runBlocking {
        val check = checkSaudi(saudi, contributedBefore2024 = false, end = EosEnd.EMPLOYER_OR_CONTRACT_END, currency = Currency.SAR, today = today)
        assertTrue(check.errors.isEmpty(), "${check.errors}")
        val outcome = calc.calculate(check.request!!, today)
        val ui = retirementUi(RetirementResult(outcome, check.eosChosen), Currency.SAR, ::saudiHeroDetails, UiKey.RETCALC_GAP_SUB)

        assertNotNull(ui.pensionMinor)
        assertEquals(outcome.pension.pensionMinor, ui.pensionMinor, "الرقم الكبير من حالة الاستخدام بالظبط")
        assertTrue(ui.heroSub.startsWith("النظام الجديد، التقاعد عند ٦٥ سنة في أبريل ٢٠٥٥، "), ui.heroSub)
        val (eos, gap) = ui.outs
        assertEquals("مكافأة نهاية الخدمة", eos.label)
        assertEquals((outcome.endOfService as EosPart.Known).result.payableMinor, eos.amountMinor)
        assertTrue(eos.sub.endsWith("كاملة"), eos.sub)
        assertEquals(outcome.gap!!.gap!!.perMonthMinor, gap.amountMinor)
        assertTrue(gap.sub.startsWith("ينقصك ") && gap.sub.endsWith("، بلا أرباح"), gap.sub)
        assertEquals(4, saudiHow(outcome, Currency.SAR).size, "«كيف حُسب المعاش؟» أربع سطور")
    }

    @Test
    fun theWayTheJobEndsIsAskedEveryTimeBeforeTheAwardIsShown() = runBlocking {
        val check = checkSaudi(saudi, contributedBefore2024 = false, end = null, currency = Currency.SAR, today = today)
        val outcome = calc.calculate(check.request!!, today)
        assertNull(outcome.gap, "من غير اختيار ما بنفترضش — الفجوة ما بتتحسبش")
        val ui = retirementUi(RetirementResult(outcome, check.eosChosen), Currency.SAR, ::saudiHeroDetails, UiKey.RETCALC_GAP_SUB)
        assertNotNull(ui.pensionMinor, "المعاش نفسه بيتحسب")
        for (row in ui.outs) {
            assertNull(row.amountMinor)
            assertEquals("غير متاح", row.valueText)
            assertEquals("اختر كيف سينتهي عملك", row.sub)
            assertTrue(row.warn)
        }
    }

    @Test
    fun aMissingHousingAllowanceIsUnavailableNotZero() = runBlocking {
        val check = checkSaudi(saudi - SaudiFields.HOUSING, false, EosEnd.EMPLOYER_OR_CONTRACT_END, Currency.SAR, today)
        val ui = retirementUi(RetirementResult(calc.calculate(check.request!!, today)), Currency.SAR, ::saudiHeroDetails, UiKey.RETCALC_GAP_SUB)
        assertNull(ui.pensionMinor)
        assertEquals("غير متاح", ui.heroNa)
        val reason = ui.heroReason ?: ui.heroSub
        assertTrue(reason.startsWith("اكتب بدل السكن الشهري"), "السبب من غير «غير متاح:» مكررة: $reason")
        val gap = ui.outs[1]
        assertNull(gap.amountMinor)
        assertEquals("غير متاح", gap.valueText)
    }

    @Test
    fun wrongFieldsStopTheRequestAndKeepTheirErrors() {
        val check = checkSaudi(mapOf(SaudiFields.SO_FAR to "2000", SaudiFields.RETIRE to "abc"), true, null, Currency.SAR, today)
        assertNull(check.request)
        assertEquals(setOf(SaudiFields.BIRTH, SaudiFields.SO_FAR, SaudiFields.RETIRE), check.errors.keys)
        val idle = retirementIncomplete()
        assertNull(idle.pensionMinor)
        assertEquals("أكمل البيانات", idle.heroNa)
        val failed = retirementFailed()
        assertNull(failed.pensionMinor)
        assertEquals("غير متاح", failed.heroNa, "قراية وقعت ⇒ «غير متاح»، مش «أكمل البيانات» ولا صفر")
    }

    @Test
    fun egyptHasNoAwardAndAlwaysTheArticle163Note() = runBlocking {
        Texts.followCountry("EG")
        val fields = mapOf(
            EgyptFields.BIRTH to "1994-06-20", EgyptFields.SO_FAR to "81", EgyptFields.WAGE to "9000", EgyptFields.LEGAL to "65",
            EgyptFields.WANT to "12000", EgyptFields.YEARS to "20", EgyptFields.HAVE to "60000",
        )
        val check = checkEgypt(fields, EgyptInsuredCategory.EMPLOYEE, Currency.EGP, today)
        assertTrue(check.errors.isEmpty(), "${check.errors}")
        val outcome = calc.calculate(check.request!!, today)
        val ui = retirementUi(RetirementResult(outcome), Currency.EGP, ::egyptHeroDetails, UiKey.RETEG_GAP_SUB)
        assertEquals(outcome.pension.pensionMinor, ui.pensionMinor)
        val eos = ui.outs[0]
        assertNull(eos.amountMinor)
        assertEquals("مش منطبقة", eos.valueText)
        assertTrue(eos.sub.startsWith("في مصر مفيش مكافأة"), eos.sub)
        val notes = egyptNotes(outcome, calc.defaults("EG", today), Currency.EGP)
        assertTrue(notes.first().startsWith("الرقم ده من غير زيادة مادة 163"), notes.first())
        assertEquals(1, egyptNotes(null, null, Currency.EGP).size, "من غير نتيجة: ملاحظة 163 بس")
    }

    @Test
    fun egyptChecksTheLegalAgeAndTheLimits() {
        val check = checkEgypt(
            mapOf(EgyptFields.BIRTH to "1994-06-20", EgyptFields.SO_FAR to "81", EgyptFields.LEGAL to "70", EgyptFields.MIN to "2700", EgyptFields.MAX to "1000"),
            EgyptInsuredCategory.EMPLOYEE, Currency.EGP, today,
        )
        assertNull(check.request)
        assertEquals(setOf(EgyptFields.LEGAL, EgyptFields.MAX), check.errors.keys)
        assertEquals("٨١.٨", coefficientText(818), "معامل جدول ٥ (× ١٠) بأرقام الجملة")
    }
}
