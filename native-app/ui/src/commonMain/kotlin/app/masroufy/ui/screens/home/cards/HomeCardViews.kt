package app.masroufy.ui.screens.home.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.budgets.CategoriesRoute
import app.masroufy.ui.screens.budgets.ProgressBar
import app.masroufy.ui.screens.budgets.SavingsGoalsRoute
import app.masroufy.ui.screens.home.ReadState
import app.masroufy.ui.screens.home.salaryLine
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.DailyAllowanceView
import app.masroufy.usecase.GoalRow
import app.masroufy.usecase.HomeScreenData

/**
 * كروت الرئيسية اللي فيها أرقام (لوحات `HomeDaily` و`HomeBars` في النموذج): «تقدر تصرف النهارده» · «أكتر حاجة صرفت فيها» · «أهدافك».
 * كل رقم جاهز من حالة استخدام (الشاشة بتعرض بس — CLAUDE.md #4)، والمجهول «غير متاح» (CLAUDE.md #10).
 */

/** رأس الكارت: أيقونة 40 · العنوان والسطر الصغير · سهم «افتح». */
@Composable
internal fun CardHead(icon: Lucide, tint: Color, title: String, sub: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(tint, size = 40.dp) { LucideIcon(icon, size = 20.dp, tint = tint) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = Type.bodyBold())
            if (sub != null) BasicText(sub, style = Type.caption().copy(color = Ink.muted))
        }
        LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
    }
}

/** الكارت بحالاته: بيحمّل (هيكل بنفس الارتفاع) · فشل (الرأس + «تعذّر التحميل») · جاهز. */
@Composable
internal fun <T> CardShell(
    state: ReadState<T>,
    icon: Lucide,
    tint: Color,
    title: String,
    sub: String?,
    onClick: () -> Unit,
    loadingHeight: Int = 150,
    body: @Composable ColumnScope.(T) -> Unit,
) {
    if (state is ReadState.Loading) {
        Skeleton(Modifier.fillMaxWidth().height(loadingHeight.dp))
        return
    }
    FloatingCard(Modifier.fillMaxWidth(), onClick = onClick, clickLabel = title) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHead(icon, tint, title, sub)
            when (state) {
                is ReadState.Ready -> body(state.value)
                else -> BasicText(t(UiKey.HOME_CARDS_FAILED), style = Type.caption().copy(color = Ink.muted))
            }
        }
    }
}

@Composable
internal fun DailyCard(state: ReadState<DailyAllowanceView>) {
    val nav = LocalNavigator.current
    val today = LocalSpace.current.shell.today()
    val view = (state as? ReadState.Ready)?.value
    val sub = view?.let { salaryLine(it.payday, today) ?: t(UiKey.HOME_DAILY_UNTIL_MONTH_END) }
    CardShell(state, Lucide.WALLET, Ink.income, t(UiKey.HOME_CARD_DAILY), sub, onClick = { nav.switchTab(Tab.OPERATIONS) }, loadingHeight = 190) { v ->
        val s = v.spend
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            AmountText(s.perDayMinor, v.currency, size = 30)
            if (s.perDayMinor != null) BasicText(t(UiKey.HOME_DAILY_PER_DAY), style = Type.of(13).copy(color = Ink.muted))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgressBar(if (v.period.days > 0) v.passedDays * 100 / v.period.days else 0, Ink.income)
            BasicText(t(UiKey.HOME_DAILY_DAY_OF, (v.passedDays + 1).toString(), v.period.days.toString()), style = Type.caption().copy(color = Ink.muted))
        }
        val basis = when {
            s.perDayMinor == null -> t(UiKey.HOME_DAILY_NA)
            s.short -> t(UiKey.HOME_DAILY_SHORT)
            else -> t(UiKey.HOME_DAILY_BASIS, amountLabel(s.onHandMinor, v.currency), amountLabel(s.duesMinor, v.currency))
        }
        BasicText(basis, style = Type.caption().copy(color = Ink.muted))
    }
}

@Composable
internal fun TopCard(state: ReadState<HomeScreenData>) {
    val nav = LocalNavigator.current
    val deps = LocalSpace.current
    val top = deps.home.cards.top
    val view = remember(state) { (state as? ReadState.Ready)?.value?.let { top.of(it) } }
    CardShell(state, Lucide.SHOPPING_BAG, Ink.transfer, t(UiKey.HOME_CARD_TOP), t(UiKey.HOME_TOP_SUB), onClick = { nav.push(CategoriesRoute) }) { _ ->
        val rows = view?.rows.orEmpty()
        if (rows.isEmpty()) BasicText(t(UiKey.HOME_TOP_EMPTY), style = Type.caption().copy(color = Ink.muted))
        rows.forEach { r ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    BasicText(r.name ?: t(UiKey.HOME_TOP_UNCATEGORIZED), style = Type.of(13, FontWeight.Bold))
                    AmountText(r.amountMinor, deps.space.currency, size = 13, weight = FontWeight.Normal, color = Ink.muted)
                }
                ProgressBar(r.barPercent, Ink.income, height = 6.dp)
            }
        }
    }
}

@Composable
internal fun GoalsCard(state: ReadState<List<GoalRow>>) {
    val nav = LocalNavigator.current
    CardShell(state, Lucide.PIGGY_BANK, Ink.income, t(UiKey.HOME_CARD_GOALS), t(UiKey.HOME_GOALS_SUB), onClick = { nav.push(SavingsGoalsRoute) }) { goals ->
        if (goals.isEmpty()) BasicText(t(UiKey.HOME_GOALS_EMPTY), style = Type.caption().copy(color = Ink.muted))
        goals.forEach { g ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(g.name, style = Type.of(13, FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1)
                    val of = t(UiKey.HOME_GOALS_OF, amountLabel(g.savedMinor, g.currency), amountLabel(g.targetMinor, g.currency, showCurrency = false))
                    // النسبة جوه عزل اتجاه عشان «62%» ما تتقلبش «%62»
                    BasicText(of + (g.percent?.let { "، ⁦$it%⁩" } ?: ""), style = Type.of(12).copy(color = Ink.muted), maxLines = 1)
                }
                ProgressBar(g.percent ?: 0, Ink.income, height = 6.dp)
            }
        }
    }
}
