package app.masroufy.ui.screens.investment

import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetSale
import app.masroufy.core.Currency
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.R06Key
import app.masroufy.core.sentenceNumber
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.ManageAssets
import app.masroufy.usecase.ManageAssetsDeps
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * OVERRIDES §117-2: «المكسب المحقق» للاستثمار كله **من غير** بيعات الأصول اللي تكلفتها مش متسجلة، وتحته سطر «بيعة واحدة مش محسوبة …»
 * أو «N بيعات». من `ManageAssets.listPortfolio` لحد شكل الشاشة. بيانات مخترعة، أعداد صحيحة بالهللة.
 */
class RealizedSkippedTest {
    private val unit = QUANTITY_SCALE

    private suspend fun portfolio(withKnown: Boolean, unknownSales: Int): InvestmentUi {
        val assets = MemoryAssetRepository()
        val lots = MemoryAssetLotRepository()
        val sales = MemoryAssetSaleRepository()
        val manage = ManageAssets(ManageAssetsDeps(assets, lots, sales, MemoryAssetPriceRepository(), SequentialIdGenerator(), FixedClock("2026-10-24T09:00:00.000Z")))
        // أصل من غير تكلفة (هدية): 10 وحدات بصفر، وكل بيعة وحدة بـ1,000 — كانت بتدخل المجموع كأنها مكسب كامل
        assets.save(Asset("a-gift", "QA_Gift", "other", "unit", Currency.SAR, archived = false, costKnown = false))
        lots.saveMany(listOf(AssetLot("l-g", "a-gift", "2026-10-01", 10 * unit, 0, 0)))
        sales.saveMany((1..unknownSales).map { AssetSale("s-g$it", "a-gift", "2026-10-1$it", unit, 100_000, 0) })
        if (withKnown) {
            assets.save(Asset("a-gold", "QA_Gold", "gold", "g", Currency.SAR, archived = false))
            lots.saveMany(listOf(AssetLot("l-1", "a-gold", "2026-10-01", 2 * unit, 200_000, 0)))
            sales.saveMany(listOf(AssetSale("s-1", "a-gold", "2026-10-20", unit, 130_000, 0, gainMinor = 30_000)))
        }
        return investmentUi(manage.listPortfolio("2026-10-24"), emptyMap(), Currency.SAR, "2026-10-24")
    }

    @Test fun unknownCostSaleIsLeftOutOfTheTotalWithAOneLine() = runBlocking<Unit> {
        val ui = portfolio(withKnown = true, unknownSales = 1)
        assertEquals(30_000L, ui.realizedMinor, "مكسب الذهب بس — مش + 1,000 الهدية")
        assertEquals(uiText(R06Key.FX55_GAIN_SKIPPED_ONE, sentenceNumber(1)), ui.realizedSkipped)
    }

    @Test fun severalSkippedSalesUseThePlural() = runBlocking<Unit> {
        val ui = portfolio(withKnown = true, unknownSales = 3)
        assertEquals(30_000L, ui.realizedMinor)
        assertEquals(uiText(R06Key.FX55_GAIN_SKIPPED_FEW, sentenceNumber(3)), ui.realizedSkipped)
    }

    @Test fun onlyUnknownCostSalesMeansNoTotalNotAConfirmedZero() = runBlocking<Unit> {
        val ui = portfolio(withKnown = false, unknownSales = 2)
        assertNull(ui.realizedMinor, "قاعدة 10: مفيش رقم مكان المجهول")
        assertEquals(uiText(R06Key.FX55_GAIN_SKIPPED_TWO, sentenceNumber(2)), ui.realizedSkipped)
    }

    @Test fun allKnownHasNoLine() = runBlocking<Unit> {
        val ui = portfolio(withKnown = true, unknownSales = 0)
        assertEquals(30_000L, ui.realizedMinor)
        assertNull(ui.realizedSkipped)
    }
}
