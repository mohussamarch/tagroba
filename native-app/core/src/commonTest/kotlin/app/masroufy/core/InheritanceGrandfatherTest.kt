package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.FULL_BROTHER
import app.masroufy.core.HeirKind.FULL_NEPHEW
import app.masroufy.core.HeirKind.FULL_SISTER
import app.masroufy.core.HeirKind.GRANDFATHER
import app.masroufy.core.HeirKind.HUSBAND
import app.masroufy.core.HeirKind.MATERNAL_BROTHER
import app.masroufy.core.HeirKind.MOTHER
import app.masroufy.core.HeirKind.PATERNAL_BROTHER
import app.masroufy.core.HeirKind.PATERNAL_SISTER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * الجد مع الإخوة — **أكبر فرق بين البلدين**:
 * - السعودية م212/3: «يكون ميراث الجد مثل ميراث الأب ويحجب الإخوة».
 * - مصر 77/1943 م22: يقاسمهم كأخ، أو ياخد الباقي بعد فروض الأخوات، وما ينزلش عن السدس (`InheritanceGrandfather.kt`).
 */
class InheritanceGrandfatherTest {
    // جد + أخ شقيق: السعودية الجد كله · مصر نص ونص
    @Test
    fun grandfatherWithABrother() {
        val sa = computed(inheritanceCase("SA", GRANDFATHER to 1, FULL_BROTHER to 1))
        assertShares(sa, GRANDFATHER to "1", FULL_BROTHER to "0")
        assertTrue(sa.hasNote(InheritanceNoteKind.GRANDFATHER_BLOCKS_SIBLINGS, "212/3"))
        assertTrue(sa.hasNote(InheritanceNoteKind.BLOCKED, "212/3"))
        val eg = computed(inheritanceCase("EG", GRANDFATHER to 1, FULL_BROTHER to 1))
        assertShares(eg, GRANDFATHER to "1/2", FULL_BROTHER to "1/2")
        assertTrue(eg.hasNote(InheritanceNoteKind.GRANDFATHER_SHARES, "22"))
    }

    // مصر — الحالة الأولى بذكور وإناث: جد · أخ شقيق · أخت شقيقة ⇒ 2 · 2 · 1 من 5
    @Test
    fun egyptGrandfatherCountsAsABrother() {
        val r = computed(inheritanceCase("EG", GRANDFATHER to 1, FULL_BROTHER to 1, FULL_SISTER to 1))
        assertShares(r, GRANDFATHER to "2/5", FULL_BROTHER to "2/5", FULL_SISTER to "1/5")
    }

    // مصر — حد السدس: زوج · أم · جد · 3 إخوة أشقاء. الباقي 1/3 ÷ 4 = 1/12 للجد < 1/6 ⇒ الجد 1/6 والإخوة 1/6 بينهم
    @Test
    fun egyptGrandfatherNeverBelowASixth() {
        val r = computed(inheritanceCase("EG", HUSBAND to 1, MOTHER to 1, GRANDFATHER to 1, FULL_BROTHER to 3))
        assertShares(r, HUSBAND to "1/2", MOTHER to "1/6", GRANDFATHER to "1/6", FULL_BROTHER to "1/6")
        assertEquals(Frac.of(1, 18), r.heir(FULL_BROTHER)!!.perPerson)
        assertTrue(r.hasNote(InheritanceNoteKind.GRANDFATHER_SIXTH, "22"))
        // السعودية: نفس الورثة ⇒ الجد عاصب زي الأب والإخوة محجوبين، والأم السدس (إخوة محجوبين بيحجبوها — م213/1-ب)
        val sa = computed(inheritanceCase("SA", HUSBAND to 1, MOTHER to 1, GRANDFATHER to 1, FULL_BROTHER to 3))
        assertShares(sa, HUSBAND to "1/2", MOTHER to "1/6", GRANDFATHER to "1/3", FULL_BROTHER to "0")
    }

    // مصر — الحالة التانية: جد · أختين شقيقتين ⇒ الأختين 2/3 فرض والجد الباقي 1/3
    @Test
    fun egyptGrandfatherTakesTheRestAfterSisters() {
        val r = computed(inheritanceCase("EG", GRANDFATHER to 1, FULL_SISTER to 2))
        assertShares(r, GRANDFATHER to "1/3", FULL_SISTER to "2/3")
        assertTrue(r.hasNote(InheritanceNoteKind.GRANDFATHER_RESIDUE, "22"))
    }

    // مصر — أخوات عصبة مع البنات + جد: بنت 1/2 · الباقي بين الجد (2) والأخت (1) ⇒ 1/3 · 1/6
    @Test
    fun egyptGrandfatherWithDaughterAndSister() {
        val r = computed(inheritanceCase("EG", DAUGHTER to 1, GRANDFATHER to 1, FULL_SISTER to 1))
        assertShares(r, DAUGHTER to "1/2", GRANDFATHER to "1/3", FULL_SISTER to "1/6")
        // السعودية: الجد يحجب الأخت ويأخذ السدس فرضًا والباقي تعصيبًا (زي الأب مع البنت — م211/2 و212/3)
        val sa = computed(inheritanceCase("SA", DAUGHTER to 1, GRANDFATHER to 1, FULL_SISTER to 1))
        assertShares(sa, DAUGHTER to "1/2", GRANDFATHER to "1/2", FULL_SISTER to "0")
        assertEquals(ShareBasis.FARD_AND_RESIDUARY, sa.heir(GRANDFATHER)!!.basis)
    }

    // مصر — اللي لأب المحجوبين بالشقيق ما بيتعدّوش في المقاسمة (آخر م22): جد · أخ شقيق · أخ لأب ⇒ نص ونص
    @Test
    fun egyptBlockedPaternalSiblingsAreNotCounted() {
        val r = computed(inheritanceCase("EG", GRANDFATHER to 1, FULL_BROTHER to 1, PATERNAL_BROTHER to 2))
        assertShares(r, GRANDFATHER to "1/2", FULL_BROTHER to "1/2", PATERNAL_BROTHER to "0")
    }

    // الجد بيحجب الإخوة لأم في البلدين (السعودية م219 · مصر م26) وبيحجب ابن الأخ في البلدين (جهة الأبوة قبل الأخوة)
    @Test
    fun grandfatherBlocksMaternalSiblingsAndNephews() {
        for (country in listOf("SA", "EG")) {
            val r = computed(inheritanceCase(country, GRANDFATHER to 1, MATERNAL_BROTHER to 1, FULL_NEPHEW to 1))
            assertShares(r, GRANDFATHER to "1", MATERNAL_BROTHER to "0", FULL_NEPHEW to "0")
        }
        val eg = computed(inheritanceCase("EG", GRANDFATHER to 1, FULL_BROTHER to 1, MATERNAL_BROTHER to 1, FULL_NEPHEW to 1))
        assertShares(eg, GRANDFATHER to "1/2", FULL_BROTHER to "1/2", MATERNAL_BROTHER to "0", FULL_NEPHEW to "0")
    }

    // مصر — شقيقة بفرضها + أخت لأب مع الجد: م22 ما بتقولش يتقسم إزاي ⇒ «لا نص — اسأل المحكمة»
    @Test
    fun egyptMixedFullAndPaternalSiblingsHaveNoText() {
        for (heirs in listOf(arrayOf(GRANDFATHER to 1, FULL_SISTER to 1, PATERNAL_SISTER to 1), arrayOf(GRANDFATHER to 1, FULL_SISTER to 2, PATERNAL_BROTHER to 1))) {
            val r = assertIs<InheritanceResult.NoText>(calculateInheritance(inheritanceCase("EG", *heirs)))
            assertEquals(NoTextReason.GRANDFATHER_MIXED_SIBLINGS, r.reason)
            assertEquals("22", r.citation.article)
            assertEquals(LawSource.EG_INHERITANCE, r.citation.source)
        }
        // نفس الورثة في السعودية بيتحسبوا (الجد يحجبهم كلهم)
        val sa = computed(inheritanceCase("SA", GRANDFATHER to 1, FULL_SISTER to 1, PATERNAL_SISTER to 1))
        assertShares(sa, GRANDFATHER to "1", FULL_SISTER to "0", PATERNAL_SISTER to "0")
    }
}
