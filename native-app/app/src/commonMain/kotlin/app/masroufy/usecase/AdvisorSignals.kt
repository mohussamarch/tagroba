package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.Category
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.DuesCategories
import app.masroufy.core.GoalProgress
import app.masroufy.core.GoalState
import app.masroufy.core.HABIT_BASELINE_MONTHS
import app.masroufy.core.Halalas
import app.masroufy.core.HabitCheck
import app.masroufy.core.Id
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.assessCoverage
import app.masroufy.core.capPaceCandidate
import app.masroufy.core.countsAsPersonalExpense
import app.masroufy.core.habitVsGoalCandidate
import app.masroufy.core.isDiscretionary
import app.masroufy.core.overcommitCandidate
import app.masroufy.core.overcommitSentGaps
import app.masroufy.core.personalShareOf
import app.masroufy.core.readPace
import app.masroufy.core.sumMoney
import app.masroufy.core.uiText
import app.masroufy.core.usualMonthMinor
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AlertReceiptStore
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.ProfileRepository
import app.masroufy.port.TransactionRepository

/**
 * «المساعد المالي» (OVERRIDES §68) — المرشحين للتنبيه من القواعد الثلاثة اللي المالك فعّلها: habitVsGoal · overcommitted · capPace.
 * من غير ما يقرر حاجة — القرار والإيصالات في `RunAlertEngine` زي باقي التنبيهات. كل مصدر null = القاعدة دي مش متوصلة ⇒ ساكتة.
 * المصروف بنفس قواعد شاشة الميزانية: النوع المقترح للواضح (`withEstimatedKinds`) · نصيبك من العملية · المستبعد من الميزانية برا ·
 * «المستحقات» برا السقف الإجمالي لو صاحب الحساب اختار كده (§56).
 */
data class AdvisorSignalsDeps(
    val txns: TransactionRepository,
    val allocations: AllocationRepository,
    val categories: CategoryRepository,
    /** يوم الراتب (بداية الشهر المالي) و«المستحقات في الميزانية» — null ⇒ الافتراضي. */
    val profile: ProfileRepository? = null,
    /** خطط الادخار (habitVsGoal) — null = مش متوصلة. */
    val goals: LoadGoalsOverview? = null,
    /** «فاضلك تقريبًا» (overcommitted) — null = مش متوصل. */
    val leftover: LoadLeftover? = null,
    /** إيصالات المحرك — عشان «تاني بس لو النقص كبر». null ⇒ مرة واحدة للفترة. */
    val receipts: AlertReceiptStore? = null,
    /** البلد (بادئة الموضوع `eg:` في الإيصالات). */
    val spaceId: String = DEFAULT_SPACE_ID,
)

class AdvisorSignals(private val deps: AdvisorSignalsDeps) {
    suspend fun alertCandidates(input: AlertGatherInput): List<AlertCandidate> {
        val out = mutableListOf<AlertCandidate>()
        val profile = deps.profile?.load()
        val categories = deps.categories.listAll()
        val names = categories.associate { it.id to it.name }
        val period = input.period
        val lines = spendLines(period.start, input.today, input, names)

        input.budget?.let { budget ->
            if (budget.spentKnown) out += capPace(budget, lines, input, categories, duesInBudget = profile?.duesInBudget ?: true)
        }
        deps.goals?.let { goals -> out += habits(goals, lines, input, categories, profile?.payday ?: DEFAULT_PAYDAY, names) }
        deps.leftover?.let { leftover ->
            val p = leftover.load(input.today)
            val prefix = if (deps.spaceId == DEFAULT_SPACE_ID) "" else "${deps.spaceId}:"
            val sent = deps.receipts?.listAll()?.let { r -> overcommitSentGaps(r.map { it.eventKey }, prefix, period.start) }
            overcommitCandidate(p, input.currency, period.start, sent, input.budget?.totalStatus?.limitMinor)?.let { out += it }
        }
        return out
    }

    /** نصيبك من كل عملية مصروف في [from]..[to] بعملة المساحة: (التصنيف، المبلغ). */
    private suspend fun spendLines(from: String, to: String, input: AlertGatherInput, names: Map<Id, String>): List<Pair<Id?, Halalas>> =
        expenseLines(deps.txns.listByDateRange(from, to).filter { it.currency == input.currency }, names)

    private suspend fun expenseLines(rows: List<Transaction>, names: Map<Id, String>): List<Pair<Id?, Halalas>> {
        val counted = withEstimatedKinds(rows, names).transactions
        val allocations = deps.allocations.listByTransactionIds(counted.map { it.id })
        return counted.filter { countsAsPersonalExpense(it.economicKind) && !it.excludedFromBudget }
            .map { it.categoryId to personalShareOf(it, allocations) }.filter { it.second > 0 }
    }

    private fun capPace(budget: BudgetScreenData, lines: List<Pair<Id?, Halalas>>, input: AlertGatherInput, categories: List<Category>, duesInBudget: Boolean): List<AlertCandidate> {
        val out = mutableListOf<AlertCandidate>()
        val byId = categories.associateBy { it.id }
        // سقف التصنيف: نفس مطابقة شاشة الميزانية (التصنيف نفسه)، والتصنيف اللي المستخدم قفل تنبيهه ما بيتنبهش
        for (cb in budget.categoryBudgets) {
            if (!cb.notifyEnabled) continue
            val amounts = lines.filter { it.first == cb.categoryId }.map { it.second }
            val reading = readPace(amounts, cb.limitMinor, input.today, input.period)
            val label = byId[cb.categoryId]?.name ?: continue
            capPaceCandidate(label, "cat:${cb.categoryId}", cb.limitMinor, reading, input.today, input.period, input.currency)?.let { out += it }
        }
        val total = budget.budget?.totalLimitMinor ?: return out
        val dues = if (duesInBudget) emptySet() else DuesCategories.idsIn(categories)
        val amounts = lines.filter { it.first !in dues }.map { it.second }
        val reading = readPace(amounts, total, input.today, input.period)
        capPaceCandidate(uiText(TextKey.ADVISOR_TOTAL_BUDGET), "total", total, reading, input.today, input.period, input.currency)?.let { out += it }
        return out
    }

    private suspend fun habits(
        goals: LoadGoalsOverview,
        lines: List<Pair<Id?, Halalas>>,
        input: AlertGatherInput,
        categories: List<Category>,
        payday: Int,
        names: Map<Id, String>,
    ): List<AlertCandidate> {
        val goal = pickGoal(goals.load(input.today), input) ?: return emptyList()
        val parentOf = categories.associate { it.id to it.parentId }
        val discretionary = lines.mapNotNull { it.first }.distinct().filter { isDiscretionary(it, parentOf) }
        if (discretionary.isEmpty()) return emptyList()
        val history = baselineMonths(input, payday, names)
        val caps = input.budget?.categoryBudgets.orEmpty().associate { it.categoryId to it.limitMinor }
        return discretionary.mapNotNull { id ->
            val usual = usualMonthMinor(history.map { month -> month?.let { m -> sumMoney(m.filter { it.first == id }.map { it.second }) } })
            val reading = readPace(lines.filter { it.first == id }.map { it.second }, caps[id] ?: usual, input.today, input.period)
            habitVsGoalCandidate(HabitCheck(id, names[id] ?: return@mapNotNull null, reading, usual, goal, input.period, input.currency))
        }
    }

    /**
     * الخطة اللي بنقيس عليها: شغالة (مش مؤرشفة · النهارده بين البداية والهدف · معدلها معروف) وبعملة المساحة — **الأقرب تاريخًا**
     * (اختيار Claude: صرف واحد بيأثر على كل الخطط، فبنقيس على أقرب واحدة بدل تنبيه لكل خطة).
     */
    private fun pickGoal(all: List<GoalProgress>, input: AlertGatherInput): GoalProgress? = all
        .filter { !it.goal.archived && it.goal.currency == input.currency && it.projectedAtTargetMinor != null }
        .filter { it.state == GoalState.ON_TRACK || it.state == GoalState.BEHIND }
        .minWithOrNull(compareBy<GoalProgress> { it.goal.targetDate }.thenBy { it.goal.id })

    /**
     * آخر [HABIT_BASELINE_MONTHS] شهور مالية **مكتملة** (من يوم الراتب): سطور مصروف كل شهر، أو null لو الشهر مش معروف
     * (مفيش ولا عملية فيه، أو فيه عملية من غير نوع — زي شاشة الميزانية).
     */
    private suspend fun baselineMonths(input: AlertGatherInput, payday: Int, names: Map<Id, String>): List<List<Pair<Id?, Halalas>>?> =
        (1..HABIT_BASELINE_MONTHS).map { i ->
            val p: Period = shiftPeriod(input.period, -i, payday)
            val rows = deps.txns.listByDateRange(p.start, p.end).filter { it.currency == input.currency }
            val coverage = assessCoverage(withEstimatedKinds(rows, names).transactions)
            if (rows.isEmpty() || coverage.unclassified > 0) null else expenseLines(rows, names)
        }
}
