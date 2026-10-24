package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Settlement
import app.masroufy.core.SettlementFx
import app.masroufy.core.remainingOfObligation
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * fix055c — «تراجع» بعد مسح سداد من صفحة الدين بيرجّعه **بالظبط**: نفس المعرّف ونفس الدين ونفس العملية ونفس المبلغ (وسعر العملة لو فيه)،
 * والمتبقي على الدين يرجع زي ما كان. بيانات مخترعة · المبالغ بالوحدة الصغرى.
 */
class SettlementUndoTest {
    private suspend fun kit(vararg s: Settlement): RemovalKit {
        val k = RemovalKit()
        k.txns.saveMany(listOf(op("t-lend", 35_851), op("t-back", 7_500, dir = Direction.IN).copy(sourceOrder = MANUAL_SOURCE_ORDER)))
        k.obligations.saveMany(listOf(Obligation("obl-1", "p-1", "t-lend", ObligationKind.RECEIVABLE, 35_851, Currency.SAR)))
        k.settlements.saveMany(s.toList())
        return k
    }

    private suspend fun RemovalKit.state(): Pair<List<Settlement>, Long> {
        val list = settlements.listByObligations(listOf("obl-1")).sortedBy { it.id }
        val obligation = obligations.listByPerson("p-1").single()
        return list to remainingOfObligation(obligation, list)
    }

    @Test fun deleteThenUndoRestoresTheSameSettlementAndTheSameRemaining() = runBlocking<Unit> {
        val mine = Settlement("stl-1", "t-back", "obl-1", 7_500)
        val other = Settlement("stl-2", "", "obl-1", 1_000)
        val k = kit(mine, other)
        val before = k.state()
        assertEquals(35_851L - 8_500L, before.second)
        val done = k.removals.settlement.remove(settlementKey("obl-1", "stl-1"), RemoveChoice.DELETE)
        val gone = k.state()
        assertEquals(listOf("stl-2"), gone.first.map { it.id }, "السداد اتشال بس — التاني زي ما هو")
        assertEquals(35_851L - 1_000L, gone.second)
        assertEquals(1, k.txns.findByIds(listOf("t-back")).size, "العملية نفسها بتفضل")
        done.undo()
        val after = k.state()
        assertEquals(before, after, "نفس السدادات بكل حقولها (الدين · العملية · المبلغ) ونفس المتبقي")
        assertEquals(mine, after.first.first { it.id == "stl-1" })
    }

    @Test fun undoKeepsTheFxRateOfAForeignSettlement() = runBlocking<Unit> {
        val fx = Settlement("stl-fx", "t-back", "obl-1", 5_000, SettlementFx(paidMinor = 66_250, paidCurrency = Currency.EGP, rate4 = 132_500))
        val k = kit(fx)
        val before = k.state()
        val done = k.removals.settlement.remove(settlementKey("obl-1", "stl-fx"), RemoveChoice.DELETE)
        assertTrue(k.state().first.isEmpty())
        done.undo()
        assertEquals(before, k.state())
    }
}
