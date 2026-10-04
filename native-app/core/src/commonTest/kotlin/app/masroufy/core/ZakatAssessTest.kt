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
        ZakatHolding.Security("a-shares", "سهم وهمي", ZakatLineKind.STOCKS, 2_000_000, ZakatShareHolding.LONG_TERM, saudiCompany = true),
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
    fun `مصر — نفس الوقائع على دار الإفتاء ⇒ الديون ليك عند التحصيل والجمعية ما حددش`() {
        val a = assess(ZakatCountry.EG, example())
        assertEquals(2_231_250L, a.nisabMinor, "85 جم عيار 21 × 300")
        assertEquals(ZakatOutcome.DUE, a.outcome)
        assertEquals(6_625_000L, a.totalZakatableMinor)
        assertEquals(165_625L, a.dueMinor)
        assertEquals(listOf(ZakatLineKind.CASH, ZakatLineKind.GOLD), a.lines.map { it.kind })
        assertEquals(setOf("r-1"), a.notComputed.map { it.holding.id }.toSet())
        assertTrue(a.notComputed.all { it.status == ZakatItemStatus.NO_RULING && it.zakatableMinor == null })
        // الدين ليك (4399): ما بيدخلش الحساب السنوي — بيتزكّى لما يتحصّل، ومن غير سؤال «هيرجع؟»
        val owed = a.items.first { it.holding.id == "o-1" }
        assertEquals(ZakatItemStatus.EXEMPT to 0L, owed.status to owed.zakatableMinor)
        assertEquals(TextKey.ZAKAT_RULE_RECEIVABLE_ON_COLLECTION, zakatRule(ZakatCountry.EG, owed.topic!!).rulingKey)
        assertEquals(owed.status, assess(ZakatCountry.EG, example(null)).items.first { it.holding.id == "o-1" }.status, "مصر ما بتسألش الواقعة")
        val debtSource = zakatRule(ZakatCountry.EG, ZakatTopic.RECEIVABLE_STRONG).source
        assertEquals(Triple("4399", "2002", "https://www.dar-alifta.org/ar/fatwa/details/14460"), Triple(debtSource.fatwaNumber, debtSource.issued, debtSource.url))
        // الأسهم طويلة الأجل: «الأرباح بس» ⇒ القيمة نفسها صفر
        val shares = a.items.first { it.holding.id == "a-shares" }
        assertEquals(ZakatItemStatus.EXEMPT to TextKey.ZAKAT_RULE_LONG_TERM_DIVIDENDS, shares.status to zakatRule(ZakatCountry.EG, shares.topic!!).rulingKey)
        // أحدث فتوى في الأسهم 8767 (2025)، وصفحتها على موقع الدار 22181
        val sharesSource = zakatRule(ZakatCountry.EG, ZakatTopic.TRADING_SHARES).source
        assertEquals(Triple("8767", "2025", "https://www.dar-alifta.org/ar/fatwa/details/22181"), Triple(sharesSource.fatwaNumber, sharesSource.issued, sharesSource.url))
        assertEquals(sharesSource, zakatRule(ZakatCountry.EG, ZakatTopic.LONG_TERM_SHARES).source)
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

    @Test
    fun `السعودية — سهم طويل لشركة مش سعودية ما حددش ومن غير الواقعة ناقص · ومصر ما بتسألش`() {
        val foreign = example().map { if (it is ZakatHolding.Security) it.copy(saudiCompany = false) else it }
        val a = assess(ZakatCountry.SA, foreign)
        val shares = a.items.first { it.holding.id == "a-shares" }
        assertEquals(ZakatItemStatus.NO_RULING to ZakatTopic.FOREIGN_LONG_TERM_SHARES, shares.status to shares.topic)
        assertEquals(2_000_000L to null, shares.valueMinor to shares.zakatableMinor, "بيتعرض بقيمته ومش محسوب")
        assertEquals(178_125L, a.dueMinor, "الباقي زي ما هو")
        assertEquals(ZakatEffect.NO_RULING, zakatRule(ZakatCountry.SA, ZakatTopic.FOREIGN_LONG_TERM_SHARES).effect)
        val unknown = example().map { if (it is ZakatHolding.Security) it.copy(saudiCompany = null) else it }
        assertEquals("saudiCompany", assess(ZakatCountry.SA, unknown).blockers.single().missingFact)
        for (company in listOf(true, false, null)) {
            val eg = assess(ZakatCountry.EG, example().map { if (it is ZakatHolding.Security) it.copy(saudiCompany = company) else it })
            assertEquals(ZakatTopic.LONG_TERM_SHARES, eg.items.first { it.holding.id == "a-shares" }.topic, "مصر: الجنسية ما بتفرقش (8767)")
        }
        // المضاربة: القيمة السوقية مهما كانت الجنسية
        val trading = foreign.map { if (it is ZakatHolding.Security) it.copy(holding = ZakatShareHolding.TRADING) else it }
        assertEquals(ZakatItemStatus.COUNTED, assess(ZakatCountry.SA, trading).items.first { it.holding.id == "a-shares" }.status)
    }

    @Test
    fun `الأمانة في البلدين ما حددش — بتظهر لوحدها والكاش ما بيتخصمش منه`() {
        for (country in ZakatCountry.entries) {
            val a = assess(country, listOf(ZakatHolding.Cash("w-1", "بنك", 4_000_000), ZakatHolding.Custody("o-9", "شخص وهمي", 500_000)))
            val custody = a.items.first { it.holding.id == "o-9" }
            assertEquals(ZakatItemStatus.NO_RULING to ZakatTopic.CUSTODY, custody.status to custody.topic, country.name)
            assertEquals(500_000L, custody.valueMinor)
            assertEquals(listOf("o-9"), a.notComputed.map { it.holding.id })
            assertEquals(4_000_000L, a.totalZakatableMinor, "الكاش كله زي ما هو")
            assertEquals(TextKey.ZAKAT_RULE_NO_RULING, zakatRule(country, ZakatTopic.CUSTODY).rulingKey)
        }
    }

    @Test
    fun `الدين اللي اتحصّل — مصر والمشكوك فيه في السعودية سطر مرة واحدة · اللي هيرجع اتزكّى في سنينه`() {
        fun collected(c: ZakatCollectability?) = ZakatHolding.CollectedReceivable("s-1", "o-1", "شخص وهمي", 1_000_000, "2025-09-01", c)
        val cash = ZakatHolding.Cash("w-1", "بنك", 4_000_000)
        val eg = assess(ZakatCountry.EG, listOf(cash, collected(null)))
        assertEquals(ZakatLine(ZakatLineKind.COLLECTED_RECEIVABLES, 1_000_000, 25_000), eg.lines.single { it.kind == ZakatLineKind.COLLECTED_RECEIVABLES })
        assertEquals(125_000L, eg.dueMinor, "100,000 كاش + 25,000 مرة واحدة على اللي اتحصّل")
        assertEquals(ZakatTopic.RECEIVABLE_COLLECTED, eg.items.last().topic)
        assertEquals("4399", zakatRule(ZakatCountry.EG, ZakatTopic.RECEIVABLE_COLLECTED).source.fatwaNumber)
        val doubtful = assess(ZakatCountry.SA, listOf(cash, collected(ZakatCollectability.DOUBTFUL)))
        assertEquals(25_000L, doubtful.lines.single { it.kind == ZakatLineKind.COLLECTED_RECEIVABLES }.dueMinor, "§3.4: بعد ما يتحصّل سنة واحدة")
        val strong = assess(ZakatCountry.SA, listOf(cash, collected(ZakatCollectability.STRONG)))
        assertTrue(strong.items.none { it.holding.id == "s-1" }, "هيرجع ⇒ كان بيتحسب كل سنة ⇒ مفيش سطر جديد")
        assertEquals(100_000L, strong.dueMinor)
        assertEquals("collectability", assess(ZakatCountry.SA, listOf(cash, collected(null))).blockers.single().missingFact)
        // اتحصّل وكل اللي معاك تحت النصاب ⇒ زي أي سطر: مفيش مطلوب
        assertEquals(ZakatOutcome.BELOW_NISAB, assess(ZakatCountry.EG, listOf(ZakatHolding.Cash("w-1", "بنك", 1_000), collected(null).copy(amountMinor = 10_000))).outcome)
        // الدين اللي اتحصّل فلوسه في المحفظة أصلًا ⇒ ما بيتضافش على سلسلة الحول
        val bank = Wallet("w-1", "بنك", Currency.SAR, "bank", 4_000_000, "2025-01-01")
        assertEquals(4_000_000L, balanceOn(zakatWealthSeries(listOf(bank), emptyList(), eg.items), "2025-12-01"))
    }

    @Test
    fun `مصادر البحث التكميلي جنب كل سطر — بنود الدليل وأرقام الفتاوى`() {
        assertEquals("3.4" to "19–20", zakatRule(ZakatCountry.SA, ZakatTopic.RECEIVABLE_STRONG).source.let { it.section to it.pages })
        assertEquals("3.2.1" to "18", zakatRule(ZakatCountry.SA, ZakatTopic.MID_YEAR_DIP).source.let { it.section to it.pages })
        assertEquals("3.6" to "22–23", zakatRule(ZakatCountry.SA, ZakatTopic.LONG_TERM_SHARES).source.let { it.section to it.pages })
        assertEquals(null to "14, 19, 27", zakatRule(ZakatCountry.SA, ZakatTopic.HAWL).source.let { it.section to it.pages })
        assertTrue(zakatRule(ZakatCountry.SA, ZakatTopic.RECEIVABLE_DOUBTFUL).source.document.contains("3.4"))
        assertEquals("5890" to "1985", zakatRule(ZakatCountry.EG, ZakatTopic.MID_YEAR_DIP).source.let { it.fatwaNumber to it.issued })
        assertEquals("413" to "2008", zakatRule(ZakatCountry.EG, ZakatTopic.HAWL).source.let { it.fatwaNumber to it.issued })
        assertEquals(TextKey.ZAKAT_RULE_DIP_START_END, zakatRule(ZakatCountry.EG, ZakatTopic.MID_YEAR_DIP).rulingKey)
        assertTrue(ZAKAT_RULES.filter { it.topic in listOf(ZakatTopic.MID_YEAR_DIP, ZakatTopic.RECEIVABLE_STRONG, ZakatTopic.RECEIVABLE_DOUBTFUL, ZakatTopic.HAWL) }.none { it.source.pending }, "اتقفلوا بالبحث")
        assertEquals("فتوى رقم 4399", zakatRule(ZakatCountry.EG, ZakatTopic.RECEIVABLE_COLLECTED).source.document)
    }
}
