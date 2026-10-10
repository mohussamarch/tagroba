package app.masroufy.usecase

import app.masroufy.core.AssistEntityType
import app.masroufy.core.AssistIntent
import app.masroufy.core.AssistRange
import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistUnderstanding
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.Period
import app.masroufy.core.ScreenLink
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.core.UserProfile
import app.masroufy.core.assistPeriodLabel
import app.masroufy.core.categoryWithChildren
import app.masroufy.core.dayBefore
import app.masroufy.core.periodForDate
import app.masroufy.core.resolveAssistRange

/**
 * إجابات أسئلة البيانات (§78 + القاعدة 10 «لا رقم بلا مصدر»): **كل رقم من حالة استخدام موجودة زي ما رجّعته بالظبط** — المساعد عمره ما
 * بيحسب ولا بيقدّر رقم بنفسه. المجهول ⇒ «غير متاح» + السبب، والمصدر الناقص (null في [AssistantSources]) ⇒ «غير متاح» برضه.
 * المبالغ اللي في النص بتتجمع في [AssistReply.amounts] عشان الاختبارات تقارنها برقم حالة الاستخدام نفسها.
 */
class AssistantAnswers(private val s: AssistantSources) {
    suspend fun answer(u: AssistUnderstanding, ctx: AssistContext): AssistReply {
        val profile = s.profileOrNull()
        val k = AnswerKit(s, ctx, u, profile, profile?.payday ?: DEFAULT_PAYDAY)
        return when (u.intent) {
            AssistIntent.SPEND_TOTAL -> k.spendTotal()
            AssistIntent.SPEND_CATEGORY -> k.spendCategory()
            AssistIntent.SPEND_MERCHANT -> k.spendMerchant()
            AssistIntent.SPEND_PERSON -> k.spendPerson()
            AssistIntent.SPEND_BIGGEST -> k.spendBiggest()
            AssistIntent.SPEND_COMPARE -> k.spendCompare()
            AssistIntent.INCOME -> k.income()
            AssistIntent.REMAINING -> k.remaining()
            AssistIntent.DAILY_ALLOWANCE -> k.dailyAllowance()
            AssistIntent.FORECAST -> k.forecast()
            AssistIntent.BUDGET_STATUS -> k.budgetStatus()
            AssistIntent.CATEGORY_BUDGET -> k.categoryBudget()
            AssistIntent.CASH_ON_HAND -> k.cashOnHand()
            AssistIntent.ON_HAND -> k.onHand()
            AssistIntent.OWED_TO_ME -> k.owedToMe()
            AssistIntent.I_OWE -> k.iOwe()
            AssistIntent.PERSON_BALANCE -> k.personBalance()
            AssistIntent.DEBTS_OVERDUE -> k.debtsOverdue()
            AssistIntent.NEXT_SALARY -> k.nextSalary()
            AssistIntent.SALARY_AMOUNT -> k.salaryAmount()
            AssistIntent.LAST_AT_MERCHANT -> k.lastAtMerchant()
            AssistIntent.GOAL_PROGRESS -> k.goalProgress()
            AssistIntent.ZAKAT -> k.zakat()
            AssistIntent.DUES_UPCOMING -> k.duesUpcoming()
            AssistIntent.BILLS -> k.bills()
            AssistIntent.PENDING_REVIEW -> k.pendingReview()
            AssistIntent.ASSETS -> k.assets()
            AssistIntent.EVENT_SPEND -> k.eventSpend()
            AssistIntent.PROJECT_SPEND -> k.projectSpend()
            AssistIntent.ROSCA -> k.rosca()
            AssistIntent.INSTALLMENTS -> k.installments()
            AssistIntent.OCCASIONS -> k.occasions()
            else -> k.na()
        }
    }
}

/** أدوات الإجابة: الفترة المطلوبة (نفس الشهر المالي بتاع الرئيسية) واسمها، ومنشئ الرد. */
internal class AnswerKit(val s: AssistantSources, val ctx: AssistContext, val u: AssistUnderstanding, val profile: UserProfile?, val payday: Int) {
    val range: AssistRange = resolveAssistRange(u.signals.period, ctx.today, payday)
    val label: String = assistPeriodLabel(u.signals.period)
    val current: Period get() = periodForDate(ctx.today, payday)
    val lexicon get() = u.signals.lexicon

    fun rb() = ReplyBuilder(ctx.currency)

    fun subject(type: AssistEntityType) = u.subject?.takeIf { it.type == type } ?: u.signals.entity(type)

    /** «غير متاح» + السبب (لو فيه) + رابط الشاشة. */
    fun na(reason: TextKey? = null, vararg links: ScreenLink): AssistReply {
        val b = rb()
        b.line(reason ?: TextKey.ASSIST_NA)
        links.forEach(b::link)
        return b.build()
    }

    suspend fun home(period: Period) = s.home?.load(LoadHomeScreenRequest(period, ctx.today, payday, includeHistory = false))

    suspend fun budget(period: Period) = s.budget?.load(LoadBudgetScreenRequest(period, ctx.today, payday))

    fun categoryName(id: String): String = lexicon.categories.firstOrNull { it.id == id }?.name ?: subject(AssistEntityType.CATEGORY)?.name.orEmpty()
}

internal suspend fun AnswerKit.spendTotal(): AssistReply {
    val b = rb()
    val period = range.period
    if (period != null) {
        val h = home(period) ?: return na()
        val e = h.expenseMinor
        if (e == null) {
            b.line(TextKey.ASSIST_SPENT_UNKNOWN, label, h.needsReviewCount.toString())
            b.link(ScreenLink.of(AssistScreen.REVIEW_QUEUE))
        } else {
            b.line(TextKey.ASSIST_SPENT, b.money(e), label)
            if (h.partial) b.approx(TextKey.ASSIST_PARTIAL, h.needsReviewCount.toString())
            h.excludedExpenseMinor?.takeIf { it > 0 }?.let { b.line(TextKey.ASSIST_EXCLUDED, b.money(it)) }
        }
    } else {
        val m = s.money?.load(range.from, range.to) ?: return na()
        val e = m.expenseMinor
        if (e == null) b.line(TextKey.ASSIST_SPENT_NA, label) else b.line(TextKey.ASSIST_SPENT, b.money(e), label)
    }
    b.link(ScreenLink.of(AssistScreen.OPERATIONS))
    return b.build()
}

internal suspend fun AnswerKit.spendCategory(): AssistReply {
    val cat = subject(AssistEntityType.CATEGORY) ?: return na(null, ScreenLink.of(AssistScreen.CATEGORIES))
    val cs = s.categorySpend?.load(range.from, range.to, categoryWithChildren(cat.id, lexicon.categories)) ?: return na()
    val name = quoted(categoryName(cat.id))
    val b = rb()
    val amount = cs.amountMinor
    if (amount == null) {
        b.line(TextKey.ASSIST_CATEGORY_UNKNOWN, name, label)
    } else {
        b.line(TextKey.ASSIST_SPENT_CATEGORY, b.money(amount), name, label, assistTimes(cs.count))
        if (cs.approximate) b.approx(TextKey.ASSIST_PARTIAL, cs.needsReviewCount.toString())
    }
    val status = range.period?.let { budget(it) }?.lines?.firstOrNull { it.categoryId == cat.id }?.status
    if (status != null) {
        b.line(TextKey.ASSIST_OF_CAP, b.money(status.limitMinor), (status.usedTenthPercent / 10).toString())
        b.link(ScreenLink.of(AssistScreen.CATEGORY_BUDGET, "categoryId" to cat.id))
    } else {
        b.link(ScreenLink.of(AssistScreen.OPERATIONS))
    }
    return b.build()
}

internal suspend fun AnswerKit.spendMerchant(): AssistReply {
    val ent = subject(AssistEntityType.MERCHANT) ?: return na()
    val merchant = lexicon.merchants.firstOrNull { it.id == ent.id } ?: return na()
    val cs = s.categorySpend?.byMerchant(range.from, range.to, merchant) ?: return na()
    val b = rb()
    val amount = cs.amountMinor
    if (amount == null) b.line(TextKey.ASSIST_MERCHANT_UNKNOWN, quoted(merchant.displayName), label)
    else b.line(TextKey.ASSIST_SPENT_MERCHANT, b.money(amount), quoted(merchant.displayName), label, assistTimes(cs.count))
    if (cs.approximate) b.approx(TextKey.ASSIST_PARTIAL, cs.needsReviewCount.toString())
    b.link(ScreenLink.of(AssistScreen.MERCHANT, "merchantId" to merchant.id))
    return b.build()
}

/** الهدايا والسلف **منفصلين** — عمرهم ما بيتجمعوا؛ والرصيد الحالي من الديون لكل بلد وعملة. */
internal suspend fun AnswerKit.spendPerson(): AssistReply {
    val p = subject(AssistEntityType.PERSON) ?: return na(null, ScreenLink.of(AssistScreen.PEOPLE))
    val ps = s.categorySpend?.byPerson(range.from, range.to, p.id) ?: return na()
    val b = rb()
    b.line(TextKey.ASSIST_PERSON_SPEND, label, p.name, b.money(ps.giftsMinor), assistTimes(ps.giftCount), b.money(ps.lentMinor), assistTimes(ps.lentCount))
    personLines(b, p.id, p.name)
    b.link(ScreenLink.of(AssistScreen.PERSON_PROFILE, "personId" to p.id))
    return b.build()
}

/** أكبر ٣ تصنيفات من توزيع الرئيسية. فترة مش شهر مالي ⇒ الشهر الحالي، والاسم بيقول كده (ما بنحسبش توزيع تاني). */
internal suspend fun AnswerKit.spendBiggest(): AssistReply {
    val period = range.period ?: current
    val shown = if (range.period == null) assistPeriodLabel(null) else label
    val h = home(period) ?: return na()
    val names = h.categories.associate { it.id to it.name }
    val top = h.distribution.filter { it.categoryId != null && it.amountMinor > 0 }.take(3)
    val b = rb()
    if (top.isEmpty()) {
        b.line(TextKey.ASSIST_BIGGEST_NONE, shown)
    } else {
        b.line(TextKey.ASSIST_BIGGEST, shown, assistList(top.map { "${names[it.categoryId].orEmpty()} ${b.money(it.amountMinor)}" }))
        if (h.partial) b.approx(TextKey.ASSIST_PARTIAL, h.needsReviewCount.toString())
    }
    b.link(ScreenLink.of(AssistScreen.HOME))
    return b.build()
}

/** الفرق بيظهر بس لما الرقمين معروفين (طرح رقمين من حالة الاستخدام — مش تقدير). */
internal suspend fun AnswerKit.spendCompare(): AssistReply {
    val period = range.period ?: current
    val previous = periodForDate(dayBefore(period.start), payday)
    val cat = subject(AssistEntityType.CATEGORY)
    val (now, before) = if (cat != null) {
        val ids = categoryWithChildren(cat.id, lexicon.categories)
        val spend = s.categorySpend ?: return na()
        spend.load(period.start, period.end, ids).amountMinor to spend.load(previous.start, previous.end, ids).amountMinor
    } else {
        val h = home(period) ?: return na()
        val hist = s.history?.load(period, payday, h) ?: return na()
        h.expenseMinor to hist.firstOrNull { it.period.key == previous.key }?.expenseMinor
    }
    val b = rb()
    val thisLabel = assistPeriodLabel(null).takeIf { period.key == current.key } ?: label
    val prevLabel = uiText(TextKey.ASSIST_PERIOD_BEFORE_IT)
    if (now == null || before == null) {
        b.line(TextKey.ASSIST_COMPARE_NA)
    } else {
        val key = when {
            now > before -> TextKey.ASSIST_COMPARE_MORE
            now < before -> TextKey.ASSIST_COMPARE_LESS
            else -> TextKey.ASSIST_COMPARE_SAME
        }
        val nowText = b.money(now)
        val beforeText = b.money(before)
        b.line(key, nowText, thisLabel, beforeText, prevLabel, if (now == before) "" else b.money(if (now > before) now - before else before - now))
    }
    b.link(ScreenLink.of(AssistScreen.HOME))
    return b.build()
}

/** الدخل الحقيقي و«حركة الفلوس» جنب بعض — عمرهم ما بيتجمعوا (§58). */
internal suspend fun AnswerKit.income(): AssistReply {
    val b = rb()
    val period = range.period
    if (period != null) {
        val h = home(period) ?: return na()
        val i = h.incomeMinor
        if (i == null) b.line(TextKey.ASSIST_INCOME_NA, label, h.needsReviewCount.toString()) else b.line(TextKey.ASSIST_INCOME, label, b.money(i))
        if (i != null && h.partial) b.approx(TextKey.ASSIST_PARTIAL, h.needsReviewCount.toString())
    } else {
        val m = s.money?.load(range.from, range.to) ?: return na()
        val i = m.incomeMinor
        if (i == null) b.line(TextKey.ASSIST_INCOME_UNKNOWN, label) else b.line(TextKey.ASSIST_INCOME, label, b.money(i))
        b.line(TextKey.ASSIST_CASH_FLOW, b.money(m.cash.inMinor), b.money(m.cash.outMinor))
    }
    b.link(ScreenLink.of(AssistScreen.INCOME_SOURCES))
    return b.build()
}
