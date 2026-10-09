package app.masroufy.wiring

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Language
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.RealEstateValuation
import app.masroufy.core.RentTerms
import app.masroufy.core.Texts
import app.masroufy.ui.screens.investment.PriceChip
import app.masroufy.ui.screens.investment.RateChip
import app.masroufy.ui.screens.investment.TradeKind
import app.masroufy.ui.screens.investment.defaultRatesFor
import app.masroufy.ui.screens.investment.goalsHint
import app.masroufy.ui.screens.investment.loadAssetDetail
import app.masroufy.ui.screens.investment.loadInvestment
import app.masroufy.ui.screens.investment.loadProjection
import app.masroufy.ui.screens.investment.projectionUi
import app.masroufy.ui.screens.investment.refreshPrices
import app.masroufy.usecase.AssetGrowthInput
import app.masroufy.usecase.NewAsset
import app.masroufy.usecase.PurchaseInput
import app.masroufy.usecase.SaleInput
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * شاشات «الاستثمار» و«تفاصيل الأصل» و«الصورة كاملة»: من حالات الاستخدام (على التجميع الحقيقي بمستودعات الذاكرة) لشكل الشاشة.
 * بنتأكد إن كل مبلغ معروض **هو نفسه** اللي حالة الاستخدام رجّعته (مفيش حساب في الشاشة)، و`null` بيفضل «غير متاح»، والكلام فصحى في السعودية
 * ومصري في مصر. الأسماء والأرقام مخترعة.
 */
class InvestmentScreensTest {
    private val g = QUANTITY_SCALE

    @AfterTest fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private suspend fun InvestmentWorld.gold(name: String = "ذهب وهمي", symbol: String? = null): String {
        val a = deps.assets.addAsset(NewAsset(name, "gold", "جرام", feedSymbol = symbol, currency = space.currency))
        deps.assets.recordPurchase(PurchaseInput(a.id, "2024-03-12", 120 * g, 4_800_000, 32_000))
        return a.id
    }

    @Test fun emptyPortfolioShowsTheEmptyStateWithZakatAndPlans() = runBlocking<Unit> {
        val w = InvestmentWorld(InvestmentWorld.SAUDI)
        val load = loadInvestment(w.deps, w.space)
        val ui = assertNotNull(load.ui)
        assertTrue(ui.empty)
        assertNull(load.feedProblem, "الملف نزل")
        assertEquals("إرشادية حسب هيئة الزكاة والضريبة والجمارك", load.zakatHint)
        assertEquals(0, load.goals)
        assertEquals("لا خطط بعد", goalsHint(0))
    }

    @Test fun aGoldBarWithoutPriceKeepsTheTotalUnavailableNotZero() = runBlocking<Unit> {
        val w = InvestmentWorld(InvestmentWorld.SAUDI)
        val id = w.gold()
        val ui = assertNotNull(loadInvestment(w.deps, w.space).ui)
        assertNull(ui.totalMinor, "أصل من غير سعر ⇒ الإجمالي «غير متاح» (القاعدة 10)")
        assertEquals("سعر «ذهب وهمي» اليوم غير متاح، فلا يكتمل الإجمالي.", ui.totalNa)
        assertEquals(4_832_000L, ui.costMinor, "التكلفة بالرسوم من حالة الاستخدام")
        assertNull(ui.unrealizedMinor)
        val line = ui.others.single()
        assertEquals(id, line.assetId)
        assertNull(line.valueMinor)
        assertEquals("١٢٠ جرام، سعر اليوم غير متاح", line.subtitle)

        // سعر يدوي ⇒ كل المبالغ من المركز نفسه
        w.deps.assets.setPrice(id, 43_800, "2026-10-09")
        val priced = assertNotNull(loadInvestment(w.deps, w.space).ui)
        val view = w.deps.assets.listPortfolio("2026-10-09")
        assertEquals(view.totals.marketValueMinor, priced.totalMinor)
        assertEquals(5_256_000L, priced.totalMinor)
        assertEquals(view.totals.unrealizedGainMinor, priced.unrealizedMinor)
        assertNull(priced.totalNa)
        assertNull(priced.realizedMinor, "مفيش بيع ⇒ مفيش سطر «المكسب المحقق»")
        assertEquals("١٢٠ جرام، السعر اليوم", priced.others.single().subtitle)

        w.deps.assets.recordSale(SaleInput(id, "2026-10-01", 20 * g, 876_000))
        val sold = assertNotNull(loadInvestment(w.deps, w.space).ui)
        assertEquals(w.deps.assets.listPortfolio("2026-10-09").totals.realizedGainMinor, sold.realizedMinor)
    }

    @Test fun egyptSpeaksEgyptian() = runBlocking<Unit> {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        val w = InvestmentWorld(InvestmentWorld.EGYPT)
        w.gold("دهب وهمي")
        val load = loadInvestment(w.deps, w.space)
        assertEquals("إرشادية حسب دار الإفتاء المصرية", load.zakatHint)
        val ui = assertNotNull(load.ui)
        assertEquals("سعر «دهب وهمي» النهارده غير متاح، فالإجمالي مش كامل.", ui.totalNa)
        assertEquals("١٢٠ جرام، سعر النهارده غير متاح", ui.others.single().subtitle)
        assertEquals("مفيش خطط لسه", goalsHint(0))
    }

    @Test fun aLinkedAssetTakesTheFeedPriceAndAnOldFeedShowsStale() = runBlocking<Unit> {
        val fresh = InvestmentWorld(InvestmentWorld.SAUDI)
        val id = fresh.gold(symbol = "GOLD_21K_GRAM")
        val load = loadInvestment(fresh.deps, fresh.space)
        assertEquals(0, load.skipped)
        assertEquals(3_150_000L, load.ui?.totalMinor, "١٢٠ جرام × ٢٦٢٫٥٠ من ملف الأسعار (SyncAssetPrices)")
        val detail = assertNotNull(loadAssetDetail(fresh.deps, fresh.space, id))
        assertEquals(PriceChip.FRESH, detail.chip)
        assertFalse(detail.staleLinked)
        assertTrue(detail.linked)

        val old = InvestmentWorld(InvestmentWorld.SAUDI, pricesAsOf = "2026-09-20")
        val oldId = old.gold(symbol = "GOLD_21K_GRAM")
        loadInvestment(old.deps, old.space)
        val stale = assertNotNull(loadAssetDetail(old.deps, old.space, oldId))
        assertEquals(PriceChip.STALE, stale.chip)
        assertTrue(stale.staleLinked, "مربوط وسعره أقدم من ٧ أيام ⇒ شريط «تعذّر تحديث الأسعار»")
        assertEquals("تحدّثت الأسعار", refreshPrices(old.deps))
    }

    @Test fun assetDetailMapsTheLogAndTheManualPrice() = runBlocking<Unit> {
        val w = InvestmentWorld(InvestmentWorld.SAUDI)
        val id = w.gold()
        val missing = assertNotNull(loadAssetDetail(w.deps, w.space, id))
        assertEquals(PriceChip.MISSING, missing.chip)
        assertNull(missing.valueMinor)
        assertEquals("لا سعر مسجّل. القيمة الحالية غير متاحة", missing.naReason)
        assertNull(missing.realizedMinor, "مفيش بيع ⇒ «لا مبيعات» مش صفر")

        w.deps.assets.setPrice(id, 43_800, "2026-10-09")
        w.deps.assets.recordSale(SaleInput(id, "2026-10-01", 20 * g, 876_000, 0))
        val ui = assertNotNull(loadAssetDetail(w.deps, w.space, id))
        val position = w.deps.assets.listPortfolio("2026-10-09").rows.single().position
        assertEquals(PriceChip.MANUAL, ui.chip)
        assertEquals(position.marketValueMinor, ui.valueMinor)
        assertEquals(position.costBasisMinor, ui.costMinor)
        assertEquals(position.realizedGainMinor, ui.realizedMinor)
        assertEquals(listOf(TradeKind.SELL, TradeKind.BUY), ui.log.map { it.kind }, "الأحدث فوق")
        assertEquals(876_000L, ui.log.first().amountMinor)
        assertEquals("١٠٠ جرام", ui.qtyText)
        assertNull(loadAssetDetail(w.deps, w.space, "asset-not-there"))
    }

    @Test fun realEstateProjectionPassesTheUseCaseNumbersThrough() = runBlocking<Unit> {
        val w = InvestmentWorld(InvestmentWorld.SAUDI)
        val flat = w.deps.assets.addAsset(NewAsset("شقة وهمية", "realEstate", currency = w.space.currency))
        w.deps.assets.recordPurchase(PurchaseInput(flat.id, "2019-05-10", g, 48_000_000, 1_200_000))
        w.deps.growth.setProfile(
            flat.id,
            AssetGrowthInput(RealEstateValuation.AREA, 120 * g, 480_000, rent = RentTerms(400_000, 150, 0)),
        )
        val estate = assertNotNull(loadInvestment(w.deps, w.space).ui).estates.single()
        assertEquals(57_600_000L, estate.valueMinor, "١٢٠ م² × ٤٬٨٠٠")
        assertEquals("عقار، ١٢٠ م² × 4,800.00 ر.س للمتر", estate.subtitle)
        assertEquals("كم ستساوي لو بعتها في ٢٠٣٠؟", estate.ask)
        assertNull(estate.saleMinor, "ملف المتوسطات مش موجود ⇒ مفيش معدل ⇒ سعر البيع «غير متاح»")

        val defaults = defaultRatesFor(w.deps, w.space)
        val none = projectionUi(loadProjection(w.deps, w.space, flat.id, 2030, null, defaults), w.space.currency)
        assertEquals(RateChip.MISSING, none.chip)
        assertFalse(none.rateKnown)
        assertNull(none.saleMinor)

        val typedView = loadProjection(w.deps, w.space, flat.id, 2030, 174, defaults)
        val typed = projectionUi(typedView, w.space.currency)
        assertEquals(RateChip.TYPED, typed.chip)
        assertEquals("١٫٧٤٪ سنويًا", typed.rateValue)
        assertEquals(typedView.projection.valueAtSaleMinor, typed.saleMinor)
        assertEquals(typedView.projection.rentTotalMinor, typed.rentMinor)
        assertEquals(typedView.projection.totalGainMinor, typed.gainMinor)
        assertEquals("بعد ٤ سنوات", typed.yearsLabel)
        assertEquals("لو بعتها في ٢٠٣٠", typed.resultTitle)
    }
}
