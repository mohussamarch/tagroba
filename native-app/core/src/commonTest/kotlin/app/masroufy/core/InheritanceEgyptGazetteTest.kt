package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.DAUGHTER_SON
import app.masroufy.core.HeirKind.FATHER
import app.masroufy.core.HeirKind.FULL_SISTER
import app.masroufy.core.HeirKind.GRANDFATHER
import app.masroufy.core.HeirKind.HUSBAND
import app.masroufy.core.HeirKind.MOTHER
import app.masroufy.core.HeirKind.PATERNAL_SISTER
import app.masroufy.core.HeirKind.SON
import app.masroufy.core.HeirKind.SON_DAUGHTER
import app.masroufy.core.HeirKind.SON_SON
import app.masroufy.core.HeirKind.WIFE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * المراجعة على **نص الجريدة الرسمية** (77/1943 — الوقائع المصرية عدد 92، 12 أغسطس 1943 · 71/1946 — عدد 65، 1 يوليو 1946؛
 * صور الجريدة على manshurat.org). اختبار لكل مادة النسخة القديمة (qadaya.net) كانت فيها مختلفة وممكن تغيّر النتيجة —
 * الجدول الكامل في OVERRIDES §69.4. كل الأرقام مخترعة والحساب على الورق جنب كل رقم.
 */
class InheritanceEgyptGazetteTest {
    private fun eg(vararg heirs: Pair<HeirKind, Int>) = computed(inheritanceCase("EG", *heirs))

    // م4 «ما يكفى لتجهيز الميت ومن تلزمه نفقته»: التجهيز (ومعاه تجهيز اللي بيصرف عليهم لو ماتوا قبل الدفن — نفس الخانة) قبل الديون.
    // تركة 10,000 · تجهيز 3,000 · ديون 8,000 ⇒ التجهيز 3,000 كامل والديون 7,000 بس والورثة صفر
    @Test
    fun art4FuneralBeforeDebts() {
        val r = computed(inheritanceCase("EG", SON to 1, estate = 1_000_000, funeral = 300_000, debts = 800_000))
        assertEquals(300_000L, r.funeralMinor)
        assertEquals(700_000L, r.debtsMinor)
        assertEquals(0L, r.heirsMinor)
        assertTrue(r.hasNote(InheritanceNoteKind.DEBTS_EXCEED, "4"))
    }

    // م8: أصحاب الفروض 12 والابن **مش** منهم (عاصب) · البنت لوحدها صاحبة فرض (½) والباقي رد
    @Test
    fun art8TheSonHasNoFixedShare() {
        assertEquals(ShareBasis.RESIDUARY, eg(SON to 1, DAUGHTER to 1).heir(SON)!!.basis)
        assertEquals(ShareBasis.RADD, eg(DAUGHTER to 1).heir(DAUGHTER)!!.basis)
    }

    // م9 «ولد أو ولد ابن» + م21: الأب ⅙ مع **أي** ولد — مع البنت ⅙ + الباقي = ½ · مع بنت الابن نفس الشيء · مع الابن ⅙ بس.
    // ابن البنت (من ذوي الأرحام) مش «ولد» ولا «ولد ابن» ⇒ الأب الباقي كله.
    // زوج · بنتين · أب · أم ⇒ 3 + 8 + 2 + 2 = 15 من 12 (عول) — سدس الأب داخل العول
    @Test
    fun art9TheFatherTakesASixthWithAnyChild() {
        val withDaughter = eg(FATHER to 1, DAUGHTER to 1)
        assertShares(withDaughter, FATHER to "1/2", DAUGHTER to "1/2")
        assertEquals(ShareBasis.FARD_AND_RESIDUARY, withDaughter.heir(FATHER)!!.basis)
        assertShares(eg(FATHER to 1, SON_DAUGHTER to 1), FATHER to "1/2", SON_DAUGHTER to "1/2")
        val withSon = eg(FATHER to 1, SON to 1)
        assertShares(withSon, FATHER to "1/6", SON to "5/6")
        assertEquals(ShareBasis.FARD, withSon.heir(FATHER)!!.basis)
        assertShares(eg(FATHER to 1, DAUGHTER_SON to 1), FATHER to "1", DAUGHTER_SON to "0")
        val awl = eg(HUSBAND to 1, DAUGHTER to 2, FATHER to 1, MOTHER to 1)
        assertShares(awl, HUSBAND to "1/5", DAUGHTER to "8/15", FATHER to "2/15", MOTHER to "2/15")
        assertEquals(listOf("12", "15"), awl.notes.first { it.kind == InheritanceNoteKind.AWL }.values)
    }

    // م19(2) «فى درجتهن مطلقا»: بنتين + ابن ابن + بنت ابن ⇒ البنتين ⅔ والباقي ⅓ لابن الابن وبنت الابن 2:1 (2/9 · 1/9).
    // (الجزء «أو كانوا أنزل منهن» محتاج ابن ابن ابن — جيل تاني مش في مدخلات النسخة دي)
    @Test
    fun art19SonsDaughterBecomesResiduaryWithTheSonsSon() {
        assertShares(eg(DAUGHTER to 2, SON_SON to 1, SON_DAUGHTER to 1), DAUGHTER to "2/3", SON_SON to "2/9", SON_DAUGHTER to "1/9")
    }

    // م20 «مع البنات أو بنات الابن»: الأخت الشقيقة مع بنت الابن (من غير بنت) عصبة ⇒ ½ · ½
    @Test
    fun art20SistersWithSonsDaughtersTakeTheRest() {
        val r = eg(SON_DAUGHTER to 1, FULL_SISTER to 1)
        assertShares(r, SON_DAUGHTER to "1/2", FULL_SISTER to "1/2")
        assertTrue(r.hasNote(InheritanceNoteKind.WITH_DAUGHTERS, "20"))
    }

    // م22 الحالة الأولى — البديل التالت «أو إناثا عصبن مع الفرع الوارث من الإناث»: جد + بنت + أخت شقيقة ⇒ البنت ½،
    // والجد يقاسم الأخت في الباقي كأخ (2:1) ⇒ ⅓ · ⅙ (+ تنبيه المالك لأن م21 كمان بتتكلم عن الجد مع البنت)
    @Test
    fun art22TheGrandfatherSharesWithSistersMadeResiduaryByDaughters() {
        val r = eg(GRANDFATHER to 1, DAUGHTER to 1, FULL_SISTER to 1)
        assertShares(r, GRANDFATHER to "1/3", DAUGHTER to "1/2", FULL_SISTER to "1/6")
        assertTrue(r.hasNote(InheritanceNoteKind.GRANDFATHER_SHARES, "22"))
        // الحالة التانية: أخوات بفرضهم من غير بنات ⇒ الجد الباقي
        assertTrue(eg(GRANDFATHER to 1, FULL_SISTER to 1).hasNote(InheritanceNoteKind.GRANDFATHER_RESIDUE, "22"))
    }

    // م28 (كانت ناقصة من النسخة القديمة): «تحجب الأخت لأبوين كل من الابن وابن الابن وإن نزل والأب»
    @Test
    fun art28TheFullSisterIsExcludedBySonSonsSonAndFather() {
        for (blocker in listOf(SON, SON_SON, FATHER)) {
            val r = eg(blocker to 1, FULL_SISTER to 2)
            assertEquals(Frac.ZERO, r.heir(FULL_SISTER)!!.share, blocker.name)
            assertTrue(r.notes.any { it.kind == InheritanceNoteKind.BLOCKED && it.citation?.article == "28" && it.heirs == listOf(FULL_SISTER, blocker) }, blocker.name)
        }
        // م29: الأخت لأب بتتحجب بالشقيقة اللي بقت عصبة مع البنات
        val r = eg(DAUGHTER to 1, FULL_SISTER to 1, PATERNAL_SISTER to 1)
        assertShares(r, DAUGHTER to "1/2", FULL_SISTER to "1/2", PATERNAL_SISTER to "0")
        assertTrue(r.hasNote(InheritanceNoteKind.BLOCKED, "29"))
    }

    // 71/1946 م37 «تصح الوصية بالثلث للوارث وغيره وتنفذ من غير إجازة الورثة»: لوارث 20,000 (تحت الثلث 24,000) ⇒ تتنفذ كلها ·
    // لوارث 30,000 من غير موافقة ⇒ الثلث بس 24,000
    @Test
    fun law71Art37ABequestToAnHeirWithinAThird() {
        val within = computed(inheritanceCase("EG", WIFE to 1, SON to 1, bequest = Bequest(2_000_000, toHeir = true)))
        assertEquals(2_000_000L, within.bequestMinor)
        assertTrue(within.notes.any { it.kind == InheritanceNoteKind.BEQUEST_HEIR_VALID && it.citation == Citation(LawSource.EG_BEQUEST, "37") })
        val over = computed(inheritanceCase("EG", WIFE to 1, SON to 1, bequest = Bequest(3_000_000, toHeir = true)))
        assertEquals(2_400_000L, over.bequestMinor)
        assertTrue(over.hasNote(InheritanceNoteKind.BEQUEST_CAPPED, "37"))
    }

    // م76–77 «ما يكمله»: ابن عايش + بنت ماتت قبله (ليها ولد وبنت). نصيبها لو عايشة ⅓ = 24,000 · اتدّى لهم 10,000 في حياته ⇒ الواجب
    // الفرق بس 14,000 (للولد 9,333.33 · للبنت 4,666.67). اتدّى لهم 30,000 (أكتر من نصيبها) ⇒ صفر
    @Test
    fun law71Arts76And77OnlyTheShortfallIsOwed() {
        val pre = { given: Long -> listOf(PredeceasedChild(false, 1, 1, givenInLifeMinor = given)) }
        val r = computed(inheritanceCase("EG", SON to 1, predeceased = pre(1_000_000)))
        assertEquals(1_400_000L, r.wajibaMinor)
        assertEquals(listOf(933_333L), r.wajiba.single().sonsMinor)
        assertEquals(listOf(466_667L), r.wajiba.single().daughtersMinor)
        assertEquals(0L, computed(inheritanceCase("EG", SON to 1, predeceased = pre(3_000_000))).wajibaMinor)
    }

    // م78 «الوصية الواجبة مقدمة على غيرها … وأوصى لغيرهم»: الواجبة خدت الثلث كله (24,000) ⇒ الوصية الاختيارية لغيرهم من غير
    // موافقة الورثة ما فضلش لها حاجة من الثلث
    @Test
    fun law71Art78TheObligatoryBequestComesFirst() {
        val r = computed(inheritanceCase("EG", SON to 1, predeceased = listOf(PredeceasedChild(false, 1, 0)), bequest = Bequest(1_000_000)))
        assertEquals(2_400_000L, r.wajibaMinor)
        assertEquals(0L, r.bequestMinor)
        assertTrue(r.hasNote(InheritanceNoteKind.BEQUEST_CAPPED, "37"))
    }
}
