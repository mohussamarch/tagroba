package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DuesCategories
import app.masroufy.core.EconomicKind
import app.masroufy.core.GoalContribution
import app.masroufy.core.LocalMoment
import app.masroufy.core.RecurringItem
import app.masroufy.core.ReviewState
import app.masroufy.core.SavingsGoal
import app.masroufy.core.Transaction
import app.masroufy.core.buildPeriod
import app.masroufy.core.emptyProfile
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAlertInbox
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryAlertReceipts
import app.masroufy.memory.MemoryAlertSettings
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryGoalContributionRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemorySavingsGoalRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUsualHours
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * أفكار المساعد الثمانية التانية من البيانات للمرشحين (OVERRIDES §68) على مستودعات الذاكرة — كل الأسامي والمبالغ مخترعة.
 * beforePayday من البيانات في `AdvisorOvercommitFlowTest` (محتاج التقويم و«فاضلك»).
 */
class AdvisorMoreFlowTest {
    private val period = buildPeriod(2026, 9, 28)
    private val coffee = "cat-مطاعم-وقهوه--قهوه-ومشروبات"
    private val grocery = "cat-بقاله-وسوبرماركت"
    private val tv = "cat-اشتراكات-رقميه--بث-وافلام"
    private val categories = listOf(
        Category("cat-مطاعم-وقهوه", null, "مطاعم وقهوة", "i", "#000", "#fff", true, 1),
        Category(coffee, "cat-مطاعم-وقهوه", "قهوة ومشروبات", "i", "#000", "#fff", true, 2),
        Category(grocery, null, "بقالة وسوبرماركت", "i", "#000", "#fff", true, 3),
        Category(tv, null, "بث وأفلام", "i", "#000", "#fff", true, 4),
    ) + DuesCategories.defaults()
    private var seq = 0

    private fun txn(date: String, amount: Long, category: String?, merchant: String? = null, kind: EconomicKind = EconomicKind.PURCHASE, dir: Direction = Direction.OUT) = Transaction(
        id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = true, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", categoryId = category, merchantId = merchant,
    )

    /** آخر 3 شهور: قهوة 400 وبقالة 900 وفاتورة كهرباء 410 كل شهر ⇒ معتاد الشهر 1,710. */
    private fun history() = listOf("2026-06-29", "2026-07-29", "2026-08-29").flatMap { d ->
        listOf(txn(d, 40_000, coffee), txn(d, 90_000, grocery), txn(d, 41_000, null, merchant = "m-elec"))
    }

    private val summer = SavingsGoal("g-1", "سفر الصيف", 1_200_000, Currency.SAR, "2026-01-01", "2026-12-31", createdAt = "c", updatedAt = "c")

    private suspend fun advisor(
        current: List<Transaction>,
        today: String,
        goal: Boolean = false,
        saved: Long = 1_000_000,
        recurring: List<RecurringItem> = emptyList(),
    ): List<AlertCandidate> {
        val txns = MemoryTransactionRepository(history() + current)
        val goals = LoadGoalsOverview(
            LoadGoalsOverviewDeps(MemorySavingsGoalRepository(listOfNotNull(summer.takeIf { goal })), MemoryGoalContributionRepository(listOf(GoalContribution("c-1", "g-1", "2026-02-01", saved, "x")))),
        )
        val deps = AdvisorSignalsDeps(
            txns, MemoryAllocationRepository(), MemoryCategoryRepository(categories), MemoryProfileRepository(emptyProfile().copy(payday = 28)),
            goals = goals, recurring = MemoryRecurringRepository(recurring),
        )
        return AdvisorSignals(deps).alertCandidates(AlertGatherInput(today, period, Currency.SAR))
    }

    private fun List<AlertCandidate>.of(kind: AlertKind) = filter { it.kind == kind }

    @Test fun unusualNeedsRepeatsAndNeverFiresOnOneLump() = runBlocking<Unit> {
        val lump = advisor(listOf(txn("2026-09-28", 200_000, grocery)), "2026-10-03")
        assertEquals(emptyList(), lump.of(AlertKind.UNUSUAL_SPEND), "خبطة واحدة ⇒ مش «غير معتاد»")
        assertEquals(1, lump.of(AlertKind.BIG_ONE).size, "بس هي بالظبط اللي «عملية كبيرة» بتمسكه")
        val three = advisor(listOf("2026-09-28", "2026-09-30", "2026-10-02").map { txn(it, 30_000, grocery) }, "2026-10-03")
        assertEquals("unusual|2026-09-28|$grocery", three.of(AlertKind.UNUSUAL_SPEND).single().threadKey, "البقالة مش اختيارية بس «غير معتاد» لأي بند")
    }

    @Test fun unusualStepsAsideWhenHabitVsGoalFired() = runBlocking<Unit> {
        val coffees = listOf("2026-09-29", "2026-10-01", "2026-10-02").map { txn(it, 15_000, coffee) }
        val withGoal = advisor(coffees, "2026-10-03", goal = true)
        assertEquals(1, withGoal.of(AlertKind.HABIT_VS_GOAL).size)
        assertEquals(emptyList(), withGoal.of(AlertKind.UNUSUAL_SPEND), "نفس البند ونفس الفترة ⇒ تنبيه واحد")
        assertEquals("unusual|2026-09-28|$coffee", advisor(coffees, "2026-10-03").of(AlertKind.UNUSUAL_SPEND).single().threadKey, "من غير خطة ⇒ «غير معتاد»")
    }

    private val elec = RecurringItem("r-elec", "فاتورة الكهرباء", "id:m-elec", "bill", 1, 41_000, Currency.SAR, "2026-10-29", true, true)
    private fun sub(id: String) = RecurringItem("r-$id", "اشتراك $id", "id:m-$id", "subscription", 1, 3_000, Currency.SAR, "2026-10-20", true, true)

    @Test fun billJumpAndDuplicateSubscriptions() = runBlocking<Unit> {
        val current = listOf(
            txn("2026-09-30", 62_000, null, merchant = "m-elec"),
            txn("2026-09-29", 3_000, tv, merchant = "m-a"), txn("2026-09-30", 3_500, tv, merchant = "m-b"),
        )
        val found = advisor(current, "2026-10-03", recurring = listOf(elec, sub("a"), sub("b")))
        assertEquals("فاتورة الكهرباء 620.00 ر.س — أعلى من المعتاد (410.00 ر.س)", found.of(AlertKind.BILL_JUMP).single().body)
        assertEquals("لديك أكثر من اشتراك في «بث وأفلام»", found.of(AlertKind.DUP_SUBS).single().title)
        assertEquals(emptyList(), found.of(AlertKind.BIG_ONE), "الفاتورة الدورية معروفة ومستنية ⇒ مش «عملية كبيرة»")
        val usual = advisor(listOf(txn("2026-09-30", 42_000, null, merchant = "m-elec")), "2026-10-03", recurring = listOf(elec, sub("a")))
        assertEquals(emptyList(), usual.of(AlertKind.BILL_JUMP) + usual.of(AlertKind.DUP_SUBS), "الفاتورة عادية واشتراك واحد")
        assertEquals(emptyList(), advisor(current, "2026-10-03").of(AlertKind.BILL_JUMP), "من غير اشتراكات متسجلة ⇒ ساكت")
    }

    @Test fun bigOneSkipsDuesAndNeedsAKnownMonth() = runBlocking<Unit> {
        val dues = advisor(listOf(txn("2026-10-01", 500_000, DuesCategories.FINANCING)), "2026-10-03")
        assertEquals(emptyList(), dues.of(AlertKind.BIG_ONE), "قسط في «المستحقات» ⇒ معروف")
        val big = advisor(listOf(txn("2026-10-01", 43_000, grocery)), "2026-10-03").of(AlertKind.BIG_ONE).single()
        assertEquals("عملية 430.00 ر.س — ربع مصروف شهرك أو أكثر", big.body, "430 من معتاد 1,710 = 25.1%")
    }

    @Test fun payFirstOnSalaryAndGoalNear() = runBlocking<Unit> {
        val salary = txn("2026-09-28", 1_500_000, null, kind = EconomicKind.SALARY, dir = Direction.IN)
        val found = advisor(listOf(salary), "2026-09-30", goal = true, saved = 1_080_000)
        // الباقي 1,200 على 4 شهور بالتقويم (30 سبتمبر ⇒ 31 ديسمبر) = 300
        assertEquals("نزل الراتب — حوّل 300.00 ر.س لخطة «سفر الصيف» قبل أن تصرف", found.of(AlertKind.PAY_FIRST).single().body)
        assertEquals("goalnear|g-1|near", found.of(AlertKind.GOAL_NEAR).single().threadKey, "1,080 من 1,200 = 90%")
        assertEquals(emptyList(), advisor(listOf(salary), "2026-09-30").of(AlertKind.PAY_FIRST), "مفيش خطة")
        assertEquals(emptyList(), advisor(emptyList(), "2026-09-30", goal = true).of(AlertKind.PAY_FIRST), "المرتب ما نزلش")
        assertEquals(emptyList(), advisor(listOf(salary), "2026-10-02", goal = true).of(AlertKind.PAY_FIRST), "بعد 4 أيام")
    }

    @Test fun weeklySummaryForTheWeekThatEnded() = runBlocking<Unit> {
        val weeks = listOf(txn("2026-09-21", 20_000, grocery), txn("2026-09-28", 25_000, grocery), txn("2026-10-02", 6_000, coffee))
        val w = advisor(weeks, "2026-10-05").of(AlertKind.WEEKLY_SUMMARY).single()
        assertEquals("weekly|2026-10-03", w.threadKey)
        assertEquals("صرفت 310.00 ر.س هذا الأسبوع · أكثر بـ110.00 ر.س من الأسبوع الماضي · الأعلى: «بقالة وسوبرماركت» (250.00 ر.س)", w.body)
        val shaky = weeks + txn("2026-09-30", 1_000, null, kind = EconomicKind.UNCLASSIFIED)
        assertEquals(emptyList(), advisor(shaky, "2026-10-05").of(AlertKind.WEEKLY_SUMMARY), "الأسبوع فيه عملية من غير نوع ⇒ مش معروف")
        assertEquals(emptyList(), advisor(emptyList(), "2026-10-05").of(AlertKind.WEEKLY_SUMMARY), "أسبوعين فاضيين")
    }

    @Test fun oneSwitchMutesTheWholeAdvisor() = runBlocking<Unit> {
        val many = advisor(listOf(txn("2026-09-28", 200_000, grocery)) + listOf("2026-09-30", "2026-10-01", "2026-10-02").map { txn(it, 30_000, grocery) }, "2026-10-05")
        assertTrue(many.map { it.kind }.toSet().size >= 3, "$many")
        val inbox = MemoryAlertInbox()
        val engine = RunAlertEngine(AlertEngineDeps(MemoryAlertSettings(setOf(AlertGroup.ADVISOR)), MemoryAlertInteractions(), MemoryUsualHours(), MemoryAlertReceipts(), inbox, FixedClock("2026-10-05T09:00:00.000Z")))
        val run = engine.run(many, LocalMoment("2026-10-05", 14))
        assertEquals(emptyList(), run.posts, "المجموعة مقفولة ⇒ ولا شريط")
        assertEquals(many.size, inbox.listAll().size, "بس السطور في الصفحة (قرار المالك §61)")
    }
}
