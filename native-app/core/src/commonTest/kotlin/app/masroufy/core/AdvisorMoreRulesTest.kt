package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** أفكار المساعد الثمانية التانية (OVERRIDES §68) — حدود التنبيه وعدمه، أرقام وأسامي مخترعة. */
class AdvisorMoreRulesTest {
    @AfterTest fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val period = Period("2026-09", "2026-09-28", "2026-10-27", 30)
    private val day6 = "2026-10-03"

    private fun unusual(amounts: List<Long>, usual: Long?, today: String = day6) =
        unusualSpendCandidate("cat-shop", "التسوق", readPace(amounts, usual, today, period), usual, period, Currency.SAR)

    @Test fun unusualBoundariesAndTheRateRules() {
        // معتاد 950؛ يوم 6 ⇒ المتوقع = المجموع × 5؛ 150% = 1,425
        val c = assertNotNull(unusual(listOf(9_500, 9_500, 9_500), 95_000), "1,425 بالظبط")
        assertEquals("unusual|2026-09-28|cat-shop", c.threadKey)
        assertEquals(uiText(TextKey.ADVISOR_UNUSUAL_BODY, "التسوق", formatMoney(28_500), uiText(TextKey.ADVISOR_RATIO_ABOVE), formatMoney(95_000)), c.body)
        assertNull(unusual(listOf(9_500, 9_500, 9_499), 95_000), "1,424.95 ⇒ مش أعلى بوضوح")
        assertNull(unusual(listOf(150_000), 95_000), "خبطة واحدة ⇒ مش معدل")
        assertNull(unusual(listOf(20_000, 20_000), 95_000), "مرتين بس")
        assertNull(unusual(listOf(9_500, 9_500, 9_500), null), "المعتاد مش معروف")
        assertNull(unusual(listOf(9_500, 9_500, 9_500), 0))
        val double = assertNotNull(unusual(listOf(19_000, 19_000, 19_000), 95_000))
        assertTrue(double.body.contains(uiText(TextKey.ADVISOR_RATIO_TRIPLE)), "2,850 = 3 أضعاف")
        assertTrue(assertNotNull(unusual(listOf(13_000, 13_000, 12_000), 95_000)).body.contains(uiText(TextKey.ADVISOR_RATIO_DOUBLE)))
    }

    private fun projection(onHand: Long?, mode: LeftoverMode = LeftoverMode.UNTIL_MONTH_END, until: String? = "2026-10-28") =
        LeftoverProjection(mode, onHand, 0, 0, onHand, false, until, 0)

    private fun before(onHand: Long?, dues: Long, usual: Long?, today: String = "2026-10-25", mode: LeftoverMode = LeftoverMode.UNTIL_MONTH_END) =
        beforePaydayCandidate(projection(onHand, mode, if (mode == LeftoverMode.FROM_WHAT_YOU_HAVE) null else "2026-10-28"), dues, usual, 30, today, Currency.SAR)

    @Test fun beforePaydayBoundaries() {
        val c = assertNotNull(before(18_000, 100_000, null))
        assertEquals("الراتب بعد 3 يومًا ومعك 180.00 ر.س — وعليك 1,000.00 ر.س مستحقات قبله", c.body)
        assertEquals("beforepay|2026-10-28", c.threadKey)
        assertNotNull(before(18_000, 100_000, null, today = "2026-10-23"), "5 أيام")
        assertNull(before(18_000, 100_000, null, today = "2026-10-22"), "6 أيام ⇒ بدري")
        assertNull(before(18_000, 100_000, null, today = "2026-10-28"), "يوم الراتب نفسه")
        assertNull(before(18_000, 18_000, null), "معاك قد المستحقات بالظبط")
        assertNotNull(before(18_000, 18_001, null))
        // معتاد الشهر 3,000 ⇒ 3 أيام = 300
        assertNotNull(before(29_999, 0, 300_000))
        assertNull(before(30_000, 0, 300_000))
        assertNull(before(null, 100_000, 300_000), "رصيد مش معروف")
        assertNull(before(10, 0, null), "مفيش حاجة معروفة نقارن بيها")
        assertNull(before(10, 100_000, null, mode = LeftoverMode.FROM_WHAT_YOU_HAVE), "من غير مرتب")
        assertTrue(before(10, 100_000, null, mode = LeftoverMode.UNTIL_NEXT_PAY)!!.body.startsWith("القبض القادم"))
    }

    private val summer = SavingsGoal("g-1", "سفر الصيف", 500_000, Currency.SAR, "2026-01-01", "2026-12-31", createdAt = "c", updatedAt = "c")

    @Test fun payFirstOnPaydayOnly() {
        val p = goalProgress(summer, 125_000, 0, "2026-10-01")
        assertEquals(125_000L, p.requiredPerMonthMinor)
        val c = assertNotNull(payFirstCandidate("2026-09-28", p, "2026-10-01", period, Currency.SAR))
        assertEquals("نزل الراتب — حوّل 1,250.00 ر.س لخطة «سفر الصيف» قبل أن تصرف", c.body)
        assertNull(payFirstCandidate("2026-09-28", p, "2026-10-02", period, Currency.SAR), "بعد 4 أيام")
        assertNull(payFirstCandidate(null, p, "2026-10-01", period, Currency.SAR), "المرتب ما نزلش")
        assertNull(payFirstCandidate("2026-09-27", p, "2026-09-28", period, Currency.SAR), "مرتب الفترة اللي فاتت")
        assertNull(payFirstCandidate("2026-09-28", p.copy(goal = summer.copy(archived = true)), "2026-10-01", period, Currency.SAR))
        assertNull(payFirstCandidate("2026-09-28", goalProgress(summer, null, 0, "2026-10-01"), "2026-10-01", period, Currency.SAR), "المطلوب مش معروف")
        assertNull(payFirstCandidate("2026-09-28", goalProgress(summer, 500_000, 0, "2026-10-01"), "2026-10-01", period, Currency.SAR), "اكتملت")
        assertNull(payFirstCandidate("2026-09-28", p, "2026-10-01", period, Currency.EGP), "عملة تانية")
    }

    /** §75-3: الراتب اللي بيتحسب للشهر الجديد ([countedIn]) — التنبيه يوم نزوله، وموضوعه شهر حسابه. */
    @Test fun payFirstFollowsTheSalaryMonth() {
        val p = goalProgress(summer, 125_000, 0, "2026-10-01")
        val aug = buildPeriod(2026, 8, 28)
        assertEquals("payfirst|2026-09-28|g-1", payFirstCandidate("2026-09-26", p, "2026-09-26", aug, Currency.SAR, countedIn = period)?.threadKey, "نزل في أغسطس وبيتحسب لسبتمبر")
        assertEquals("payfirst|2026-09-28|g-1", payFirstCandidate("2026-09-27", p, "2026-09-28", period, Currency.SAR, countedIn = period)?.threadKey)
        assertNull(payFirstCandidate("2026-08-26", p, "2026-08-28", period, Currency.SAR, countedIn = aug), "راتب فترة فاتت")
    }

    private val bill = RecurringItem("r-1", "فاتورة الكهرباء", "id:m-1", "bill", 1, 41_000, Currency.SAR, "2026-11-01", true, true)

    @Test fun billJumpComparesOnePaymentWithItsUsual() {
        val c = assertNotNull(billJumpCandidate(bill, "t-9", 62_000, listOf(41_000, 41_000, 40_000)))
        assertEquals("فاتورة الكهرباء 620.00 ر.س — أعلى من المعتاد (410.00 ر.س)", c.body)
        assertNotNull(billJumpCandidate(bill, "t-9", 51_250, listOf(30_000, 41_000, 41_000, 40_000)), "125% بالظبط من آخر 3")
        assertNull(billJumpCandidate(bill, "t-9", 51_249, listOf(41_000, 41_000, 40_000)))
        assertNull(billJumpCandidate(bill, "t-9", 90_000, listOf(41_000, 40_000)), "دفعتين بس قبلها ⇒ المعتاد مش معروف")
    }

    @Test fun dupSubsGroupsByCategory() {
        fun sub(id: String) = bill.copy(id = id, name = "اشتراك $id", kind = "subscription")
        val two = dupSubsCandidates(listOf(SubscriptionPlace(sub("b"), "cat-tv", "بث وأفلام"), SubscriptionPlace(sub("a"), "cat-tv", "بث وأفلام"), SubscriptionPlace(sub("c"), "cat-music", "موسيقى")))
        val c = two.single()
        assertEquals("dupsubs|cat-tv|a,b", c.threadKey)
        assertEquals("لديك أكثر من اشتراك في «بث وأفلام»", c.title)
        assertEquals(emptyList(), dupSubsCandidates(listOf(SubscriptionPlace(sub("a"), "cat-tv", "بث وأفلام"))))
    }

    @Test fun bigOneIsAShareOfTheUsualMonth() {
        val c = assertNotNull(bigOneCandidate("t-1", 230_000, "2026-10-01", 920_000, Currency.SAR))
        assertEquals("عملية 2,300.00 ر.س — ربع مصروف شهرك أو أكثر", c.body)
        assertTrue(bigOneCandidate("t-1", 184_000, "2026-10-01", 920_000, Currency.SAR)!!.body.endsWith(uiText(TextKey.ADVISOR_SHARE_FIFTH)), "20% بالظبط")
        assertNull(bigOneCandidate("t-1", 183_999, "2026-10-01", 920_000, Currency.SAR))
        assertNull(bigOneCandidate("t-1", 900_000, "2026-10-01", null, Currency.SAR), "المعتاد مش معروف")
        assertTrue(bigOneCandidate("t-1", 920_000, "2026-10-01", 920_000, Currency.SAR)!!.body.endsWith(uiText(TextKey.ADVISOR_SHARE_FULL)))
    }

    @Test fun goalNearEncouragesTwice() {
        val near = assertNotNull(goalNearCandidate(goalProgress(summer, 450_000, 0, "2026-10-01")))
        assertEquals("goalnear|g-1|near", near.threadKey)
        assertEquals("جمعت 4,500.00 ر.س من 5,000.00 ر.س — بقي 500.00 ر.س", near.body)
        assertNull(goalNearCandidate(goalProgress(summer, 449_999, 0, "2026-10-01")))
        assertEquals("goalnear|g-1|reached", goalNearCandidate(goalProgress(summer, 500_000, 0, "2026-10-01"))?.threadKey)
        assertNull(goalNearCandidate(goalProgress(summer.copy(archived = true), 480_000, 0, "2026-10-01")))
        assertNull(goalNearCandidate(goalProgress(summer, null, 0, "2026-10-01")))
    }

    @Test fun weeklySummary() {
        assertEquals("2026-10-03", lastWeekEnd("2026-10-05"), "الاتنين ⇒ السبت اللي فات")
        assertEquals("2026-10-03", lastWeekEnd("2026-10-04"))
        assertEquals("2026-09-26", lastWeekEnd("2026-10-03"), "السبت نفسه لسه ما خلصش")
        val names = mapOf("cat-a" to "مطاعم", "cat-b" to "بقالة")
        val now = WeekSpend(50_000, mapOf("cat-a" to 30_000L, "cat-b" to 20_000L, null to 5_000L), 4)
        val c = assertNotNull(weeklySummaryCandidate("2026-10-03", now, WeekSpend(40_000, emptyMap(), 2), names, Currency.SAR))
        assertEquals("صرفت 500.00 ر.س هذا الأسبوع · أكثر بـ100.00 ر.س من الأسبوع الماضي · الأعلى: «مطاعم» (300.00 ر.س)", c.body)
        assertEquals("weekly|2026-10-03", c.threadKey)
        assertTrue(weeklySummaryCandidate("w", now, WeekSpend(60_000, emptyMap(), 1), names, Currency.SAR)!!.body.contains("أقل بـ100.00"))
        assertTrue(weeklySummaryCandidate("w", now, WeekSpend(50_000, emptyMap(), 1), names, Currency.SAR)!!.body.contains(uiText(TextKey.ADVISOR_WEEKLY_SAME)))
        assertTrue(!weeklySummaryCandidate("w", now, null, names, Currency.SAR)!!.body.contains("الماضي"), "من غير مقارنة لو الأسبوع اللي فات مش معروف")
        assertNull(weeklySummaryCandidate("w", null, now, names, Currency.SAR), "الأسبوع ده مش معروف")
        assertNull(weeklySummaryCandidate("w", WeekSpend(0, emptyMap(), 0), WeekSpend(0, emptyMap(), 0), names, Currency.SAR), "مفيش بيانات")
    }

    @Test fun deliveryClassesForTheEight() {
        val noon = LocalMoment("2026-10-05", 12)
        fun d(kind: AlertKind) = decideAlert(AlertCandidate(kind, "t", "x", "y"), KindStats(), UsualHours(List(24) { if (it == 21) 6 else 0 }), noon).delivery
        for (k in listOf(AlertKind.UNUSUAL_SPEND, AlertKind.BILL_JUMP, AlertKind.DUP_SUBS, AlertKind.BIG_ONE, AlertKind.GOAL_NEAR)) assertEquals(AlertDelivery.DIGEST, d(k), k.wire)
        for (k in listOf(AlertKind.BEFORE_PAYDAY, AlertKind.PAY_FIRST, AlertKind.WEEKLY_SUMMARY)) assertEquals(AlertDelivery.AT_USUAL_TIME, d(k), k.wire)
        assertTrue(AlertKind.entries.filter { it.group == AlertGroup.ADVISOR }.size == 11, "مجموعة واحدة للـ11 نوع (خطة الادخار مش تنبيه)")
    }
}
