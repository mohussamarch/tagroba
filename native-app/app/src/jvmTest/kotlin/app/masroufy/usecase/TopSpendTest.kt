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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «أكثر ما صرفت عليه» (طلب المالك 2026-10-16 — OVERRIDES §98): الكارت تلاتة بالكتير من نفس الترتيب · الترتيب الكامل والمجاميع ·
 * النسب مجموعها حوالي 100 · «بلا تصنيف» سطر لوحده · الشهر اللي فات · السرعة على 2,400 عملية في 40 تصنيف.
 * **بيانات مخترعة بس** (المستودع عام).
 */
class TopSpendTest {
    private var seq = 0

    private fun txn(date: String, amount: Long, category: String?, kind: EconomicKind = EconomicKind.PURCHASE, confirmed: Boolean = true) = Transaction(
        id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = confirmed,
        observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = true, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", categoryId = category,
    )

    private fun category(i: Int) = Category("c-%02d".format(i), null, "تصنيف وهمي $i", "tag", "#336699", "#99BBDD", true, i)

    private fun useCase(txns: List<Transaction>, cats: List<Category>) =
        LoadTopCategories(LoadHomeScreen(LoadHomeScreenDeps(MemoryTransactionRepository(txns), MemoryCategoryRepository(cats), MemoryAllocationRepository())))

    private val cats = (0 until 5).map(::category)

    /** خمس تصنيفات + «بلا تصنيف» في أكتوبر 2026 (الشهر المالي من 28 سبتمبر)، وواحدة قبل بداية الشهر. */
    private val month = listOf(
        txn("2026-10-01", 50_000, "c-00"), txn("2026-10-02", 30_000, "c-00"),
        txn("2026-10-03", 120_000, "c-01"),
        txn("2026-10-04", 9_000, "c-02"),
        txn("2026-10-05", 40_000, null), txn("2026-10-06", 25_000, null),
        txn("2026-10-07", 10_000, "c-03"),
        txn("2026-10-08", 1_000, "c-04"),
        txn("2026-09-20", 900_000, "c-04"),
    )

    @Test
    fun theCardShowsAtMostThreeRowsFromTheSameRanking() = runBlocking {
        val top = useCase(month, cats)
        val card = top.load("2026-10-10", 28)
        val all = top.loadAll("2026-10-10", 28)
        assertEquals(3, card.rows.size, "الكارت تلاتة بالكتير مهما كان عدد التصنيفات")
        assertEquals(all.rows.take(3).map { it.categoryId }, card.rows.map { it.categoryId }, "نفس الترتيب")
        assertEquals(all.rows.take(3).map { it.amountMinor }, card.rows.map { it.amountMinor })
    }

    @Test
    fun theFullListIsRankedWithSumsCountsAndTheUncategorizedRow() = runBlocking {
        val view = useCase(month, cats).loadAll("2026-10-10", 28)
        assertEquals(listOf("c-01", "c-00", null, "c-03", "c-02", "c-04"), view.rows.map { it.categoryId })
        assertEquals(listOf(120_000L, 80_000L, 65_000L, 10_000L, 9_000L, 1_000L), view.rows.map { it.amountMinor }, "سبتمبر 20 برّه الشهر المالي")
        assertEquals(listOf(1, 2, 2, 1, 1, 1), view.rows.map { it.count })
        assertEquals(285_000L, view.totalMinor)
        val none = view.rows.single { it.categoryId == null }
        assertNull(none.name, "«بلا تصنيف» اسمه بيتكتب في الشاشة")
        assertEquals("تصنيف وهمي 1", view.rows.first().name)
        assertEquals("#336699", view.rows.first().colorHex)
        assertEquals(100, view.rows.first().barPercent)
    }

    @Test
    fun sharesSumToAboutOneHundred() = runBlocking {
        val view = useCase(month, cats).loadAll("2026-10-10", 28)
        val tenths = view.rows.sumOf { it.shareTenthPercent }
        assertTrue(abs(tenths - 1000) <= view.rows.size, "مجموع النسب بالعُشر $tenths")
        val whole = view.rows.sumOf { it.sharePercent }
        assertTrue(abs(whole - 100) <= view.rows.size, "مجموع النسب $whole")
        assertEquals(421L, view.rows.first().shareTenthPercent, "120,000 من 285,000 = 42.1٪")
        assertEquals(42, view.rows.first().sharePercent)
    }

    @Test
    fun lastMonthReadsThePreviousFinancialMonth() = runBlocking {
        val view = useCase(month, cats).loadAll("2026-10-10", 28, monthsBack = 1)
        assertTrue(view.period.start <= "2026-09-20" && view.period.end >= "2026-09-20" && view.period.end < "2026-09-28", "الشهر المالي اللي فات")
        assertEquals(listOf("c-04"), view.rows.map { it.categoryId })
        assertEquals(900_000L, view.totalMinor)
    }

    @Test
    fun anEmptyMonthHasNoRowsAndAZeroTotal() = runBlocking {
        val view = useCase(emptyList(), cats).loadAll("2026-10-10", 28)
        assertTrue(view.rows.isEmpty())
        assertEquals(0L, view.totalMinor)
    }

    @Test
    fun twoThousandFourHundredOperationsInFortyCategoriesRankFast() = runBlocking {
        val forty = (0 until 40).map(::category)
        val big = ArrayList<Transaction>()
        for (i in 0 until 2_400) big += txn("2026-10-%02d".format(1 + i % 15), 1_000L * (i % 40 + 1) + i / 40, "c-%02d".format(i % 40))
        repeat(30) { big += txn("2026-10-09", 5_000, null) }
        // تسخين الـJVM على عينة صغيرة (تحميل الكلاسات) — القياس على الكبيرة بس
        useCase(month, cats).loadAll("2026-10-10", 28)
        val top = useCase(big, forty)
        val started = System.nanoTime()
        val view = top.loadAll("2026-10-15", 28)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue(ms < 700, "الترتيب أخد $ms مللي ثانية")
        assertEquals(41, view.rows.size, "40 تصنيف + «بلا تصنيف»")
        // كل تصنيف k: 60 عملية × 1,000×(k+1) + (0+1+…+59) ⇒ الأكبر c-39 والأصغر c-00، و«بلا تصنيف» (150,000) بعد c-01 (121,770)
        assertEquals((39 downTo 0).map { "c-%02d".format(it) }.let { it.take(38) + null + it.drop(38) }, view.rows.map { it.categoryId })
        assertEquals(60_000L * 40 + 1_770, view.rows.first().amountMinor)
        assertEquals(60, view.rows.first().count)
        val expected = (0 until 40).sumOf { 60_000L * (it + 1) + 1_770 } + 150_000
        assertEquals(expected, view.totalMinor)
        assertEquals(expected, view.rows.sumOf { it.amountMinor })
    }
}
