package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.DAUGHTER_DAUGHTER
import app.masroufy.core.HeirKind.DAUGHTER_SON
import app.masroufy.core.HeirKind.FULL_BROTHER
import app.masroufy.core.HeirKind.FULL_SISTER_DAUGHTER
import app.masroufy.core.HeirKind.FULL_SISTER_SON
import app.masroufy.core.HeirKind.HUSBAND
import app.masroufy.core.HeirKind.MATERNAL_AUNT_FULL
import app.masroufy.core.HeirKind.MATERNAL_BROTHER_SON
import app.masroufy.core.HeirKind.MATERNAL_GRANDFATHER
import app.masroufy.core.HeirKind.MATERNAL_SISTER_DAUGHTER
import app.masroufy.core.HeirKind.MATERNAL_UNCLE_FULL
import app.masroufy.core.HeirKind.MATERNAL_UNCLE_PATERNAL
import app.masroufy.core.HeirKind.MOTHER
import app.masroufy.core.HeirKind.PATERNAL_AUNT_FULL
import app.masroufy.core.HeirKind.PATERNAL_SISTER_SON
import app.masroufy.core.HeirKind.SON_DAUGHTER_SON
import app.masroufy.core.HeirKind.WIFE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ذوو الأرحام في السعودية — **بالتنزيل** (نظام الأحوال الشخصية م232–236، النص الرسمي). كل حالة جنبها الحساب على الورق:
 * القريب بياخد نصيب اللي بيوصله (م235)، فبنحل المسألة بالورثة دول ونقسم نصيب كل واحد على اللي في مكانه **بالتساوي** (ذكر = أنثى).
 * التركة 72,000.00 والأسامي والأرقام مخترعة.
 */
class InheritanceDistantSaudiTest {
    private fun sa(vararg heirs: Pair<HeirKind, Int>, branches: List<DistantBranch> = emptyList()) =
        computed(inheritanceCase("SA", *heirs, branches = branches))

    // ابن البنت لوحده: مكان البنت ⇒ النص + الرد = كل التركة (م234/1 · م235)
    @Test
    fun aDaughtersSonAloneTakesEverything() {
        val r = sa(DAUGHTER_SON to 1)
        assertShares(r, DAUGHTER_SON to "1")
        assertEquals(ShareBasis.DISTANT, r.heir(DAUGHTER_SON)!!.basis)
        assertTrue(r.hasNote(InheritanceNoteKind.DISTANT_TANZIL, "235"))
        assertEquals(listOf(7_200_000L), r.amounts(DAUGHTER_SON))
    }

    // ولد وبنت من نفس البنت: «دون تفاضل بين سهم الذكر وسهم الأنثى» (م235) ⇒ ½ · ½ (مصر 2:1)
    @Test
    fun maleAndFemaleAreEqual() {
        val r = sa(DAUGHTER_SON to 1, DAUGHTER_DAUGHTER to 1, branches = listOf(DistantBranch(DAUGHTER, 1, 1)))
        assertShares(r, DAUGHTER_SON to "1/2", DAUGHTER_DAUGHTER to "1/2")
    }

    // أولاد بنتين: بنت (أ) ليها ابن، وبنت (ب) ليها 3 بنات. البنتين = الثلثين + الرد ⇒ كل بنت ½.
    // ابن (أ) ½ = 36,000 · كل بنت من (ب) ⅙ = 12,000 — مش ¼ لكل واحد (لو اتقسم على الأشخاص)
    @Test
    fun eachChildTakesTheirOwnParentsShare() {
        val branches = listOf(DistantBranch(DAUGHTER, 1, 0), DistantBranch(DAUGHTER, 0, 3))
        val r = sa(DAUGHTER_SON to 1, DAUGHTER_DAUGHTER to 3, branches = branches)
        assertShares(r, DAUGHTER_SON to "1/2", DAUGHTER_DAUGHTER to "1/2")
        assertEquals(Frac.of(1, 6), r.heir(DAUGHTER_DAUGHTER)!!.perPerson)
        assertEquals(listOf(3_600_000L), r.amounts(DAUGHTER_SON))
        assertEquals(listOf(1_200_000L, 1_200_000L, 1_200_000L), r.amounts(DAUGHTER_DAUGHTER))
        // نفس الأشخاص من غير ما نعرف من كام بنت ⇒ سؤال، مش تخمين
        val asked = calculateInheritance(inheritanceCase("SA", DAUGHTER_SON to 1, DAUGHTER_DAUGHTER to 3))
        val u = assertIs<InheritanceResult.Unsupported>(asked)
        assertEquals(UnsupportedReason.ASK_DISTANT_BRANCHES, u.reason)
        assertEquals(InheritanceLaw.SA, u.law)
        // بنات مختلفين ⇒ الفرد مش متساوي ⇒ perPerson فاضي والمبالغ بالترتيب
        val mixed = sa(DAUGHTER_DAUGHTER to 2, branches = listOf(DistantBranch(DAUGHTER, 0, 1), DistantBranch(DAUGHTER, 0, 1)))
        assertEquals(Frac.of(1, 2), mixed.heir(DAUGHTER_DAUGHTER)!!.perPerson)
        val uneven = sa(DAUGHTER_SON to 1, DAUGHTER_DAUGHTER to 2, branches = listOf(DistantBranch(DAUGHTER, 1, 1), DistantBranch(DAUGHTER, 0, 1)))
        // بنت (أ) ½ بين ابنها وبنتها ¼ ¼ · بنت (ب) ½ لبنتها ⇒ بنات البنات ¼ و½ في نفس النوع
        assertEquals(listOf(Frac.of(1, 4), Frac.of(1, 2)), uneven.heir(DAUGHTER_DAUGHTER)!!.personShares)
        assertNull(uneven.heir(DAUGHTER_DAUGHTER)!!.perPerson)
    }

    // جهتين مختلفتين بيورثوا مع بعض (م236): بنت البنت (البنوة) + ابن الأخت الشقيقة (الأبوة) ⇒ البنت ½ والأخت الباقي مع البنات ½
    @Test
    fun differentSidesInheritTogether() {
        val r = sa(DAUGHTER_DAUGHTER to 1, FULL_SISTER_SON to 1)
        assertShares(r, DAUGHTER_DAUGHTER to "1/2", FULL_SISTER_SON to "1/2")
    }

    // ابن البنت + الخال: البنت ½ + الأم ⅙ ⇒ رد على 4 ⇒ ¾ · ¼ (المسألة المشهورة «بنت بنت وخال»)
    @Test
    fun daughtersSonWithMaternalUncle() {
        val r = sa(DAUGHTER_SON to 1, MATERNAL_UNCLE_FULL to 1)
        assertShares(r, DAUGHTER_SON to "3/4", MATERNAL_UNCLE_FULL to "1/4")
        assertEquals(listOf(5_400_000L), r.amounts(DAUGHTER_SON))
        assertEquals(listOf(1_800_000L), r.amounts(MATERNAL_UNCLE_FULL))
    }

    // ابن البنت + العمة: البنت ½ · الأب ⅙ + الباقي ⅓ = ½ ⇒ ½ · ½
    @Test
    fun daughtersSonWithPaternalAunt() {
        val r = sa(DAUGHTER_SON to 1, PATERNAL_AUNT_FULL to 1)
        assertShares(r, DAUGHTER_SON to "1/2", PATERNAL_AUNT_FULL to "1/2")
    }

    // نفس الجهة (البنوة) والأقرب للميت بيسقط الأبعد (م236): ابن البنت (جيلين) قبل ابن بنت الابن (3 أجيال)
    @Test
    fun nearerOnTheSameSideDropsTheFarther() {
        val r = sa(DAUGHTER_SON to 1, SON_DAUGHTER_SON to 1)
        assertShares(r, DAUGHTER_SON to "1", SON_DAUGHTER_SON to "0")
        assertEquals(ShareBasis.BLOCKED, r.heir(SON_DAUGHTER_SON)!!.basis)
        assertTrue(r.hasNote(InheritanceNoteKind.BLOCKED, "236"))
    }

    // بنت الأخت الشقيقة + ابن الأخت لأب (نفس الجهة ونفس القرب): الشقيقة ½ · اللي لأب ⅙ تكملة ⇒ رد على 4 ⇒ ¾ · ¼
    @Test
    fun sistersChildrenTakeTheirMothersShares() {
        val r = sa(FULL_SISTER_DAUGHTER to 1, PATERNAL_SISTER_SON to 1)
        assertShares(r, FULL_SISTER_DAUGHTER to "3/4", PATERNAL_SISTER_SON to "1/4")
    }

    // «إرثاً وحجباً» (م235) من **نفس الجهة**: العمة (مكان الأب) والأب بيحجب الأخت (م217) ⇒ ابن الأخت الشقيقة ما بيورثش
    // (الاتنين جهة الأبوة ونفس القرب بالطريقتين ⇒ م236 ما بتقولش حاجة). من جهة تانية ⇒ «لا نص» (InheritanceDistantSidesTest)
    @Test
    fun theTanzilBlocksToo() {
        val r = sa(PATERNAL_AUNT_FULL to 1, FULL_SISTER_SON to 1)
        assertShares(r, PATERNAL_AUNT_FULL to "1", FULL_SISTER_SON to "0")
        assertTrue(r.notes.any { it.kind == InheritanceNoteKind.BLOCKED && it.citation?.article == "235" && it.heirs == listOf(FULL_SISTER_SON, PATERNAL_AUNT_FULL) })
    }

    // الخال والخالة الشقيقين (نفس الصلة بالأم) ⇒ ½ · ½. ابن الأخ لأم + بنت الأخت لأم ⇒ الثلث بينهم ثم الرد ⇒ ½ · ½
    @Test
    fun sameTieSharesEqually() {
        assertShares(sa(MATERNAL_UNCLE_FULL to 1, MATERNAL_AUNT_FULL to 1), MATERNAL_UNCLE_FULL to "1/2", MATERNAL_AUNT_FULL to "1/2")
        assertShares(sa(MATERNAL_BROTHER_SON to 1, MATERNAL_SISTER_DAUGHTER to 1), MATERNAL_BROTHER_SON to "1/2", MATERNAL_SISTER_DAUGHTER to "1/2")
        // العمة (مكان الأب) + خال وخالة شقيقين (مكان الأم): الأم ⅓ والأب الباقي ⅔ ⇒ العمة ⅔ · الخال ⅙ · الخالة ⅙ (مصر: ⅔ · 2/9 · 1/9)
        assertShares(
            sa(PATERNAL_AUNT_FULL to 1, MATERNAL_UNCLE_FULL to 1, MATERNAL_AUNT_FULL to 1),
            PATERNAL_AUNT_FULL to "2/3", MATERNAL_UNCLE_FULL to "1/6", MATERNAL_AUNT_FULL to "1/6",
        )
        // الخال (مكان الأم ⅓) + ابن الأخ لأم (مكان الأخ لأم ⅙) ⇒ رد ⇒ ⅔ · ⅓
        assertShares(sa(MATERNAL_UNCLE_FULL to 1, MATERNAL_BROTHER_SON to 1), MATERNAL_UNCLE_FULL to "2/3", MATERNAL_BROTHER_SON to "1/3")
    }

    // «لا نص»: الجد أبو الأم مع الخال — 2 أجيال مقابل 3، بس الاتنين بينهم وبين الميت الأم بس ⇒ م236 ما بتحددش طريقة العد
    // · خال شقيق وخال لأب في مكان الأم ⇒ م235 ما بتقولش نصيبها يتقسم إزاي
    @Test
    fun unclearPlacesAskTheCourt() {
        val near = assertIs<InheritanceResult.NoText>(calculateInheritance(inheritanceCase("SA", MATERNAL_GRANDFATHER to 1, MATERNAL_UNCLE_FULL to 1)))
        assertEquals(NoTextReason.DISTANT_NEARNESS, near.reason)
        assertEquals(Citation(LawSource.SA_PERSONAL_STATUS, "236"), near.citation)
        val place = assertIs<InheritanceResult.NoText>(calculateInheritance(inheritanceCase("SA", MATERNAL_UNCLE_FULL to 1, MATERNAL_UNCLE_PATERNAL to 1)))
        assertEquals(NoTextReason.DISTANT_SAME_PLACE, place.reason)
        assertEquals("حساب تقريبي للتخطيط، والقسمة الرسمية بصك حصر الورثة من المحكمة", place.disclaimer)
        // الجد أبو الأم لوحده ⇒ مكان الأم ⇒ كله
        assertShares(sa(MATERNAL_GRANDFATHER to 1), MATERNAL_GRANDFATHER to "1")
    }

    // الرد على الزوج/الزوجة **بعد** ذوي الأرحام (م231/2 · م234/2): زوجة + ابن البنت ⇒ الزوجة ¼ (مش ⅛ — ابن البنت مش «فرع وارث» م205)
    // والباقي ¾ لابن البنت. من غير ذوي أرحام ⇒ الزوجة كل التركة (رد)
    @Test
    fun spouseRaddComesAfterDistantRelatives() {
        val r = sa(WIFE to 1, DAUGHTER_SON to 1)
        assertShares(r, WIFE to "1/4", DAUGHTER_SON to "3/4")
        assertEquals(ShareBasis.FARD, r.heir(WIFE)!!.basis)
        assertTrue(r.hasNote(InheritanceNoteKind.DISTANT_BEFORE_SPOUSE_RADD, "231/2"))
        assertShares(computed(inheritanceCase("SA", WIFE to 1, distantRelatives = false)), WIFE to "1")
        // زوج + بنت البنت + الخال: الزوج ½ والباقي ½ بينهم ¾ · ¼ ⇒ ⅜ · ⅛
        val h = sa(HUSBAND to 1, DAUGHTER_DAUGHTER to 1, MATERNAL_UNCLE_FULL to 1)
        assertShares(h, HUSBAND to "1/2", DAUGHTER_DAUGHTER to "3/8", MATERNAL_UNCLE_FULL to "1/8")
        // «فيه قرايب» ومحدش مكتوب ⇒ غير مدعوم بسبب واضح (مش حساب)
        val unlisted = calculateInheritance(inheritanceCase("SA", WIFE to 1, distantRelatives = true))
        assertEquals(UnsupportedReason.DISTANT_RELATIVES, assertIs<InheritanceResult.Unsupported>(unlisted).reason)
    }

    // مع صاحب فرض أو عاصب ما بيورثوش (م234): أخ + ابن البنت ⇒ الأخ كله · الأم + الخال ⇒ الأم كله (رد م231/1)
    @Test
    fun regularHeirsExcludeThem() {
        val r = sa(FULL_BROTHER to 1, DAUGHTER_SON to 1)
        assertShares(r, FULL_BROTHER to "1", DAUGHTER_SON to "0")
        assertEquals(ShareBasis.BLOCKED, r.heir(DAUGHTER_SON)!!.basis)
        assertTrue(r.hasNote(InheritanceNoteKind.DISTANT_EXCLUDED, "234"))
        assertShares(sa(MOTHER to 1, MATERNAL_UNCLE_FULL to 1), MOTHER to "1", MATERNAL_UNCLE_FULL to "0")
        assertShares(sa(DAUGHTER to 1, DAUGHTER_SON to 2), DAUGHTER to "1", DAUGHTER_SON to "0")
    }

    // أولاد كل شخص لازم يطابقوا الأعداد · ابن الأخ الشقيق عاصب مش من ذوي الأرحام
    @Test
    fun branchesMustMatchTheCounts() {
        fun reason(case: InheritanceCase) = assertIs<InheritanceResult.Invalid>(calculateInheritance(case)).reason
        assertEquals(InvalidReason.DISTANT_BRANCHES, reason(inheritanceCase("SA", DAUGHTER_SON to 2, branches = listOf(DistantBranch(DAUGHTER, 1, 0)))))
        assertEquals(InvalidReason.DISTANT_BRANCHES, reason(inheritanceCase("SA", branches = listOf(DistantBranch(FULL_BROTHER, 1, 0)))))
        assertEquals(InvalidReason.DISTANT_BRANCHES, reason(inheritanceCase("SA", branches = listOf(DistantBranch(MOTHER, 0, 1)))))
        assertEquals(InvalidReason.DISTANT_BRANCHES, reason(inheritanceCase("SA", DAUGHTER_SON to 1, branches = listOf(DistantBranch(DAUGHTER, 1, 0), DistantBranch(DAUGHTER, 0, 0)))))
        assertEquals(InvalidReason.ONLY_ONE, reason(inheritanceCase("SA", MATERNAL_GRANDFATHER to 2)))
    }
}
