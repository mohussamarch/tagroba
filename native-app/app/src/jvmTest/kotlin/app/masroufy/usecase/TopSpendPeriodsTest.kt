package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * تصميم «أكتر ما صرفت عليه» (0.5.1): الفرق عن الشهر اللي فات (أعداد صحيحة، و«مش معروف» ⇒ مفيش رقم — مش 0%) · «آخر 3 شهور» بتجمع
 * الشهور ومداها من أول أقدمهم لآخر أحدثهم · شريط الحصص (أول 5 والباقي) · السرعة على 3,600 عملية. **بيانات مخترعة بس** (المستودع عام).
 */
class TopSpendPeriodsTest {
    private var seq = 0

    private fun txn(date: String, amount: Long, category: String?, kind: EconomicKind = EconomicKind.PURCHASE, confirmed: Boolean = true) = Transaction(
        id = "p-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = confirmed,
        observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = true, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", categoryId = category,
    )

    private fun category(i: Int) = Category("c-%02d".format(i), null, "تصنيف وهمي $i", "tag", "#336699", "#99BBDD", true, i)

    private fun useCase(txns: List<Transaction>, cats: List<Category>) =
        LoadTopCategories(LoadHomeScreen(LoadHomeScreenDeps(MemoryTransactionRepository(txns), MemoryCategoryRepository(cats), MemoryAllocationRepository())))

    private val cats = (0 until 8).map(::category)

    // الشهر المالي الحالي 28 سبتمبر – 27 أكتوبر 2026 (يوم الراتب 28)، واللي فات 28 أغسطس – 27 سبتمبر، واللي قبله 28 يوليو – 27 أغسطس
    private val now = listOf(
        txn("2026-10-01", 6_000, "c-00"), txn("2026-10-02", 5_400, "c-00"),
        txn("2026-10-03", 9_200, "c-01"),
        txn("2026-10-04", 5_000, "c-02"),
        txn("2026-10-05", 3_000, "c-03"),
        txn("2026-10-06", 2_000, null),
    )
    private val last = listOf(
        txn("2026-09-10", 10_000, "c-00"),
        txn("2026-09-11", 9_800, "c-01"),
        txn("2026-09-12", 3_000, "c-03"),
        txn("2026-09-13", 1_000, null),
    )
    private val before = listOf(txn("2026-08-01", 2_000, "c-00"), txn("2026-08-02", 7_000, "c-05"))

    @Test
    fun thisMonthComparesEachCategoryWithLastMonthInWholePercents() = runBlocking {
        val v = useCase(now + last + before, cats).loadAll("2026-10-10", 28)
        assertTrue(v.current)
        val change = v.rows.associate { it.categoryId to it.changePercent }
        assertEquals(14, change["c-00"], "11,400 من 10,000 = زاد 14%")
        assertEquals(-6, change["c-01"], "9,200 من 9,800 = قلّ 6.1% ⇒ 6")
        assertNull(change["c-02"], "ما كانش فيه صرف الشهر اللي فات ⇒ مفيش سهم (مش زيادة مزيفة)")
        assertEquals(0, change["c-03"], "نفس المبلغ ⇒ صفر محسوب (الشاشة ما بتعرضش سهم)")
        assertEquals(100, change[null], "«بلا تصنيف» بيتقارن برضه")
    }

    @Test
    fun lastMonthAndThreeMonthsHaveNoChange() = runBlocking {
        val top = useCase(now + last + before, cats)
        val lastMonth = top.loadAll("2026-10-10", 28, monthsBack = 1)
        val three = top.loadAll("2026-10-10", 28, monthsBack = 0, months = 3)
        assertFalse(lastMonth.current)
        assertFalse(three.current)
        assertTrue((lastMonth.rows + three.rows).all { it.changePercent == null }, "الفرق في «الشهر ده» بس (التصميم)")
    }

    @Test
    fun threeMonthsAddUpTheMonthsAndSpanFromTheOldestStartToTheNewestEnd() = runBlocking {
        val v = useCase(now + last + before, cats).loadAll("2026-10-10", 28, monthsBack = 0, months = 3)
        assertEquals("2026-07-28", v.period.start)
        assertEquals("2026-10-27", v.period.end)
        assertEquals(3, v.months)
        assertEquals(31 + 31 + 30, v.period.days)
        val c0 = v.rows.single { it.categoryId == "c-00" }
        assertEquals(6_000L + 5_400 + 10_000 + 2_000, c0.amountMinor)
        assertEquals(4, c0.count)
        assertEquals("c-00", v.rows.first().categoryId, "الأكبر الأول")
        val total = (now + last + before).sumOf { it.amountMinor }
        assertEquals(total, v.totalMinor)
        assertEquals((now + last + before).size, v.operationCount)
        assertEquals(listOf("c-00", "c-01", "c-05", "c-03", "c-02", null), v.rows.map { it.categoryId })
    }

    @Test
    fun anUnsureLastMonthGivesNoChangeInsteadOfAFakeOne() = runBlocking {
        val unsure = last + txn("2026-09-14", 4_000, null, kind = EconomicKind.UNCLASSIFIED, confirmed = false)
            .copy(suggestedKind = EconomicKind.INTERNAL_TRANSFER) // مستنية «نلغي الاتنين؟» ⇒ نوعها مش معروف
        val v = useCase(now + unsure, cats).loadAll("2026-10-10", 28)
        assertTrue(v.rows.all { it.changePercent == null }, "الشهر اللي فات فيه عمليات نوعها مش معروف ⇒ مفيش أسهم")
        val none = useCase(now, cats).loadAll("2026-10-10", 28)
        assertTrue(none.rows.all { it.changePercent == null }, "الشهر اللي فات فاضي ⇒ مفيش أسهم")
    }

    @Test
    fun theShareBarHasTheTopFiveAndTheRest() = runBlocking {
        val seven = (0 until 7).map { txn("2026-10-0${it + 1}", 1_000L * (7 - it), "c-%02d".format(it)) }
        val v = useCase(seven, cats).loadAll("2026-10-10", 28)
        assertEquals(TOP_SPEND_SHARE_SEGMENTS, v.shares.size)
        assertEquals(v.rows.take(5).map { it.categoryId }, v.shares.map { it.categoryId })
        assertEquals(107L, v.restTenthPercent, "(2,000 + 1,000) من 28,000 = 10.7%")
        assertTrue(abs(v.shares.sumOf { it.tenthPercent } + v.restTenthPercent - 1000) <= 6)
        val few = useCase(seven.take(2), cats).loadAll("2026-10-10", 28)
        assertEquals(2, few.shares.size)
        assertEquals(0L, few.restTenthPercent, "مفيش باقي ⇒ مفيش رمادي")
    }

    @Test
    fun threeThousandSixHundredOperationsRankFastForThisMonthAndThreeMonths() = runBlocking {
        val forty = (0 until 40).map(::category)
        val big = ArrayList<Transaction>()
        val months = listOf("2026-10", "2026-09", "2026-08", "2026-07")
        for ((m, ym) in months.withIndex()) {
            for (i in 0 until 900) big += txn("$ym-%02d".format(1 + i % 9), 1_000L * (i % 40 + 1) + m * 7 + i / 40, "c-%02d".format(i % 40))
        }
        // تسخين الـJVM على عينة صغيرة (تحميل الكلاسات) — القياس على الكبيرة بس
        useCase(now + last, cats).loadAll("2026-10-10", 28)
        val top = useCase(big, forty)
        var started = System.nanoTime()
        val thisMonth = top.loadAll("2026-10-10", 28)
        val msNow = (System.nanoTime() - started) / 1_000_000
        started = System.nanoTime()
        val three = top.loadAll("2026-10-10", 28, monthsBack = 0, months = 3)
        val msThree = (System.nanoTime() - started) / 1_000_000
        assertTrue(msNow < 700, "«الشهر ده» مع الفرق أخد $msNow مللي ثانية")
        assertTrue(msThree < 700, "«آخر 3 شهور» أخد $msThree مللي ثانية")
        assertEquals(40, thisMonth.rows.size)
        assertTrue(thisMonth.rows.all { it.changePercent != null }, "كل التصنيفات كان فيها صرف الشهر اللي فات")
        assertEquals(big.filter { it.occurredAt >= "2026-08" }.sumOf { it.amountMinor }, three.totalMinor)
        assertEquals(2_700, three.operationCount)
    }
}
