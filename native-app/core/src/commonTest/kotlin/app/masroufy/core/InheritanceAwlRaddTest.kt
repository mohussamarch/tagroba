package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.FATHER
import app.masroufy.core.HeirKind.FULL_SISTER
import app.masroufy.core.HeirKind.HUSBAND
import app.masroufy.core.HeirKind.MATERNAL_BROTHER
import app.masroufy.core.HeirKind.MATERNAL_GRANDMOTHER
import app.masroufy.core.HeirKind.MATERNAL_SISTER
import app.masroufy.core.HeirKind.MOTHER
import app.masroufy.core.HeirKind.WIFE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * العَوْل (السعودية م230 · مصر م15) بكل أصوله المشهورة، والرَّدّ (السعودية م231 · مصر م30) بزوج ومن غير زوج.
 * الأمثلة من جداول العول في كتب الفرائض (أصل 6 يعول لـ7 و8 و9 و10 · أصل 12 لـ13 و15 و17 · أصل 24 لـ27).
 */
class InheritanceAwlRaddTest {
    private fun awl(r: InheritanceResult.Computed) = r.notes.first { it.kind == InheritanceNoteKind.AWL }.values

    // 6 ⇒ 7: زوج 1/2 · أختين شقيقتين 2/3 ⇒ 3/7 · 4/7
    @Test
    fun awlSixToSeven() {
        val r = computed(inheritanceCase("SA", HUSBAND to 1, FULL_SISTER to 2))
        assertShares(r, HUSBAND to "3/7", FULL_SISTER to "4/7")
        assertEquals(listOf("6", "7"), awl(r))
    }

    // 6 ⇒ 9: زوج 1/2 · أختين شقيقتين 2/3 · أختين لأم 1/3 ⇒ 3/9 · 4/9 · 2/9 (المروانية في الكتب بالأخوات لأم)
    @Test
    fun awlSixToNine() {
        val r = computed(inheritanceCase("EG", HUSBAND to 1, FULL_SISTER to 2, MATERNAL_SISTER to 2))
        assertShares(r, HUSBAND to "1/3", FULL_SISTER to "4/9", MATERNAL_SISTER to "2/9")
        assertEquals(listOf("6", "9"), awl(r))
    }

    // أصل المسألة بالمجموعة مش بالأفراد: الجدتين سدس واحد · أخ وأختين لأم ثلث واحد ⇒ لسه أصل 6
    // زوج 1/2 · شقيقتين 2/3 · جدتين 1/6 ⇒ 6 ⇒ 8 · زوج · شقيقتين · أخ لأم وأختين لأم ⇒ 6 ⇒ 9
    @Test
    fun awlBaseUsesTheGroupedShares() {
        val grandmothers = computed(inheritanceCase("SA", HUSBAND to 1, FULL_SISTER to 2, HeirKind.PATERNAL_GRANDMOTHER to 1, MATERNAL_GRANDMOTHER to 1))
        assertEquals(listOf("6", "8"), awl(grandmothers))
        assertEquals(Frac.of(1, 16), grandmothers.heir(MATERNAL_GRANDMOTHER)!!.perPerson)
        val maternal = computed(inheritanceCase("EG", HUSBAND to 1, FULL_SISTER to 2, MATERNAL_BROTHER to 1, MATERNAL_SISTER to 2))
        assertEquals(listOf("6", "9"), awl(maternal))
        assertEquals(Frac.of(2, 27), maternal.heir(MATERNAL_BROTHER)!!.perPerson)
    }

    // 6 ⇒ 10 (الشُّرَيحية / أم الفروخ): زوج · أم · أختين شقيقتين · أختين لأم ⇒ 3 · 1 · 4 · 2 من 10
    @Test
    fun awlSixToTen() {
        val r = computed(inheritanceCase("SA", HUSBAND to 1, MOTHER to 1, FULL_SISTER to 2, MATERNAL_SISTER to 2))
        assertShares(r, HUSBAND to "3/10", MOTHER to "1/10", FULL_SISTER to "2/5", MATERNAL_SISTER to "1/5")
        assertEquals(listOf("6", "10"), awl(r))
    }

    // 12 ⇒ 13: زوجة 1/4 · أم 1/6 · أختين شقيقتين 2/3 ⇒ 3 · 2 · 8 من 13
    @Test
    fun awlTwelveToThirteen() {
        val r = computed(inheritanceCase("EG", WIFE to 1, MOTHER to 1, FULL_SISTER to 2))
        assertShares(r, WIFE to "3/13", MOTHER to "2/13", FULL_SISTER to "8/13")
        assertEquals(listOf("12", "13"), awl(r))
    }

    // 12 ⇒ 15: زوج 1/4 · بنتين 2/3 · أب 1/6 · أم 1/6 ⇒ 3 · 8 · 2 · 2 من 15 (الأب سدس بس — الباقي صفر)
    @Test
    fun awlTwelveToFifteen() {
        val r = computed(inheritanceCase("SA", HUSBAND to 1, DAUGHTER to 2, FATHER to 1, MOTHER to 1))
        assertShares(r, HUSBAND to "1/5", DAUGHTER to "8/15", FATHER to "2/15", MOTHER to "2/15")
        assertEquals(listOf("12", "15"), awl(r))
        assertEquals(ShareBasis.FARD, r.heir(FATHER)!!.basis)
    }

    // 12 ⇒ 17: زوجة 1/4 · أم 1/6 · أختين شقيقتين 2/3 · أختين لأم 1/3 ⇒ 3 · 2 · 8 · 4 من 17
    @Test
    fun awlTwelveToSeventeen() {
        val r = computed(inheritanceCase("SA", WIFE to 1, MOTHER to 1, FULL_SISTER to 2, MATERNAL_BROTHER to 2))
        assertShares(r, WIFE to "3/17", MOTHER to "2/17", FULL_SISTER to "8/17", MATERNAL_BROTHER to "4/17")
        assertEquals(listOf("12", "17"), awl(r))
    }

    // رد من غير زوج: أم 1/6 · بنت 1/2 ⇒ الباقي 1/3 يرجع بنسبة 1:3 ⇒ أم 1/4 · بنت 3/4
    @Test
    fun raddWithoutSpouse() {
        for (country in listOf("SA", "EG")) {
            val r = computed(inheritanceCase(country, MOTHER to 1, DAUGHTER to 1))
            assertShares(r, MOTHER to "1/4", DAUGHTER to "3/4")
            assertTrue(r.hasNote(InheritanceNoteKind.RADD, if (country == "SA") "231/1" else "30"))
            assertEquals(ShareBasis.RADD, r.heir(DAUGHTER)!!.basis)
        }
    }

    // رد مع زوج: الزوجة فرضها بس (1/8) والباقي 7/8 للأم والبنت 1:3 ⇒ 7/32 · 21/32
    @Test
    fun raddWithSpouseKeepsTheSpouseFixed() {
        val r = computed(inheritanceCase("SA", WIFE to 1, MOTHER to 1, DAUGHTER to 1))
        assertShares(r, WIFE to "1/8", MOTHER to "7/32", DAUGHTER to "21/32")
        assertEquals(ShareBasis.FARD, r.heir(WIFE)!!.basis)
        val husband = computed(inheritanceCase("EG", HUSBAND to 1, DAUGHTER to 3))
        assertShares(husband, HUSBAND to "1/4", DAUGHTER to "3/4")
        assertEquals(Frac.of(1, 4), husband.heir(DAUGHTER)!!.perPerson)
    }

    // جدة + أخ لأم: سدس وسدس ثم رد ⇒ النص لكل واحد
    @Test
    fun raddBetweenGrandmotherAndMaternalBrother() {
        val r = computed(inheritanceCase("EG", MATERNAL_GRANDMOTHER to 1, MATERNAL_BROTHER to 1))
        assertShares(r, MATERNAL_GRANDMOTHER to "1/2", MATERNAL_BROTHER to "1/2")
    }

    // بنتين لوحدهم ⇒ التركة كلها رد، نص لكل واحدة
    @Test
    fun twoDaughtersAloneTakeEverything() {
        val r = computed(inheritanceCase("SA", DAUGHTER to 2))
        assertShares(r, DAUGHTER to "1")
        assertEquals(listOf(3_600_000L, 3_600_000L), r.amounts(DAUGHTER))
    }

    // الزوج/الزوجة لوحدهم: الرد عليهم بس لو مفيش ذوو أرحام (السعودية م231/2 و234 · مصر م30) ⇒ لازم نسأل.
    @Test
    fun spouseAloneDependsOnDistantRelatives() {
        for (country in listOf("SA", "EG")) {
            val asked = calculateInheritance(inheritanceCase(country, WIFE to 2))
            assertEquals(UnsupportedReason.ASK_DISTANT_RELATIVES, assertIs<InheritanceResult.Unsupported>(asked).reason)
            val distant = calculateInheritance(inheritanceCase(country, WIFE to 2, distantRelatives = true))
            assertEquals(UnsupportedReason.DISTANT_RELATIVES, assertIs<InheritanceResult.Unsupported>(distant).reason)
            val alone = computed(inheritanceCase(country, WIFE to 2, distantRelatives = false))
            assertShares(alone, WIFE to "1")
            assertEquals(Frac.of(1, 2), alone.heir(WIFE)!!.perPerson)
            assertTrue(alone.hasNote(InheritanceNoteKind.RADD_TO_SPOUSE, if (country == "SA") "231/2" else "30"))
        }
    }
}
