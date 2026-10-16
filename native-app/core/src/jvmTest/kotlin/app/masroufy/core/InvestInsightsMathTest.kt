package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** حزمة INSIGHTS (مواصفة النقل §2.7): النصيحة · الحبل · اللي رجعلك · «استثماراتك النهارده» — بيانات مخترعة بس، وأعداد صحيحة بس. */
class InvestInsightsMathTest {
    private fun facts(cash: Long? = null, spend: Long? = null, split: InvestableSplit? = null, save: Long? = null, bought: Boolean = false, returns: Long = 0) =
        TipFacts(cash, spend, split, save, bought, returns)

    private fun split(p: Long = 0, g: Long = 0, s: Long = 0, n: Long = 0) = InvestableSplit(p, g, s, n, 0)

    @Test
    fun situationsFollowOrderAndThresholds() {
        assertEquals(
            listOf(TipSituation.EMERGENCY, TipSituation.CONCENTRATED, TipSituation.SAVES_NOT_INVESTING, TipSituation.HAS_RETURNS),
            situationsOf(facts(cash = 29_999, spend = 10_000, split = split(g = 71, s = 29), save = 5_000, returns = 1)),
        )
        // الحد بالظبط مش حالة: 3 أضعاف الصرف · 70% بالظبط
        assertEquals(emptyList(), situationsOf(facts(cash = 30_000, spend = 10_000, split = split(g = 70, s = 30))))
        // الناقص مش صفر: الرصيد أو الصرف مش معروف ⇒ مفيش «طوارئ»
        assertEquals(emptyList(), situationsOf(facts(cash = null, spend = 10_000)))
        assertEquals(emptyList(), situationsOf(facts(cash = 0, spend = null)))
        // بيحوّش بس اشترى أصل في آخر 3 شهور ⇒ مش «مش بيستثمر» · التحويش مش معروف ⇒ لأ
        assertEquals(emptyList(), situationsOf(facts(save = 5_000, bought = true)))
        assertEquals(emptyList(), situationsOf(facts(save = null)))
        // «حاجاتي» فاضية (صفر) ⇒ مش «كله في نوع واحد»
        assertEquals(emptyList(), situationsOf(facts(split = split())))
    }

    @Test
    fun tipQueuePutsSituationTipsFirstAndClosedNeverReturn() {
        // البنك الموثّق بس: كل نصيحة ليها مصدر برابط وتاريخ تأكد، ومن غير تكرار
        assertTrue(VERIFIED_TIPS.isNotEmpty() && VERIFIED_TIPS.all { it.source.url.startsWith("https://") && it.source.checkedAt.isNotBlank() })
        assertEquals(VERIFIED_TIPS.size, VERIFIED_TIPS.map { it.id }.toSet().size)
        assertTrue(SITUATION_TIPS.values.flatten().all { id -> VERIFIED_TIPS.any { it.id == id } })
        val q = tipQueue(listOf(TipSituation.HAS_RETURNS, TipSituation.SAVES_NOT_INVESTING, TipSituation.EMERGENCY), emptySet(), "SA")
        // «فيه أرباح» مالهاش نصيحة موثّقة ⇒ اللي بعدها بالترتيب اللي اتقال
        assertEquals(
            listOf("small-regular-amounts-over-time", "match-money-to-horizon", "idle-cash-loses-value", "emergency-fund-first", "emergency-fund-habit"),
            q.take(5).map { it.tip.id },
        )
        assertEquals(TipSituation.SAVES_NOT_INVESTING, q.first().situation)
        assertEquals(TipSituation.EMERGENCY, q[4].situation)
        assertNull(q[5].situation) // الباقي عام ⇒ «نصيحة»
        assertEquals(q.size, q.map { it.tip.id }.toSet().size) // نصيحة الحالة ما بتتكررش في الباقي
        assertEquals("match-money-to-horizon", tipQueue(listOf(TipSituation.SAVES_NOT_INVESTING), setOf("small-regular-amounts-over-time"), "SA").first().tip.id)
        // نصايح السعودية بس ما بتظهرش في مصر والعكس
        assertTrue(tipQueue(emptyList(), emptySet(), "EG").none { it.tip.country == "sa" })
        assertTrue(tipQueue(emptyList(), emptySet(), "SA").none { it.tip.country == "eg" })
        assertTrue(tipQueue(TipSituation.entries, VERIFIED_TIPS.map { it.id }.toSet(), "SA").isEmpty())
        assertEquals(listOf("a", "x"), closeTip(closeTip(listOf("a"), "x"), "x"))
        val tip = VERIFIED_TIPS.first()
        assertEquals(tip.eg, tip.words(ArabicVariant.EGYPTIAN, Language.AR))
        assertEquals(tip.sa, tip.words(ArabicVariant.MSA, Language.AR))
        assertEquals(tip.sa, tip.words(ArabicVariant.EGYPTIAN, Language.EN))
    }

    private fun source(id: String, kind: IncomeSourceKind, start: String, end: String? = null, name: String = id) =
        IncomeSource(id, name, name, kind, Currency.SAR, start, end)

    @Test
    fun ropeMergesJobsTicksTheChangeAndClipsToTheWindow() {
        val today = "2026-10-16"
        val sources = listOf(
            source("j1", IncomeSourceKind.JOB, "2023-05-01", "2026-04-27", "شركة أ"),
            source("j2", IncomeSourceKind.JOB, "2026-04-28", null, "شركة ب"),
            source("p", IncomeSourceKind.PART_TIME, "2026-08-10"),
            source("c", IncomeSourceKind.CLIENT, "2026-01-01", "2026-06-30"),
            source("old", IncomeSourceKind.OTHER, "2024-01-01", "2025-03-01"),
        )
        val sales = listOf(RopeKnot("2026-03-04", "جوال قديم"), RopeKnot("2025-01-01", "برا الفترة"))
        val rope = assertNotNull(buildIncomeRope(sources, sales, today))
        assertEquals("2025-10-16", rope.windowStart)
        assertEquals(listOf(IncomeSourceKind.JOB, IncomeSourceKind.CLIENT, IncomeSourceKind.PART_TIME), rope.strands.map { it.kind })
        val salary = rope.strands.first()
        assertEquals("2025-10-16", salary.from) // مقصوص على الفترة
        assertEquals(today, salary.to)
        assertEquals("شركة ب", salary.name)
        assertEquals(listOf("شركة أ", "شركة ب"), salary.jobs.map { it.name })
        assertEquals(listOf("2026-04-28"), rope.ticks)
        assertEquals(2, rope.activeCount) // المرتب مرة واحدة + البارت تايم
        assertEquals("2026-06-30", rope.strands[1].to)
        assertEquals(listOf("جوال قديم"), rope.knots.map { it.label })
        assertNull(buildIncomeRope(emptyList(), sales, today))
        assertNull(buildIncomeRope(listOf(source("old", IncomeSourceKind.RENT, "2020-01-01", "2024-01-01")), emptyList(), today))
    }

    private var seq = 0

    private fun ret(date: String, amount: Long, cat: String?, currency: Currency = Currency.SAR, reversed: Boolean = false) = Transaction(
        id = "r-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.INVESTMENT_INCOME,
        economicKindConfirmed = true, observedDirection = Direction.IN, amountMinor = amount, currency = currency, categoryConfirmed = true,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", categoryId = cat,
        reversedById = if (reversed) "rev" else null,
    )

    @Test
    fun returnsGroupByCategoryAndRateZeroGivesPerTimes120() {
        val txns = listOf(
            ret("2026-01-15", 100_000, "rent"), ret("2026-03-15", 50_000, "rent"), ret("2026-09-02", 30_000, null),
            ret("2026-05-01", 99_999, "rent", reversed = true), ret("2026-05-01", 77_777, "rent", Currency.EGP), ret("2025-12-31", 55_555, "rent"),
        )
        val r = assertNotNull(investReturns(txns, Currency.SAR, "2026-10-16", mixBp = 0, cpiBp = 0))
        assertEquals(180_000, r.totalMinor)
        assertEquals(listOf(ReturnRow("rent", 150_000, listOf(1, 3)), ReturnRow(null, 30_000, listOf(9))), r.rows)
        val m = assertNotNull(r.math)
        assertEquals(18_000, m.perMonthMinor) // 180,000 ÷ 10 شهور (أكتوبر)
        assertEquals(18_000L * 120, m.reinvestMinor) // معدل صفر وغلاء صفر ⇒ نفس الفلوس
        assertEquals(18_000L * 120, m.spentMinor)
        // المتوسطات ناقصة ⇒ مفيش حساب (مش صفر)
        assertNull(investReturns(txns, Currency.SAR, "2026-10-16", null, 174)?.math)
        assertNull(investReturns(txns, Currency.SAR, "2026-10-16", 795, null)?.math)
        assertNull(investReturns(emptyList(), Currency.SAR, "2026-10-16", 795, 174))
    }

    @Test
    fun reinvestRoundsPerAndGrowsAboveSpent() {
        assertEquals(33, reinvest(100, 3, 0, 0)!!.perMonthMinor)
        assertEquals(51, reinvest(101, 2, 0, 0)!!.perMonthMinor) // النص لفوق
        val m = assertNotNull(reinvest(1_200_000, 12, 1_000, 174))
        assertTrue(m.reinvestMinor > m.spentMinor)
        assertTrue(m.spentMinor < 100_000L * 120) // خصم الغلاء
        assertEquals(1_000, m.mixBp)
    }

    private fun snap(date: String, now: Map<String, Long>, prevDate: String?, prev: Map<String, Long>) = UserSetting.InvestDaily(
        "sa", date, now.keys.toList(), now.values.toList(), prevDate, prev.keys.toList(), prev.values.toList(), "x",
    )

    @Test
    fun todayChangeCountsOnlyItemsInBothDays() {
        val c = assertNotNull(todayChange(snap("2026-10-16", mapOf("a" to 1_100, "b" to 2_000, "c" to 5_000), "2026-10-15", mapOf("a" to 1_000, "b" to 2_000, "d" to 9_000))))
        assertEquals(100, c.gainMinor) // «c» الجديدة و«d» المتشالة برّه
        assertEquals(333, c.pctBp) // 100 ÷ 3000
        assertEquals(-1_000, todayChange(snap("d", mapOf("a" to 900), "p", mapOf("a" to 1_000)))!!.pctBp)
        assertNull(todayChange(snap("d", mapOf("a" to 900), null, emptyMap())))
        assertNull(todayChange(snap("d", mapOf("a" to 900), "p", mapOf("a" to 0))))
        assertEquals(1, ratioBp(1, 20_000)) // نص نقطة ⇒ لبعيد عن الصفر
        assertEquals(-1, ratioBp(-1, 20_000))
    }

    @Test
    fun snapshotShiftsOnANewDayOnly() {
        val first = shiftSnapshot(null, "sa", "2026-10-15", mapOf("a" to 1_000), "t1")
        assertNull(first.prevDate)
        val sameDay = shiftSnapshot(first, "sa", "2026-10-15", mapOf("a" to 1_050), "t2")
        assertNull(sameDay.prevDate)
        assertEquals(listOf(1_050L), sameDay.values)
        val next = shiftSnapshot(sameDay, "sa", "2026-10-16", mapOf("a" to 1_100), "t3")
        assertEquals("2026-10-15", next.prevDate)
        assertEquals(listOf(1_050L), next.prevValues)
        assertEquals(476, todayChange(next)!!.pctBp) // 50 ÷ 1050 = 4.76%
        assertEquals("investDaily.sa", next.key)
    }

    private fun avg(key: String, bp: Int) = AverageEntry(key, bp, "2015", "2024", true, false, "src", null, "m")

    @Test
    fun holdingsMixIsAnExactWeightedFloor() {
        val rates = InvestRates(200, 1_000, 795, avg("CPI_SA", 200), avg("GOLD", 1_000), avg("STOCKS_SA", 795))
        assertEquals(600, holdingsMixBp(split(p = 100, g = 100), rates))
        assertEquals(733, holdingsMixBp(InvestableSplit(1, 2, 0, 50, 0), InvestRates(200, 1_000, 0, avg("a", 200), avg("b", 1_000), avg("c", 0))))
        assertNull(holdingsMixBp(split(n = 500), rates)) // اللي ما بيكبرش بس ⇒ مفيش متوسط
    }
}
