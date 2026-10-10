package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Project
import app.masroufy.core.ProjectKind
import app.masroufy.core.ProjectRule
import app.masroufy.core.ProjectSummary
import app.masroufy.core.ReviewState
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.daysBetween
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.ProjectDetail
import app.masroufy.usecase.ProjectLists
import app.masroufy.usecase.ProjectRow

/**
 * «المشاريع» و«تفاصيل المشروع» من `ManageProjects.list`/`detail` — ترتيب وكلام بس. الرقم الكبير (كلّفني / كسبت منه) جاي من
 * `PeopleMoney.projectHeadline` (`core` في `:wiring`): الشخصي = صرفت − جاءك، والعمل = جاءك − صرفت وممكن يبقى سالب (§47).
 */
internal data class Headline(val line: MoneyLine, val tone: AmountTone, val colorExpense: Boolean)

internal data class ProjectCardUi(
    val project: Project,
    val kindLabel: String,
    val question: String,
    val headline: Headline?,
    val line: String,
    val deadline: String,
    val approx: String?,
)

internal data class ProjectsUi(val active: List<ProjectCardUi>, val archived: List<ProjectCardUi>)

internal fun kindLabel(k: ProjectKind) = t(if (k == ProjectKind.WORK) UiKey.PROJECTS_KIND_WORK else UiKey.PROJECTS_KIND_PERSONAL)

internal fun questionOf(k: ProjectKind) = t(if (k == ProjectKind.WORK) UiKey.PROJECTS_Q_WORK else UiKey.PROJECTS_Q_PERSONAL)

/** الشغل بإشارة (+ أخضر · − أحمر) · الشخصي «كلّفني» من غير إشارة (أحمر لو كلّفك، أخضر لو جالك أكتر). */
internal fun headlineOf(kind: ProjectKind, s: ProjectSummary, currency: Currency, value: Halalas): Headline? {
    if (s.count == 0) return null
    return if (kind == ProjectKind.WORK) Headline(MoneyLine(value, currency), if (value < 0) AmountTone.EXPENSE else AmountTone.INCOME, value < 0)
    else Headline(MoneyLine(value, currency), AmountTone.PLAIN, value > 0)
}

internal fun deadlineText(deadline: IsoDate?, today: IsoDate): String {
    deadline ?: return t(UiKey.PROJECTS_NO_DEADLINE)
    val days = daysBetween(today, deadline)
    return joinLine(dayMonth(deadline), if (days >= 0) relativeDays(days) else t(UiKey.PROJECTS_PASSED))
}

internal fun projectsUi(lists: ProjectLists, currency: Currency, today: IsoDate, headline: (ProjectKind, ProjectSummary) -> Halalas): ProjectsUi {
    fun card(r: ProjectRow): ProjectCardUi {
        val p = r.project
        val s = r.summary
        val line = if (s.count == 0) t(UiKey.PROJECTS_NO_OPS)
        else t(UiKey.PROJECTS_LINE, amountLabel(s.spentMinor, currency, showCurrency = false), amountLabel(s.receivedMinor, currency, showCurrency = false))
        return ProjectCardUi(
            p, kindLabel(p.kind), questionOf(p.kind), headlineOf(p.kind, s, currency, headline(p.kind, s)), line, deadlineText(p.deadline, today),
            s.estimatedCount.takeIf { it > 0 }?.let { t(UiKey.PROJECTS_APPROX, countOf(it, Noun.OPS)) },
        )
    }
    return ProjectsUi(lists.active.map(::card), lists.archived.map(::card))
}

// ── تفاصيل المشروع

internal data class ProjectOpUi(val txnId: Id, val name: String, val date: String, val byRule: Boolean, val unsure: Boolean, val amountMinor: Halalas, val currency: Currency, val tone: AmountTone)

internal data class RuleUi(val rule: ProjectRule, val mode: String, val text: String, val direction: String)

internal data class ProjectDetailUi(
    val project: Project,
    val sub: String,
    val question: String,
    val headline: Headline?,
    val out: MoneyLine,
    val inn: MoneyLine,
    val formula: String,
    val approx: String?,
    val ops: List<ProjectOpUi>,
    val rules: List<RuleUi>,
)

internal fun modeLabel(m: RuleMatchMode) = t(
    when (m) {
        RuleMatchMode.CONTAINS -> UiKey.PROJECT_DETAIL_MODE_CONTAINS
        RuleMatchMode.STARTS_WITH -> UiKey.PROJECT_DETAIL_MODE_STARTS
        RuleMatchMode.EXACT -> UiKey.PROJECT_DETAIL_MODE_EXACT
    },
)

/** الاتجاه المتخزن: "out" · "in" · "any". */
internal fun dirLabel(d: String) = t(
    when (d) {
        "in" -> UiKey.PROJECT_DETAIL_DIR_IN
        "any" -> UiKey.PROJECT_DETAIL_DIR_ANY
        else -> UiKey.PROJECT_DETAIL_DIR_OUT
    },
)

internal fun projectDetailUi(d: ProjectDetail, currency: Currency, headline: Halalas): ProjectDetailUi {
    val p = d.project
    val s = d.summary
    val sub = joinLine(kindLabel(p.kind), p.deadline?.let { t(UiKey.PROJECT_DETAIL_UNTIL, dayMonth(it)) } ?: t(UiKey.PROJECTS_NO_DEADLINE))
    val ops = d.transactions.map { pt ->
        val txn = pt.transaction
        val out = txn.observedDirection == Direction.OUT
        ProjectOpUi(
            txn.id, txnTitle(txn), dayMonth(txn.occurredAt), pt.source == "rule", txn.reviewState == ReviewState.NEEDS_REVIEW,
            txn.amountMinor, txn.currency, if (out) AmountTone.EXPENSE else AmountTone.INCOME,
        )
    }
    return ProjectDetailUi(
        project = p,
        sub = sub,
        question = questionOf(p.kind),
        headline = headlineOf(p.kind, s, currency, headline),
        out = MoneyLine(s.spentMinor, currency),
        inn = MoneyLine(s.receivedMinor, currency),
        formula = t(if (p.kind == ProjectKind.WORK) UiKey.PROJECT_DETAIL_FORMULA_WORK else UiKey.PROJECT_DETAIL_FORMULA_PERSONAL),
        approx = s.estimatedCount.takeIf { it > 0 }?.let { t(UiKey.PROJECTS_APPROX, countOf(it, Noun.OPS)) },
        ops = ops,
        rules = d.rules.map { RuleUi(it, modeLabel(it.matchMode), t(UiKey.PROJECT_DETAIL_QUOTED, it.matchText), t(UiKey.PROJECT_DETAIL_DIR, dirLabel(it.direction))) },
    )
}
