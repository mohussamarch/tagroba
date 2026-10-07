package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.FATHER
import app.masroufy.core.HeirKind.FULL_BROTHER
import app.masroufy.core.HeirKind.FULL_SISTER
import app.masroufy.core.HeirKind.GRANDFATHER
import app.masroufy.core.HeirKind.HUSBAND
import app.masroufy.core.HeirKind.MATERNAL_BROTHER
import app.masroufy.core.HeirKind.MATERNAL_GRANDMOTHER
import app.masroufy.core.HeirKind.MATERNAL_SISTER
import app.masroufy.core.HeirKind.MOTHER
import app.masroufy.core.HeirKind.PATERNAL_GRANDMOTHER
import app.masroufy.core.HeirKind.WIFE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * المسائل المشهورة بأسمائها (كتب الفرائض — مثلًا «التحقيقات المرضية» للفوزان و«الرحبية» وشروحها) + نص المادة في البلدين.
 * كل اختبار مكتوب جنبه الأنصبة المتوقعة ومصدرها. الأسماء والمبالغ مخترعة.
 */
class InheritanceClassicCasesTest {
    // العُمَرية الأولى (قضاء عمر — كتب الفرائض): زوج · أم · أب ⇒ الزوج 1/2 · الأم ثلث الباقي = 1/6 · الأب 1/3
    // السعودية م213/3 · مصر م14
    @Test
    fun umariyyaWithHusband() {
        for (country in listOf("SA", "EG")) {
            val r = computed(inheritanceCase(country, HUSBAND to 1, MOTHER to 1, FATHER to 1))
            assertShares(r, HUSBAND to "1/2", MOTHER to "1/6", FATHER to "1/3")
            assertTrue(r.hasNote(InheritanceNoteKind.UMARIYYA, if (country == "SA") "213/3" else "14"))
        }
    }

    // العُمَرية التانية: زوجة · أم · أب ⇒ الزوجة 1/4 · الأم ثلث الباقي = 1/4 · الأب 1/2
    @Test
    fun umariyyaWithWife() {
        for (country in listOf("SA", "EG")) {
            val r = computed(inheritanceCase(country, WIFE to 1, MOTHER to 1, FATHER to 1))
            assertShares(r, WIFE to "1/4", MOTHER to "1/4", FATHER to "1/2")
        }
    }

    // مع الجد مكان الأب مفيش عُمَرية: زوج · أم · جد ⇒ 1/2 · 1/3 من الكل · 1/6 (السعودية م212/3 نصًا · مصر م14 «والأب فقط»)
    @Test
    fun noUmariyyaWithGrandfather() {
        for (country in listOf("SA", "EG")) {
            val r = computed(inheritanceCase(country, HUSBAND to 1, MOTHER to 1, GRANDFATHER to 1))
            assertShares(r, HUSBAND to "1/2", MOTHER to "1/3", GRANDFATHER to "1/6")
            assertTrue(r.hasNote(InheritanceNoteKind.GRANDFATHER_MOTHER_THIRD))
            assertTrue(!r.hasNote(InheritanceNoteKind.UMARIYYA))
        }
    }

    // المُشَرَّكة / الحِمارية: زوج · أم · أخوين لأم · أخ شقيق.
    // السعودية م227 نصًا: الشقيق يسقط ⇒ 1/2 · 1/6 · 1/3 · 0.
    // مصر م10: الشقيق يشارك أولاد الأم في الثلث بالتساوي ⇒ 1/2 · 1/6 · (2/9 للأخوين) · 1/9 للشقيق.
    @Test
    fun mushtarakaSaudiDropsEgyptShares() {
        val heirs = arrayOf(HUSBAND to 1, MOTHER to 1, MATERNAL_BROTHER to 2, FULL_BROTHER to 1)
        val sa = computed(inheritanceCase("SA", *heirs))
        assertShares(sa, HUSBAND to "1/2", MOTHER to "1/6", MATERNAL_BROTHER to "1/3", FULL_BROTHER to "0")
        assertEquals(ShareBasis.NOTHING_LEFT, sa.heir(FULL_BROTHER)!!.basis)
        assertTrue(sa.hasNote(InheritanceNoteKind.MUSHTARAKA_DROPPED, "227"))
        val eg = computed(inheritanceCase("EG", *heirs))
        assertShares(eg, HUSBAND to "1/2", MOTHER to "1/6", MATERNAL_BROTHER to "2/9", FULL_BROTHER to "1/9")
        assertEquals(Frac.of(1, 9), eg.heir(MATERNAL_BROTHER)!!.perPerson)
        assertTrue(eg.hasNote(InheritanceNoteKind.MUSHTARAKA_SHARED, "10"))
    }

    // نفس المُشَرَّكة بالجدة مكان الأم (السعودية م227 بتقول «وأماً أو جدة») ومع أخت شقيقة جنب الأخ (مصر م10 «أو مع أخت شقيقة»):
    // مصر: الثلث على 4 (أخ لأم · أخت لأم · أخ شقيق · أخت شقيقة) = 1/12 لكل واحد.
    @Test
    fun mushtarakaWithGrandmotherAndASister() {
        val heirs = arrayOf(HUSBAND to 1, MATERNAL_GRANDMOTHER to 1, MATERNAL_BROTHER to 1, MATERNAL_SISTER to 1, FULL_BROTHER to 1, FULL_SISTER to 1)
        val sa = computed(inheritanceCase("SA", *heirs))
        assertShares(
            sa, HUSBAND to "1/2", MATERNAL_GRANDMOTHER to "1/6", MATERNAL_BROTHER to "1/6", MATERNAL_SISTER to "1/6", FULL_BROTHER to "0", FULL_SISTER to "0",
        )
        val eg = computed(inheritanceCase("EG", *heirs))
        assertShares(
            eg, HUSBAND to "1/2", MATERNAL_GRANDMOTHER to "1/6", MATERNAL_BROTHER to "1/12", MATERNAL_SISTER to "1/12",
            FULL_BROTHER to "1/12", FULL_SISTER to "1/12",
        )
    }

    // الأكدرية: زوج · أم · جد · أخت شقيقة.
    // السعودية م212/3: الجد يحجب الأخت ⇒ الورثة زوج وأم وجد ⇒ 1/2 · 1/3 من الكل · 1/6 — المسألة ما بتقومش أصلًا.
    // مصر م22 (الحالة التانية + حد السدس، من غير ضم نصيب الأخت للجد): 1/2 · 1/3 · 1/2 · 1/6 ⇒ عول من 6 لـ9 ⇒ 3/9 · 2/9 · 3/9 · 1/9.
    // (مذهب زيد في الكتب: 9 · 6 · 8 · 4 من 27 — القانون المصري ما أخدش بيه: مفيش نص بالضم.)
    @Test
    fun akdariyya() {
        val heirs = arrayOf(HUSBAND to 1, MOTHER to 1, GRANDFATHER to 1, FULL_SISTER to 1)
        val sa = computed(inheritanceCase("SA", *heirs))
        assertShares(sa, HUSBAND to "1/2", MOTHER to "1/3", GRANDFATHER to "1/6", FULL_SISTER to "0")
        assertEquals(ShareBasis.BLOCKED, sa.heir(FULL_SISTER)!!.basis)
        val eg = computed(inheritanceCase("EG", *heirs))
        assertShares(eg, HUSBAND to "1/3", MOTHER to "2/9", GRANDFATHER to "1/9", FULL_SISTER to "1/3")
        assertTrue(eg.hasNote(InheritanceNoteKind.GRANDFATHER_SIXTH, "22"))
        assertEquals(listOf("6", "9"), eg.notes.first { it.kind == InheritanceNoteKind.AWL }.values)
    }

    // المباهلة (عول 6 ⇒ 8): زوج · أم · أخت شقيقة ⇒ 3/8 · 2/8 · 3/8 (السعودية م230 · مصر م15)
    @Test
    fun mubahalaAwlSixToEight() {
        for (country in listOf("SA", "EG")) {
            val r = computed(inheritanceCase(country, HUSBAND to 1, MOTHER to 1, FULL_SISTER to 1))
            assertShares(r, HUSBAND to "3/8", MOTHER to "1/4", FULL_SISTER to "3/8")
            assertEquals(listOf("6", "8"), r.notes.first { it.kind == InheritanceNoteKind.AWL }.values)
            assertTrue(r.hasNote(InheritanceNoteKind.AWL, if (country == "SA") "230" else "15"))
        }
    }

    // أم الأرامل (عول 12 ⇒ 17): 3 زوجات · جدتين · 4 أخوات لأم · 8 أخوات شقيقات ⇒ 17 ست، كل واحدة سهم: 1/17
    @Test
    fun ummAlArameelEveryWomanGetsOneSeventeenth() {
        for (country in listOf("SA", "EG")) {
            val r = computed(
                inheritanceCase(
                    country, WIFE to 3, PATERNAL_GRANDMOTHER to 1, MATERNAL_GRANDMOTHER to 1, MATERNAL_SISTER to 4, FULL_SISTER to 8, estate = 1_700_000,
                ),
            )
            for (h in r.heirs) assertEquals(Frac.of(1, 17), h.perPerson, h.kind.name)
            assertTrue(r.heirs.all { h -> h.amountsMinor.all { it == 100_000L } }, "17,000 ÷ 17 = 1,000 بالظبط لكل واحدة")
            assertEquals(listOf("12", "17"), r.notes.first { it.kind == InheritanceNoteKind.AWL }.values)
        }
    }

    // المنبرية (عول 24 ⇒ 27 — سُئل عنها علي على المنبر): زوجة · بنتين · أب · أم ⇒ 3/27 · 16/27 · 4/27 · 4/27
    @Test
    fun minbariyyaAwlTwentyFourToTwentySeven() {
        for (country in listOf("SA", "EG")) {
            val r = computed(inheritanceCase(country, WIFE to 1, DAUGHTER to 2, FATHER to 1, MOTHER to 1))
            assertShares(r, WIFE to "1/9", DAUGHTER to "16/27", FATHER to "4/27", MOTHER to "4/27")
            assertEquals(listOf("24", "27"), r.notes.first { it.kind == InheritanceNoteKind.AWL }.values)
        }
    }
}
