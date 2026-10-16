package app.masroufy.usecase

import app.masroufy.core.AverageEntry
import app.masroufy.core.Currency
import app.masroufy.core.FeedPrice
import app.masroufy.core.HoldingGroup
import app.masroufy.core.HoldingOrigin
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.HoldingType
import app.masroufy.core.Id
import app.masroufy.core.InvestRates
import app.masroufy.core.InvestRatesState
import app.masroufy.core.NewHolding
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.RealEstateValuation
import app.masroufy.core.ZakatFact
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.ZakatSubject
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryZakatFactRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «ضيف حاجة» ⇒ `AddHolding` ⇒ «حاجاتي» (`LoadHoldings`) على مستودعات الذاكرة — بيانات مخترعة بالكامل. */
class AddHoldingTest {
    private val today = "2026-03-10"

    private inner class World(currency: Currency = Currency.SAR, feed: Map<String, FeedPrice> = emptyMap()) {
        val assetRepo = MemoryAssetRepository()
        val lots = MemoryAssetLotRepository()
        val prices = MemoryAssetPriceRepository()
        val facts = MemoryZakatFactRepository()
        val written = mutableListOf<Triple<Id, ZakatPurpose, Int?>>()
        val clock = FixedClock("${today}T09:00:00.000Z")
        val assets = ManageAssets(ManageAssetsDeps(assetRepo, lots, MemoryAssetSaleRepository(), prices, SequentialIdGenerator(), clock))
        val growth = ManageAssetGrowth(ManageAssetGrowthDeps(assetRepo, lots, MemoryAssetSaleRepository(), prices, clock))
        val add = AddHolding(
            AddHoldingDeps(
                assets, growth, assetRepo, lots, prices,
                feedPrice = FeedPriceLookup { symbol, cur -> feed[symbol]?.takeIf { it.currency == cur.name } },
                metalFacts = MetalFactsWriter { id, purpose, karat, fineness ->
                    written += Triple(id, purpose, karat ?: fineness)
                    facts.save(ZakatFact(id, ZakatSubject.ASSET, purpose = purpose, karat = karat, fineness = fineness, updatedAt = today))
                },
                currency = currency, uow = MemoryUnitOfWork(emptyList()), ids = SequentialIdGenerator(),
            ),
        )
        val load = LoadHoldings(LoadHoldingsDeps(assets, facts, currency))
    }

    private fun gram(symbol: String, minor: Long, cur: String = "SAR") = FeedPrice(symbol, symbol, "gram", minor, today, "مصدر مخترع", cur)

    @Test fun storedKindIsOldAppSafeForEveryType() = runBlocking {
        val w = World()
        for (type in HoldingType.entries) {
            val qty = if (type.metal) 10 * QUANTITY_SCALE else QUANTITY_SCALE
            w.add.add(NewHolding(type, type.wire, type.defaultPurpose, HoldingOrigin.BUY, qty, valueMinor = 100_000), today)
        }
        val stored = w.assetRepo.listAll().associate { it.holdingType to it }
        // الأنواع اللي التطبيق القديم يعرفها بس (الفضة والعقار بعلامة — المحوّل بيكتبهم "other")
        for (a in stored.values) assertTrue(a.kind in listOf("gold", "silver", "stock", "other"), a.kind)
        assertEquals("gold", stored.getValue("gold").kind)
        assertEquals("silver", stored.getValue("silver").kind)
        assertEquals("stock", stored.getValue("stk").kind)
        assertEquals("other", stored.getValue("co").kind)
        for (re in listOf("flat", "land", "shop")) assertTrue(stored.getValue(re).realEstate && stored.getValue(re).kind == "other", re)
        val snap = w.load.load(today)
        assertEquals(HoldingType.entries.toSet(), snap.items.map { it.type }.toSet())
    }

    @Test fun goldIsLinkedToItsKaratRowValuedTodayAndZakatFactsWritten() = runBlocking {
        val w = World(feed = mapOf("GOLD_21K_GRAM" to gram("GOLD_21K_GRAM", 40_000)))
        val id = w.add.add(
            NewHolding(HoldingType.GOLD, "ذهب", HoldingPurpose.RESERVE, HoldingOrigin.BUY, 120 * QUANTITY_SCALE, priceMinor = 4_500_000, makingMinor = 300_000, karat = 21),
            today,
        )
        val asset = w.assetRepo.listAll().single()
        assertEquals("GOLD_21K_GRAM", asset.feedSymbol)
        assertNull(asset.costKnown)
        val lot = w.lots.listAll().single()
        assertEquals(4_200_000L, lot.principalMinor) // المصنعية جوه السعر ⇒ مش فوقه
        assertEquals(300_000L, lot.feeMinor)
        assertEquals(listOf(Triple<Id, ZakatPurpose, Int?>(id, ZakatPurpose.SAVING, 21)), w.written)
        val item = w.load.load(today).items.single()
        assertEquals(4_800_000L, item.valueMinor) // 120 جرام × 400 ر.س
        assertEquals(HoldingPurpose.RESERVE, item.purpose)
        assertEquals(21, item.karat)
    }

    @Test fun egyptGoldUsesTheEgpRowAndMissingPriceIsUnknownNotZero() = runBlocking {
        val w = World(Currency.EGP)
        w.add.add(NewHolding(HoldingType.GOLD, "دهب", HoldingPurpose.INVEST, HoldingOrigin.GIFT, 5 * QUANTITY_SCALE, karat = 18, jewellery = true), today)
        val asset = w.assetRepo.listAll().single()
        assertEquals("GOLD_18K_GRAM_EGP", asset.feedSymbol)
        assertEquals(false, asset.costKnown) // هدية من غير قيمة ⇒ التكلفة مش معروفة
        assertEquals("gift", asset.origin)
        assertEquals(ZakatPurpose.WEAR, w.written.single().second)
        val snap = w.load.load(today)
        assertNull(snap.items.single().valueMinor)
        assertTrue(snap.goldPriceMissing)
    }

    @Test fun inheritedWithoutValueHasUnknownCostAndZeroPrincipal() = runBlocking {
        val w = World()
        w.add.add(NewHolding(HoldingType.STK, "سهم مخترع", HoldingPurpose.INVEST, HoldingOrigin.INHERIT, 30 * QUANTITY_SCALE), today)
        val asset = w.assetRepo.listAll().single()
        assertEquals(false, asset.costKnown)
        assertEquals("inherit", asset.origin)
        assertEquals(0L, w.lots.listAll().single().principalMinor)
        assertNull(w.load.load(today).items.single().valueMinor) // مفيش سعر ⇒ «غير متاح»
    }

    @Test fun propertyIsWholeValueWithAreaRentAndPlace() = runBlocking {
        val w = World()
        w.add.add(
            NewHolding(
                HoldingType.SHOP, "محل", HoldingPurpose.TRADE, HoldingOrigin.BUY, QUANTITY_SCALE, valueMinor = 90_000_000,
                areaSqm = 45 * QUANTITY_SCALE, place = "شارع مخترع", monthlyRentMinor = 500_000,
            ),
            today,
        )
        val asset = w.assetRepo.listAll().single()
        assertEquals(RealEstateValuation.WHOLE, asset.valuation)
        assertEquals(45 * QUANTITY_SCALE, asset.areaSqm)
        assertEquals(500_000L, asset.monthlyRentMinor)
        assertEquals("شارع مخترع", asset.place)
        assertEquals(false, asset.costKnown)
        val item = w.load.load(today).items.single()
        assertEquals(90_000_000L, item.valueMinor)
        assertEquals(HoldingGroup.PROPERTY, item.group)
    }

    @Test fun duplicateNamesGetANumberAndArchivedOrOtherCurrencyAreHandled() = runBlocking {
        val w = World()
        repeat(2) { w.add.add(NewHolding(HoldingType.CAR, "سيارة", HoldingPurpose.PERSONAL, HoldingOrigin.BUY, QUANTITY_SCALE, valueMinor = 1_000_000), today) }
        assertEquals(listOf("سيارة", "سيارة 2"), w.assetRepo.listAll().map { it.name })
        w.assets.archiveAsset(w.assetRepo.listAll().first().id, true)
        w.assetRepo.save(w.assetRepo.listAll().last().copy(id = "usd", name = "دولاري", currency = Currency.USD))
        val snap = w.load.load(today)
        assertEquals(listOf("سيارة 2", "دولاري"), snap.items.map { it.asset.name })
        assertNull(snap.items.last().valueMinor) // عملة تانية ⇒ «غير متاح»
    }

    @Test fun setPurposeIsReadBack() = runBlocking {
        val w = World()
        val id = w.add.add(NewHolding(HoldingType.WATCH, "ساعة", HoldingPurpose.PERSONAL, HoldingOrigin.BUY, QUANTITY_SCALE, valueMinor = 700_000), today)
        SetHoldingPurpose(w.assetRepo).set(id, HoldingPurpose.INVEST)
        assertEquals(HoldingPurpose.INVEST, w.load.load(today).items.single().purpose)
    }

    @Test fun personalChoiceIsSavedPerUserAndDefaultsToOff() = runBlocking {
        val w = World()
        val store = app.masroufy.memory.MemoryUserSettingsStore()
        val load = LoadHoldings(LoadHoldingsDeps(w.assets, w.facts, Currency.SAR, store))
        val prefs = ManageHoldingsPrefs(store, w.clock)
        assertEquals(false, load.load(today).includePersonal)
        assertEquals(false, prefs.includePersonal())
        prefs.setIncludePersonal(true)
        assertEquals(true, load.load(today).includePersonal)
        prefs.setIncludePersonal(false)
        assertEquals(false, prefs.includePersonal())
        assertEquals(1, store.listAll().size) // مستند واحد على الحساب
    }

    @Test fun previewUsesTheKaratRowAndIsNullWithoutIt() = runBlocking {
        val preview = PreviewHoldingValue { symbol, _ -> if (symbol == "GOLD_24K_GRAM") gram(symbol, 50_000) else null }
        assertEquals(500_000L, preview.metalToday(HoldingType.GOLD, 24, 10 * QUANTITY_SCALE, Currency.SAR))
        assertNull(preview.metalToday(HoldingType.GOLD, 21, 10 * QUANTITY_SCALE, Currency.SAR))
        assertNull(preview.metalToday(HoldingType.GOLD, 14, 10 * QUANTITY_SCALE, Currency.SAR)) // عيار مش في الملف
    }

    @Test fun groupProjection() {
        val e = AverageEntry("K", 0, "2015", "2024", true, false, "مصدر رسمي مخترع", null, "cagr")
        val ready = InvestRatesState.Ready(InvestRates(0, 0, 0, e, e, e))
        val p = ProjectHoldingGroup()
        val g = p.project(1_000_000, 10_000, HoldingGroup.METAL, ready, 5)!!
        assertEquals(6, g.leave.size)
        assertEquals(1_000_000L, g.leave.last())
        assertEquals(1_600_000L, g.plan.last()) // 60 شهر × 100 ر.س
        assertNull(p.project(1_000_000, 0, HoldingGroup.COLLECTIBLES, ready, 5)) // مفيش متوسط رسمي
        assertNull(p.project(1_000_000, 0, HoldingGroup.METAL, InvestRatesState.Unavailable(listOf("gold")), 5))
        assertNull(p.project(0, 0, HoldingGroup.SHARES, ready, 5)) // القيمة صفر/مجهولة ⇒ مفيش تشارت
        val slider = planSliderFor(Currency.EGP)
        assertEquals(80, slider.steps)
        assertEquals(200_000L, slider.atStep(slider.stepOf(slider.defaultMinor)))
        assertEquals(100, planSliderFor(Currency.SAR).steps)
    }
}
