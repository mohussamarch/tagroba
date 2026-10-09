package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Language
import app.masroufy.core.Texts
import app.masroufy.ui.nav.Navigator
import app.masroufy.ui.screens.buildRegistry
import app.masroufy.ui.screens.investment.InheritanceCalculatorRoute
import app.masroufy.ui.screens.investment.InheritanceSavedRoute
import app.masroufy.ui.screens.investment.InheritanceScenarioRoute
import app.masroufy.ui.screens.investment.RetirementCalculatorRoute
import app.masroufy.ui.screens.investment.SavingsCalculatorRoute
import app.masroufy.ui.screens.operations.ReviewQueueRoute
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * الحاسبات متسجّلة من ملف منطقة «الاستثمار» (`registerInvestment` ⇒ `registerCalculators`) بأسماء لوحات النموذج، والحسبة المحفوظة بتفتح
 * نفس الشاشة. «المراجعة» بتاعة «العمليات» لسه مش متسجّلة هنا ⇒ «قيد البناء» لحد الدمج (مش وقوع). وصيغ المدد والسن في الجمل.
 */
class CalculatorRoutesTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test
    fun everyCalculatorScreenIsRegisteredByItsBoardName() {
        val r = buildRegistry()
        for (route in listOf(SavingsCalculatorRoute, RetirementCalculatorRoute, InheritanceCalculatorRoute, InheritanceScenarioRoute("inherit-1"), InheritanceSavedRoute)) {
            assertTrue(r.has(route), route.name)
        }
        assertEquals(
            listOf("SavingsCalculator", "RetirementCalculator", "InheritanceCalculator", "InheritanceCalculator", "InheritanceSaved"),
            listOf(SavingsCalculatorRoute, RetirementCalculatorRoute, InheritanceCalculatorRoute, InheritanceScenarioRoute("x"), InheritanceSavedRoute).map { it.name },
        )
        assertFalse(r.has(ReviewQueueRoute), "شاشة «العمليات» — بتتسجّل من منطقتها وقت الدمج")
    }

    @Test
    fun openingASavedScenarioFromTheSavedListReplacesTheCalculatorBelow() {
        val nav = Navigator()
        nav.push(InheritanceCalculatorRoute)
        nav.push(InheritanceSavedRoute)
        // نفس خطوات «افتح» في الحسبات المحفوظة
        val belowIsCalculator = nav.stack[nav.stack.lastIndex - 1].route.name == InheritanceCalculatorRoute.name
        if (belowIsCalculator) nav.pop()
        nav.replace(InheritanceScenarioRoute("inherit-1"))
        assertEquals(listOf<Any>(InheritanceScenarioRoute("inherit-1")), nav.stack.map { it.route }, "من غير حاسبة فوق حاسبة")
    }

    @Test
    fun durationsAndAgesReadNaturallyInBothArabics() {
        assertEquals(listOf("شهر واحد", "شهران", "٣ أشهر", "١١ شهرًا"), listOf(1, 2, 3, 11).map(::monthsPhrase))
        assertEquals(listOf("سنة", "سنتان", "١٠ سنوات", "١٥ شهرًا"), listOf(12, 24, 120, 15).map(::durationPhrase), "سنين كاملة بالسنين")
        assertEquals("٦٤ سنة و٨ أشهر", agePhrase(64 * 12 + 8))
        assertEquals("٦٥ سنة", agePhrase(65 * 12))
        assertEquals("٧ أكتوبر ٢٠٢٨", fullDate("2028-10-07"))
        Texts.followCountry("EG")
        assertEquals(listOf("شهرين", "٣ شهور", "١١ شهر", "سنتين", "٥ سنين"), listOf(monthsPhrase(2), monthsPhrase(3), monthsPhrase(11), yearsPhrase(2), yearsPhrase(5)))
    }

    @Test
    fun fieldsAreReadAsTextWithoutGuessing() {
        assertEquals(Parsed.Empty, parseCountField("  "))
        assertEquals(Parsed.Ok(36), parseCountField("٣٦"))
        assertEquals(Parsed.Bad, parseCountField("3.5"))
        assertEquals(Parsed.Bad, parseCountField("1234567"))
        assertEquals(Parsed.Ok("2028-10-07"), parseDateField("2028-10-07"))
        assertEquals(Parsed.Bad, parseDateField("2028-02-30"))
        assertEquals(Parsed.Bad, parseAmountField("12.345", app.masroufy.core.Currency.SAR), "ما بيقربش بصمت")
        assertEquals("−12.50", plainAmount(-1250, app.masroufy.core.Currency.SAR), "علامة الطرح الحقيقية")
    }
}
