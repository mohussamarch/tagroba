package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.Merchant
import app.masroufy.core.salaryTxnKey
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** r06-delete (§118-1) — التاجر (العمليات بتفضل بأساميها) ومصدر الدخل. كل الأسامي والمبالغ مخترعة. */
class RemoveMerchantIncomeTest {
    private val shop = Merchant("m-1", "محل تجربة", "محل تجربة")
    private val job = IncomeSource(
        "inc-1", "شركة تجربة", "شركة تجربة", IncomeSourceKind.JOB, Currency.SAR, "2026-01-01",
        payerKeys = listOf(salaryTxnKey("sal-1"), salaryTxnKey("sal-2")),
    )

    private suspend fun kit(ops: Boolean = true) = RemovalKit().apply {
        merchants.saveMany(listOf(shop))
        incomeSources.saveMany(listOf(job))
        if (ops) txns.saveMany(listOf(
            op("buy-1", 20_00, merchantId = "m-1"), op("buy-2", 35_00, merchantId = "m-1", rawMerchantName = "محل تجربة فرع"),
            op("sal-1", 9_000_00, EconomicKind.SALARY, Direction.IN, day = "2026-08-27"), op("sal-2", 9_000_00, EconomicKind.SALARY, Direction.IN),
            op("x", 5_00),
        ))
    }

    @Test
    fun merchant_noOps_deleteThenUndo() = runBlocking {
        val k = kit(ops = false)
        assertEquals(listOf(RemoveChoice.DELETE), k.removals.merchant.ask("m-1").choices)
        val done = k.removals.merchant.remove("m-1", RemoveChoice.DELETE)
        assertTrue(k.merchants.listAll().isEmpty())
        done.undo()
        assertEquals(listOf(shop), k.merchants.listAll())
    }

    @Test
    fun merchant_keepsNames_totalsUnchanged_undoExact() = runBlocking {
        val k = kit()
        val ask = k.removals.merchant.ask("m-1")
        assertEquals(2, ask.linkedCount)
        assertEquals(listOf(RemoveChoice.KEEP_OPERATIONS), ask.choices, "التاجر: العمليات بتفضل دايمًا")
        val before = k.picture()
        val totals = k.totals()
        val done = k.removals.merchant.remove("m-1", RemoveChoice.KEEP_OPERATIONS)
        assertEquals(totals, k.totals())
        val names = k.all().filter { it.id.startsWith("buy") }.associate { it.id to it.rawMerchantName }
        assertEquals(mapOf("buy-1" to "محل تجربة", "buy-2" to "محل تجربة فرع"), names)
        assertTrue(k.all().none { it.merchantId == "m-1" })
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(listOf(shop), k.merchants.listAll())
    }

    @Test
    fun income_noOps_deleteThenUndo() = runBlocking {
        val k = kit(ops = false)
        assertEquals(0, k.removals.incomeSource.ask("inc-1").linkedCount)
        val done = k.removals.incomeSource.remove("inc-1", RemoveChoice.DELETE)
        assertTrue(k.incomeSources.listAll().isEmpty())
        done.undo()
        assertEquals(listOf(job), k.incomeSources.listAll())
    }

    @Test
    fun income_keep_totalsUnchanged_undoExact() = runBlocking {
        val k = kit()
        val ask = k.removals.incomeSource.ask("inc-1")
        assertEquals(2, ask.linkedCount)
        assertEquals(listOf(RemoveChoice.ARCHIVE, RemoveChoice.KEEP_OPERATIONS, RemoveChoice.WITH_OPERATIONS), ask.choices)
        val before = k.picture()
        val totals = k.totals()
        val done = k.removals.incomeSource.remove("inc-1", RemoveChoice.KEEP_OPERATIONS)
        assertEquals(totals, k.totals())
        assertEquals(setOf("sal-1", "sal-2"), k.all().filter { it.sourceRemoved == "income:شركة تجربة" }.map { it.id }.toSet())
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(listOf(job), k.incomeSources.listAll())
    }

    @Test
    fun income_withOperations_undoExact() = runBlocking {
        val k = kit()
        val before = k.picture()
        val done = k.removals.incomeSource.remove("inc-1", RemoveChoice.WITH_OPERATIONS)
        assertEquals(listOf("buy-1", "buy-2", "x"), k.all().map { it.id }.sorted())
        done.undo()
        assertEquals(before, k.picture())
    }
}
