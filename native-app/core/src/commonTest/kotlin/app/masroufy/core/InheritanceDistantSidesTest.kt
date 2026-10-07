package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.DAUGHTER_DAUGHTER
import app.masroufy.core.HeirKind.DAUGHTER_SON
import app.masroufy.core.HeirKind.FULL_SISTER_DAUGHTER
import app.masroufy.core.HeirKind.FULL_SISTER_SON
import app.masroufy.core.HeirKind.HUSBAND
import app.masroufy.core.HeirKind.MATERNAL_BROTHER
import app.masroufy.core.HeirKind.MATERNAL_BROTHER_DAUGHTER
import app.masroufy.core.HeirKind.MATERNAL_BROTHER_SON
import app.masroufy.core.HeirKind.MATERNAL_SISTER_DAUGHTER
import app.masroufy.core.HeirKind.MATERNAL_UNCLE_FULL
import app.masroufy.core.HeirKind.PATERNAL_AUNT_FULL
import app.masroufy.core.HeirKind.PATERNAL_SISTER_SON
import app.masroufy.core.HeirKind.SON_DAUGHTER_SON
import app.masroufy.core.HeirKind.WIFE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * السعودية — م235 «إرثاً وحجباً» × م236 «وإذا اختلفت الجهات فيرث البعيد مع وجود القريب» (رد المالك §69.7 ⇒ «اسأل المحكمة»).
 * الشرط: قريب نصيبه صفر في مسألة التنزيل الكاملة، وأكبر من صفر لو جهته لوحدها ⇒ «لا نص». الأسامي والأرقام مخترعة.
 */
class InheritanceDistantSidesTest {
    private fun sa(vararg heirs: Pair<HeirKind, Int>, branches: List<DistantBranch> = emptyList()) =
        calculateInheritance(inheritanceCase("SA", *heirs, branches = branches))

    private fun assertSidesNoText(r: InheritanceResult) {
        val n = assertIs<InheritanceResult.NoText>(r)
        assertEquals(NoTextReason.DISTANT_SIDES_BLOCKING, n.reason)
        assertEquals(Citation(LawSource.SA_PERSONAL_STATUS, "236"), n.citation)
    }

    // حالة المالك: ابن البنت (البنوة) + ابن الأخ لأم (الأمومة). التنزيل: البنت بتحجب الأخ لأم (م219) ⇒ صفر.
    // الأمومة لوحدها: الأخ لأم ⅙ + الرد ⇒ كله ⇒ أكبر من صفر ⇒ «لا نص» (كان «ابن البنت كله»)
    @Test
    fun theOwnersCaseAsksTheCourt() {
        val r = sa(DAUGHTER_SON to 1, MATERNAL_BROTHER_SON to 1)
        assertSidesNoText(r)
        val n = r as InheritanceResult.NoText
        assertEquals("حساب تقريبي للتخطيط، والقسمة الرسمية بصك حصر الورثة من المحكمة", n.disclaimer)
        assertTrue(n.text.startsWith("لا نص — اسأل المحكمة: "), n.text)
        // نفس النمط بأعداد تانية ومع الزوج/الزوجة (الزوجة بتاخد فرضها والباقي هو اللي فيه التعارض)
        assertSidesNoText(sa(DAUGHTER_SON to 2, MATERNAL_BROTHER_SON to 3, branches = listOf(DistantBranch(DAUGHTER, 2, 0), DistantBranch(MATERNAL_BROTHER, 3, 0))))
        assertSidesNoText(sa(WIFE to 1, DAUGHTER_SON to 1, MATERNAL_BROTHER_SON to 1))
        assertSidesNoText(sa(HUSBAND to 1, DAUGHTER_DAUGHTER to 1, MATERNAL_SISTER_DAUGHTER to 1))
    }

    // نفس النمط من وارثين تانيين: الأب (مكان العمة — الأبوة) بيحجب الأخت لأم · بنت الابن (البنوة) بتحجب الأخ لأم
    @Test
    fun theSamePatternFromOtherPlaces() {
        assertSidesNoText(sa(PATERNAL_AUNT_FULL to 1, MATERNAL_SISTER_DAUGHTER to 1))
        assertSidesNoText(sa(SON_DAUGHTER_SON to 1, MATERNAL_BROTHER_DAUGHTER to 1))
    }

    // غير مباشر: ابن البنت + بنت الأخت الشقيقة + ابن الأخت لأب. التنزيل: البنت ½ والشقيقة عصبة مع البنت بتاخد الباقي وتحجب
    // الأخت لأب (م218/م226). الأبوة لوحدها: الشقيقة ½ واللي لأب ⅙ ثم الرد ⇒ ¾ · ¼ ⇒ ابن الأخت لأب كان هيورث ⇒ «لا نص»
    @Test
    fun anIndirectBlockFromAnotherSideAsksToo() {
        assertSidesNoText(sa(DAUGHTER_SON to 1, FULL_SISTER_DAUGHTER to 1, PATERNAL_SISTER_SON to 1))
        val alone = computed(inheritanceCase("SA", FULL_SISTER_DAUGHTER to 1, PATERNAL_SISTER_SON to 1))
        assertShares(alone, FULL_SISTER_DAUGHTER to "3/4", PATERNAL_SISTER_SON to "1/4")
    }

    // مش داخلين في الشرط ⇒ الحساب زي ما هو:
    // (1) جهات مختلفة والنصيب بيقل بس: ابن البنت + الخال ¾ · ¼ · بنت البنت + ابن الأخت الشقيقة ½ · ½
    // (2) الحجب من نفس الجهة حتى مع وجود جهة تانية: العمة + ابن الأخت الشقيقة + الخال ⇒ الأب بيحجب الأخت (الأبوة لوحدها كمان) ⇒
    //     الأم ⅓ (أخت واحدة) والأب الباقي ⅔ ⇒ العمة ⅔ · الخال ⅓ · ابن الأخت صفر
    // (3) نفس الجهة من غير حجب: الخال + ابن الأخ لأم (الاتنين الأمومة) ⅔ · ⅓
    @Test
    fun whatTheConditionLeavesAlone() {
        assertShares(computed(inheritanceCase("SA", DAUGHTER_SON to 1, MATERNAL_UNCLE_FULL to 1)), DAUGHTER_SON to "3/4", MATERNAL_UNCLE_FULL to "1/4")
        assertShares(computed(inheritanceCase("SA", DAUGHTER_DAUGHTER to 1, FULL_SISTER_SON to 1)), DAUGHTER_DAUGHTER to "1/2", FULL_SISTER_SON to "1/2")
        val sameSide = computed(inheritanceCase("SA", PATERNAL_AUNT_FULL to 1, FULL_SISTER_SON to 1, MATERNAL_UNCLE_FULL to 1))
        assertShares(sameSide, PATERNAL_AUNT_FULL to "2/3", FULL_SISTER_SON to "0", MATERNAL_UNCLE_FULL to "1/3")
        assertTrue(sameSide.notes.any { it.kind == InheritanceNoteKind.BLOCKED && it.citation?.article == "235" && it.heirs == listOf(FULL_SISTER_SON, PATERNAL_AUNT_FULL) })
        assertShares(computed(inheritanceCase("SA", MATERNAL_UNCLE_FULL to 1, MATERNAL_BROTHER_SON to 1)), MATERNAL_UNCLE_FULL to "2/3", MATERNAL_BROTHER_SON to "1/3")
    }

    // مصر مالهاش دعوة (بالقرابة م31): ابن البنت (الصنف الأول) قبل ابن الأخ لأم (التالت) ⇒ كله زي ما هو
    @Test
    fun egyptIsUnchanged() {
        val r = computed(inheritanceCase("EG", DAUGHTER_SON to 1, MATERNAL_BROTHER_SON to 1))
        assertShares(r, DAUGHTER_SON to "1", MATERNAL_BROTHER_SON to "0")
    }

    // خاصية على 1500 مسألة سعودية عشوائية: أي قريب نصيبه صفر في حساب كامل لازم يكون صفر (أو مايتحسبش) لو جهته لوحدها —
    // يعني محدش بيتمنع بسبب جهة تانية من غير «لا نص». وكل «لا نص» من النوع ده جهة واحدة فيها لوحدها بيورث فعلًا
    @Test
    fun noRelativeIsSilentlyKeptOutByAnotherSide() {
        val kinds = HeirKind.entries.filter { it.isDistant && it != HeirKind.MATERNAL_GRANDFATHER }
        var seed = 20261006L
        fun next(bound: Int): Int {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33) % bound).toInt()
        }
        var checked = 0
        var sidesNoText = 0
        repeat(1500) {
            val heirs = mutableMapOf<HeirKind, Int>()
            for (k in kinds) if (next(5) == 0) heirs[k] = 1
            if (heirs.isEmpty()) heirs[kinds[next(kinds.size)]] = 1
            val r = calculateInheritance(inheritanceCase("SA", *heirs.toList().toTypedArray()))
            fun aloneOf(k: HeirKind): InheritanceResult {
                val side = distantInfo(k).side
                return calculateInheritance(inheritanceCase("SA", *heirs.filterKeys { distantInfo(it).side == side }.toList().toTypedArray()))
            }
            if (r is InheritanceResult.Computed) {
                for (h in r.heirs.filter { !it.share.isPositive }) {
                    val alone = aloneOf(h.kind)
                    checked++
                    assertFalse(alone is InheritanceResult.Computed && alone.heir(h.kind)!!.share.isPositive, "${h.kind} اتمنع بجهة تانية: $heirs")
                }
            } else if (r is InheritanceResult.NoText && r.reason == NoTextReason.DISTANT_SIDES_BLOCKING) {
                sidesNoText++
                assertTrue(heirs.keys.map { distantInfo(it).side }.distinct().size > 1, "جهة واحدة: $heirs")
            }
        }
        assertTrue(checked > 100 && sidesNoText > 50, "اتفحص $checked · لا نص $sidesNoText")
    }
}
