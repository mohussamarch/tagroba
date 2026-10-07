package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ملف المتوسطات والمعدلات الافتراضية (OVERRIDES §69.3 · §69.6) — الأرقام = البحث المتراجع (10 سنين لحد 2025). */
class DefaultRatesTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun row(bp: Any?, start: String, end: String, official: Boolean, manual: Boolean = false, extra: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "kind" to if (manual) "manual" else "computed", "valueBp" to bp, "periodStart" to start, "periodEnd" to end, "official" to official,
            "sourceName" to "source", "method" to "method", "stale" to false,
        ).apply { if (manual) put("reviewedOn", "2026-10-05") else put("computedOn", "2026-10-01") } + extra

    private val rows: Map<String, Map<String, Any?>> = linkedMapOf(
        AverageKeys.GOLD_USD to row(1488L, "2015-12", "2025-12", true),
        AverageKeys.GOLD_USD_ANNUAL to row(1148L, "2015", "2025", true),
        AverageKeys.GOLD_EGP to row(3422L, "2015", "2025", true),
        AverageKeys.EGP_PER_USD to row(2040L, "2015", "2025", true),
        AverageKeys.CPI_SA to row(174L, "2015", "2025", true),
        AverageKeys.CPI_EG to row(1633L, "2015", "2025", true),
        AverageKeys.STOCKS_SA to row(795L, "2014-12", "2024-12", false, manual = true),
        AverageKeys.STOCKS_EG to row(1942L, "2015-12", "2025-12", false, manual = true, extra = mapOf("confidence" to "C")),
        AverageKeys.POLICY_RATE_SA to row(400L, "2026-09", "2026-09", true, manual = true, extra = mapOf("confidence" to "C")),
        AverageKeys.POLICY_RATE_EG to row(1900L, "2026-09", "2026-09", true, manual = true, extra = mapOf("confidence" to "C")),
        AverageKeys.REAL_ESTATE_INDEX_SA to row(260L, "2025-06", "2026-06", true, manual = true),
        AverageKeys.REAL_ESTATE_INDEX_EG to row(1452L, "2019", "2024", false, manual = true),
    )

    private fun feed(map: Map<String, Any?> = rows, generatedAt: String = "2026-10-01T02:00:00.000Z") =
        parseAveragesFeed(mapOf("schema" to 1L, "generatedAt" to generatedAt, "averages" to map, "failures" to emptyList<Any>()))

    private fun List<DefaultRate>.of(cls: GrowthClass) = first { it.growthClass == cls }

    @Test
    fun saudiDefaultsCarryTheirSources() {
        val r = defaultRates(feed(), "SA", today = "2026-10-05")
        assertEquals(listOf(1488, 174, null, 795, 0), r.map { it.rateBp }, "ذهب · عقار (غلاء رسمي) · وديعة يكتبها · أسهم بالتوزيعات · كاش")
        assertTrue(r.of(GrowthClass.DEPOSIT).userMustType)
        assertEquals(400, r.of(GrowthClass.DEPOSIT).info?.valueBp, "فايدة البنك المركزي معلومة جنبها مش مكانها")
        assertEquals("متوسط سعر الذهب الشهري — البنك الدولي · 2015-12–2025-12 · رسمي", r.of(GrowthClass.GOLD).sourceText)
        assertEquals("مؤشر MSCI للسوق السعودية مع التوزيعات · 2014-12–2024-12 · خاص (غير رسمي) · رقم يدوي — رُوجع في 2026-10-05", r.of(GrowthClass.LOCAL_STOCKS).sourceText)
        assertEquals("التضخم — الهيئة العامة للإحصاء (عبر البنك الدولي) · 2015–2025 · رسمي", r.of(GrowthClass.REAL_ESTATE).sourceText)
        assertTrue(r.none { it.stale })
        assertNull(r.of(GrowthClass.GOLD).note, "السطر ده لمصر بس")
    }

    @Test
    fun egyptGoldSaysHowMuchIsThePound() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        val r = defaultRates(feed(), "EG", today = "2026-10-05")
        assertEquals(listOf(3422, 1633, null, 1942, 0), r.map { it.rateBp })
        val note = r.of(GrowthClass.GOLD).note!!
        assertEquals(2040, note.currencyFallBp)
        assertEquals(1148, note.inUsdBp)
        assertEquals("جزء كبير من الزيادة سببه نزول الجنيه — الجنيه نزل قدام الدولار 20.40% في السنة في المتوسط (2015–2025)، وبالدولار الزيادة 11.48% في السنة", note.text)
        // أسهم مصر — رد المالك (§69.3): الرقم 19.42% وجنبه سطر الجنيه (≈ −0.81% بالدولار)
        val stocks = r.of(GrowthClass.LOCAL_STOCKS)
        assertFalse(stocks.userMustType)
        assertEquals(-81, stocks.note!!.inUsdBp)
        // «المستخدم يكتب» فاضل بالإعداد: سطر الجنيه بنزوله بس (من غير رقم بالدولار، مش صفر)
        val typed = defaultRates(feed(), "EG", GrowthRatesConfig(egyptStocks = EgyptStocksMode.USER_TYPES), today = "2026-10-05").of(GrowthClass.LOCAL_STOCKS)
        assertTrue(typed.userMustType)
        assertNull(typed.note!!.inUsdBp)
        assertEquals(1900, r.of(GrowthClass.DEPOSIT).info?.valueBp)
    }

    @Test
    fun theTwoPendingOwnerChoicesAreConfigSwitches() {
        val indexAndStocks = GrowthRatesConfig(RealEstateRateSource.PRICE_INDEX, EgyptStocksMode.DEFAULT_WITH_NOTE)
        assertEquals(260, defaultRates(feed(), "SA", indexAndStocks).of(GrowthClass.REAL_ESTATE).rateBp)
        val eg = defaultRates(feed(), "EG", indexAndStocks)
        assertEquals(1452, eg.of(GrowthClass.REAL_ESTATE).rateBp)
        assertEquals(1942, eg.of(GrowthClass.LOCAL_STOCKS).rateBp)
        assertEquals(-81, eg.of(GrowthClass.LOCAL_STOCKS).note?.inUsdBp, "19.42% بالجنيه ≈ −0.81% بالدولار")
        assertFalse(eg.of(GrowthClass.LOCAL_STOCKS).userMustType)
    }

    @Test
    fun missingIsNotAvailableNeverZero() {
        val none = defaultRates(null, "SA")
        assertEquals(listOf(null, null, null, null, 0), none.map { it.rateBp }, "الكاش بس صفر — بالتعريف مش مجهول")
        assertEquals("غير متاح: ملف المتوسطات لم يصل بعد", none.of(GrowthClass.GOLD).reason)
        val withoutGold = defaultRates(feed(rows - AverageKeys.GOLD_USD), "SA")
        assertNull(withoutGold.of(GrowthClass.GOLD).rateBp)
        assertEquals("غير متاح: هذا المتوسط غير موجود في الملف — اكتب النسبة بنفسك", withoutGold.of(GrowthClass.GOLD).reason)
        val other = defaultRates(feed(), "AE")
        assertEquals(listOf(null, null, null, null, 0), other.map { it.rateBp })
        assertEquals("غير متاح: لا توجد متوسطات لهذا البلد بعد", other.of(GrowthClass.LOCAL_STOCKS).reason)
        assertEquals(1488, defaultRates(feed(), null).of(GrowthClass.GOLD).rateBp, "من غير بلد = الافتراضي (السعودية) زي باقي التطبيق")
        assertNull(inflationEntry(feed(), "AE"))
    }

    @Test
    fun staleEntriesAndOldFilesAreMarked() {
        val staleCpi = rows + (AverageKeys.CPI_SA to row(174L, "2015", "2025", true, extra = mapOf("stale" to true, "staleSince" to "2026-11-01")))
        val r = defaultRates(feed(staleCpi), "SA", today = "2026-11-02")
        assertTrue(r.of(GrowthClass.REAL_ESTATE).stale)
        assertFalse(r.of(GrowthClass.GOLD).stale)
        assertTrue(r.of(GrowthClass.REAL_ESTATE).sourceText.endsWith("قديم: آخر تحديث لم ينجح — الرقم من 2026-11-01"))
        // الملف نفسه أقدم من 45 يوم ⇒ كله قديم
        val old = defaultRates(feed(), "SA", today = "2026-11-16")
        assertTrue(old.filter { it.rateBp != null && it.growthClass != GrowthClass.CASH }.all { it.stale })
        assertFalse(defaultRates(feed(), "SA", today = "2026-11-15").of(GrowthClass.GOLD).stale, "45 يوم بالظبط لسه مش قديم")
    }

    @Test
    fun parserRejectsBadLinesOnTheirOwn() {
        val bad = rows + mapOf(
            "FLOAT" to row(14.88, "2015", "2025", true),
            "NO_SOURCE" to row(100L, "2015", "2025", true) - "sourceName",
            "MANUAL_NO_DATE" to row(100L, "2015", "2025", true, manual = true) - "reviewedOn",
            "NO_FLAG" to row(100L, "2015", "2025", true) - "official",
            "BAD_PERIOD" to row(100L, "2025", "2015", true),
            "HUGE" to row(200_000L, "2015", "2025", true),
            "NOT_OBJECT" to "x",
        )
        val f = feed(bad)
        assertEquals(12, f.entries.size)
        assertEquals(listOf("FLOAT", "NO_SOURCE", "MANUAL_NO_DATE", "NO_FLAG", "BAD_PERIOD", "HUGE", "NOT_OBJECT"), f.rejected.map { it.key })
        assertEquals(1488, f.entries.getValue(AverageKeys.GOLD_USD).valueBp)
        assertTrue(f.entries.getValue(AverageKeys.STOCKS_EG).newsSourced)
        assertFailsWith<PriceFeedError> { parseAveragesFeed(mapOf("schema" to 2L, "generatedAt" to "2026-10-01T00:00:00Z", "averages" to rows)) }
        assertFailsWith<PriceFeedError> { parseAveragesFeed(mapOf("schema" to 1L, "averages" to rows)) }
        assertFailsWith<PriceFeedError> { parseAveragesFeed(mapOf("schema" to 1L, "generatedAt" to "2026-10-01T00:00:00Z")) }
        assertFailsWith<PriceFeedError> { parseAveragesFeed(listOf(1)) }
    }

    @Test
    fun classLabelsInEveryVariant() {
        forEachTextVariant { label ->
            assertTrue(GrowthClass.entries.all { it.label.isNotBlank() }, label)
        }
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("كاش في البيت", GrowthClass.CASH.label)
        Texts.language = Language.EN
        assertEquals("Local stocks", GrowthClass.LOCAL_STOCKS.label)
    }
}
