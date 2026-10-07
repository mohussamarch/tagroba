package app.masroufy.usecase

import app.masroufy.core.ASSET_KIND_REAL_ESTATE
import app.masroufy.core.Currency
import app.masroufy.core.GrowthClass
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.RealEstateValuation
import app.masroufy.core.displayKind
import app.masroufy.core.growthClassOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * العقار نوع لوحده من الإضافة للورث (OVERRIDES §69.7): بيتضاف بنوع «عقار» ⇒ بيتخزن "other" + العلامة (التطبيق الحالي) ⇒
 * المحفظة والتوقّع والورث بيشوفوه «عقار». كل الأسامي والأرقام مخترعة.
 */
class RealEstateKindFlowTest {
    private class Repos {
        val assets = MemoryAssetRepository()
        val lots = MemoryAssetLotRepository()
        val sales = MemoryAssetSaleRepository()
        val prices = MemoryAssetPriceRepository()
        val clock = FixedClock("2026-10-05T09:00:00.000Z")
        val manage = ManageAssets(ManageAssetsDeps(assets, lots, sales, prices, SequentialIdGenerator(), clock))
        val growth = ManageAssetGrowth(ManageAssetGrowthDeps(assets, lots, sales, prices, clock))
        val inheritance = CalculateInheritance(
            CalculateInheritanceDeps(
                "SA", Currency.SAR, clock,
                EstateHoldingsDeps(MemoryWalletRepository(), MemoryTransactionRepository(), assets, lots, sales, prices),
            ),
        )
    }

    @Test
    fun addedAsRealEstateStoredAsOtherWithTheMarker() = runBlocking<Unit> {
        val r = Repos()
        val flat = r.manage.addAsset(NewAsset("شقة وهمية", ASSET_KIND_REAL_ESTATE))
        val stored = r.assets.listAll().single()
        assertEquals(Triple("other", true, "وحدة"), Triple(stored.kind, stored.realEstate, stored.unitLabel))
        assertEquals(ASSET_KIND_REAL_ESTATE, r.manage.listPortfolio().rows.single().asset.displayKind)
        assertEquals(GrowthClass.REAL_ESTATE, growthClassOf(stored, Currency.SAR))
        // «أخرى» عادية بتفضل «أخرى» من غير علامة
        val other = r.manage.addAsset(NewAsset("أصل وهمي", "other"))
        assertEquals(false to "other", other.realEstate to other.displayKind)
        // تعديل حقول التوقّع ما بيشيلش العلامة
        r.growth.setProfile(flat.id, AssetGrowthInput(expectedRateBp = 300))
        assertTrue(r.assets.listAll().first { it.id == flat.id }.realEstate)
    }

    // الورث: العقار بيتجاب بقيمته بطريقته حتى من غير شراء متسجل (كان بيتساب) · المتباع كله ما بيتجابش · نوعه «عقار»
    @Test
    fun inheritancePrefillTreatsRealEstateAsItsOwnKind() = runBlocking<Unit> {
        val r = Repos()
        // (1) بالعلامة وبسعره كله من غير شراء ⇒ 900,000.00
        val flat = r.manage.addAsset(NewAsset("شقة وهمية", ASSET_KIND_REAL_ESTATE))
        r.manage.setPrice(flat.id, 90_000_000, "2026-10-01")
        // (2) بسعر المتر من غير شراء: 500 متر × 1,000.00 = 500,000.00
        val land = r.manage.addAsset(NewAsset("أرض وهمية", "other"))
        r.growth.setProfile(land.id, AssetGrowthInput(RealEstateValuation.AREA, 500 * QUANTITY_SCALE, 100_000))
        // (3) بالعلامة ومن غير سعر ⇒ «غير متاح» (null — مش صفر) والمستخدم يكتبه
        val shop = r.manage.addAsset(NewAsset("محل وهمي", ASSET_KIND_REAL_ESTATE))
        // (4) اتشرى واتباع كله ⇒ مش في التركة
        val sold = r.manage.addAsset(NewAsset("شاليه وهمي", ASSET_KIND_REAL_ESTATE))
        r.manage.recordPurchase(PurchaseInput(sold.id, "2020-01-01", QUANTITY_SCALE, 40_000_000))
        r.manage.recordSale(SaleInput(sold.id, "2025-01-01", QUANTITY_SCALE, 45_000_000))
        // (5) بسعر المتر ومعاه شراء: القيمة = المساحة × المتر (240 × 40,000.00 = 9,600,000.00)
        val home = r.manage.addAsset(NewAsset("بيت وهمي", "other"))
        r.manage.recordPurchase(PurchaseInput(home.id, "2018-03-01", QUANTITY_SCALE, 600_000_000))
        r.growth.setProfile(home.id, AssetGrowthInput(RealEstateValuation.AREA, 240 * QUANTITY_SCALE, 4_000_000, "2026-10-01"))

        val drafts = r.inheritance.prefillMyEstate()
        assertEquals(
            listOf(
                EstateItemDraft("شقة وهمية", 90_000_000, EstateItemSource.ASSET, flat.id, ASSET_KIND_REAL_ESTATE),
                EstateItemDraft("أرض وهمية", 50_000_000, EstateItemSource.ASSET, land.id, ASSET_KIND_REAL_ESTATE),
                EstateItemDraft("محل وهمي", null, EstateItemSource.ASSET, shop.id, ASSET_KIND_REAL_ESTATE),
                EstateItemDraft("بيت وهمي", 960_000_000, EstateItemSource.ASSET, home.id, ASSET_KIND_REAL_ESTATE),
            ),
            drafts,
        )
    }
}
