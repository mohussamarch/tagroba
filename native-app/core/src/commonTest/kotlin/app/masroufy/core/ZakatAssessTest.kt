package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * حساب الزكاة (OVERRIDES §62) — مكتوب بالإيد، كل الأسماء والأرقام مخترعة.
 * المثال الكامل نفس مثال الصفحة المرسومة: كاش 40,000 · دهب ادخار 100 جم عيار 21 · دهب لبس 50 جم · أسهم طويلة 20,000 ·
 * دين ليك 3,000 · جمعية +2,000 · دين عليك 10,000 ⇒ السعودية 71,250 ⇒ 1,781.25.
 */
class ZakatAssessTest {
    private fun g(grams: Long): Quantity = grams * QUANTITY_SCALE

    /** سعر الجرام الصافي 300.00 والفضة 3.50 (أمثلة). */
    private val prices = ZakatPrices(goldPureGramMinor = 30_000, silverPureGramMinor = 350)

    private fun example(receivable: ZakatCollectability? = ZakatCollectability.STRONG) = listOf(
        ZakatHolding.Cash("w-1", "بنك وهمي", 4_000_000),
        ZakatHolding.Metal("a-gold", "سبيكة وهمية", ZakatMetal.GOLD, g(100), 21, null, ZakatPurpose.SAVING, null),
        ZakatHolding.Metal("a-ring", "شبكة وهمية", ZakatMetal.GOLD, g(50), 21, null, ZakatPurpose.WEAR, null),
        ZakatHolding.Security("a-shares", "سهم وهمي", ZakatLineKind.STOCKS, 2_000_000, ZakatShareHolding.LONG_TERM),
        ZakatHolding.Receivable("o-1", "شخص وهمي", 300_000, receivable),
        ZakatHolding.RoscaCredit("r-1", "جمعية وهمية", 200_000),
        ZakatHolding.Debt("o-2", "شخص تاني", 1_000_000),
    )

    private fun assess(country: ZakatCountry, holdings: List<ZakatHolding>, p: ZakatPrices = prices, hawl: HawlState = HawlState.Complete(true)) =
        assessZakat(country, Currency.SAR, "2026-02-18", zakatItems(country, holdings, p), nisabMinor(country, p), hawl)

    @Test
    fun `السعودية — المثال الكامل 71250 ⇒ 178125 هللة`() {
        val a = assess(ZakatCountry.SA, example())
        assertEquals(ZakatOutcome.DUE, a.outcome)
        assertEquals(208_250L, a.nisabMinor, "الأقل من 85 جم دهب و595 جم فضة ⇒ فضة 595 × 3.50")
        assertEquals(7_125_000L, a.totalZakatableMinor)
        assertEquals(178_125L, a.dueMinor)
        assertEquals(
            listOf(
                ZakatLine(ZakatLineKind.CASH, 4_000_000, 100_000), ZakatLine(ZakatLineKind.GOLD, 2_625_000, 65_625),
                ZakatLine(ZakatLineKind.RECEIVABLES, 300_000, 7_500), ZakatLine(ZakatLineKind.ROSCA, 200_000, 5_000),
            ),
            a.lines,
        )
        val ring = a.items.first { it.holding.id == "a-ring" }
        assertEquals(ZakatItemStatus.EXEMPT to ZakatTopic.WORN_JEWELRY, ring.status to ring.topic)
        assertEquals(1_312_500L to 0L, ring.valueMinor to ring.zakatableMinor, "اللبس بيتعرض بقيمته ومعفي")
        val shares = a.items.first { it.holding.id == "a-shares" }
        assertEquals(ZakatItemStatus.EXEMPT to ZakatTopic.LONG_TERM_SHARES, shares.status to shares.topic)
        val debt = a.items.first { it.holding.id == "o-2" }
        assertEquals(ZakatItemStatus.NOT_DEDUCTED, debt.status)
        assertEquals(1_000_000L, debt.valueMinor)
        assertTrue(a.notComputed.isEmpty() && a.blockers.isEmpty())
        assertEquals(ZakatAuthority.ZATCA, zakatRule(ZakatCountry.SA, ring.topic!!).source.authority)
    }

    @Test
    fun `مصر — نفس الوقائع على دار الإفتاء ⇒ الديون ليك والجمعية ما بيتحسبوش`() {
        val a = assess(ZakatCountry.EG, example())
        assertEquals(2_231_250L, a.nisabMinor, "85 جم عيار 21 × 300")
        assertEquals(ZakatOutcome.DUE, a.outcome)
        assertEquals(6_625_000L, a.totalZakatableMinor)
        assertEquals(165_625L, a.dueMinor)
        assertEquals(listOf(ZakatLineKind.CASH, ZakatLineKind.GOLD), a.lines.map { it.kind })
        assertEquals(setOf("o-1", "r-1"), a.notComputed.map { it.holding.id }.toSet())
        assertTrue(a.notComputed.all { it.status == ZakatItemStatus.NO_RULING && it.zakatableMinor == null })
        assertEquals(TextKey.ZAKAT_RULE_NO_RULING, zakatRule(ZakatCountry.EG, ZakatTopic.RECEIVABLE_STRONG).rulingKey)
        // الأسهم طويلة الأجل: «الأرباح بس» ⇒ القيمة نفسها صفر
        val shares = a.items.first { it.holding.id == "a-shares" }
        assertEquals(ZakatItemStatus.EXEMPT to TextKey.ZAKAT_RULE_LONG_TERM_DIVIDENDS, shares.status to zakatRule(ZakatCountry.EG, shares.topic!!).rulingKey)
        // الدين عليك: أحدث فتوى 15532 (2020)
        val source = zakatRule(ZakatCountry.EG, ZakatTopic.DEBTS_OWED).source
        assertEquals("15532" to "2020", source.fatwaNumber to source.issued)
        assertEquals(ZakatItemStatus.NOT_DEDUCTED, a.items.first { it.holding.id == "o-2" }.status)
    }

    @Test
    fun `الواقعة بتغيّر السطر — مشكوك فيه ما بيتحسبش ومضاربة بتتحسب`() {
        val doubtful = assess(ZakatCountry.SA, example(ZakatCollectability.DOUBTFUL))
        assertEquals(6_825_000L, doubtful.totalZakatableMinor)
        assertFalse(doubtful.lines.any { it.kind == ZakatLineKind.RECEIVABLES })
        val trading = example().map { if (it is ZakatHolding.Security) it.copy(holding = ZakatShareHolding.TRADING) else it }
        assertEquals(9_125_000L, assess(ZakatCountry.SA, trading).totalZakatableMinor)
    }

    @Test
    fun `تحت النصاب ⇒ صفر بسببه مش غير متاح`() {
        val a = assess(ZakatCountry.SA, listOf(ZakatHolding.Cash("w-1", "كاش", 200_000)))
        assertEquals(ZakatOutcome.BELOW_NISAB, a.outcome)
        assertEquals(0L, a.dueMinor)
        assertEquals(listOf(ZakatLine(ZakatLineKind.CASH, 200_000, 0)), a.lines)
        // بالظبط على النصاب ⇒ عليه
        assertEquals(ZakatOutcome.DUE, assess(ZakatCountry.SA, listOf(ZakatHolding.Cash("w-1", "كاش", 208_250))).outcome)
    }

    @Test
    fun `الحول بدأ من جديد ⇒ مفيش مطلوب`() {
        val a = assess(ZakatCountry.SA, example(), hawl = HawlState.Restarted("2025-06-01"))
        assertEquals(ZakatOutcome.HAWL_RESTARTED, a.outcome)
        assertEquals(0L, a.dueMinor)
        assertTrue(a.lines.all { it.dueMinor == 0L })
    }

    @Test
    fun `العملات الرقمية ما بتتحسبش في البلدين والنوع التاني مش داخل`() {
        val holdings = listOf(
            ZakatHolding.Cash("w-1", "بنك", 4_000_000),
            ZakatHolding.Digital("a-coin", "عملة وهمية", 900_000),
            ZakatHolding.Other("a-x", "حاجة وهمية", 50_000),
        )
        for (country in ZakatCountry.entries) {
            val a = assess(country, holdings)
            val coin = a.items.first { it.holding.id == "a-coin" }
            assertEquals(ZakatItemStatus.NO_RULING, coin.status, country.name)
            assertEquals(900_000L to null, coin.valueMinor to coin.zakatableMinor)
            assertEquals(ZakatItemStatus.NOT_COVERED, a.items.first { it.holding.id == "a-x" }.status)
            assertEquals(4_000_000L, a.totalZakatableMinor, "الرقمي ما اتضافش")
            assertEquals(listOf(ZakatLineKind.CASH), a.lines.map { it.kind })
        }
    }

    @Test
    fun `واقعة ناقصة ⇒ السطر غير متاح والإجمالي غير متاح مش صفر`() {
        val noPurpose = example().map { if (it.id == "a-gold") (it as ZakatHolding.Metal).copy(purpose = null) else it }
        val a = assess(ZakatCountry.SA, noPurpose)
        assertEquals(ZakatOutcome.PARTIAL, a.outcome)
        assertNull(a.dueMinor)
        assertNull(a.totalZakatableMinor)
        assertEquals("purpose", a.blockers.single().missingFact)
        assertEquals(ZakatLine(ZakatLineKind.GOLD, null, null), a.lines.first { it.kind == ZakatLineKind.GOLD })
        assertEquals(100_000L, a.lines.first { it.kind == ZakatLineKind.CASH }.dueMinor, "المعروف ليه مطلوبه")
        // تحت النصاب وفيه مجهول ممكن يعدّيه ⇒ غير متاح
        val small = listOf(ZakatHolding.Cash("w-1", "كاش", 1_000), ZakatHolding.Receivable("o-1", "شخص", 900_000, null))
        val b = assess(ZakatCountry.SA, small)
        assertEquals(ZakatOutcome.UNAVAILABLE, b.outcome)
        assertNull(b.dueMinor)
        assertEquals("collectability", b.blockers.single().missingFact)
    }

    @Test
    fun `سعر ناقص ⇒ النصاب غير متاح ومفيش صفر مؤكد`() {
        assertNull(nisabMinor(ZakatCountry.SA, ZakatPrices(30_000, null)), "الأقل من الاتنين محتاج الاتنين")
        val egNoGold = assess(ZakatCountry.EG, listOf(ZakatHolding.Cash("w-1", "بنك", 4_000_000)), ZakatPrices(null, null))
        assertEquals(ZakatOutcome.UNAVAILABLE, egNoGold.outcome)
        assertNull(egNoGold.dueMinor)
        assertNull(egNoGold.lines.single().dueMinor)
        // دهب ادخار من غير عيار ومن غير سعر ليه ⇒ الواقعة الناقصة هي العيار
        val noKarat = ZakatHolding.Metal("a-1", "دهب", ZakatMetal.GOLD, g(10), null, null, ZakatPurpose.SAVING, null)
        assertEquals("karat", zakatItems(ZakatCountry.SA, listOf(noKarat), prices).single().missingFact)
        // من غير السعر الصافي ⇒ سعر الأصل نفسه (للجرام بعياره)
        val own = noKarat.copy(ownUnitPriceMinor = 26_250)
        assertEquals(262_500L, zakatItems(ZakatCountry.SA, listOf(own), ZakatPrices(null, 350)).single().zakatableMinor)
        assertEquals(ZakatItemStatus.NO_VALUE, zakatItems(ZakatCountry.SA, listOf(noKarat.copy(karat = 21)), ZakatPrices(null, 350)).single().status)
    }

    @Test
    fun `الوزن × العيار × السعر حساب صحيح بتقريب واحد`() {
        // 1 جم عيار 18 × 333.33 = 249.9975 ⇒ 250.00
        assertEquals(25_000L, valueOfPureQuantity(g(1), 18, 24, 33_333))
        // 10 جم فضة 925 × 3.50 = 32.375 ⇒ 32.38
        assertEquals(3_238L, metalValueMinor(ZakatHolding.Metal("s", "فضة", ZakatMetal.SILVER, g(10), null, 925, ZakatPurpose.SAVING, null), prices))
        // نص جرام عيار 21 × 300 = 131.25 بالظبط
        assertEquals(13_125L, valueOfPureQuantity(QUANTITY_SCALE / 2, 21, 24, 30_000))
        // 2.5% على السطر: 0.025 ⇒ 0 · 0.5 ⇒ 1 (نص لفوق) · 0.475 ⇒ 0
        assertEquals(listOf(0L, 1L, 0L), listOf(zakatDueOf(1), zakatDueOf(20), zakatDueOf(19)))
        assertFailsWith<QuantityError> { valueOfPureQuantity(g(1), 25, 24, 30_000) }
    }

    @Test
    fun `الأسعار من الملف اليومي بعملة الحساب بس`() {
        val feed = PriceFeed(
            "2026-10-04T00:00:00Z", "SAR",
            listOf(FeedPrice("GOLD_24K_GRAM", "ذهب", "جرام", 30_000, "2026-10-04", "مصدر وهمي"), FeedPrice("SILVER_GRAM", "فضة", "جرام", 350, "2026-10-03", "مصدر وهمي")),
            emptyList(), emptyList(),
        )
        assertEquals(ZakatPrices(30_000, 350, "2026-10-03"), zakatPricesFromFeed(feed, Currency.SAR))
        assertEquals(ZakatPrices(null, null), zakatPricesFromFeed(feed, Currency.EGP), "مفيش تحويل بسعر صرف — الجنيه ناقص")
    }
}
