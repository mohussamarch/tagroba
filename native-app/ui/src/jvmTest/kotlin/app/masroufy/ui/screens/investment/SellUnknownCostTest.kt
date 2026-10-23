package app.masroufy.ui.screens.investment

import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetSale
import app.masroufy.core.Currency
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.Wallet
import app.masroufy.core.computePosition
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.AddTransaction
import app.masroufy.usecase.AddTransactionDeps
import app.masroufy.usecase.AssetRow
import app.masroufy.usecase.ManageAssets
import app.masroufy.usecase.ManageAssetsDeps
import app.masroufy.usecase.SaleInput
import app.masroufy.usecase.SellAsset
import app.masroufy.usecase.SellAssetDeps
import app.masroufy.usecase.SellAssetInput
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** smoke 0.5.4 ملاحظة 2 — أصل من غير تكلفة: المكسب «غير متاح» (قاعدة 10)، البيعة بتتعد، ومفيش سطر «شراء −0.00». بيانات مخترعة. */
class SellUnknownCostTest {
    private val unit = QUANTITY_SCALE

    @Test fun unknownCostShowsNoGainNumberAndTheSaleStillCounts() = runBlocking {
        val assets = MemoryAssetRepository()
        val lots = MemoryAssetLotRepository()
        val sales = MemoryAssetSaleRepository()
        val wallets = MemoryWalletRepository(listOf(Wallet("w-cash", "كاش تجربة", Currency.SAR, "cash", 0, "2026-01-01")))
        val txns = MemoryTransactionRepository(emptyList())
        val ids = SequentialIdGenerator()
        val clock = FixedClock("2026-10-23T09:00:00.000Z")
        val manage = ManageAssets(ManageAssetsDeps(assets, lots, sales, MemoryAssetPriceRepository(), ids, clock))
        val sell = SellAsset(SellAssetDeps(manage, assets, lots, sales, wallets, txns, AddTransaction(AddTransactionDeps(txns, wallets, ids, clock)), clock))
        // زي «ضيف حاجة» من غير سعر: الأصل costKnown=false ودفعة بصفر
        val asset = Asset("a-car", "QA_Car", "other", "unit", Currency.SAR, archived = false, costKnown = false)
        assets.save(asset)
        lots.saveMany(listOf(AssetLot("l-1", asset.id, "2026-10-01", unit, 0, 0)))

        val sale = SaleInput(asset.id, "2026-10-23", unit, 1_800_000)
        val preview = sell.preview(sale)
        assertFalse(preview.costKnown, "المعاينة لازم تقول إن التكلفة مش متسجلة")
        sell.sell(SellAssetInput(sale, toWalletId = "w-cash"))
        assertNull(sales.listAll().single().gainMinor, "ما يتحفظش مكسب من غير مصدر")

        val row = AssetRow(asset, computePosition(asset.id, lots.listAll(), sales.listAll()), lots.listAll(), sales.listAll())
        val ui = assetDetailUi(row, null, Currency.SAR, "2026-10-23")
        assertTrue(ui.realizedUnknown, "فيه بيعة ⇒ «غير متاح» مش «لا مبيعات»")
        assertNull(ui.realizedMinor)
        assertNull(ui.costMinor)
        assertEquals(listOf(TradeKind.SELL), ui.log.map { it.kind }, "مفيش سطر «شراء −0.00»")
    }

    @Test fun knownCostKeepsTheGainAndThePurchaseRow() = runBlocking {
        val asset = Asset("a-g", "QA_Gold", "gold", "g", Currency.SAR, archived = false)
        val lot = AssetLot("l-1", asset.id, "2026-10-01", unit, 100_000, 0)
        val sale = AssetSale("s-1", asset.id, "2026-10-20", unit, 120_000, 0, gainMinor = 20_000)
        val ui = assetDetailUi(AssetRow(asset, computePosition(asset.id, listOf(lot), listOf(sale)), listOf(lot), listOf(sale)), null, Currency.SAR, "2026-10-23")
        assertFalse(ui.realizedUnknown)
        assertEquals(20_000L, ui.realizedMinor)
        assertEquals(listOf(TradeKind.SELL, TradeKind.BUY), ui.log.map { it.kind })
    }
}
