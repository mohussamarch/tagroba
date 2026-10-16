package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «حاجاتي» (مواصفة r05 §2.6) — بيانات مخترعة بالكامل. */
class HoldingsViewTest {
    private fun asset(id: String, kind: String = "other", holdingType: String? = null, purpose: String? = null, realEstate: Boolean = false) =
        Asset(id, "حاجة $id", kind, "وحدة", Currency.SAR, archived = false, realEstate = realEstate, holdingType = holdingType, purpose = purpose)

    private fun item(id: String, type: HoldingType, purpose: HoldingPurpose, value: Long?) =
        HoldingValue(asset(id, holdingType = type.wire, purpose = purpose.wire), type, purpose, value, QUANTITY_SCALE)

    private val snapshot = HoldingsSnapshot(
        Currency.SAR,
        listOf(
            item("g", HoldingType.GOLD, HoldingPurpose.RESERVE, 1_000_000),
            item("f", HoldingType.FLAT, HoldingPurpose.INVEST, 10_000_000),
            item("s", HoldingType.STK, HoldingPurpose.INVEST, 2_000_000),
            item("c", HoldingType.CAR, HoldingPurpose.PERSONAL, 1_000_000),
            item("w", HoldingType.WATCH, HoldingPurpose.PERSONAL, null),
        ),
        goldPriceMissing = false,
    )

    @Test fun personalIsInTheStripButOutOfTheTotal() {
        val v = holdingsView(snapshot, null)
        assertEquals(13_000_000L, v.totalMinor)
        assertEquals(listOf(HoldingPurpose.INVEST, HoldingPurpose.RESERVE, HoldingPurpose.PERSONAL), v.strip.map { it.purpose })
        assertEquals(100, v.strip.sumOf { it.percent })

        assertEquals(listOf(86, 7, 7), v.strip.map { it.percent })
        assertEquals(0, v.unknownCount) // الساعة شخصية ⇒ مش محسوبة في «غير المعروف» اللي برّه الإجمالي
    }

    @Test fun thingsThatLoseValueFollowThePersonalChoiceWhateverTheirPurpose() {
        // رد المالك Q13: العربية والجوال زي الشخصي — حتى لو غرضها اتغيّر لـ«استثمار» بإيده
        val phone = item("p", HoldingType.PHONE, HoldingPurpose.INVEST, 300_000)
        val s = snapshot.copy(items = snapshot.items + phone)
        assertEquals(13_000_000L, holdingsView(s, null).totalMinor)
        assertEquals(14_300_000L, holdingsView(s.copy(includePersonal = true), null).totalMinor)
        assertEquals(0L, investableSplit(s.items).noGrowthMinor)
        assertEquals(1_300_000L, investableSplit(s.items, includePersonal = true).noGrowthMinor)
        // من غير أي حاجة شخصية ولا بتخسر قيمتها ⇒ السؤال مش ظاهر
        assertTrue(holdingsView(s.copy(items = listOf(phone)), null).hasPersonal)
        assertFalse(holdingsView(s.copy(items = snapshot.items.take(3)), null).hasPersonal)
    }

    @Test fun ownerChoiceCanCountPersonalThings() {
        // رد المالك ٢أ: الافتراضي من غير الشخصي، والمستخدم يقدر يدخّله في المجموع والتوقّع
        val v = holdingsView(snapshot.copy(includePersonal = true), null)
        assertEquals(14_000_000L, v.totalMinor)
        assertEquals(1, v.unknownCount) // الساعة الشخصية من غير قيمة بقت محسوبة في «غير المعروف»
        assertTrue(v.hasPersonal && v.includePersonal)
        assertEquals(1_000_000L, investableSplit(snapshot.items, includePersonal = true).noGrowthMinor) // العربية ثابتة من غير زيادة
        assertEquals(0L, investableSplit(snapshot.items).noGrowthMinor)
    }

    @Test fun tilesFollowTheFixedOrderAndUnknownIsNotZero() {
        val v = holdingsView(snapshot, null)
        assertEquals(
            listOf(HoldingGroup.METAL, HoldingGroup.PROPERTY, HoldingGroup.SHARES, HoldingGroup.PERSONAL, HoldingGroup.COLLECTIBLES),
            v.tiles.map { it.group },
        )
        assertNull(v.tiles.last().valueMinor) // المقتنيات كلها «غير متاح»
        assertEquals(1, v.tiles.last().count)
    }

    @Test fun filterKeepsOnlyThatPurposeInTilesButNotInTheStrip() {
        val v = holdingsView(snapshot, HoldingPurpose.INVEST)
        assertEquals(listOf(HoldingGroup.PROPERTY, HoldingGroup.SHARES), v.tiles.map { it.group })
        assertEquals(3, v.strip.size)
        assertEquals(13_000_000L, v.totalMinor)
    }

    @Test fun allInvestableUnknownGivesNoTotalInsteadOfZero() {
        val s = HoldingsSnapshot(Currency.SAR, listOf(item("g", HoldingType.GOLD, HoldingPurpose.INVEST, null)), goldPriceMissing = true)
        val v = holdingsView(s, null)
        assertNull(v.totalMinor)
        assertTrue(v.goldPriceMissing)
        assertEquals(1, v.unknownCount)
        assertTrue(v.strip.isEmpty())
    }

    @Test fun emptySnapshot() {
        val v = holdingsView(HoldingsSnapshot(Currency.EGP, emptyList(), false), null)
        assertTrue(v.empty)
        assertEquals(0L, v.totalMinor)
        assertTrue(v.tiles.isEmpty())
    }

    @Test fun largestRemainderAlwaysSumsTo100() {
        assertEquals(listOf(34, 33, 33), purposePercents(listOf(1, 1, 1)))
        assertEquals(listOf(100), purposePercents(listOf(7)))
        assertEquals(listOf(0, 0), purposePercents(listOf(0, 0)))
        assertEquals(listOf(67, 33), purposePercents(listOf(2, 1)))
        val odd = purposePercents(listOf(1_234_567, 7_654_321, 99, 3))
        assertEquals(100, odd.sum())
    }

    @Test fun legacyKindsMapToTypes() {
        assertEquals(HoldingType.GOLD, holdingTypeOf(asset("1", kind = "gold")))
        assertEquals(HoldingType.SILVER, holdingTypeOf(asset("2", kind = "silver")))
        assertEquals(HoldingType.STK, holdingTypeOf(asset("3", kind = "fund")))
        assertEquals(HoldingType.FLAT, holdingTypeOf(asset("4", kind = "other", realEstate = true)))
        assertEquals(HoldingType.OTHER, holdingTypeOf(asset("5", kind = "digital")))
        assertEquals(HoldingType.SHOP, holdingTypeOf(asset("6", kind = "other", holdingType = "shop", realEstate = true)))
    }

    @Test fun jewelleryDefaultsToPersonalUnlessWritten() {
        assertEquals(HoldingPurpose.PERSONAL, purposeOf(asset("1", kind = "gold"), jewellery = true))
        assertEquals(HoldingPurpose.INVEST, purposeOf(asset("1", kind = "gold"), jewellery = false))
        assertEquals(HoldingPurpose.RESERVE, purposeOf(asset("1", kind = "gold", purpose = "reserve"), jewellery = true))
        assertEquals(HoldingPurpose.TRADE, purposeOf(asset("1", kind = "other", holdingType = "shop", realEstate = true), jewellery = false))
    }

    @Test fun investableSplitAndMixAreIntegerAndSkipPersonal() {
        val split = investableSplit(snapshot.items + item("x", HoldingType.OTHER, HoldingPurpose.INVEST, 500_000) + item("y", HoldingType.CO, HoldingPurpose.TRADE, null))
        assertEquals(InvestableSplit(10_000_000, 1_000_000, 2_000_000, 500_000, 1), split)
        val e = AverageEntry("K", 0, "2015", "2024", true, false, "مصدر رسمي مخترع", null, "cagr")
        val rates = InvestRates(cpiBp = 200, goldBp = 1000, sharesBp = 701, cpi = e, gold = e, shares = e)
        // (10M·200 + 1M·1000 + 2M·701) ÷ 13M = 4_402_000_000 ÷ 13_000_000 = 338.6 ⇒ 338 (لتحت)
        assertEquals(338, holdingsMixBp(split, rates))
        assertNull(holdingsMixBp(InvestableSplit(0, 0, 0, 500_000, 0), rates))
    }

    @Test fun groupGrowthWithZeroRatesIsExactlyLinear() {
        val g = growHoldingGroup(startMinor = 1_000_000, addMinor = 50_000, rateBp = 0, cpiBp = 0, years = 2)
        assertEquals(listOf(1_000_000L, 1_000_000L, 1_000_000L), g.leave)
        assertEquals(listOf(1_000_000L, 1_600_000L, 2_200_000L), g.plan)
    }

    @Test fun groupGrowthAtInflationRateStaysFlatInTodaysMoneyAndDeflatesAdditions() {
        // العقار بيزيد زي الغلاء ⇒ بفلوس النهارده ثابت بالظبط (مش «≈ 299,999» لأرض 300,000)
        val g = growHoldingGroup(startMinor = 100_000_000, addMinor = 0, rateBp = 400, cpiBp = 400, years = 10)
        assertEquals(11, g.leave.size)
        assertEquals(List(11) { 100_000_000L }, g.leave)
        assertEquals(List(11) { 30_000_000L }, growHoldingGroup(30_000_000, 0, 174, 174, 10).leave)
        val added = growHoldingGroup(startMinor = 30_000_000, addMinor = 50_000, rateBp = 174, cpiBp = 174, years = 3).plan
        assertEquals(30_000_000L, added[0])
        assertTrue(added[3] > 30_000_000L + 50_000L * 34 && added[3] <= 30_000_000L + 50_000L * 36) // الإضافات بفلوس النهارده
        val plan = growHoldingGroup(startMinor = 0, addMinor = 10_000, rateBp = 0, cpiBp = 1000, years = 1).plan
        // 12 × 100 ر.س بعد سنة ÷ 1.10 = 1,090.91
        assertEquals(listOf(0L, 109_091L), plan)
    }

    @Test fun groupGrowthAboveInflationCompoundsByTheYearlyRate() {
        // 10% في السنة من غير غلاء ⇒ بعد سنة ≈ 11,000 وبعد سنتين ≈ 12,100 (هللات من التقريب الشهري بس) — أعداد صحيحة
        val g = growHoldingGroup(startMinor = 1_000_000, addMinor = 0, rateBp = 1000, cpiBp = 0, years = 2)
        assertTrue(kotlin.math.abs(g.leave[1] - 1_100_000L) <= 200, "year 1: ${g.leave[1]}")
        assertTrue(kotlin.math.abs(g.leave[2] - 1_210_000L) <= 400, "year 2: ${g.leave[2]}")
        assertEquals(g.leave, g.plan) // من غير زيادة شهرية الخطين واحد
        val withAdd = growHoldingGroup(startMinor = 1_000_000, addMinor = 10_000, rateBp = 1000, cpiBp = 0, years = 1)
        assertTrue(withAdd.plan[1] > withAdd.leave[1] + 12 * 10_000L, "additions also grow: ${withAdd.plan[1]}")
    }
}
