package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.HeirKind
import app.masroufy.core.InheritanceLaw
import app.masroufy.core.Language
import app.masroufy.core.SpecialCircumstance
import app.masroufy.core.Texts
import app.masroufy.memory.FixedClock
import app.masroufy.usecase.CalculateInheritance
import app.masroufy.usecase.CalculateInheritanceDeps
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * نتيجة «حاسبة الورث» (`InheritanceResult` + `InheritanceSplit`): من المسودة ⇒ `CalculateInheritance` ⇒ حالة الشاشة. كل مبلغ وكسر من
 * المحرك (هنا الكلام والألوان) — ومجموع قسمة كل شيء = قيمته بالظبط.
 */
class InheritanceResultPresenterTest {
    private val clock = FixedClock("2026-10-07T09:00:00.000Z")
    private val saudi = CalculateInheritance(CalculateInheritanceDeps("SA", Currency.SAR, clock))
    private val egypt = CalculateInheritance(CalculateInheritanceDeps("EG", Currency.EGP, clock))

    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun draft(vararg heirs: Pair<HeirKind, Int>, country: String = "SA") = InheritanceDraft(
        countryCode = country,
        items = listOf(EstateRow("الشقة", "2,400")),
        heirs = heirs.toMap(),
        step = 5,
    )

    private fun ui(d: InheritanceDraft): InheritanceResultUi =
        inheritanceResultUi((if (d.countryCode == "EG") egypt else saudi).calculate(d.toCase()!!), d.currency)

    @Test
    fun wifeSonAndDaughterGetTheirSharesFromTheEngine() {
        val r = ui(draft(HeirKind.WIFE to 1, HeirKind.SON to 1, HeirKind.DAUGHTER to 1))
        val v = assertIs<InheritanceView.Computed>(r.view)
        assertEquals(240_000, v.poolMinor)
        assertTrue(v.claims.isEmpty())
        assertEquals("من تركة 2,400.00 ر.س، لا تجهيز ولا ديون ولا وصية، شيء واحد", v.heroSub)
        val wife = v.heirs.first { it.name == "الزوجة" }
        assertEquals(30_000, wife.amountMinor)
        assertEquals("1/8", wife.fraction)
        assertEquals("الثمن", wife.basis)
        val son = v.heirs.first { it.name == "الابن" }
        assertEquals(140_000, son.amountMinor)
        assertEquals("7/12", son.fraction)
        assertEquals("الباقي، للذكر مثل حظ الأنثيين", son.basis)
        assertEquals(70_000, v.heirs.first { it.name == "البنت" }.amountMinor)
        assertEquals(240_000, v.heirs.sumOf { it.amountMinor }, "المجموع = اللي بيتقسم بالظبط")
        assertEquals(listOf(0, 1, 2), v.heirs.mapNotNull { it.color }.sorted(), "لون لكل وارث ليه نصيب بالترتيب")
        val item = v.items.single()
        assertEquals(240_000, item.parts.sumOf { it.amountMinor }, "قسمة الشيء = قيمته")
        assertTrue(r.disclaimer.isNotBlank())
        assertEquals("كل شيء يحمل من الديون والوصية بنسبة قيمته، والمجاميع مضبوطة بالهللة.", v.itemsNote)
    }

    @Test
    fun aLoneWifeAsksAboutDistantRelativesFirstThenTakesTheRest() {
        val ask = ui(draft(HeirKind.WIFE to 1))
        assertEquals(InheritanceView.AskDistant, ask.view, "الإجابة بتغيّر القسمة — من غير تخمين")
        val no = ui(draft(HeirKind.WIFE to 1).copy(distantRelatives = false))
        val v = assertIs<InheritanceView.Computed>(no.view)
        assertEquals(240_000, v.heirs.single().amountMinor, "الرد على الزوجة")
        assertEquals("فرض + الرَّدّ", v.heirs.single().basis)
    }

    @Test
    fun specialCircumstancesAndImpossibleInputsStopInsteadOfGuessing() {
        val pregnant = ui(draft(HeirKind.WIFE to 1, HeirKind.SON to 1).copy(before = BeforeDraft(special = setOf(SpecialCircumstance.PREGNANCY))))
        val stop = assertIs<InheritanceView.Stop>(pregnant.view)
        assertEquals(StopKind.UNSUPPORTED, stop.kind)
        assertEquals("غير مدعوم في هذه النسخة", stopKindLabel(stop.kind))
        assertEquals("وجود حمل يحتاج حفظ نصيب له حتى الولادة، وهذه النسخة لا تحسبه", stop.text)

        val both = assertIs<InheritanceView.Stop>(ui(draft(HeirKind.HUSBAND to 1, HeirKind.WIFE to 1, HeirKind.SON to 1)).view)
        assertEquals(StopKind.INVALID, both.kind)
        assertEquals("لا يجتمع زوج وزوجة في مسألة واحدة", both.text)

        val bad = badAmountResultUi(InheritanceLaw.SA, Currency.SAR)
        assertEquals(StopKind.INVALID, assertIs<InheritanceView.Stop>(bad.view).kind)
        assertTrue(bad.disclaimer.isNotBlank())
    }

    @Test
    fun whatComesOutBeforeTheSplitIsListedAndEgyptCountsInPiastres() {
        Texts.followCountry("EG")
        val d = draft(HeirKind.WIFE to 1, HeirKind.SON to 2, country = "EG").copy(before = BeforeDraft(funeral = "100", debts = "300"))
        val v = assertIs<InheritanceView.Computed>(ui(d).view)
        assertEquals(listOf(10_000L, 30_000L), v.claims.map { it.amountMinor })
        assertEquals(200_000, v.poolMinor, "2,400 − 100 − 300 (من المحرك)")
        assertTrue(v.heroSub.startsWith("من تركة 2,400.00 ج.م بعد"), v.heroSub)
        val sons = v.heirs.first { it.name.startsWith("الابن") }
        assertEquals("الابن (2)", sons.name)
        assertTrue(sons.sub.startsWith("لكل واحد "), sons.sub)
        assertEquals("كل حاجة شايلة من الديون والوصية بنسبة قيمتها، والمجاميع مظبوطة بالقرش.", v.itemsNote)
        val parts = v.items.single().parts
        assertEquals(240_000, parts.sumOf { it.amountMinor })
        assertTrue(parts.any { it.label == "الابن 1" } && parts.any { it.label == "الابن 2" }, "كل ابن لوحده في القسمة: ${parts.map { it.label }}")
        assertNull(parts.first { it.label == "تجهيز الميت" }.color, "التجهيز رمادي")
    }
}
