package app.masroufy.core

import app.masroufy.core.HeirKind.DAUGHTER
import app.masroufy.core.HeirKind.FULL_BROTHER
import app.masroufy.core.HeirKind.FULL_SISTER
import app.masroufy.core.HeirKind.GRANDFATHER
import app.masroufy.core.HeirKind.HUSBAND
import app.masroufy.core.HeirKind.MOTHER
import app.masroufy.core.HeirKind.PATERNAL_SISTER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * (1) مسائل مصر الغامضة (رد المالك §69.3): **الحساب زي ما هو + ملاحظة** «ممكن يختلف عن حكم المحكمة — النص مش واضح في المسألة دي».
 * (2) ذوو الأرحام عشوائي في البلدين (مولّد ثابت): أي نتيجة محسوبة بتحقق الثوابت، وغير كده سبب معروف.
 */
class InheritanceUnclearAndRandomTest {
    // الجد مع البنت والأخت (م22): نفس أرقام جلسة 20 (½ · ⅓ · ⅙) + الملاحظة. السعودية مفيهاش الملاحظة
    @Test
    fun egyptGrandfatherWithDaughtersAndSiblingsIsFlagged() {
        val eg = computed(inheritanceCase("EG", DAUGHTER to 1, GRANDFATHER to 1, FULL_SISTER to 1))
        assertShares(eg, DAUGHTER to "1/2", GRANDFATHER to "1/3", FULL_SISTER to "1/6")
        assertTrue(eg.hasNote(InheritanceNoteKind.UNCLEAR_TEXT, "22"))
        val brother = computed(inheritanceCase("EG", DAUGHTER to 1, GRANDFATHER to 1, FULL_BROTHER to 1))
        assertShares(brother, DAUGHTER to "1/2", GRANDFATHER to "1/4", FULL_BROTHER to "1/4")
        assertTrue(brother.hasNote(InheritanceNoteKind.UNCLEAR_TEXT, "22"))
        val sa = computed(inheritanceCase("SA", DAUGHTER to 1, GRANDFATHER to 1, FULL_SISTER to 1))
        assertFalse(sa.hasNote(InheritanceNoteKind.UNCLEAR_TEXT))
        // الجد مع الإخوة من غير بنات ⇒ النص واضح (م22) ⇒ مفيش ملاحظة
        assertFalse(computed(inheritanceCase("EG", GRANDFATHER to 1, FULL_BROTHER to 1)).hasNote(InheritanceNoteKind.UNCLEAR_TEXT))
        assertFalse(computed(inheritanceCase("EG", HUSBAND to 1, MOTHER to 1, GRANDFATHER to 1, FULL_BROTHER to 3)).hasNote(InheritanceNoteKind.UNCLEAR_TEXT))
    }

    // الأكدرية (زوج · أم · جد · أخت): نفس العول 6 ⇒ 9 + الملاحظة. وبأختين: زوج ½ · أم ⅙ · جد ⅙ · أختين ⅔ ⇒ عول 6 ⇒ 9 بنفس الملاحظة
    @Test
    fun egyptAkdariyyaIsFlagged() {
        val eg = computed(inheritanceCase("EG", HUSBAND to 1, MOTHER to 1, GRANDFATHER to 1, FULL_SISTER to 1))
        assertShares(eg, HUSBAND to "1/3", MOTHER to "2/9", GRANDFATHER to "1/9", FULL_SISTER to "1/3")
        assertTrue(eg.hasNote(InheritanceNoteKind.UNCLEAR_TEXT, "22"))
        val two = computed(inheritanceCase("EG", HUSBAND to 1, MOTHER to 1, GRANDFATHER to 1, FULL_SISTER to 2))
        assertShares(two, HUSBAND to "1/3", MOTHER to "1/9", GRANDFATHER to "1/9", FULL_SISTER to "4/9")
        assertTrue(two.hasNote(InheritanceNoteKind.UNCLEAR_TEXT, "22"))
        assertFalse(computed(inheritanceCase("SA", HUSBAND to 1, MOTHER to 1, GRANDFATHER to 1, FULL_SISTER to 1)).hasNote(InheritanceNoteKind.UNCLEAR_TEXT))
    }

    // أشقاء ولأب الاتنين مع الجد في مصر ⇒ «لا نص» زي ما هي (رد المالك: تفضل)
    @Test
    fun mixedSiblingsWithTheGrandfatherStayNoText() {
        val r = calculateInheritance(inheritanceCase("EG", GRANDFATHER to 1, FULL_SISTER to 1, PATERNAL_SISTER to 1))
        assertEquals(NoTextReason.GRANDFATHER_MIXED_SIBLINGS, assertIs<InheritanceResult.NoText>(r).reason)
    }

    private var seed = 20261005L

    private fun next(bound: Int): Int {
        seed = (seed * 6364136223846793005L + 1442695040888963407L)
        return ((seed ushr 33) % bound).toInt()
    }

    // 2000 مسألة ذوي أرحام (+ زوج/زوجة أحيانًا) في البلدين — والأولاد موزعين على أهاليهم في نص المسائل
    @Test
    fun randomDistantCasesKeepEveryHalala() {
        val outcomes = mutableMapOf<String, Int>()
        var computedCount = 0
        val distantKinds = HeirKind.entries.filter { it.isDistant }
        repeat(2000) { n ->
            val country = if (n % 2 == 0) "SA" else "EG"
            val heirs = mutableMapOf<HeirKind, Int>()
            for (k in distantKinds) if (next(6) == 0) heirs[k] = if (k == HeirKind.MATERNAL_GRANDFATHER) 1 else 1 + next(4)
            if (heirs.isEmpty()) heirs[distantKinds[next(distantKinds.size)]] = 1
            when (next(4)) {
                0 -> heirs[HeirKind.WIFE] = 1 + next(4)
                1 -> heirs[HUSBAND] = 1
            }
            val branches = if (next(2) == 0) emptyList() else DISTANT_BRANCH_CHILDREN.flatMap { (parent, kids) ->
                val sons = kids.first?.let { heirs[it] } ?: 0
                val daughters = heirs[kids.second] ?: 0
                // كل ولد لوحده عند شخص (أكتر توزيع ممكن)، أو كلهم عند شخص واحد
                if (sons + daughters == 0) emptyList()
                else if (next(2) == 0) listOf(DistantBranch(parent, sons, daughters))
                else List(sons) { DistantBranch(parent, 1, 0) } + List(daughters) { DistantBranch(parent, 0, 1) }
            }
            val case = InheritanceCase(
                country, heirs, List(1 + next(3)) { EstateItem("حاجة $it", next(40_000_000).toLong() + 1) },
                debtsMinor = if (next(3) == 0) next(5_000_000).toLong() else 0, distantBranches = branches,
            )
            val r = calculateInheritance(case)
            val key = when (r) {
                is InheritanceResult.Computed -> "Computed"
                is InheritanceResult.NoText -> "NoText/${r.reason}"
                is InheritanceResult.Unsupported -> "Unsupported/${r.reason}"
                is InheritanceResult.Invalid -> "Invalid/${r.reason}"
            }
            outcomes[key] = (outcomes[key] ?: 0) + 1
            if (r is InheritanceResult.Computed) {
                computedCount++
                assertExact(r)
                assertTrue(r.heirs.filter { it.kind.isDistant }.any { it.share.isPositive }, "حد من ذوي الأرحام لازم يورث: $heirs")
            } else {
                assertTrue(r is InheritanceResult.NoText || (r is InheritanceResult.Unsupported && r.reason == UnsupportedReason.ASK_DISTANT_BRANCHES), "$key: $heirs")
                if (r is InheritanceResult.Unsupported) assertTrue(branches.isEmpty())
            }
        }
        assertTrue(computedCount > 600, "اتحسب $computedCount بس: $outcomes")
        assertTrue(outcomes.keys.any { it.startsWith("NoText") } && outcomes.keys.any { it.startsWith("Unsupported") }, "$outcomes")
    }
}
