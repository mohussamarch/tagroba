package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.DAUGHTER_DAUGHTER
import app.masroufy.core.HeirKind.DAUGHTER_SON
import app.masroufy.core.HeirKind.FULL_BROTHER_DAUGHTER
import app.masroufy.core.HeirKind.FULL_SISTER_SON
import app.masroufy.core.HeirKind.HUSBAND
import app.masroufy.core.HeirKind.MATERNAL_AUNT_FULL
import app.masroufy.core.HeirKind.MATERNAL_BROTHER_SON
import app.masroufy.core.HeirKind.MATERNAL_GRANDFATHER
import app.masroufy.core.HeirKind.MATERNAL_HALF_UNCLE
import app.masroufy.core.HeirKind.MATERNAL_UNCLE_FULL
import app.masroufy.core.HeirKind.MATERNAL_UNCLE_PATERNAL
import app.masroufy.core.HeirKind.MOTHER
import app.masroufy.core.HeirKind.PATERNAL_AUNT_FULL
import app.masroufy.core.HeirKind.PATERNAL_AUNT_MATERNAL
import app.masroufy.core.HeirKind.PATERNAL_BROTHER_DAUGHTER
import app.masroufy.core.HeirKind.PATERNAL_SISTER_SON
import app.masroufy.core.HeirKind.SON
import app.masroufy.core.HeirKind.SON_DAUGHTER_SON
import app.masroufy.core.HeirKind.WIFE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * ذوو الأرحام في مصر — **بالقرابة** (77/1943 م31–38 — نص الجريدة الرسمية). الصنف ثم الدرجة ثم ولد العاصب ثم القوة، والتلتين والتلت
 * بين الجهتين في الصنف الرابع، وللذكر مثل حظ الأنثيين **على الأشخاص** (م38). التركة 72,000.00 والأرقام مخترعة.
 */
class InheritanceDistantEgyptTest {
    private fun eg(vararg heirs: Pair<HeirKind, Int>) = computed(inheritanceCase("EG", *heirs))

    // م38: ابن البنت + بنت البنت ⇒ ⅔ · ⅓ (السعودية ½ · ½). ومن غير ما نعرف أولاد مين — القسمة على الأشخاص
    @Test
    fun maleTakesTwiceTheFemale() {
        val r = eg(DAUGHTER_SON to 1, DAUGHTER_DAUGHTER to 1)
        assertShares(r, DAUGHTER_SON to "2/3", DAUGHTER_DAUGHTER to "1/3")
        assertEquals(listOf(4_800_000L), r.amounts(DAUGHTER_SON))
        assertTrue(r.hasNote(InheritanceNoteKind.DISTANT_EGYPT_ORDER, "31"))
        // ابن + 3 بنات بنات (من غير توزيع على البنات) ⇒ 2 · 1 · 1 · 1 من 5
        val many = eg(DAUGHTER_SON to 1, DAUGHTER_DAUGHTER to 3)
        assertShares(many, DAUGHTER_SON to "2/5", DAUGHTER_DAUGHTER to "3/5")
        assertEquals(Frac.of(1, 5), many.heir(DAUGHTER_DAUGHTER)!!.perPerson)
    }

    // م32: الأقرب درجة — بنت البنت (درجة 2) قبل ابن بنت الابن (درجة 3)
    @Test
    fun nearerDegreeFirst() {
        val r = eg(DAUGHTER_DAUGHTER to 1, SON_DAUGHTER_SON to 2)
        assertShares(r, DAUGHTER_DAUGHTER to "1", SON_DAUGHTER_SON to "0")
        assertTrue(r.hasNote(InheritanceNoteKind.BLOCKED, "32"))
    }

    // م31: الصنف قبل الصنف — الجد أبو الأم (التاني) قبل ابن الأخت (التالت) · بنت البنت (الأول) قبل الجد
    @Test
    fun classesComeInOrder() {
        assertShares(eg(MATERNAL_GRANDFATHER to 1, FULL_SISTER_SON to 1), MATERNAL_GRANDFATHER to "1", FULL_SISTER_SON to "0")
        val r = eg(DAUGHTER_DAUGHTER to 1, MATERNAL_GRANDFATHER to 1, MATERNAL_UNCLE_FULL to 1)
        assertShares(r, DAUGHTER_DAUGHTER to "1", MATERNAL_GRANDFATHER to "0", MATERNAL_UNCLE_FULL to "0")
        assertTrue(r.hasNote(InheritanceNoteKind.BLOCKED, "31"))
    }

    // م34: نفس الدرجة ⇒ الأقوى: أصله لأبوين قبل لأب قبل لأم
    @Test
    fun strongerTieFirstInTheThirdClass() {
        val r = eg(FULL_SISTER_SON to 1, PATERNAL_SISTER_SON to 1, MATERNAL_BROTHER_SON to 3)
        assertShares(r, FULL_SISTER_SON to "1", PATERNAL_SISTER_SON to "0", MATERNAL_BROTHER_SON to "0")
        assertTrue(r.hasNote(InheritanceNoteKind.BLOCKED, "34"))
        assertFalse(r.hasNote(InheritanceNoteKind.UNCLEAR_TEXT))
    }

    // م34 بنص الجريدة («وإلا قدم»): ولد العاصب الأول، والقوة بعده. بنت الأخ الشقيق (بنت عاصب) قبل ابن الأخت الشقيقة، وبنت الأخ لأب
    // (أضعف قرابة بس بنت عاصب) قبل ابن الأخت الشقيقة. الاتنين أولاد عاصب ⇒ القوة (الشقيق). وفي قراية تانية ابن الأخت (ابن صاحبة فرض
    // مش «ذي رحم») ما يتأخرش ⇒ النتيجة بتنبيه «ممكن يختلف عن حكم المحكمة»
    @Test
    fun childOfAnAgnateComesFirst() {
        val r = eg(FULL_BROTHER_DAUGHTER to 1, FULL_SISTER_SON to 1)
        assertShares(r, FULL_BROTHER_DAUGHTER to "1", FULL_SISTER_SON to "0")
        assertTrue(r.hasNote(InheritanceNoteKind.UNCLEAR_TEXT, "34"))
        assertShares(eg(PATERNAL_BROTHER_DAUGHTER to 2, FULL_SISTER_SON to 1), PATERNAL_BROTHER_DAUGHTER to "1", FULL_SISTER_SON to "0")
        val agnates = eg(FULL_BROTHER_DAUGHTER to 1, PATERNAL_BROTHER_DAUGHTER to 1)
        assertShares(agnates, FULL_BROTHER_DAUGHTER to "1", PATERNAL_BROTHER_DAUGHTER to "0")
        assertFalse(agnates.hasNote(InheritanceNoteKind.UNCLEAR_TEXT))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        try {
            assertEquals("ممكن يختلف عن حكم المحكمة — النص مش واضح في المسألة دي", r.notes.first { it.kind == InheritanceNoteKind.UNCLEAR_TEXT }.text)
        } finally {
            Texts.arabicVariant = ArabicVariant.MSA
        }
    }

    // م35: العمة الشقيقة (قرابة الأب) + خال وخالة شقيقين (قرابة الأم) ⇒ العمة ⅔ · الخال ⅓ × ⅔ = 2/9 · الخالة ⅓ × ⅓ = 1/9
    @Test
    fun twoThirdsToTheFathersSide() {
        val r = eg(PATERNAL_AUNT_FULL to 1, MATERNAL_UNCLE_FULL to 1, MATERNAL_AUNT_FULL to 1)
        assertShares(r, PATERNAL_AUNT_FULL to "2/3", MATERNAL_UNCLE_FULL to "2/9", MATERNAL_AUNT_FULL to "1/9")
        assertTrue(r.hasNote(InheritanceNoteKind.DISTANT_SIDES, "35"))
        assertEquals(listOf(4_800_000L), r.amounts(PATERNAL_AUNT_FULL))
        assertEquals(listOf(1_600_000L), r.amounts(MATERNAL_UNCLE_FULL))
        assertEquals(listOf(800_000L), r.amounts(MATERNAL_AUNT_FULL))
    }

    // م35: جوه الجهة الأقوى قرابة — الخالة الشقيقة قبل الخال لأب · العمة لأم والعم لأم (نفس القوة) ⇒ 1:2
    @Test
    fun strongerTieInsideEachSide() {
        assertShares(eg(MATERNAL_UNCLE_PATERNAL to 1, MATERNAL_AUNT_FULL to 1), MATERNAL_UNCLE_PATERNAL to "0", MATERNAL_AUNT_FULL to "1")
        assertShares(eg(PATERNAL_AUNT_MATERNAL to 1, MATERNAL_HALF_UNCLE to 1), PATERNAL_AUNT_MATERNAL to "1/3", MATERNAL_HALF_UNCLE to "2/3")
        // في السعودية نفس الاتنين في مكان الأب بنفس الصلة (أخوات الأب من أمه) ⇒ بالتساوي
        assertShares(computed(inheritanceCase("SA", PATERNAL_AUNT_MATERNAL to 1, MATERNAL_HALF_UNCLE to 1)), PATERNAL_AUNT_MATERNAL to "1/2", MATERNAL_HALF_UNCLE to "1/2")
    }

    // م30: الرد على الزوج/الزوجة بعد ذوي الأرحام — زوج + ابن البنت ⇒ ½ · ½ (مش الزوج كله) · زوجة + خال ⇒ ¼ · ¾
    @Test
    fun spouseRaddComesAfterDistantRelatives() {
        val r = eg(HUSBAND to 1, DAUGHTER_SON to 1)
        assertShares(r, HUSBAND to "1/2", DAUGHTER_SON to "1/2")
        assertTrue(r.hasNote(InheritanceNoteKind.DISTANT_BEFORE_SPOUSE_RADD, "30"))
        assertShares(eg(WIFE to 1, MATERNAL_UNCLE_FULL to 1), WIFE to "1/4", MATERNAL_UNCLE_FULL to "3/4")
        assertShares(computed(inheritanceCase("EG", HUSBAND to 1, distantRelatives = false)), HUSBAND to "1")
    }

    // م31: مع صاحب فرض نسبي ما بيورثوش — الأم + ابن البنت ⇒ الأم كله (رد م30)
    @Test
    fun regularHeirsExcludeThem() {
        val r = eg(MOTHER to 1, DAUGHTER_SON to 1)
        assertShares(r, MOTHER to "1", DAUGHTER_SON to "0")
        assertTrue(r.hasNote(InheritanceNoteKind.DISTANT_EXCLUDED, "31"))
    }

    // الواجبة (71/1946 م76) شرطها إن الفرع «غير وارث»: لو ذوو الأرحام هم اللي بيورثوا ⇒ غير مدعوم. ولو فيه ابن عايش ⇒ الواجبة عادي
    // وابن البنت المكتوب ما بيورثش بالرحم
    @Test
    fun wajibaWithInheritingDistantRelativesIsUnsupported() {
        val pre = listOf(PredeceasedChild(false, 1, 0))
        val r = calculateInheritance(inheritanceCase("EG", WIFE to 1, DAUGHTER_SON to 1, predeceased = pre))
        assertEquals(UnsupportedReason.WAJIBA_WITH_DISTANT, assertIs<InheritanceResult.Unsupported>(r).reason)
        assertEquals("حساب تقريبي للتخطيط، والقسمة الرسمية بإعلام الوراثة من المحكمة", r.disclaimer)
        val withSon = computed(inheritanceCase("EG", SON to 1, DAUGHTER_SON to 1, predeceased = pre))
        assertEquals(Frac.ZERO, withSon.heir(DAUGHTER_SON)!!.share)
        assertTrue(withSon.wajibaMinor > 0)
    }

    // مصر ما بتحتاجش توزيع الأولاد على أهاليهم — بس لو اتكتب لازم يطابق
    @Test
    fun branchesAreOptionalButChecked() {
        val r = computed(inheritanceCase("EG", DAUGHTER_SON to 1, DAUGHTER_DAUGHTER to 3, branches = listOf(DistantBranch(DAUGHTER, 1, 0), DistantBranch(DAUGHTER, 0, 3))))
        assertShares(r, DAUGHTER_SON to "2/5", DAUGHTER_DAUGHTER to "3/5")
        val bad = calculateInheritance(inheritanceCase("EG", DAUGHTER_SON to 1, branches = listOf(DistantBranch(DAUGHTER, 2, 0))))
        assertEquals(InvalidReason.DISTANT_BRANCHES, assertIs<InheritanceResult.Invalid>(bad).reason)
    }
}
