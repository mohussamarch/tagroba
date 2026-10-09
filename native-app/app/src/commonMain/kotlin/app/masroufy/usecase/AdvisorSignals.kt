package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.Category
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.DuesCategories
import app.masroufy.core.GoalProgress
import app.masroufy.core.HABIT_BASELINE_MONTHS
import app.masroufy.core.Halalas
import app.masroufy.core.HabitCheck
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.assessCoverage
import app.masroufy.core.capPaceCandidate
import app.masroufy.core.countsAsPersonalExpense
import app.masroufy.core.goalForHabit
import app.masroufy.core.habitVsGoalCandidate
import app.masroufy.core.isDiscretionary
import app.masroufy.core.overcommitCandidate
import app.masroufy.core.overcommitSentGaps
import app.masroufy.core.personalShareOf
import app.masroufy.core.readPace
import app.masroufy.core.recurringKey
import app.masroufy.core.sumMoney
import app.masroufy.core.uiText
import app.masroufy.core.usualMonthMinor
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AlertReceiptStore
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.ProfileRepository
import app.masroufy.port.RecurringRepository
import app.masroufy.port.TransactionRepository

/**
 * «المساعد المالي» (OVERRIDES §68) — المرشحين للتنبيه من الأفكار الـ12 اللي المالك شغّلها. من غير ما يقرر حاجة — القرار والإيصالات
 * في `RunAlertEngine` زي باقي التنبيهات. كل مصدر null = الأفكار اللي محتاجاه ساكتة.
 * المصروف بنفس قواعد شاشة الميزانية: النوع المقترح للواضح (`withEstimatedKinds`) · نصيبك من العملية · المستبعد من الميزانية برا ·
 * «المستحقات» برا السقف الإجمالي لو صاحب الحساب اختار كده (§56). الثمانية التانية في `AdvisorMoreSignals.kt`.
 */
data class AdvisorSignalsDeps(
    val txns: TransactionRepository,
    val allocations: AllocationRepository,
    val categories: CategoryRepository,
    /** يوم الراتب (بداية الشهر المالي) و«المستحقات في الميزانية» — null ⇒ الافتراضي. */
    val profile: ProfileRepository? = null,
    /** خطط الادخار (habitVsGoal · payFirst · goalNear) — null = مش متوصلة. */
    val goals: LoadGoalsOverview? = null,
    /** «فاضلك تقريبًا» (overcommitted · beforePayday) — null = مش متوصل. */
    val leftover: LoadLeftover? = null,
    /** إيصالات المحرك — عشان «تاني بس لو النقص كبر». null ⇒ مرة واحدة للفترة. */
    val receipts: AlertReceiptStore? = null,
    /** البلد (بادئة الموضوع `eg:` في الإيصالات). */
    val spaceId: String = DEFAULT_SPACE_ID,
    /** الاشتراكات والفواتير الدورية (billJump · dupSubs) — null = مش متوصلة. */
    val recurring: RecurringRepository? = null,
    /** التقويم — مستحقات قبل الراتب (beforePayday). null ⇒ بنقارن بالصرف المعتاد بس. */
    val calendar: LoadCalendar? = null,
    /** §75-15: عدّ «محتاجة تأكيد» لسطر التذكير في الملخص الأسبوعي — null = مش متوصل ⇒ من غير السطر. */
    val needsConfirmation: CountNeedsConfirmation? = null,
)

/** نصيبك من عملية مصروف واحدة. [key] = مفتاح التاجر للاشتراكات (`recurringKey`). */
internal data class SpendLine(val id: Id, val date: IsoDate, val categoryId: Id?, val amountMinor: Halalas, val key: String)

/** اللي كل الأفكار محتاجاه، متحسب مرة واحدة. [baseline] = آخر 3 شهور مالية مكتملة (null = الشهر مش معروف). */
internal class AdvisorContext(
    val input: AlertGatherInput,
    val categories: List<Category>,
    val names: Map<Id, String>,
    val lines: List<SpendLine>,
    val baseline: List<List<SpendLine>?>,
    val payday: Int,
    val duesIds: Set<Id>,
    val goals: List<GoalProgress>?,
) {
    /** المعتاد للبند (أو للشهر كله لو [categoryId] = null وفي [all]). */
    fun usual(categoryId: Id?, all: Boolean = false): Halalas? =
        usualMonthMinor(baseline.map { m -> m?.let { rows -> sumMoney(rows.filter { all || it.categoryId == categoryId }.map { it.amountMinor }) } })
}

class AdvisorSignals(private val deps: AdvisorSignalsDeps) {
    suspend fun alertCandidates(input: AlertGatherInput): List<AlertCandidate> {
        val out = mutableListOf<AlertCandidate>()
        val profile = deps.profile?.load()
        val categories = deps.categories.listAll()
        val names = categories.associate { it.id to it.name }
        val period = input.period
        val payday = profile?.payday ?: DEFAULT_PAYDAY
        val ctx = AdvisorContext(
            input, categories, names, lines(period.start, input.today, input, names), baselineMonths(input, payday, names), payday,
            DuesCategories.idsIn(categories), deps.goals?.load(input.today),
        )

        input.budget?.let { budget ->
            if (budget.spentKnown) out += capPace(budget, ctx, duesInBudget = profile?.duesInBudget ?: true)
        }
        ctx.goals?.let { goals -> out += habits(goals, ctx) }
        val leftover = deps.leftover?.load(input.today)
        leftover?.let { p ->
            val prefix = if (deps.spaceId == DEFAULT_SPACE_ID) "" else "${deps.spaceId}:"
            val sent = deps.receipts?.listAll()?.let { r -> overcommitSentGaps(r.map { it.eventKey }, prefix, period.start) }
            overcommitCandidate(p, input.currency, period.start, sent, input.budget?.totalStatus?.limitMinor)?.let { out += it }
        }
        out += moreAdvisorCandidates(ctx, out, leftover, deps)
        return out
    }

    /** سطور مصروف [from]..[to] بعملة المساحة. */
    private suspend fun lines(from: String, to: String, input: AlertGatherInput, names: Map<Id, String>): List<SpendLine> =
        spendLinesOf(deps.txns.listByDateRange(from, to).filter { it.currency == input.currency }, names, deps.allocations)

    private fun capPace(budget: BudgetScreenData, ctx: AdvisorContext, duesInBudget: Boolean): List<AlertCandidate> {
        val out = mutableListOf<AlertCandidate>()
        val input = ctx.input
        val byId = ctx.categories.associateBy { it.id }
        // سقف التصنيف: نفس مطابقة شاشة الميزانية (التصنيف نفسه)، والتصنيف اللي المستخدم قفل تنبيهه ما بيتنبهش
        for (cb in budget.categoryBudgets) {
            if (!cb.notifyEnabled) continue
            val amounts = ctx.lines.filter { it.categoryId == cb.categoryId }.map { it.amountMinor }
            val reading = readPace(amounts, cb.limitMinor, input.today, input.period)
            val label = byId[cb.categoryId]?.name ?: continue
            capPaceCandidate(label, "cat:${cb.categoryId}", cb.limitMinor, reading, input.today, input.period, input.currency)?.let { out += it }
        }
        val total = budget.budget?.totalLimitMinor ?: return out
        val dues = if (duesInBudget) emptySet() else ctx.duesIds
        val amounts = ctx.lines.filter { it.categoryId !in dues }.map { it.amountMinor }
        val reading = readPace(amounts, total, input.today, input.period)
        capPaceCandidate(uiText(TextKey.ADVISOR_TOTAL_BUDGET), "total", total, reading, input.today, input.period, input.currency)?.let { out += it }
        return out
    }

    private fun habits(goals: List<GoalProgress>, ctx: AdvisorContext): List<AlertCandidate> {
        val input = ctx.input
        val goal = goalForHabit(goals, input.currency) ?: return emptyList()
        val parentOf = ctx.categories.associate { it.id to it.parentId }
        val discretionary = ctx.lines.mapNotNull { it.categoryId }.distinct().filter { isDiscretionary(it, parentOf) }
        val caps = input.budget?.categoryBudgets.orEmpty().associate { it.categoryId to it.limitMinor }
        return discretionary.mapNotNull { id ->
            val usual = ctx.usual(id)
            val reading = readPace(ctx.lines.filter { it.categoryId == id }.map { it.amountMinor }, caps[id] ?: usual, input.today, input.period)
            habitVsGoalCandidate(HabitCheck(id, ctx.names[id] ?: return@mapNotNull null, reading, usual, goal, input.period, input.currency))
        }
    }

    /**
     * آخر [HABIT_BASELINE_MONTHS] شهور مالية **مكتملة** (من يوم الراتب): سطور مصروف كل شهر، أو null لو الشهر مش معروف
     * (مفيش ولا عملية فيه، أو فيه عملية من غير نوع — زي شاشة الميزانية).
     */
    private suspend fun baselineMonths(input: AlertGatherInput, payday: Int, names: Map<Id, String>): List<List<SpendLine>?> =
        (1..HABIT_BASELINE_MONTHS).map { i ->
            val p: Period = shiftPeriod(input.period, -i, payday)
            val rows = deps.txns.listByDateRange(p.start, p.end).filter { it.currency == input.currency }
            // الداخل المستني (§75-1) عمره ما بيبقى صرف ⇒ ما بيخلّيش الشهر «مش معروف» للمعتاد
            if (rows.isEmpty() || assessCoverage(withEstimatedKinds(rows, names).withoutPendingIncoming).unclassified > 0) null else spendLinesOf(rows, names, deps.allocations)
        }
}

/** نصيبك من كل عملية مصروف (نفس قواعد `categoryDistribution`). */
internal suspend fun spendLinesOf(rows: List<Transaction>, names: Map<Id, String>, allocations: AllocationRepository): List<SpendLine> {
    val counted = withEstimatedKinds(rows, names).transactions
    val shares = allocations.listByTransactionIds(counted.map { it.id })
    return counted.filter { countsAsPersonalExpense(it.economicKind) && !it.excludedFromBudget }
        .map { SpendLine(it.id, it.occurredAt, it.categoryId, personalShareOf(it, shares), recurringKey(it)) }.filter { it.amountMinor > 0 }
}
