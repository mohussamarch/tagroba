package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.FATHER
import app.masroufy.core.HeirKind.FULL_BROTHER
import app.masroufy.core.HeirKind.HUSBAND
import app.masroufy.core.HeirKind.MOTHER
import app.masroufy.core.HeirKind.SON
import app.masroufy.core.HeirKind.WIFE
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** المدخلات الغلط · الحالات اللي النسخة دي ما بتحسبهاش · التنبيه تحت كل نتيجة · النصوص بالنسختين والإنجليزي. */
class InheritanceInputsTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun invalid(case: InheritanceCase): InvalidReason = assertIs<InheritanceResult.Invalid>(calculateInheritance(case)).reason

    @Test
    fun invalidInputs() {
        assertEquals(InvalidReason.WIVES_MAX, invalid(inheritanceCase("SA", WIFE to 5)))
        assertEquals(InvalidReason.HUSBAND_AND_WIFE, invalid(inheritanceCase("SA", HUSBAND to 1, WIFE to 1)))
        assertEquals(InvalidReason.ONLY_ONE, invalid(inheritanceCase("EG", FATHER to 2)))
        assertEquals(InvalidReason.ONLY_ONE, invalid(inheritanceCase("EG", HUSBAND to 2)))
        assertEquals(InvalidReason.COUNT_RANGE, invalid(inheritanceCase("SA", SON to -1)))
        assertEquals(InvalidReason.COUNT_RANGE, invalid(inheritanceCase("SA", SON to INHERIT_MAX_COUNT + 1)))
        assertEquals(InvalidReason.NO_ITEMS, invalid(inheritanceCase("SA", SON to 1, items = emptyList())))
        assertEquals(InvalidReason.ITEM_NAME, invalid(inheritanceCase("SA", SON to 1, items = listOf(EstateItem(" ", 100)))))
        assertEquals(InvalidReason.NEGATIVE_AMOUNT, invalid(inheritanceCase("SA", SON to 1, estate = -1)))
        assertEquals(InvalidReason.NEGATIVE_AMOUNT, invalid(inheritanceCase("SA", SON to 1, debts = -5)))
        assertEquals(InvalidReason.NEGATIVE_AMOUNT, invalid(inheritanceCase("SA", SON to 1, bequest = Bequest(-1))))
        assertEquals(InvalidReason.PREDECEASED_NO_CHILDREN, invalid(inheritanceCase("EG", SON to 1, predeceased = listOf(PredeceasedChild(true, 0, 0)))))
        val huge = listOf(EstateItem("أ", MAX_SAFE_HALALAS), EstateItem("ب", MAX_SAFE_HALALAS))
        assertEquals(InvalidReason.TOO_LARGE, invalid(inheritanceCase("SA", SON to 1, items = huge)))
    }

    @Test
    fun unsupportedCasesSayWhyAndNeverGuess() {
        val reasons = mapOf(
            SpecialCircumstance.PREGNANCY to UnsupportedReason.PREGNANCY,
            SpecialCircumstance.MISSING_HEIR to UnsupportedReason.MISSING_HEIR,
            SpecialCircumstance.SUCCESSIVE_DEATHS to UnsupportedReason.SUCCESSIVE_DEATHS,
            SpecialCircumstance.TAKHARUJ to UnsupportedReason.TAKHARUJ,
        )
        for ((special, reason) in reasons) {
            val r = calculateInheritance(inheritanceCase("SA", SON to 1, special = setOf(special)))
            assertEquals(reason, assertIs<InheritanceResult.Unsupported>(r).reason)
        }
        assertEquals(UnsupportedReason.COUNTRY, assertIs<InheritanceResult.Unsupported>(calculateInheritance(inheritanceCase("AE", SON to 1))).reason)
        assertEquals(UnsupportedReason.NO_HEIRS, assertIs<InheritanceResult.Unsupported>(calculateInheritance(inheritanceCase("SA"))).reason)
        val distant = calculateInheritance(inheritanceCase("EG", distantRelatives = true))
        assertEquals(UnsupportedReason.DISTANT_RELATIVES, assertIs<InheritanceResult.Unsupported>(distant).reason)
    }

    // النص تحت كل نتيجة (§69) بنص المالك بالحرف — ومصر «إعلام الوراثة» (رد المالك §69.3)
    @Test
    fun disclaimerUnderEveryResult() {
        val saudi = "حساب تقريبي للتخطيط، والقسمة الرسمية بصك حصر الورثة من المحكمة"
        val egypt = "حساب تقريبي للتخطيط، والقسمة الرسمية بإعلام الوراثة من المحكمة"
        val results = listOf(
            calculateInheritance(inheritanceCase("SA", SON to 1)) to saudi,
            calculateInheritance(inheritanceCase("SA", WIFE to 5)) to saudi,
            calculateInheritance(inheritanceCase("AE", SON to 1)) to saudi,
            calculateInheritance(inheritanceCase("SA", HeirKind.DAUGHTER_SON to 1, HeirKind.MATERNAL_UNCLE_FULL to 1, HeirKind.MATERNAL_UNCLE_PATERNAL to 1)) to saudi,
            calculateInheritance(inheritanceCase("EG", SON to 1)) to egypt,
            calculateInheritance(inheritanceCase("EG", WIFE to 5)) to egypt,
            calculateInheritance(inheritanceCase("EG", WIFE to 1)) to egypt,
            calculateInheritance(inheritanceCase("EG", HeirKind.GRANDFATHER to 1, HeirKind.FULL_SISTER to 1, HeirKind.PATERNAL_SISTER to 1)) to egypt,
        )
        assertEquals(setOf("Computed", "Invalid", "Unsupported", "NoText"), results.map { it.first::class.simpleName }.toSet())
        assertEquals(setOf("Computed", "Invalid", "Unsupported", "NoText"), results.filter { it.second == egypt }.map { it.first::class.simpleName }.toSet())
        for ((r, text) in results) assertEquals(text, r.disclaimer, r.toString())
    }

    // كل ملاحظة ونتيجة ليها نص في الفصحى والمصري والإنجليزي، ومفيش متغير فاضل من غير ما يتملي
    @Test
    fun everyTextRendersInEveryVariant() {
        val samples = listOf(
            inheritanceCase("EG", HUSBAND to 1, MOTHER to 1, FATHER to 1, bequest = Bequest(100_000_000, toHeir = true)),
            inheritanceCase("SA", WIFE to 1, DAUGHTER to 2, FATHER to 1, MOTHER to 1, debts = 99_999_999, bequest = Bequest(1, toHeir = true)),
            inheritanceCase("EG", SON to 1, FULL_BROTHER to 1, predeceased = listOf(PredeceasedChild(false, 1, 1, givenInLifeMinor = 10))),
        )
        forEachTextVariant { label ->
            for (case in samples) {
                val r = computed(case)
                for (note in r.notes) {
                    assertFalse(note.text.contains('{'), "$label: ${note.kind} = ${note.text}")
                    note.citation?.let { assertFalse(it.text.contains('{'), "$label: ${it.text}") }
                }
                for (h in r.heirs) assertTrue(h.kind.label.isNotBlank())
                for (part in r.items.flatMap { it.parts }) assertTrue(part.claimant.label.isNotBlank())
                assertTrue(r.disclaimer.isNotBlank())
            }
            for (reason in UnsupportedReason.entries) assertFalse(InheritanceResult.Unsupported(reason, args = listOf("x")).text.contains('{'))
            for (reason in InvalidReason.entries) assertFalse(InheritanceResult.Invalid(reason, listOf("x", "100")).text.contains('{'))
            for (reason in NoTextReason.entries) assertFalse(InheritanceResult.NoText(reason, Citation(LawSource.EG_INHERITANCE, "22")).text.contains('{'))
            assertFalse(InheritanceResult.Invalid(InvalidReason.COUNT_RANGE, listOf("x", "100")).text.contains('{'))
        }
        Texts.arabicVariant = ArabicVariant.MSA
        assertEquals("المادة 213/3 من نظام الأحوال الشخصية (1443هـ)", Citation(LawSource.SA_PERSONAL_STATUS, "213/3").text)
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("الأب مش بيورث عشان فيه الابن (الحَجْب)", InheritanceNote(InheritanceNoteKind.BLOCKED, null, listOf(FATHER, SON)).text)
    }

    // الأسامي اختيارية وبتطلع بنفس الترتيب
    @Test
    fun optionalNamesFollowTheOrder() {
        val case = inheritanceCase("SA", SON to 2).copy(names = mapOf(SON to listOf("سامي", "")))
        val r = computed(case)
        assertEquals(listOf("سامي", null), r.heir(SON)!!.names)
    }
}
