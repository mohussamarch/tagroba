package app.masroufy.usecase

import app.masroufy.core.AssistEntityType
import app.masroufy.core.AssistIntent
import app.masroufy.core.AssistLexicon
import app.masroufy.core.AssistMessage
import app.masroufy.core.AssistScreen
import app.masroufy.core.AssistTopic
import app.masroufy.core.BudgetLevel
import app.masroufy.core.Chip
import app.masroufy.core.ChipBar
import app.masroufy.core.ChipRef
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.DueFlow
import app.masroufy.core.DueSource
import app.masroufy.core.FOLLOW_UPS
import app.masroufy.core.MAX_CHIPS
import app.masroufy.core.MAX_LEARNED_CHIPS
import app.masroufy.core.ScreenLink
import app.masroufy.core.StartAction
import app.masroufy.core.StartItem
import app.masroufy.core.StartItemKind
import app.masroufy.core.StartPick
import app.masroufy.core.TAB_CHIPS
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.core.shiftDays
import app.masroufy.core.billAlertThread
import app.masroufy.core.billUnrecorded
import app.masroufy.core.budgetAlertThread
import app.masroufy.core.daysBetween
import app.masroufy.core.dueAlertThread
import app.masroufy.core.formatMoney
import app.masroufy.core.frequentTopics
import app.masroufy.core.periodForDate
import app.masroufy.core.pickStartItems
import app.masroufy.core.smsAlertThread

/**
 * «أمور لم تُنجزها بعد» (آخر §76 على فرع التصميم + ردود المالك 2026-10-09): رسايل بنك مستنية · فاتورة دورية عدّى يومها · تصنيف عند
 * نسبة التنبيه · دين ليك ميعاده فات (**معلومة بس من غير زرار**). كارت مقفول بـ«×» أو إشعاره اتمسح من الجرس ⇒ ما بيظهرش.
 */
class AssistantStart(private val deps: AssistantDeps) {
    suspend fun candidates(ctx: AssistContext, lex: AssistLexicon): List<StartItem> {
        val src = deps.sources
        val payday = src.profileOrNull()?.payday ?: DEFAULT_PAYDAY
        val period = periodForDate(ctx.today, payday)
        val out = mutableListOf<StartItem>()
        src.sms?.takeIf { it.available }?.waiting()?.messageIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            out += StartItem(
                "sms:${ids.last()}", StartItemKind.SMS_WAITING, uiText(TextKey.ASSIST_START_SMS, ids.size.toString()),
                StartAction.OpenScreen(ScreenLink.of(AssistScreen.BANK_SMS), uiText(TextKey.ASSIST_ACTION_REVIEW)), smsAlertThread(ids.last()),
            )
        }
        val rows = src.txns.listByDateRange(shiftDays(period.start, -7), ctx.today)
        lex.recurring.filter { billUnrecorded(it, rows, ctx.today, period) }.sortedBy { it.nextDueAt }.forEach { item ->
            out += StartItem(
                "bill:${item.id}:${item.nextDueAt}", StartItemKind.BILL_UNRECORDED, uiText(TextKey.ASSIST_START_BILL, quoted(item.name), assistDate(item.nextDueAt, ctx.today)),
                StartAction.AskInChat(ChipRef.of(AssistIntent.RECORD_DUE_BILL, item.id), uiText(TextKey.ASSIST_ACTION_RECORD)),
                billAlertThread(item.id, item.nextDueAt, ctx.space.id),
            )
        }
        src.budget?.load(LoadBudgetScreenRequest(period, ctx.today, payday))?.let { bd ->
            val names = bd.categories.associate { it.id to it.name }
            bd.lines.filter { it.status?.thresholdCrossed == true && it.status?.level != BudgetLevel.OVER }
                .sortedByDescending { it.status!!.usedTenthPercent }.forEach { line ->
                    out += StartItem(
                        "budget:${period.start}:${line.categoryId}", StartItemKind.CATEGORY_AT_THRESHOLD,
                        uiText(TextKey.ASSIST_START_BUDGET, quoted(names[line.categoryId].orEmpty()), (line.status!!.usedTenthPercent / 10).toString()),
                        StartAction.OpenScreen(ScreenLink.of(AssistScreen.CATEGORY_BUDGET, "categoryId" to line.categoryId), uiText(TextKey.ASSIST_ACTION_OPEN)),
                        budgetAlertThread(period.start, line.categoryId, ctx.space.id),
                    )
                }
        }
        src.dues?.dueItems(shiftDays(ctx.today, -3650), ctx.today)
            ?.filter { it.flow == DueFlow.RECEIVE && it.source == DueSource.DEBT && it.dueAt < ctx.today }?.sortedBy { it.dueAt }?.forEach { d ->
                out += StartItem(
                    "debt:${d.sourceId}:${d.dueAt}", StartItemKind.DEBT_OVERDUE,
                    uiText(TextKey.ASSIST_START_DEBT, d.title, formatMoney(d.amountMinor, d.currency), assistDays(daysBetween(d.dueAt, ctx.today))),
                    null, dueAlertThread(d, ctx.space.id),
                )
            }
        return out
    }

    suspend fun pick(ctx: AssistContext, lex: AssistLexicon): StartPick {
        val closed = ManageStartCards(deps.stores.forgotten, deps.clock).closedKeys()
        val dismissed = deps.stores.alertDismissals?.listAll().orEmpty().map { it.threadKey }.toSet()
        return pickStartItems(candidates(ctx, lex), closed, dismissed)
    }
}

/** النية بتاخد اسم من نوع إيه (عشان اقتراح المتابعة ينقل نفس الاسم). */
private fun subjectType(intent: AssistIntent?): AssistEntityType? = when (intent) {
    AssistIntent.SPEND_CATEGORY, AssistIntent.CATEGORY_BUDGET, AssistIntent.SPEND_COMPARE -> AssistEntityType.CATEGORY
    AssistIntent.SPEND_MERCHANT, AssistIntent.LAST_AT_MERCHANT -> AssistEntityType.MERCHANT
    AssistIntent.SPEND_PERSON, AssistIntent.PERSON_BALANCE -> AssistEntityType.PERSON
    else -> null
}

/**
 * الاقتراحات تحت مستطيل الكتابة (§78-٥ + النموذج): بعد أول رسالة ⇒ أسئلة متابعة للموضوع؛ قبلها ⇒ «بتسأل عنها كتير» (موضوع اتسأل مرتين
 * أو أكتر، لو التعلم شغال) + اقتراحات الصفحة، أقصى ٦. الزكاة بتتشال لو المحتوى الإسلامي مخفي.
 */
internal fun chipBar(ctx: AssistContext, lex: AssistLexicon, last: AssistMessage?, topics: List<AssistTopic>, learningOn: Boolean, zakat: Boolean): ChipBar {
    fun ok(r: ChipRef) = (zakat || r.topic != AssistScreen.ZAKAT.navWire) && (r.subjectId?.let { entityById(lex, it) != null } ?: true)
    val page = TAB_CHIPS[ctx.tab].orEmpty().filter(::ok)
    fun bar(label: String, refs: List<ChipRef>, learned: Set<ChipRef>) =
        ChipBar(label, refs.distinct().take(MAX_CHIPS).map { Chip(it, chipLabel(it, lex), it in learned) })
    val from = last?.topic?.let { AssistIntent.fromWire(it) }
    val follow = FOLLOW_UPS[from].orEmpty().map { to ->
        ChipRef.of(to, last?.subjectId?.takeIf { subjectType(from) != null && subjectType(from) == subjectType(to) })
    }.filter(::ok)
    if (follow.isNotEmpty()) return bar(uiText(TextKey.ASSIST_CHIPS_FOLLOW), follow + page, emptySet())
    val learned = if (!learningOn) emptyList() else frequentTopics(topics).map { ChipRef(it.topic, it.subjectId) }.filter(::ok).take(MAX_LEARNED_CHIPS)
    val label = if (learned.isNotEmpty()) uiText(TextKey.ASSIST_CHIPS_FREQUENT) else uiText(TextKey.ASSIST_CHIPS_PAGE, uiText(tabScreen(ctx).label))
    return bar(label, learned + page, learned.toSet())
}

private fun tabScreen(ctx: AssistContext): AssistScreen = when (ctx.tab) {
    app.masroufy.core.AssistTab.HOME -> AssistScreen.HOME
    app.masroufy.core.AssistTab.OPERATIONS -> AssistScreen.OPERATIONS
    app.masroufy.core.AssistTab.PEOPLE -> AssistScreen.PEOPLE
    app.masroufy.core.AssistTab.INVESTMENT -> AssistScreen.INVESTMENT
    app.masroufy.core.AssistTab.MORE -> AssistScreen.MORE
}

/** اسم الاقتراح: سؤال جاهز للنية (بالاسم لو فيه) · «افتح X» للشاشة · «أضف عملية بصوتي». */
fun chipLabel(ref: ChipRef, lex: AssistLexicon): String {
    if (ref.voice) return uiText(TextKey.ASSIST_CHIP_VOICE)
    AssistScreen.entries.firstOrNull { it.navWire == ref.topic }?.let { return uiText(TextKey.ASSIST_OPEN_SCREEN, uiText(it.label)) }
    val intent = AssistIntent.fromWire(ref.topic) ?: return ref.topic
    val name = ref.subjectId?.let { entityById(lex, it)?.name }
    if (name != null) NAMED_QUESTIONS[intent]?.let { return uiText(it, name) }
    return uiText(QUESTIONS[intent] ?: TextKey.ASSIST_Q_SPEND_TOTAL)
}

private val NAMED_QUESTIONS: Map<AssistIntent, TextKey> = mapOf(
    AssistIntent.SPEND_CATEGORY to TextKey.ASSIST_QN_SPEND_ON, AssistIntent.EVENT_SPEND to TextKey.ASSIST_QN_SPEND_ON,
    AssistIntent.PROJECT_SPEND to TextKey.ASSIST_QN_SPEND_ON, AssistIntent.CATEGORY_BUDGET to TextKey.ASSIST_QN_CAP,
    AssistIntent.SPEND_MERCHANT to TextKey.ASSIST_QN_SPEND_AT, AssistIntent.LAST_AT_MERCHANT to TextKey.ASSIST_QN_LAST_AT,
    AssistIntent.PERSON_BALANCE to TextKey.ASSIST_QN_PERSON, AssistIntent.SPEND_PERSON to TextKey.ASSIST_QN_GAVE,
    AssistIntent.GOAL_PROGRESS to TextKey.ASSIST_QN_GOAL, AssistIntent.BILLS to TextKey.ASSIST_QN_BILL,
    AssistIntent.RECORD_DUE_BILL to TextKey.ASSIST_QN_RECORD, AssistIntent.SPEND_COMPARE to TextKey.ASSIST_QN_COMPARE,
)

private val QUESTIONS: Map<AssistIntent, TextKey> = mapOf(
    AssistIntent.SPEND_TOTAL to TextKey.ASSIST_Q_SPEND_TOTAL, AssistIntent.SPEND_CATEGORY to TextKey.ASSIST_Q_SPEND_BIGGEST,
    AssistIntent.SPEND_BIGGEST to TextKey.ASSIST_Q_SPEND_BIGGEST, AssistIntent.SPEND_COMPARE to TextKey.ASSIST_Q_SPEND_COMPARE,
    AssistIntent.INCOME to TextKey.ASSIST_Q_INCOME, AssistIntent.REMAINING to TextKey.ASSIST_Q_REMAINING,
    AssistIntent.DAILY_ALLOWANCE to TextKey.ASSIST_Q_DAILY, AssistIntent.FORECAST to TextKey.ASSIST_Q_FORECAST,
    AssistIntent.BUDGET_STATUS to TextKey.ASSIST_Q_BUDGET, AssistIntent.CATEGORY_BUDGET to TextKey.ASSIST_Q_BUDGET,
    AssistIntent.CASH_ON_HAND to TextKey.ASSIST_Q_CASH, AssistIntent.ON_HAND to TextKey.ASSIST_Q_ON_HAND,
    AssistIntent.OWED_TO_ME to TextKey.ASSIST_Q_OWED_TO_ME, AssistIntent.I_OWE to TextKey.ASSIST_Q_I_OWE,
    AssistIntent.PERSON_BALANCE to TextKey.ASSIST_Q_OWED_TO_ME, AssistIntent.SPEND_PERSON to TextKey.ASSIST_Q_OWED_TO_ME,
    AssistIntent.DEBTS_OVERDUE to TextKey.ASSIST_Q_OVERDUE, AssistIntent.NEXT_SALARY to TextKey.ASSIST_Q_NEXT_SALARY,
    AssistIntent.SALARY_AMOUNT to TextKey.ASSIST_Q_SALARY, AssistIntent.LAST_AT_MERCHANT to TextKey.ASSIST_Q_SPEND_TOTAL,
    AssistIntent.SPEND_MERCHANT to TextKey.ASSIST_Q_SPEND_TOTAL, AssistIntent.GOAL_PROGRESS to TextKey.ASSIST_Q_GOAL,
    AssistIntent.ZAKAT to TextKey.ASSIST_Q_ZAKAT, AssistIntent.DUES_UPCOMING to TextKey.ASSIST_Q_DUES,
    AssistIntent.BILLS to TextKey.ASSIST_Q_BILLS, AssistIntent.PENDING_REVIEW to TextKey.ASSIST_Q_PENDING,
    AssistIntent.ASSETS to TextKey.ASSIST_Q_ASSETS, AssistIntent.EVENT_SPEND to TextKey.ASSIST_Q_EVENTS,
    AssistIntent.PROJECT_SPEND to TextKey.ASSIST_Q_PROJECTS, AssistIntent.ROSCA to TextKey.ASSIST_Q_ROSCA,
    AssistIntent.INSTALLMENTS to TextKey.ASSIST_Q_INSTALLMENTS, AssistIntent.OCCASIONS to TextKey.ASSIST_Q_OCCASIONS,
    AssistIntent.SPLIT to TextKey.ASSIST_Q_SPLIT, AssistIntent.RECORD_DUE_BILL to TextKey.ASSIST_Q_BILLS,
)
