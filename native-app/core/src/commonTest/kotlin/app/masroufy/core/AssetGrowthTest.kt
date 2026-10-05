package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** توقّع الأصل + مقارنة الخمسة (OVERRIDES §69 · §69.6) — مثال المالك بأرقام مخترعة شبهه. */
class AssetGrowthTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val sqm = QUANTITY_SCALE

    @Test
    fun theOwnersApartment() {
        // اشتراها بـ6,000,000 والمتر 25,000 ⇒ 240 متر
        assertEquals(240 * sqm, areaFromPurchase(600_000_000, 2_500_000))
        val apartment = Asset("a-1", "شقة وهمية", "other", "وحدة", Currency.SAR, false, valuation = RealEstateValuation.AREA, areaSqm = 240 * sqm, pricePerSqmMinor = 4_000_000)
        val position = computePosition("a-1", emptyList())
        assertEquals(960_000_000L, currentValueOf(apartment, position), "المتر بقى 40,000 ⇒ 9,600,000")
        assertNull(currentValueOf(apartment.copy(pricePerSqmMinor = null), position), "ناقص سعر المتر ⇒ غير متاح مش صفر")
        assertFailsWith<AssetError> { areaFromPurchase(600_000_000, 0) }
        // من غير شراء متسجل: الذهب كميته مش معروفة ⇒ غير متاح (مش صفر) · العقار «القيمة كلها» ⇒ السعر هو القيمة
        val priced = computePosition("g", emptyList(), price = AssetPrice("g", 50_000, "2026-10-01", "manual"), today = "2026-10-05")
        assertNull(currentValueOf(apartment.copy(kind = "gold", valuation = null), priced, lotsRecorded = false))
        assertEquals(50_000L, currentValueOf(apartment.copy(valuation = RealEstateValuation.WHOLE), priced, lotsRecorded = false))
        assertNull(currentValueOf(apartment.copy(valuation = RealEstateValuation.WHOLE), computePosition("g", emptyList()), lotsRecorded = false))
    }

    @Test
    fun theFullPictureUntilTheSaleYear() {
        // إيجار 15,000 في الشهر + 5% كل سنة + شهر فاضي: 1,500,000×11 + 1,575,000×11 + 1,653,750×11 + 1,736,438×11 (1,736,437.5 لفوق)
        val rent = RentTerms(1_500_000, 500, 1)
        assertEquals(71_117_068L, rentUntil(rent, 4))
        val p = projectAsset(960_000_000, 600_000_000, "2018-03-01", "2026-10-05", 2030, 174, rent)
        assertEquals(4, p.years)
        assertEquals(1_028_580_216L, p.valueAtSaleMinor)
        assertEquals(360_000_000L, p.gainNowMinor) // 9,600,000 − 6,000,000
        assertEquals(428_580_216L, p.gainAtSaleMinor)
        assertEquals(499_697_284L, p.totalGainMinor) // + الإيجار
        assertTrue(p.gaps.isEmpty())
        // البيع السنة دي: القيمة النهارده ومفيش إيجار
        val now = projectAsset(960_000_000, 600_000_000, null, "2026-10-05", 2026, 174, rent)
        assertEquals(960_000_000L, now.valueAtSaleMinor)
        assertEquals(0L, now.rentTotalMinor)
    }

    @Test
    fun eachMissingPieceSaysWhatToEnter() {
        val p = projectAsset(null, null, null, "2026-10-05", 2030, null, RentTerms())
        assertEquals(listOf(ProjectionGap.NO_VALUE, ProjectionGap.NO_COST, ProjectionGap.NO_RATE), p.gaps)
        assertNull(p.valueAtSaleMinor)
        assertNull(p.totalGainMinor)
        assertEquals(0L, p.rentTotalMinor, "مفيش إيجار مكتوب = مش مأجّر (خانة اختيارية)")
        assertEquals("قيمة الأصل اليوم غير متاحة: اكتب سعره، أو المساحة وسعر المتر", ProjectionGap.NO_VALUE.text)
        val noRate = projectAsset(960_000_000, null, null, "2026-10-05", 2030, null, RentTerms(100_000))
        assertNull(noRate.valueAtSaleMinor)
        assertEquals(4_800_000L, noRate.rentTotalMinor) // 1,000 × 12 × 4
        assertFailsWith<AssetError> { projectAsset(1, 1, null, "2026-10-05", 2025, 0, RentTerms()) }
        assertFailsWith<AssetError> { projectAsset(1, 1, null, "2026-10-05", 2127, 0, RentTerms()) }
        assertFailsWith<AssetError> { rentUntil(RentTerms(100, 0, 13), 1) }
        assertFailsWith<AssetError> { rentUntil(RentTerms(-1), 1) }
    }

    @Test
    fun growthClassForDefaults() {
        val base = Asset("x", "س", "gold", "جرام", Currency.SAR, false)
        assertEquals(GrowthClass.GOLD, growthClassOf(base, Currency.SAR))
        assertEquals(GrowthClass.REAL_ESTATE, growthClassOf(base.copy(kind = "other", valuation = RealEstateValuation.WHOLE), Currency.SAR))
        assertEquals(GrowthClass.LOCAL_STOCKS, growthClassOf(base.copy(kind = "stock"), Currency.SAR))
        assertNull(growthClassOf(base.copy(kind = "stock", currency = Currency.USD), Currency.SAR), "سهم برا السوق المحلي ⇒ مفيش افتراضي")
        assertNull(growthClassOf(base.copy(kind = "silver"), Currency.SAR))
        assertNull(growthClassOf(base.copy(kind = "other"), Currency.SAR))
    }

    @Test
    fun theFiveSideBySide() {
        // 1,000 في الشهر 10 سنين بمعدلات السعودية (الوديعة 4.50% كتبها المستخدم)
        val rates = mapOf(GrowthClass.GOLD to 1488, GrowthClass.REAL_ESTATE to 174, GrowthClass.DEPOSIT to 450, GrowthClass.LOCAL_STOCKS to 795)
        val c = compareGrowth(100_000, 120, 0, "2026-10-05", rates, todayMoney = true, inflationBp = 174)
        assertEquals(12_000_000L, c.paidInMinor)
        assertEquals("2036-10-05", c.endDate)
        assertEquals(listOf(25_832_688L, 13_087_780L, 15_119_806L, 17_965_843L, 12_000_000L), c.lines.map { it.reachedMinor })
        assertEquals(0L, c.lines.last().gainMinor, "الكاش ما بيزيدش")
        assertEquals(10_098_664L, c.lines.last().todayMoneyMinor, "120,000 بعد 10 سنين بغلاء 1.74% = 100,986.64 بفلوس النهارده")
        assertEquals(21_739_636L, c.lines.first().todayMoneyMinor)
        // من غير الوديعة (ما اتكتبتش) ⇒ السطر كله غير متاح، والباقي زي ما هو
        val noDeposit = compareGrowth(100_000, 120, 0, "2026-10-05", rates - GrowthClass.DEPOSIT)
        val deposit = noDeposit.lines.first { it.growthClass == GrowthClass.DEPOSIT }
        assertNull(deposit.reachedMinor)
        assertNull(deposit.todayMoneyMinor)
        assertNull(noDeposit.lines.first().todayMoneyMinor, "الزرار مقفول")
        // الزرار شغال والتضخم مش معروف ⇒ غير متاح (مش الرقم من غير خصم)
        assertNull(compareGrowth(100_000, 120, 0, "2026-10-05", rates, todayMoney = true, inflationBp = null).lines.first().todayMoneyMinor)
        assertFailsWith<GrowthError> { compareGrowth(0, 120, 0, "2026-10-05", rates) }
        assertFailsWith<GrowthError> { compareGrowth(100, 0, 0, "2026-10-05", rates) }
    }

    @Test
    fun backupCheckForTheNewAssetFields() {
        val ok = mapOf("valuation" to "area", "areaSqm" to 24_000_000_000L, "pricePerSqmMinor" to 4_000_000L, "pricePerSqmAsOf" to "2026-10-01", "vacantMonthsPerYear" to 1L, "rentIncreaseBp" to 500L, "expectedRateBp" to -100L)
        assertNull(checkAssetGrowthRow(ok))
        assertNull(checkAssetGrowthRow(emptyMap()), "الأصل القديم من غير الحقول سليم")
        assertEquals("valuation", checkAssetGrowthRow(ok + ("valuation" to "house")))
        assertEquals("areaSqm", checkAssetGrowthRow(ok + ("areaSqm" to 0L)))
        assertEquals("pricePerSqmAsOf", checkAssetGrowthRow(ok + ("pricePerSqmAsOf" to "2026-13-01")))
        assertEquals("vacantMonthsPerYear", checkAssetGrowthRow(ok + ("vacantMonthsPerYear" to 13L)))
        assertEquals("expectedRateBp", checkAssetGrowthRow(ok + ("expectedRateBp" to 1.5)))
        assertEquals("rentIncreaseBp", checkAssetGrowthRow(ok + ("rentIncreaseBp" to 200_000L)))
    }
}
