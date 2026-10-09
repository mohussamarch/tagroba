package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.Direction
import app.masroufy.core.DueFlow
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.LeftoverProjection
import app.masroufy.core.Period
import app.masroufy.core.SubscriptionPlace
import app.masroufy.core.Transaction
import app.masroufy.core.WeekSpend
import app.masroufy.core.addMoney
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.core.assessCoverage
import app.masroufy.core.beforePaydayCandidate
import app.masroufy.core.bigOneCandidate
import app.masroufy.core.billJumpCandidate
import app.masroufy.core.countingDate
import app.masroufy.core.countingReadStart
import app.masroufy.core.dupSubsCandidates
import app.masroufy.core.periodForDate
import app.masroufy.core.goalNearCandidate
import app.masroufy.core.lastWeekEnd
import app.masroufy.core.payFirstCandidate
import app.masroufy.core.readPace
import app.masroufy.core.recurringKey
import app.masroufy.core.shiftMonths
import app.masroufy.core.sumMoney
import app.masroufy.core.unusualSpendCandidate
import app.masroufy.core.usualMonthMinor
import app.masroufy.core.weeklySummaryCandidate
import app.masroufy.core.withEstimatedKinds
import kotlin.coroutines.cancellation.CancellationException

/**
 * الأفكار الثمانية التانية للمساعد (OVERRIDES §68): unusual · beforePayday · payFirst · billJump · dupSubs · bigOne · goalNear · weekly.
 * [already] = اللي اتولد قبلها (habitVsGoal) — unusual ما بيتكررش على نفس البند في نفس الفترة.
 */
internal suspend fun moreAdvisorCandidates(
    ctx: AdvisorContext,
    already: List<AlertCandidate>,
    leftover: LeftoverProjection?,
    deps: AdvisorSignalsDeps,
): List<AlertCandidate> {
    val out = mutableListOf<AlertCandidate>()
    val input = ctx.input
    val period = input.period
    val currency = input.currency
    val usualOutsideDues = usualMonthMinor(ctx.baseline.map { m -> m?.let { rows -> sumMoney(rows.filter { it.categoryId !in ctx.duesIds }.map { it.amountMinor }) } })

    // unusual: أي بند (غير «المستحقات») — قاعدة المعدل نفسها، والبند اللي طلع له habitVsGoal ما بيتكررش
    val caps = input.budget?.categoryBudgets.orEmpty().associate { it.categoryId to it.limitMinor }
    val habitThreads = already.map { it.threadKey }.toSet()
    for (id in ctx.lines.mapNotNull { it.categoryId }.distinct()) {
        if (id in ctx.duesIds || "habit|${period.start}|$id" in habitThreads) continue
        val usual = ctx.usual(id)
        val reading = readPace(ctx.lines.filter { it.categoryId == id }.map { it.amountMinor }, caps[id] ?: usual, input.today, period)
        unusualSpendCandidate(id, ctx.names[id] ?: continue, reading, usual, period, currency)?.let { out += it }
    }

    // beforePayday: المستحقات اللي ليها مبلغ من النهارده لحد القبض الجاي (من غير يومه)
    leftover?.let { p ->
        val until = p.until
        val dues = if (until == null || deps.calendar == null) 0L else sumMoney(
            deps.calendar.items(input.today, until, input.today)
                .filter { it.flow == DueFlow.PAY && it.currency == currency && it.date < until }
                .mapNotNull { it.amountMinor ?: it.reservedMinor },
        )
        beforePaydayCandidate(p, dues, usualOutsideDues, period.days, input.today, currency)?.let { out += it }
    }

    // payFirst: آخر مرتب (نوعه «مرتب» فعلًا — مش التقدير) بيتحسب للفترة دي أو للجاية (§75-3: اللي نزل قبل يوم الراتب بشوية للشهر
    // الجديد) — التنبيه يوم ما ينزل، وموضوعه شهر حسابه ⇒ مرة واحدة للشهر حتى لو نزل قبل أوله
    val goals = ctx.goals.orEmpty().filter { it.goal.currency == currency }
    if (goals.isNotEmpty()) {
        val salary = deps.txns.listByDateRange(countingReadStart(period.start), input.today)
            .filter { it.currency == currency && it.observedDirection == Direction.IN && it.economicKind == EconomicKind.SALARY }
            .maxByOrNull { it.occurredAt }
        val countedIn = salary?.let { periodForDate(countingDate(it, ctx.payday), ctx.payday) }
        for (g in goals) payFirstCandidate(salary?.occurredAt, g, input.today, period, currency, countedIn)?.let { out += it }
        for (g in goals) goalNearCandidate(g)?.let { out += it }
    }

    // billJump · dupSubs: الاشتراكات والفواتير الدورية الشغالة (الكيان الموجود `RecurringItem`)
    val items = deps.recurring?.listAll().orEmpty().filter { it.active && it.currency == currency && !it.merchantKey.startsWith("manual:") }
    if (items.isNotEmpty()) {
        val history = deps.txns.listByDateRange(shiftMonths(input.today, -13), input.today)
            .filter { it.currency == currency && it.observedDirection == Direction.OUT && it.economicKind in RECURRING_LIKE }
            .groupBy(::recurringKey)
        val places = mutableListOf<SubscriptionPlace>()
        for (item in items) {
            val paid = history[item.merchantKey].orEmpty().distinctBy { it.id }.sortedWith(compareBy<Transaction>({ it.occurredAt }, { it.sourceOrder }))
            val latest = paid.lastOrNull() ?: continue
            if (latest.occurredAt >= period.start) {
                billJumpCandidate(item, latest.id, latest.amountMinor, paid.dropLast(1).map { it.amountMinor })?.let { out += it }
            }
            val categoryId = latest.categoryId
            if (item.kind == "subscription" && categoryId != null) ctx.names[categoryId]?.let { places += SubscriptionPlace(item, categoryId, it) }
        }
        out += dupSubsCandidates(places)
    }

    // bigOne: عملية واحدة كبيرة بالنسبة لشهرك — برا «المستحقات» والفواتير والاشتراكات الدورية (معروفة ومستنية)
    val recurringKeys = items.map { it.merchantKey }.toSet()
    for (line in ctx.lines) {
        if (line.categoryId in ctx.duesIds || line.key in recurringKeys) continue
        bigOneCandidate(line.id, line.amountMinor, line.date, usualOutsideDues, currency)?.let { out += it }
    }

    // weekly: الأسبوع اللي خلص (الحد ⇒ السبت) مقارنة باللي قبله
    val weekEnd = lastWeekEnd(input.today)
    val weekStart = plusDays(weekEnd, -6)
    val prevEnd = plusDays(weekStart, -1)
    val rows = deps.txns.listByDateRange(plusDays(prevEnd, -6), weekEnd).filter { it.currency == currency }
    val thisWeek = weekSpend(rows.filter { it.occurredAt >= weekStart }, ctx, deps)
    val lastWeek = weekSpend(rows.filter { it.occurredAt <= prevEnd }, ctx, deps)
    // §75-15: «عندك N عملية محتاجة تأكيد» في نفس الملخص (حتى لو صرف الأسبوع مش معروف)
    weeklySummaryCandidate(weekEnd, thisWeek, lastWeek, ctx.names, currency, needsConfirmationCount(deps, input.today, period))?.let { out += it }
    return out
}

/**
 * عدد «محتاجة تأكيد» للتذكير — **أسئلة البلد دي بس** (المساعد بيشتغل مرة لكل بلد؛ عدّ واحد مشترك لكل البلاد كان هيطلع نفس التذكير
 * مرتين). مش متوصل أو فشل ⇒ صفر (من غير سطر — ما بنقولش «0» مكان «مش معروف»).
 */
private suspend fun needsConfirmationCount(deps: AdvisorSignalsDeps, today: String, period: Period): Int {
    val count = deps.needsConfirmation ?: return 0
    return try {
        count.load(today, period, deps.spaceId).total
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        0
    }
}

/** نفس أنواع كشف الاشتراكات (`detectRecurring`): شراء أو لسه من غير نوع. */
private val RECURRING_LIKE = setOf(EconomicKind.PURCHASE, EconomicKind.UNCLASSIFIED)

/** مصروف أسبوع، أو null لو فيه عملية من غير نوع (مش معروف) — الداخل المستني (§75-1) مش منها: عمره ما بيبقى صرف. */
private suspend fun weekSpend(rows: List<Transaction>, ctx: AdvisorContext, deps: AdvisorSignalsDeps): WeekSpend? {
    if (assessCoverage(withEstimatedKinds(rows, ctx.names).withoutPendingIncoming).unclassified > 0) return null
    val lines = spendLinesOf(rows, ctx.names, deps.allocations)
    val byCategory = LinkedHashMap<Id?, Halalas>()
    for (l in lines) byCategory[l.categoryId] = addMoney(byCategory[l.categoryId] ?: 0L, l.amountMinor)
    return WeekSpend(sumMoney(lines.map { it.amountMinor }), byCategory, rows.size)
}

private fun plusDays(date: String, days: Int): String = dayNumberToIso(toDayNumber(parseIsoDate(date)) + days)
