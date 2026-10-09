package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.EstateItem
import app.masroufy.core.EstateOwner
import app.masroufy.core.HeirKind
import app.masroufy.core.InheritanceCase
import app.masroufy.core.InheritanceScenario
import app.masroufy.core.Language
import app.masroufy.core.PredeceasedChild
import app.masroufy.core.SpecialCircumstance
import app.masroufy.core.Texts
import app.masroufy.core.calculateInheritance
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «حاسبة الورث» بخطواتها (`InheritanceCalculator` · `InheritanceHeirs` · `InheritanceBefore`) و«الحسبات المحفوظة» (`InheritanceSaved`):
 * المسودة كنصوص ⇒ `InheritanceCase` (من غير حساب)، فحص كل خطوة قبل «التالي»، الحفظ مع الشاشة، والحسبة المحفوظة ⇒ مسودة على النتيجة.
 */
class InheritanceDraftTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test
    fun eachStepChecksItsOwnFieldsBeforeNext() {
        val start = InheritanceDraft("SA", estateOf = EstateOwner.OTHER)
        assertEquals("اختر شخصًا من أشخاصك أو اكتب اسمًا", checkStep(start))
        assertNull(checkStep(start.copy(personName = "سالم")))
        assertNull(checkStep(start.copy(personId = "p-1")))

        val items = InheritanceDraft("SA", step = 2)
        assertEquals("أضف شيئًا واحدًا على الأقل من التركة", checkStep(items))
        assertEquals("اكتب قيمة تقريبية لكل شيء، أو احذفه", checkStep(items.copy(items = listOf(EstateRow("الشقة", "")))), "قيمة مجهولة ⇒ تتكتب أو تتشال (مش صفر)")
        assertNotNull(checkStep(items.copy(items = listOf(EstateRow(" ", "100")))))
        assertNull(checkStep(items.copy(items = listOf(EstateRow("الشقة", "576,000")))))

        val before = InheritanceDraft("SA", step = 4)
        assertNotNull(checkStep(before.copy(before = BeforeDraft(debts = "abc"))))
        assertNull(checkStep(before.copy(before = BeforeDraft(debts = "15,000"))))
    }

    @Test
    fun theDraftBecomesTheCaseWithoutAnyCalculation() {
        val d = InheritanceDraft(
            countryCode = "EG",
            items = listOf(EstateRow("شقة المعادي", "2,400,000"), EstateRow("وديعة", "٦٠٠٠٠٠")),
            heirs = mapOf(HeirKind.WIFE to 1, HeirKind.SON to 2, HeirKind.DAUGHTER to 0),
            names = mapOf(HeirKind.SON to "يوسف، عمر", HeirKind.DAUGHTER to "ريم"),
            before = BeforeDraft(funeral = "15000", bequest = "1000", toHeir = true, preOn = true, preSon = false, preDaughters = 1),
        )
        val c = d.toCase()!!
        assertEquals(listOf(EstateItem("شقة المعادي", 240_000_000), EstateItem("وديعة", 60_000_000)), c.items)
        assertEquals(mapOf(HeirKind.WIFE to 1, HeirKind.SON to 2), c.heirs, "العدد صفر ما بيدخلش")
        assertEquals(mapOf(HeirKind.SON to listOf("يوسف", "عمر")), c.names, "أسامي وارث مش موجود بتتشال")
        assertEquals(1_500_000, c.funeralMinor)
        assertEquals(100_000, c.bequest!!.amountMinor)
        assertTrue(c.bequest!!.toHeir)
        assertEquals(listOf(PredeceasedChild(false, 0, 1, 0)), c.predeceasedChildren, "مصر بس: الوصية الواجبة")
        assertTrue(InheritanceDraft("SA", before = BeforeDraft(preOn = true)).toCase()!!.predeceasedChildren.isEmpty(), "السعودية ما فيهاش وصية واجبة")
        assertNull(d.copy(items = listOf(EstateRow("x", "1.234"))).toCase(), "مبلغ مش مقروء ⇒ مفيش مسألة")
    }

    @Test
    fun theDraftSurvivesTheScreenStateExactly() {
        val d = InheritanceDraft(
            countryCode = "SA", scenarioId = "inherit-1", scenarioName = "تركتي — بعد الشقة", estateOf = EstateOwner.OTHER, personId = "p-1",
            personName = "سالم", items = listOf(EstateRow("الشقة", "576,000.00"), EstateRow("ذهب", "")),
            heirs = mapOf(HeirKind.WIFE to 1, HeirKind.SON to 2), names = mapOf(HeirKind.SON to "يوسف، عمر"),
            before = BeforeDraft(debts = "15000", consent = true, special = setOf(SpecialCircumstance.MISSING_HEIR, SpecialCircumstance.TAKHARUJ)),
            distantRelatives = false, step = 4,
        )
        assertEquals(d, decodeDraft(encodeDraft(d)))
        assertEquals(InheritanceDraft("EG"), decodeDraft(encodeDraft(InheritanceDraft("EG"))))
        assertNull(decodeDraft("نص مش مسودة"))
    }

    @Test
    fun aSavedScenarioOpensOnTheResultWithItsOwnCountry() {
        val case = InheritanceCase(
            countryCode = "EG",
            heirs = mapOf(HeirKind.WIFE to 1, HeirKind.SON to 1, HeirKind.DAUGHTER to 1),
            items = listOf(EstateItem("شقة المعادي", 240_000_000)),
            funeralMinor = 1_500_000,
            predeceasedChildren = listOf(PredeceasedChild(false, 1, 1, 0)),
        )
        val s = InheritanceScenario("inherit-2", "تركة الوالد — للتخطيط", EstateOwner.OTHER, "p-9", case, "2026-09-15T10:00:00Z", "2026-09-21T10:00:00Z")
        val d = draftFrom(s, listOf(PersonChoice("p-9", "سالم")))
        assertEquals(5, d.step)
        assertEquals("EG", d.countryCode)
        assertEquals(Currency.EGP, d.currency)
        assertEquals("سالم", d.personName)
        assertEquals("2,400,000.00", d.items.single().value)
        assertEquals("15,000.00", d.before.funeral)
        assertEquals("", d.before.debts, "صفر محفوظ ⇒ الخانة فاضية")
        assertTrue(d.before.preOn)
        assertEquals(case, d.toCase(), "فتح وحفظ من غير تعديل = نفس المسألة")
        assertEquals("تركة الوالد — للتخطيط", d.toScenarioDraft("تركة الوالد — للتخطيط")!!.name)
        assertEquals("تركة الوالد — للتخطيط", defaultScenarioName(d, "2026-10-07"), "المحفوظة بتحتفظ باسمها")
        assertEquals("تركتي — ٧ أكتوبر", defaultScenarioName(InheritanceDraft("SA"), "2026-10-07"))
        assertEquals("تركة سالم — ٧ أكتوبر", defaultScenarioName(InheritanceDraft("SA", estateOf = EstateOwner.OTHER, personName = " سالم "), "2026-10-07"))
    }

    @Test
    fun savedCardsShowTheEngineTotalAndNeverAGuessedOne() {
        val case = InheritanceCase("SA", mapOf(HeirKind.WIFE to 1, HeirKind.SON to 2, HeirKind.DAUGHTER to 1), listOf(EstateItem("الشقة", 57_600_000), EstateItem("ذهب", 5_256_000)))
        val s = InheritanceScenario("inherit-1", "تركتي — بعد شراء الشقة", EstateOwner.MINE, null, case, "2026-09-02T08:00:00Z", "2026-10-05T08:00:00Z")
        val row = savedRowUi(s, emptyList(), calculateInheritance(case))
        assertEquals(62_856_000, row.totalMinor, "مجموع التركة من المحرك")
        assertEquals("تركتي أنا", row.whose)
        assertEquals("بقانون السعودية", row.law)
        assertEquals("شيئان", row.itemsLine)
        assertEquals("الزوجة، الابن ٢، البنت", row.heirs)
        assertEquals("عُدّلت ٥ أكتوبر، أُنشئت ٢ سبتمبر", row.whenText)

        val draft = s.copy(input = case.copy(countryCode = "EG", heirs = emptyMap()), estateOf = EstateOwner.OTHER, personId = "p-1")
        val dRow = savedRowUi(draft, listOf(PersonChoice("p-1", "سالم")), calculateInheritance(draft.input))
        assertNull(dRow.totalMinor, "مسودة من غير نتيجة ⇒ «غير متاح» مش صفر")
        assertEquals("بلا ورثة بعد (مسودة)", dRow.heirs)
        assertEquals("تركة سالم", dRow.whose)
        assertTrue(dRow.lawEg)
        assertEquals(Currency.EGP, dRow.currency)
        assertEquals(1, maxCount(HeirKind.HUSBAND))
        assertEquals(4, maxCount(HeirKind.WIFE))
        assertNull(heirsSummary(emptyMap()))
    }
}
