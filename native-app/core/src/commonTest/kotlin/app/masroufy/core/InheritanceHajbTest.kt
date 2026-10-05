package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.FATHER
import app.masroufy.core.HeirKind.FULL_BROTHER
import app.masroufy.core.HeirKind.FULL_COUSIN
import app.masroufy.core.HeirKind.FULL_NEPHEW
import app.masroufy.core.HeirKind.FULL_SISTER
import app.masroufy.core.HeirKind.FULL_UNCLE
import app.masroufy.core.HeirKind.GRANDFATHER
import app.masroufy.core.HeirKind.MATERNAL_GRANDMOTHER
import app.masroufy.core.HeirKind.MOTHER
import app.masroufy.core.HeirKind.PATERNAL_BROTHER
import app.masroufy.core.HeirKind.PATERNAL_COUSIN
import app.masroufy.core.HeirKind.PATERNAL_GRANDMOTHER
import app.masroufy.core.HeirKind.PATERNAL_NEPHEW
import app.masroufy.core.HeirKind.PATERNAL_SISTER
import app.masroufy.core.HeirKind.PATERNAL_UNCLE
import app.masroufy.core.HeirKind.SON
import app.masroufy.core.HeirKind.SON_DAUGHTER
import app.masroufy.core.HeirKind.SON_SON
import app.masroufy.core.HeirKind.WIFE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * الحجب وبنات الابن والأخوات كعصبة — السعودية م214–229 · مصر م12–29. الأمثلة من أبواب «الحجب» و«ميراث بنات الابن» و«العصبة مع الغير».
 */
class InheritanceHajbTest {
    // بنت + بنت ابن + أخ شقيق ⇒ 1/2 · 1/6 تكملة الثلثين · الباقي 1/3 (السعودية م216/2 · مصر م12)
    @Test
    fun sonsDaughterCompletesTwoThirds() {
        val r = computed(inheritanceCase("SA", DAUGHTER to 1, SON_DAUGHTER to 2, FULL_BROTHER to 1))
        assertShares(r, DAUGHTER to "1/2", SON_DAUGHTER to "1/6", FULL_BROTHER to "1/3")
        assertEquals(Frac.of(1, 12), r.heir(SON_DAUGHTER)!!.perPerson)
        assertTrue(r.hasNote(InheritanceNoteKind.TAKMILA, "216/2"))
    }

    // بنتين بيحجبوا بنت الابن (السعودية م216 · مصر م27) …
    @Test
    fun twoDaughtersBlockTheSonsDaughter() {
        val r = computed(inheritanceCase("EG", DAUGHTER to 2, SON_DAUGHTER to 1, FULL_BROTHER to 1))
        assertShares(r, DAUGHTER to "2/3", SON_DAUGHTER to "0", FULL_BROTHER to "1/3")
        assertTrue(r.hasNote(InheritanceNoteKind.BLOCKED, "27"))
    }

    // … إلا لو معاها ابن ابن يعصّبها: بنتين 2/3 · ابن ابن 2/9 · بنت ابن 1/9 (والأخ محجوب بابن الابن)
    @Test
    fun sonsSonMakesHerResiduary() {
        val r = computed(inheritanceCase("SA", DAUGHTER to 2, SON_DAUGHTER to 1, SON_SON to 1, FULL_BROTHER to 1))
        assertShares(r, DAUGHTER to "2/3", SON_SON to "2/9", SON_DAUGHTER to "1/9", FULL_BROTHER to "0")
    }

    // الابن بيحجب ابن الابن وبنت الابن (السعودية م228/1 و216)
    @Test
    fun sonBlocksGrandchildren() {
        val r = computed(inheritanceCase("SA", SON to 1, DAUGHTER to 1, SON_SON to 3, SON_DAUGHTER to 1))
        assertShares(r, SON to "2/3", DAUGHTER to "1/3", SON_SON to "0", SON_DAUGHTER to "0")
    }

    // الأخت مع البنت عصبة مع الغير: بنت · أخت شقيقة ⇒ النص لكل واحدة (السعودية م217/3 · مصر م20)
    @Test
    fun sisterTakesTheRestWithDaughters() {
        for (country in listOf("SA", "EG")) {
            val r = computed(inheritanceCase(country, DAUGHTER to 1, FULL_SISTER to 1))
            assertShares(r, DAUGHTER to "1/2", FULL_SISTER to "1/2")
            assertTrue(r.hasNote(InheritanceNoteKind.WITH_DAUGHTERS))
        }
    }

    // والأخت دي بتحجب زي الأخ: بنت · أخت شقيقة · أخ لأب ⇒ الأخ لأب محجوب (السعودية م226 · مصر م20)
    @Test
    fun sisterWithDaughtersBlocksLikeABrother() {
        val sa = computed(inheritanceCase("SA", DAUGHTER to 1, FULL_SISTER to 1, PATERNAL_BROTHER to 1))
        assertShares(sa, DAUGHTER to "1/2", FULL_SISTER to "1/2", PATERNAL_BROTHER to "0")
        assertTrue(sa.hasNote(InheritanceNoteKind.BLOCKED, "226"))
        val eg = computed(inheritanceCase("EG", DAUGHTER to 2, FULL_SISTER to 1, FULL_NEPHEW to 1))
        assertShares(eg, DAUGHTER to "2/3", FULL_SISTER to "1/3", FULL_NEPHEW to "0")
        assertTrue(eg.hasNote(InheritanceNoteKind.BLOCKED, "20"))
    }

    // أخت شقيقة · أخت لأب · عم ⇒ 1/2 · 1/6 تكملة · الباقي 1/3 (السعودية م218/2 · مصر م13)
    @Test
    fun paternalSisterCompletesTwoThirds() {
        val r = computed(inheritanceCase("EG", FULL_SISTER to 1, PATERNAL_SISTER to 1, FULL_UNCLE to 1))
        assertShares(r, FULL_SISTER to "1/2", PATERNAL_SISTER to "1/6", FULL_UNCLE to "1/3")
    }

    // شقيقتين · أخت لأب · أخ لأب ⇒ 2/3 · والباقي للأخ والأخت لأب 2:1 (السعودية م218/3 · مصر م19)
    @Test
    fun paternalBrotherRescuesHisSister() {
        val r = computed(inheritanceCase("SA", FULL_SISTER to 2, PATERNAL_SISTER to 1, PATERNAL_BROTHER to 1))
        assertShares(r, FULL_SISTER to "2/3", PATERNAL_BROTHER to "2/9", PATERNAL_SISTER to "1/9")
        val blocked = computed(inheritanceCase("SA", FULL_SISTER to 2, PATERNAL_SISTER to 1, FULL_UNCLE to 1))
        assertShares(blocked, FULL_SISTER to "2/3", PATERNAL_SISTER to "0", FULL_UNCLE to "1/3")
    }

    // الأخ الشقيق بيحجب الأخ والأخت لأب (السعودية م228/1 و218 · مصر م18 و29)
    @Test
    fun fullBrotherBlocksPaternalSiblings() {
        for (country in listOf("SA", "EG")) {
            val r = computed(inheritanceCase(country, FULL_BROTHER to 1, PATERNAL_BROTHER to 1, PATERNAL_SISTER to 2))
            assertShares(r, FULL_BROTHER to "1", PATERNAL_BROTHER to "0", PATERNAL_SISTER to "0")
            assertEquals(ShareBasis.BLOCKED, r.heir(PATERNAL_SISTER)!!.basis)
            assertTrue(r.hasNote(InheritanceNoteKind.BLOCKED, if (country == "SA") "218" else "29"))
        }
    }

    // سلسلة حجب: الأب بيحجب الجد والإخوة والعم · الأم الثلث (أخ واحد بس)
    @Test
    fun fatherBlocksTheWholeChain() {
        val r = computed(inheritanceCase("SA", FATHER to 1, MOTHER to 1, GRANDFATHER to 1, FULL_BROTHER to 1, FULL_UNCLE to 1))
        assertShares(r, FATHER to "2/3", MOTHER to "1/3", GRANDFATHER to "0", FULL_BROTHER to "0", FULL_UNCLE to "0")
        assertTrue(r.hasNote(InheritanceNoteKind.BLOCKED, "212/2"))
    }

    // الإخوة المحجوبين بالأب بيرجّعوا الأم للسدس (السعودية م213/1-ب «وارثين أو محجوبين» · مصر م23 «والمحجوب يحجب غيره»)
    @Test
    fun blockedBrothersStillReduceTheMother() {
        for (country in listOf("SA", "EG")) {
            val r = computed(inheritanceCase(country, FATHER to 1, MOTHER to 1, FULL_BROTHER to 2))
            assertShares(r, FATHER to "5/6", MOTHER to "1/6", FULL_BROTHER to "0")
        }
    }

    // ابن · أب · أم · زوجة · أخ شقيق · عم ⇒ 1/8 · 1/6 · 1/6 · الباقي للابن 13/24
    @Test
    fun sonWithParentsAndWife() {
        val r = computed(inheritanceCase("EG", SON to 1, FATHER to 1, MOTHER to 1, WIFE to 1, FULL_BROTHER to 1, FULL_UNCLE to 1))
        assertShares(r, SON to "13/24", FATHER to "1/6", MOTHER to "1/6", WIFE to "1/8", FULL_BROTHER to "0", FULL_UNCLE to "0")
    }

    // العصبة البعيدة: الأقرب جهة ثم درجة ثم قوة (السعودية م228 · مصر م17–18)
    @Test
    fun farResiduariesByDirectionDegreeStrength() {
        val nephews = computed(inheritanceCase("SA", FULL_NEPHEW to 1, PATERNAL_NEPHEW to 2, FULL_UNCLE to 1))
        assertShares(nephews, FULL_NEPHEW to "1", PATERNAL_NEPHEW to "0", FULL_UNCLE to "0")
        val uncles = computed(inheritanceCase("EG", FULL_UNCLE to 2, PATERNAL_UNCLE to 1, FULL_COUSIN to 1, PATERNAL_COUSIN to 1))
        assertShares(uncles, FULL_UNCLE to "1", PATERNAL_UNCLE to "0", FULL_COUSIN to "0", PATERNAL_COUSIN to "0")
        assertTrue(uncles.hasNote(InheritanceNoteKind.BLOCKED, "18"))
        val cousins = computed(inheritanceCase("SA", PATERNAL_UNCLE to 1, FULL_COUSIN to 3))
        assertShares(cousins, PATERNAL_UNCLE to "1", FULL_COUSIN to "0")
        val last = computed(inheritanceCase("EG", MOTHER to 1, PATERNAL_COUSIN to 2))
        assertShares(last, MOTHER to "1/3", PATERNAL_COUSIN to "2/3")
    }

    // الجدات: السعودية الأب ما بيحجبش أمه (م214/1) ⇒ الجدتين السدس بينهم · مصر الأب بيحجب أم الأب (م25) ⇒ أم الأم السدس لوحدها
    @Test
    fun fatherAndGrandmothersDifferByCountry() {
        val sa = computed(inheritanceCase("SA", FATHER to 1, PATERNAL_GRANDMOTHER to 1, MATERNAL_GRANDMOTHER to 1))
        assertShares(sa, FATHER to "5/6", PATERNAL_GRANDMOTHER to "1/12", MATERNAL_GRANDMOTHER to "1/12")
        val eg = computed(inheritanceCase("EG", FATHER to 1, PATERNAL_GRANDMOTHER to 1, MATERNAL_GRANDMOTHER to 1))
        assertShares(eg, FATHER to "5/6", PATERNAL_GRANDMOTHER to "0", MATERNAL_GRANDMOTHER to "1/6")
        assertTrue(eg.hasNote(InheritanceNoteKind.BLOCKED, "25"))
        val withMother = computed(inheritanceCase("SA", MOTHER to 1, MATERNAL_GRANDMOTHER to 1, FULL_UNCLE to 1))
        assertShares(withMother, MOTHER to "1/3", MATERNAL_GRANDMOTHER to "0", FULL_UNCLE to "2/3")
        assertTrue(withMother.hasNote(InheritanceNoteKind.BLOCKED, "214/2"))
    }
}
