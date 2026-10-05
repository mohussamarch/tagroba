package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.AssetError
import app.masroufy.core.AverageKeys
import app.masroufy.core.AveragesFeed
import app.masroufy.core.Currency
import app.masroufy.core.EgyptStocksMode
import app.masroufy.core.GrowthClass
import app.masroufy.core.GrowthRatesConfig
import app.masroufy.core.Language
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.RealEstateValuation
import app.masroufy.core.RentTerms
import app.masroufy.core.Texts
import app.masroufy.core.parseAveragesFeed
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.longOrNull
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «هتوصل لكام؟» من البيانات للنتيجة (OVERRIDES §69.6): ملف المتوسطات **اللي السكربت نفسه طلّعه** (`averages-sample.json` — من
 * `scripts/fetch-averages.mjs` على بيانات البنك الدولي العامة، 2026-10-01) ⇒ المعدلات ⇒ المقارنة وتوقّع الأصل. الأصول مخترعة.
 */
class GrowthFlowTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun plain(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonArray -> e.map(::plain)
        is JsonObject -> LinkedHashMap(e.mapValues { (_, v) -> plain(v) })
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.booleanOrNull
            e.longOrNull != null -> e.longOrNull
            else -> e.double
        }
    }

    private val feed: AveragesFeed = parseAveragesFeed(plain(Json.parseToJsonElement(GrowthFlowTest::class.java.getResource("/averages-sample.json")!!.readText(Charsets.UTF_8))))

    @Test
    fun theScriptsFileGivesTheResearchFigures() {
        assertEquals(12, feed.entries.size)
        assertTrue(feed.rejected.isEmpty() && feed.failures.isEmpty())
        val values = feed.entries.mapValues { it.value.valueBp }
        assertEquals(1488, values[AverageKeys.GOLD_USD])
        assertEquals(3422, values[AverageKeys.GOLD_EGP])
        assertEquals(174, values[AverageKeys.CPI_SA])
        assertEquals(1633, values[AverageKeys.CPI_EG])
        assertEquals(2040, values[AverageKeys.EGP_PER_USD])
        assertEquals(795, values[AverageKeys.STOCKS_SA])
        val sa = LoadDefaultRates().load(feed, "SA", "2026-10-05")
        assertEquals(listOf(1488, 174, null, 795, 0), sa.rates.map { it.rateBp })
        assertEquals(174, sa.inflation?.valueBp)
        val eg = LoadDefaultRates(GrowthRatesConfig(egyptStocks = EgyptStocksMode.DEFAULT_WITH_NOTE)).load(feed, "EG", "2026-10-05")
        assertEquals(listOf(3422, 1633, null, 1942, 0), eg.rates.map { it.rateBp })
    }

    @Test
    fun savingsComparisonWithTypedRatesAndTodaysMoney() {
        val out = CompareSavingsGrowth().compare(100_000, 120, 0, "2026-10-05", feed, "SA", userRates = mapOf(GrowthClass.DEPOSIT to 450), todayMoney = true)
        assertEquals(listOf(25_832_688L, 13_087_780L, 15_119_806L, 17_965_843L, 12_000_000L), out.comparison.lines.map { it.reachedMinor })
        val deposit = out.choices.first { it.line.growthClass == GrowthClass.DEPOSIT }
        assertTrue(deposit.typedByUser)
        assertEquals("النسبة التي كتبتها أنت", deposit.sourceText)
        assertEquals(400, deposit.default.info?.valueBp, "فايدة البنك المركزي معلومة جنبها")
        assertEquals(10_098_664L, out.comparison.lines.last().todayMoneyMinor)
        assertTrue(out.inflationText!!.startsWith("التضخم — الهيئة العامة للإحصاء"))
        assertNull(out.todayMoneyReason)
        // من غير ما يكتب فايدة بنكه ⇒ السطر «اكتب فائدة بنكك» مش صفر
        val untyped = CompareSavingsGrowth().compare(100_000, 120, 0, "2026-10-05", feed, "SA")
        val d = untyped.choices.first { it.line.growthClass == GrowthClass.DEPOSIT }
        assertNull(d.line.reachedMinor)
        assertEquals("اكتب فائدة بنكك على الوديعة", d.sourceText)
        // من غير ملف ⇒ «بقيمة المال اليوم» غير متاح بسببه
        val noFile = CompareSavingsGrowth().compare(100_000, 120, 0, "2026-10-05", null, "SA", todayMoney = true)
        assertEquals("«بقيمة المال اليوم» غير متاح: معدل التضخم الرسمي غير معروف", noFile.todayMoneyReason)
        assertEquals(listOf(null, null, null, null, 12_000_000L), noFile.comparison.lines.map { it.reachedMinor })
    }

    @Test
    fun egyptTypedStocksRecomputeThePoundLine() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        val out = CompareSavingsGrowth().compare(100_000, 60, 0, "2026-10-05", feed, "EG", userRates = mapOf(GrowthClass.LOCAL_STOCKS to 1942))
        val stocks = out.choices.first { it.line.growthClass == GrowthClass.LOCAL_STOCKS }
        assertEquals(-81, stocks.note?.inUsdBp, "رقمه هو بالجنيه ⇒ بالدولار −0.81%")
        assertEquals("النسبة اللي إنت كتبتها", stocks.sourceText)
        val gold = out.choices.first()
        assertEquals(3422, gold.line.rateBp)
        assertTrue(gold.note!!.text.startsWith("جزء كبير من الزيادة سببه نزول الجنيه"))
    }

    private class Repos(today: String = "2026-10-05T09:00:00.000Z") {
        val assets = MemoryAssetRepository()
        val lots = MemoryAssetLotRepository()
        val sales = MemoryAssetSaleRepository()
        val prices = MemoryAssetPriceRepository()
        val clock = FixedClock(today)
        val manage = ManageAssets(ManageAssetsDeps(assets, lots, sales, prices, SequentialIdGenerator(), clock))
        val growth = ManageAssetGrowth(ManageAssetGrowthDeps(assets, lots, sales, prices, clock))
    }

    @Test
    fun theOwnersApartmentFromPurchaseToSale() = runBlocking<Unit> {
        val r = Repos()
        val flat = r.manage.addAsset(NewAsset("شقة وهمية", "other"))
        r.manage.recordPurchase(PurchaseInput(flat.id, "2018-03-01", QUANTITY_SCALE, 600_000_000))
        r.growth.setProfile(flat.id, AssetGrowthInput(RealEstateValuation.AREA, 240 * QUANTITY_SCALE, 2_500_000, "2018-03-01", RentTerms(1_500_000, 500, 1)))
        assertEquals(600_000_000L, r.prices.listAll().single().pricePerUnitMinor, "سعر الشقة اتحدّث من سعر المتر")
        // المتر بقى 40,000 ⇒ الشقة 9,600,000 في المحفظة نفسها
        r.growth.updatePricePerSqm(flat.id, 4_000_000, "2026-10-01")
        val price = r.prices.listAll().single()
        assertEquals(960_000_000L to "2026-10-01", price.pricePerUnitMinor to price.asOf)
        assertEquals(960_000_000L, r.manage.listPortfolio("2026-10-05").totals.marketValueMinor)
        assertEquals(RentTerms(1_500_000, 500, 1), profileOf(r.assets.listAll().single()).rent, "تعديل سعر المتر ساب الإيجار زي ما هو")

        val defaults = LoadDefaultRates().load(feed, "SA", "2026-10-05")
        val view = r.growth.project(flat.id, 2030, "SA", defaults)
        assertEquals(ProjectionRateFrom.DEFAULT, view.rateFrom)
        assertEquals(1_028_580_216L, view.projection.valueAtSaleMinor) // بغلاء السعودية الرسمي (اختيار (أ) لحد رد المالك)
        assertEquals(71_117_068L, view.projection.rentTotalMinor)
        assertEquals(499_697_284L, view.projection.totalGainMinor)
        assertEquals("2018-03-01", view.projection.purchasedAt)
        assertTrue(view.rateSourceText.contains("الهيئة العامة للإحصاء"))
        // رقم كتبه دلوقتي ⇒ بيغلب، ورقم محفوظ على الأصل ⇒ بيغلب الافتراضي
        assertEquals(ProjectionRateFrom.TYPED_NOW, r.growth.project(flat.id, 2030, "SA", defaults, typedRateBp = 500).rateFrom)
        r.growth.setProfile(flat.id, profileOf(r.assets.listAll().single()).copy(expectedRateBp = 300))
        val saved = r.growth.project(flat.id, 2030, "SA", defaults)
        assertEquals(ProjectionRateFrom.SAVED_ON_ASSET to 300, saved.rateFrom to saved.projection.rateBp)
    }

    @Test
    fun wholeValueGoldAndNothingRecorded() = runBlocking<Unit> {
        val r = Repos()
        val villa = r.manage.addAsset(NewAsset("فيلا وهمية", "other"))
        r.growth.setProfile(villa.id, AssetGrowthInput(RealEstateValuation.WHOLE))
        r.manage.setPrice(villa.id, 300_000_000, "2026-10-01")
        val v = r.growth.project(villa.id, 2027, "SA", LoadDefaultRates().load(feed, "SA", "2026-10-05"))
        assertEquals(305_220_000L, v.projection.valueAtSaleMinor) // 3,000,000 × 1.0174
        assertNull(v.projection.gainAtSaleMinor, "مفيش شراء متسجل ⇒ المكسب غير متاح")
        assertTrue(r.prices.listAll().single().source == "manual")
        // ذهب من غير ملف المتوسطات ⇒ مفيش معدل ⇒ «غير متاح» بسببه
        val gold = r.manage.addAsset(NewAsset("ذهب وهمي", "gold", currency = Currency.SAR))
        val g = r.growth.project(gold.id, 2030, "SA", LoadDefaultRates().load(null, "SA", "2026-10-05"))
        assertEquals(ProjectionRateFrom.NONE, g.rateFrom)
        assertEquals("غير متاح: ملف المتوسطات لم يصل بعد", g.rateSourceText)
        // عقار بسعر المتر من غير شراء ⇒ القيمة من المساحة ومفيش سعر وحدة يتكتب
        val land = r.manage.addAsset(NewAsset("أرض وهمية", "other"))
        r.growth.setProfile(land.id, AssetGrowthInput(RealEstateValuation.AREA, 500 * QUANTITY_SCALE, 100_000))
        assertTrue(r.prices.listAll().none { it.assetId == land.id })
        assertEquals(50_000_000L, r.growth.project(land.id, 2026, "SA", null).projection.currentValueMinor)
        assertEquals("2026-10-05", r.assets.listAll().first { it.id == land.id }.pricePerSqmAsOf, "سعر المتر من غير تاريخ ⇒ النهارده")
    }

    @Test
    fun badInputsAreRefusedBeforeAnyWrite() = runBlocking<Unit> {
        val r = Repos()
        val gold = r.manage.addAsset(NewAsset("ذهب وهمي", "gold"))
        assertFailsWith<AssetError> { r.growth.setProfile(gold.id, AssetGrowthInput(areaSqm = QUANTITY_SCALE)) }
        assertFailsWith<AssetError> { r.growth.setProfile(gold.id, AssetGrowthInput(RealEstateValuation.AREA, 0)) }
        assertFailsWith<AssetError> { r.growth.setProfile(gold.id, AssetGrowthInput(RealEstateValuation.AREA, pricePerSqmMinor = -1)) }
        assertFailsWith<AssetError> { r.growth.setProfile(gold.id, AssetGrowthInput(rent = RentTerms(100, null, 13))) }
        assertFailsWith<IllegalArgumentException> { r.growth.setProfile(gold.id, AssetGrowthInput(expectedRateBp = 100_001)) }
        assertFailsWith<AssetError> { r.growth.updatePricePerSqm(gold.id, 100) }
        assertEquals(null, r.assets.listAll().single().valuation, "ولا حاجة اتكتبت")
    }
}
