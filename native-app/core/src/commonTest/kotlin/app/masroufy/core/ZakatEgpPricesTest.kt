package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * أسعار الدهب والفضة بالجنيه لزكاة مصر (OVERRIDES §62 — جلسة 18). ملف الأسعار بقى فيه سطور `*_EGP` عليها `"currency": "EGP"`
 * والسعر بالقرش؛ السطور القديمة من غير الحقل = ريال. كل الأسعار هنا **مخترعة**.
 */
class ZakatEgpPricesTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    /** نفس شكل `public/prices.json` اللي `fetch-prices.mjs` بيكتبه — JSON متحوّل لـ Map. */
    private fun row(price: Long, currency: String? = null): Map<String, Any?> =
        linkedMapOf<String, Any?>("name" to "سعر وهمي", "unit" to "جرام", "pricePerUnitMinor" to price, "asOf" to "2026-10-05", "source" to "مصدر وهمي")
            .also { if (currency != null) it["currency"] = currency }

    private fun feed(vararg rows: Pair<String, Map<String, Any?>>): PriceFeed =
        parsePriceFeed(mapOf("generatedAt" to "2026-10-05T03:00:00Z", "baseCurrency" to "SAR", "prices" to linkedMapOf(*rows)))

    private val oldShape = feed("GOLD_24K_GRAM" to row(30_000), "GOLD_21K_GRAM" to row(26_250), "SILVER_GRAM" to row(350), "2222" to row(2_850))

    private val withEgp = feed(
        "GOLD_24K_GRAM" to row(30_000), "SILVER_GRAM" to row(350),
        "GOLD_24K_GRAM_EGP" to row(400_000, "EGP"), "GOLD_21K_GRAM_EGP" to row(350_000, "EGP"), "SILVER_GRAM_EGP" to row(4_700, "EGP"),
    )

    @Test
    fun `ملف التطبيق القديم من غير حقل العملة بيتقري ريال زي ما كان`() {
        assertEquals(4, oldShape.prices.size)
        assertTrue(oldShape.prices.all { it.currency == "SAR" })
        assertTrue(oldShape.rejected.isEmpty())
        assertEquals(ZakatPrices(30_000, 350, "2026-10-05"), zakatPricesFromFeed(oldShape, Currency.SAR))
    }

    @Test
    fun `مصر بملف فيه سطور الريال بس تفضل غير متاح`() {
        val egp = zakatPricesFromFeed(oldShape, Currency.EGP)
        assertEquals(ZakatPrices(null, null), egp, "سعر الريال عمره ما بيتاخد للجنيه")
        assertNull(nisabMinor(ZakatCountry.EG, egp))
        assertEquals(ZakatPrices(null, null), zakatPricesFromFeed(null, Currency.EGP))
    }

    @Test
    fun `مصر بسطر الجنيه النصاب بيتحسب 85 جرام عيار 21`() {
        val egp = zakatPricesFromFeed(withEgp, Currency.EGP)
        assertEquals(ZakatPrices(400_000, 4_700, "2026-10-05"), egp, "من سطور الجنيه، مش الريال")
        // 85 × 21/24 × 4,000.00 = 297,500.00 جنيه
        assertEquals(29_750_000L, nisabMinor(ZakatCountry.EG, egp))
        // والسعودية ما اتغيرتش: سطور الريال بس، والنصاب الأقل (فضة 595 × 3.50)
        val sar = zakatPricesFromFeed(withEgp, Currency.SAR)
        assertEquals(ZakatPrices(30_000, 350, "2026-10-05"), sar)
        assertEquals(208_250L, nisabMinor(ZakatCountry.SA, sar))
    }

    @Test
    fun `قطعة دهب في مصر بتتقيّم بسعر الجنيه`() {
        val ring = ZakatHolding.Metal("a-ring", "سبيكة وهمية", ZakatMetal.GOLD, 10 * QUANTITY_SCALE, 21, null, ZakatPurpose.SAVING, null)
        assertEquals(3_500_000L, metalValueMinor(ring, zakatPricesFromFeed(withEgp, Currency.EGP)), "10 × 21/24 × 4,000.00")
        assertNull(metalValueMinor(ring, zakatPricesFromFeed(oldShape, Currency.EGP)), "من غير سعر بالجنيه ⇒ غير متاح، مش سعر الريال")
    }

    @Test
    fun `سطر بعملة غير عملته ما بيتاخدش حتى لو اسمه صح`() {
        // سطر اسمه بالجنيه بس عملته مكتوبة ريال — أو سطر الريال اتكتب عليه جنيه بالغلط: ولا واحد بيتاخد للعملة التانية
        val swapped = feed("GOLD_24K_GRAM" to row(30_000, "EGP"), "GOLD_24K_GRAM_EGP" to row(400_000, "SAR"), "SILVER_GRAM" to row(350))
        assertEquals(30_000L, feedPriceIn(swapped, "GOLD_24K_GRAM", Currency.EGP)?.pricePerUnitMinor, "السطر نفسه بعملته")
        assertNull(feedPriceIn(swapped, "GOLD_24K_GRAM", Currency.SAR), "مفيش سطر دهب بالريال")
        assertNull(feedPriceIn(oldShape, "GOLD_24K_GRAM", Currency.USD))
    }

    @Test
    fun `العملة المكتوبة غلط بترفض السطر لوحده والفاضية تبقى عملة الملف`() {
        for (variant in listOf(ArabicVariant.MSA, ArabicVariant.EGYPTIAN)) {
            Texts.arabicVariant = variant
            val f = parsePriceFeed(
                mapOf(
                    "generatedAt" to "2026-10-05T03:00:00Z", "baseCurrency" to "SAR",
                    "prices" to linkedMapOf(
                        "A" to row(100, "egp"), "B" to row(100, "EG"), "C" to (row(100) + ("currency" to 5L)), "D" to (row(100) + ("currency" to null)),
                        "E" to row(100, ""),
                    ),
                ),
            )
            assertEquals(listOf("A" to "EGP", "D" to "SAR"), f.prices.map { it.symbol to it.currency })
            assertEquals(listOf("B", "C", "E"), f.rejected.map { it.key })
            assertTrue(f.rejected.all { it.reason == uiText(TextKey.FEED_BAD_CURRENCY) })
        }
    }
}
