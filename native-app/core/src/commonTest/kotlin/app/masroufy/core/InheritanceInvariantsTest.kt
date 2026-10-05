package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * آلاف المسائل العشوائية (بمولّد ثابت — نفس الأرقام كل مرة) في البلدين: أي نتيجة محسوبة لازم تحقق الثوابت (`assertExact`):
 * الأنصبة مجموعها 1 · كل حاجة مجموعها قيمتها بالهللة · كل وارث مجموعه على الحاجات = مبلغه · مفيش سالب · كل مبلغ في حدود هللة من الكسر.
 * وأي نتيجة مش محسوبة لازم تكون سبب معروف (مفيش استثناء طاير).
 */
class InheritanceInvariantsTest {
    private var seed = 20261005L

    private fun next(bound: Int): Int {
        seed = (seed * 6364136223846793005L + 1442695040888963407L)
        return ((seed ushr 33) % bound).toInt()
    }

    @Test
    fun randomCasesKeepEveryHalala() {
        var computedCount = 0
        val outcomes = mutableMapOf<String, Int>()
        repeat(3000) { n ->
            val country = if (n % 2 == 0) "SA" else "EG"
            val heirs = mutableMapOf<HeirKind, Int>()
            for (k in HeirKind.entries) {
                if (next(4) != 0) continue
                heirs[k] = when (k) {
                    HeirKind.WIFE -> 1 + next(4)
                    HeirKind.HUSBAND, HeirKind.FATHER, HeirKind.MOTHER, HeirKind.GRANDFATHER,
                    HeirKind.PATERNAL_GRANDMOTHER, HeirKind.MATERNAL_GRANDMOTHER -> 1
                    else -> 1 + next(7)
                }
            }
            if (HeirKind.HUSBAND in heirs && HeirKind.WIFE in heirs) heirs.remove(if (next(2) == 0) HeirKind.HUSBAND else HeirKind.WIFE)
            val items = List(1 + next(4)) { EstateItem("حاجة $it", next(50_000_000).toLong() + next(100)) }
            val case = InheritanceCase(
                countryCode = country,
                heirs = heirs,
                items = items,
                funeralMinor = if (next(3) == 0) next(500_000).toLong() else 0,
                debtsMinor = if (next(3) == 0) next(20_000_000).toLong() else 0,
                bequest = if (next(3) == 0) Bequest(next(30_000_000).toLong(), toHeir = next(4) == 0, heirsConsent = next(3) == 0) else null,
                predeceasedChildren = if (next(5) == 0) listOf(PredeceasedChild(next(2) == 0, next(3), 1 + next(3))) else emptyList(),
                distantRelatives = if (next(2) == 0) false else null,
            )
            val r = calculateInheritance(case)
            outcomes[r::class.simpleName ?: "?"] = (outcomes[r::class.simpleName ?: "?"] ?: 0) + 1
            if (r is InheritanceResult.Computed) {
                computedCount++
                assertExact(r)
                assertEquals(items.sumOf { it.valueMinor }, r.grossMinor)
            }
        }
        assertTrue(computedCount > 2000, "اتحسب $computedCount بس: $outcomes")
    }

    @Test
    fun matrixRoundingKeepsRowsAndColumns() {
        repeat(500) {
            val rows = List(1 + next(5)) { next(1_000_000).toLong() }
            val total = rows.sum()
            val cols = largestRemainder(total, List(1 + next(9)) { Frac.of(1L + next(7), 1) }.let { w -> val s = w.sumFrac(); w.map { it / s } })
            val m = roundMatrix(rows, cols)
            rows.forEachIndexed { k, v -> assertEquals(v, m[k].sum()) }
            cols.forEachIndexed { c, v -> assertEquals(v, m.sumOf { it[c] }) }
            assertTrue(m.all { r -> r.all { it >= 0 } })
        }
    }

    @Test
    fun fractionsStayExactAndCatchOverflow() {
        assertEquals(Frac.of(1, 2), Frac.of(3, 6))
        assertEquals(Frac.of(-1, 2), Frac.of(1, -2))
        assertEquals(Frac.ONE, Frac.of(1, 3) + Frac.of(2, 3))
        assertEquals("13/24", (Frac.ONE - Frac.of(1, 8) - Frac.of(1, 6) - Frac.of(1, 6)).toString())
        assertEquals(2_000_000_000_000_000_000L to 0L, mulDivRem(4_000_000_000_000_000_000L, 1, 2))
        assertEquals(9_000_000_000_000_000_000L to 0L, mulDivRem(9_000_000_000_000_000_000L, 9_000_000_000_000_000_000L, 9_000_000_000_000_000_000L))
        assertEquals(3L to 1L, mulDivRem(10, 1, 3))
        var caught = false
        try {
            mulChecked(Long.MAX_VALUE / 2, 3)
        } catch (_: InheritanceOverflow) {
            caught = true
        }
        assertTrue(caught)
    }
}
