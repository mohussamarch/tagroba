package app.masroufy.usecase

import app.masroufy.core.Budget
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EstimatePolicy
import app.masroufy.core.Period
import app.masroufy.core.ReviewState
import app.masroufy.core.SALARY_EARLY_DAYS
import app.masroufy.core.Transaction
import app.masroufy.core.buildPeriod
import app.masroufy.core.countingReadStart
import app.masroufy.core.daysBetween
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * حساب الشهر بقرارات المالك (§75-1 · §75-3) على الشاشات اللي بتعرض أرقام فترة: الرئيسية وتاريخها · العمليات · «حركة الفلوس» ·
 * الميزانية. يوم الراتب 28 ⇒ فترة 2026-08 = 28 أغسطس..27 سبتمبر · فترة 2026-09 = 28 سبتمبر..27 أكتوبر. كل المبالغ مخترعة.
 */
class MonthAccountingFlowTest {
    @AfterTest
    fun reset() {
        EstimatePolicy.current = EstimatePolicy.OWNER_2026_10
    }

    private val aug = buildPeriod(2026, 8, 28)
    private val sep = buildPeriod(2026, 9, 28)

    private fun txn(id: String, date: String, minor: Long, kind: EconomicKind, dir: Direction) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = kind != EconomicKind.UNCLASSIFIED,
        observedDirection = dir, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    private fun salary(id: String, date: String) = txn(id, date, 1_200_000, EconomicKind.SALARY, Direction.IN)
    private fun spend(id: String, date: String, minor: Long) = txn(id, date, minor, EconomicKind.PURCHASE, Direction.OUT)
    private fun unknownIn(id: String, date: String, minor: Long) = txn(id, date, minor, EconomicKind.UNCLASSIFIED, Direction.IN)

    /** المستودع بيسجّل كل قراية بمداها — القراية لازم تفضل محدودة بالفترة (ARCHITECTURE §5.6). */
    private class Recording(real: MemoryTransactionRepository) : TransactionRepository by real {
        val reads = mutableListOf<Pair<String, String>>()
        private val inner = real

        override suspend fun listByDateRange(fromIso: String, toIso: String): List<Transaction> {
            reads += fromIso to toIso
            return inner.listByDateRange(fromIso, toIso)
        }
    }

    private class World(rows: List<Transaction>) {
        val txns = Recording(MemoryTransactionRepository(rows))
        private val categories = MemoryCategoryRepository()
        private val allocations = MemoryAllocationRepository()
        val homeCase = LoadHomeScreen(LoadHomeScreenDeps(txns, categories, allocations, MemoryBudgetRepository()))
        val history = LoadHomeHistory(LoadHomeHistoryDeps(txns, allocations, categories))
        val list = LoadTransactionsScreen(LoadTransactionsScreenDeps(txns, categories, allocations))
        val money = LoadMoneySummary(LoadMoneySummaryDeps(txns, categories, allocations))

        suspend fun home(p: Period, today: String, history: Boolean = true) = homeCase.load(LoadHomeScreenRequest(p, today, 28, includeHistory = history))
        suspend fun list(p: Period) = list.load(LoadTransactionsScreenRequest(period = p))
        suspend fun budget(p: Period, today: String) = LoadBudgetScreen(
            LoadBudgetScreenDeps(txns, categories, allocations, MemoryBudgetRepository(listOf(Budget(p.key, p.key, p.start, p.end, 300_000, 80, "x", "x")))),
        ).load(LoadBudgetScreenRequest(p, today, 28))
    }

    /** راتب نزل يوم 26 سبتمبر (قبل يوم 28 بيومين) + صرف في الفترتين. */
    private fun earlySalary() = World(listOf(salary("sal", "2026-09-26"), spend("aug-food", "2026-09-05", 100_000), spend("sep-food", "2026-10-01", 50_000)))

    @Test fun salaryTwoDaysBeforePaydayCountsInTheNewMonthEverywhere() = runBlocking<Unit> {
        val w = earlySalary()
        val homeSep = w.home(sep, "2026-10-05")
        assertEquals(1_200_000, homeSep.incomeMinor, "الرئيسية: راتب 26 سبتمبر في فترة 28 سبتمبر")
        assertEquals(1_150_000, homeSep.remainingMinor)
        assertEquals(2, homeSep.transactionCount)
        assertTrue(homeSep.latest.any { it.id == "sal" })
        assertEquals(listOf(1_200_000L, 0L), homeSep.recentPeriods.take(2).map { it.incomeMinor }, "الشريط: الجديد ليه والقديم لأ")

        val homeAug = w.home(aug, "2026-09-27")
        assertEquals(0, homeAug.incomeMinor, "مش في فترة أغسطس")
        assertEquals(100_000, homeAug.expenseMinor)
        assertEquals(listOf("sal"), homeAug.countedInNextPeriod.map { it.id }, "بيتعرض بعلامة «بيتحسب للشهر الجديد»")
        assertTrue(homeAug.latest.any { it.id == "sal" }, "ما بيختفيش من أحدث العمليات")
        assertEquals(1, homeAug.transactionCount)

        val history = w.history.load(sep, 28, homeSep)
        assertEquals(listOf(sep.key to 1_200_000L, aug.key to 0L), history.take(2).map { it.period.key to it.incomeMinor }, "تاريخ الرئيسية")

        assertEquals(1_200_000, w.money.load(sep.start, sep.end, 28).incomeMinor, "حركة الفلوس بشهور مالية")
        assertEquals(0, w.money.load(aug.start, aug.end, 28).incomeMinor)
        assertEquals(1_200_000, w.money.load(aug.start, sep.end, 28).incomeMinor, "مرة واحدة في مدى الشهرين")
        assertEquals(1_200_000, w.money.load("2026-09-01", "2026-09-30").incomeMinor, "سنة/شهر ميلادي ⇒ بتاريخها")

        val listSep = w.list(sep)
        assertTrue(listSep.transactions.any { it.id == "sal" }, "شاشة العمليات: في قايمة الفترة الجديدة")
        assertEquals(1_200_000, listSep.incomeMinor)
        val listAug = w.list(aug)
        assertFalse(listAug.transactions.any { it.id == "sal" })
        assertEquals(listOf("sal"), listAug.countedInNextPeriod.map { it.id })
        assertEquals(0, listAug.incomeMinor)
        val byToday = w.list.load(LoadTransactionsScreenRequest(today = "2026-10-05", payday = 28))
        assertEquals(listSep.transactions.map { it.id }, byToday.transactions.map { it.id })

        // الميزانية: الراتب ما بيلمسش الصرف — المتبقي من السقف زي ما هو في الفترتين
        assertEquals(250_000, w.budget(sep, "2026-10-05").totalStatus!!.remainingMinor)
        assertEquals(200_000, w.budget(aug, "2026-09-20").totalStatus!!.remainingMinor)
    }

    @Test fun midMonthSalaryAndOtherIncomingStayInTheirMonth() = runBlocking<Unit> {
        val w = World(
            listOf(
                salary("sal-mid", "2026-09-12"),
                txn("bonus", "2026-09-26", 50_000, EconomicKind.BONUS, Direction.IN),
                unknownIn("gift", "2026-09-26", 30_000),
            ),
        )
        val homeAug = w.home(aug, "2026-09-27", history = false)
        assertEquals(1_250_000, homeAug.incomeMinor, "راتب النص الأول من الفترة (16 يوم قبل 28) والمكافأة في أغسطس — الراتب بس اللي بيتنقل، ومن النص التاني بس")
        assertEquals(listOf("gift"), homeAug.pendingIncomingIds, "الداخل المجهول في شهره ومستني")
        assertEquals(emptyList(), homeAug.countedInNextPeriod)
        assertEquals(0, w.home(sep, "2026-10-05", history = false).incomeMinor)
    }

    @Test fun legacyMovesNothing() = runBlocking<Unit> {
        EstimatePolicy.current = EstimatePolicy.LEGACY
        val w = earlySalary()
        val homeAug = w.home(aug, "2026-09-27")
        assertEquals(1_200_000, homeAug.incomeMinor, "التطبيق القديم: في شهر تاريخه")
        assertEquals(emptyList(), homeAug.countedInNextPeriod)
        assertEquals(0, w.home(sep, "2026-10-05").incomeMinor)
        assertEquals(1_200_000, w.money.load(aug.start, aug.end, 28).incomeMinor)
        assertTrue(w.txns.reads.all { (from, _) -> from.endsWith("-28") }, "القراية من أول الفترة بالظبط: ${w.txns.reads}")
    }

    @Test fun readsStayBoundedByThePeriodAndHalfAMonth() = runBlocking<Unit> {
        val w = earlySalary()
        w.home(sep, "2026-10-05")
        val periods = (0 until 6).map { if (it == 0) sep else shiftPeriod(sep, -it, 28) }
        assertEquals(periods.map { countingReadStart(it.start) to it.end }, w.txns.reads, "قراية واحدة لكل فترة: من 15 يوم قبلها لآخرها")
        w.txns.reads.clear()
        w.list(sep)
        w.budget(sep, "2026-10-05")
        w.history.load(sep, 28, w.home(sep, "2026-10-05", history = false))
        w.money.load(sep.start, sep.end, 28)
        assertTrue(w.txns.reads.isNotEmpty())
        assertTrue(w.txns.reads.all { (from, to) -> daysBetween(from, to) + 1 <= 31 + SALARY_EARLY_DAYS }, "ولا قراية أطول من فترة ونص: ${w.txns.reads}")
    }

    @Test fun pendingIncomingIsShownApartAndNumbersStayAvailable() = runBlocking<Unit> {
        val w = World(listOf(salary("sal", "2026-09-28"), spend("food", "2026-10-01", 50_000), unknownIn("in-1", "2026-10-02", 150_000)))
        val home = w.home(sep, "2026-10-05")
        assertEquals(1_200_000, home.incomeMinor, "الـ1,500 مش دخل")
        assertEquals(50_000, home.expenseMinor)
        assertEquals(1, home.pendingIncomingCount)
        assertEquals(mapOf(Currency.SAR to 150_000L), home.pendingIncomingMinor)
        assertEquals(listOf("in-1"), home.pendingIncomingIds)
        assertFalse(home.partial, "الصرف كله معروف")
        assertEquals(1_150_000, home.remainingMinor, "§18: الرقم ظاهر من الدخل المؤكد، والمستني معاه ملاحظة")
        assertNotNull(home.allowance.amountMinor)
        assertNotNull(home.forecast.projectedMinor, "التوقع للصرف — الداخل المستني ما بيسكّتهوش")
        assertEquals(1, home.recentPeriods.first().pendingIncomingCount)

        val list = w.list(sep)
        assertEquals(1_200_000, list.incomeMinor)
        assertEquals(1_150_000, list.remainingMinor)
        assertEquals(listOf("in-1"), list.pendingIncomingIds)
        assertEquals(mapOf(Currency.SAR to 150_000L), list.pendingIncomingMinor)
        assertEquals(1, list.needsReviewCount, "المستني جوه «محتاجة تأكيد»")

        EstimatePolicy.current = EstimatePolicy.LEGACY
        val old = w.home(sep, "2026-10-05")
        assertEquals(1_350_000, old.incomeMinor, "التخمين القديم: الوارد راتب")
        assertEquals(0, old.pendingIncomingCount)
    }

    /** فترة كل اللي فيها داخل مستني: الصرف معروف (صفر فعلًا) — مش «غير متاح» زي فترة كل عملياتها من غير نوع. */
    @Test fun aPeriodWithOnlyPendingIncomingIsStillKnown() = runBlocking<Unit> {
        val w = World(listOf(unknownIn("in-1", "2026-10-02", 150_000)))
        val home = w.home(sep, "2026-10-05")
        assertEquals(0L to 0L, home.incomeMinor to home.expenseMinor)
        assertEquals(0L to 1, home.recentPeriods.first().let { it.incomeMinor to it.pendingIncomingCount })
        assertEquals(0L to 1, w.history.load(sep, 28, home).first().let { it.incomeMinor to it.pendingIncomingCount })
        assertEquals(0L to 0L, w.money.load(sep.start, sep.end, 28).let { it.incomeMinor to it.expenseMinor })
        val budget = w.budget(sep, "2026-10-05")
        assertTrue(budget.spentKnown, "الميزانية: «0 من 300» صح هنا — مفيش صرف مجهول")
        assertEquals(300_000, budget.totalStatus!!.remainingMinor)
        // مراجعة S6: شاشة العمليات زي الرئيسية (مش «غير متاح»)، والميزانية مش «تقريبي» — المستني مش جوه الرقم ولا بتخمين
        assertEquals(0L to 0L, w.list(sep).let { it.incomeMinor to it.expenseMinor })
        assertEquals(1, w.list(sep).pendingIncomingCount)
        assertTrue(budget.spentReliable)
        assertEquals(null, budget.spentNote)
        val older = World(listOf(unknownIn("in-0", "2026-09-02", 150_000))).home(sep, "2026-10-05").recentPeriods[1]
        assertEquals(aug.key to 0L, older.period.key to older.incomeMinor, "فترة قديمة فيها مستني بس ⇒ معروفة برضه")
    }

    /** يوم راتب 31: فترة فبراير بتبدأ 28 فبراير. شاشة العمليات من غير يوم راتب في الطلب بتعرفه من حدود الفترة. */
    @Test fun transactionsScreenFindsPaydayThirtyOneFromThePeriod() = runBlocking<Unit> {
        val feb = buildPeriod(2027, 2, 31)
        val w = World(listOf(salary("sal-mar", "2027-03-29")))
        assertEquals("2027-02-28" to "2027-03-30", feb.start to feb.end)
        assertEquals(listOf("sal-mar"), w.list(feb).countedInNextPeriod.map { it.id }, "يومين قبل 31 مارس ⇒ للفترة الجاية")
        assertEquals(31, paydayOf(feb))
        assertEquals(28, paydayOf(sep))
    }

    /** «اللي بتحوّشه فعلًا» (حاسبة الادخار): كل راتب في شهره الجديد، والداخل المستني برّه وبيتعد. الشهر الحالي (سبتمبر) مش داخل. */
    @Test fun actualSavingFollowsTheSameMonthRules() = runBlocking<Unit> {
        val rows = listOf(
            salary("s-jun", "2026-06-26"), salary("s-jul", "2026-07-27"), salary("s-aug", "2026-08-28"), salary("s-sep", "2026-09-25"),
            spend("f-jun", "2026-07-01", 100_000), spend("f-jul", "2026-08-01", 100_000), spend("f-aug", "2026-09-01", 100_000),
            unknownIn("p-jul", "2026-08-10", 40_000),
        )
        val load = LoadActualSaving(LoadActualSavingDeps(MemoryTransactionRepository(rows), MemoryAllocationRepository(), MemoryCategoryRepository()))
        val now = load.load(Currency.SAR, "2026-10-05")
        assertEquals(listOf("2026-06", "2026-07", "2026-08"), now.months.map { it.period.key })
        assertEquals(listOf(1_100_000L, 1_100_000L, 1_100_000L), now.months.map { it.savedMinor }, "راتب واحد لكل شهر")
        assertEquals(listOf(0, 1, 0), now.months.map { it.pendingIncomingCount }, "يوليو فيه داخل مستني ⇒ «لحد دلوقتي»")
        assertEquals(1_100_000, now.averageMinor)

        EstimatePolicy.current = EstimatePolicy.LEGACY
        assertEquals(listOf(1_100_000L, -60_000L, 2_300_000L), load.load(Currency.SAR, "2026-10-05").months.map { it.savedMinor }, "القديم: بتاريخها والمجهول راتب")
    }
}
