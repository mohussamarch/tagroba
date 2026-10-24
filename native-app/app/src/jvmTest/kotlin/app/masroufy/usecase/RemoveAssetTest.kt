package app.masroufy.usecase

import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetSale
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** r06-delete (§118-1) — مسح الأصل بصفقاته (الشرا والبيع اللي `SellAsset` عمل عمليته). كل الأسامي والمبالغ مخترعة. */
class RemoveAssetTest {
    private val gold = Asset("a-1", "ذهب تجربة", "gold", "جرام", Currency.SAR, archived = false)

    private suspend fun kit(trades: Boolean = true) = RemovalKit().apply {
        assets.save(gold)
        if (trades) {
            txns.saveMany(listOf(op("buy", 1_000_00), op("sell", 600_00, EconomicKind.PERSONAL_SALE, Direction.IN), op("other", 50_00)))
            lots.saveMany(listOf(AssetLot("l-1", "a-1", "2026-02-01", 10L, 1_000_00, 0, transactionId = "buy")))
            sales.saveMany(listOf(AssetSale("s-1", "a-1", "2026-09-01", 4L, 600_00, 0, transactionId = "sell", toWalletId = "w-1", gainMinor = 200_00)))
        }
    }

    @Test
    fun noTrades_deleteThenUndo() = runBlocking {
        val k = kit(trades = false)
        assertEquals(listOf(RemoveChoice.DELETE), k.removals.asset.ask("a-1").choices)
        val done = k.removals.asset.remove("a-1", RemoveChoice.DELETE)
        assertTrue(k.assets.listAll().isEmpty())
        done.undo()
        assertEquals(listOf(gold), k.assets.listAll())
    }

    @Test
    fun archive_thenUndo() = runBlocking {
        val k = kit()
        val ask = k.removals.asset.ask("a-1")
        assertEquals(2, ask.linkedCount)
        assertEquals(listOf(RemoveChoice.ARCHIVE, RemoveChoice.KEEP_OPERATIONS, RemoveChoice.WITH_OPERATIONS), ask.choices)
        val done = k.removals.asset.remove("a-1", RemoveChoice.ARCHIVE)
        assertEquals(true, k.assets.listAll().single().archived)
        done.undo()
        assertEquals(listOf(gold), k.assets.listAll())
    }

    @Test
    fun keep_totalsUnchanged_undoExact() = runBlocking {
        val k = kit()
        val before = k.picture()
        val totals = k.totals()
        val done = k.removals.asset.remove("a-1", RemoveChoice.KEEP_OPERATIONS)
        assertTrue(k.assets.listAll().isEmpty() && k.lots.listAll().isEmpty() && k.sales.listAll().isEmpty())
        assertEquals(totals, k.totals())
        assertEquals(setOf("buy", "sell"), k.all().filter { it.sourceRemoved == "asset:ذهب تجربة" }.map { it.id }.toSet())
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(1, k.lots.listAll().size)
        assertEquals(1, k.sales.listAll().size)
    }

    @Test
    fun withOperations_removesSaleOperation_undoExact() = runBlocking {
        val k = kit()
        val before = k.picture()
        val done = k.removals.asset.remove("a-1", RemoveChoice.WITH_OPERATIONS)
        assertEquals(listOf("other"), k.all().map { it.id }, "عملية البيع والشرا اتمسحوا")
        done.undo()
        assertEquals(before, k.picture())
        assertEquals("sell", k.sales.listAll().single().transactionId)
        assertEquals(listOf(gold), k.assets.listAll())
    }
}
