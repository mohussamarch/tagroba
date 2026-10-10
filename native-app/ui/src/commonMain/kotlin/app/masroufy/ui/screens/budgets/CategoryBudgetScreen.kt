package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.ApproxBadge
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.OperationRow
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.screens.operations.OperationDetailRoute
import app.masroufy.ui.screens.operations.ReviewQueueRoute
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type

/**
 * «ميزانية تصنيف» (`CategoryBudget`): المصروف قصاد السقف (البطاقة البطلة بخط «النهارده») · الوتيرة · المتوسط والصرف غير المعتاد ·
 * «السقف والتنبيه» (`BudgetLimitSheet`) · الفرعيات · عمليات الشهر. الحالات: عادي · فاضي (مفيش عمليات) · غير متاح (أنواع الشهر مش معروفة).
 */
@Composable
fun CategoryBudgetScreen(categoryId: String) {
    val space = LocalSpace.current
    val deps = space.budgets
    val today = space.shell.today()
    var reload by remember { mutableIntStateOf(0) }
    val state = rememberLoad(space to categoryId, reload) { loadCategoryBudget(deps, categoryId, today, space.space.currency) }
    val ready = (state as? Load.Ready)?.value
    DetailScaffold(
        title = ready?.name ?: "",
        subtitle = ready?.periodLine,
        dot = ready?.let { parseHexColor(it.colorHex) },
    ) {
        when (state) {
            Load.Loading -> {
                item { Skeleton(Modifier.fillMaxWidth().height(150.dp), radius = Radius.hero, strong = true) }
                item { Skeleton(Modifier.fillMaxWidth().height(220.dp)) }
            }
            is Load.Failed -> item { ErrorCard(t(UiKey.BUDGETS_ERROR_TITLE), t(UiKey.BUDGETS_ERROR_BODY), { reload++ }) }
            is Load.Ready -> {
                val ui = state.value
                if (ui == null) item { EmptyState(t(TextKey.CATEGORY_THIS_NOT_FOUND)) } else categoryBudgetItems(ui, deps) { reload++ }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.categoryBudgetItems(ui: CategoryBudgetUi, deps: BudgetsDeps, refresh: () -> Unit) {
    if (!ui.known) item(key = "na") { UnknownCard(ui) }
    item(key = "hero") { CategoryHero(ui) }
    if (ui.known) item(key = "pace") { PaceCard(ui) }
    item(key = "tiles") { Tiles(ui) }
    item(key = "limit") { LimitSection(ui, deps, refresh) }
    item(key = "subs") { SubsSection(ui) }
    item(key = "tx") { TransactionsSection(ui) }
}

@Composable
private fun UnknownCard(ui: CategoryBudgetUi) {
    val nav = LocalNavigator.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Ink.alertBg).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(t(UiKey.CAT_BUDGET_NA_TITLE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.focus))
        BasicText(t(UiKey.CAT_BUDGET_NA_BODY), style = Type.caption().copy(color = Ink.focus))
        // «راجعها» ⇒ مراجعة الغامض (منطقة العمليات) — لحد الدمج «قيد البناء»
        PrimaryButton(t(UiKey.CAT_BUDGET_REVIEW), { nav.push(ReviewQueueRoute) }, height = 44.dp)
    }
}

/** ألوان الشارة على البترولي (النموذج: فاتحة على الغامق). */
private fun heroInk(tone: Tone): Color = when (tone) {
    Tone.OVER -> Ink.rose
    Tone.NEAR -> Ink.amber
    else -> Ink.onHeroMuted
}

@Composable
private fun CategoryHero(ui: CategoryBudgetUi) {
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(UiKey.CAT_BUDGET_HERO, ui.monthName), style = Type.body().copy(color = Ink.onHeroMuted))
                val ink = heroInk(ui.tone)
                BasicText(
                    ui.chip,
                    Modifier.clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp),
                    style = Type.of(12, FontWeight.Bold).copy(color = ink),
                )
            }
            val limit = ui.limitMinor
            if (!ui.known) {
                BasicText(t(TextKey.NOT_AVAILABLE), style = Type.of(28, FontWeight.Bold).copy(color = Color.White))
                val waiting = if (limit != null) t(UiKey.CAT_BUDGET_NA_LIMIT, plain(limit, ui.currency)) else t(UiKey.CAT_BUDGET_NA_WAIT)
                BasicText(waiting, style = Type.caption().copy(color = Ink.onHeroMuted))
                return@Column
            }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.Bottom) {
                    AmountText(ui.spentMinor, ui.currency, size = 32, showCurrency = false, color = Color.White)
                    val of = if (limit != null) t(UiKey.CAT_BUDGET_OF, plain(limit, ui.currency)) else app.masroufy.core.currencySymbol(ui.currency)
                    BasicText(of, style = Type.of(15).copy(color = Ink.onHeroMuted))
                }
            }
            if (ui.percent != null) {
                HeroBar(ui.percent, ui.elapsedPercent, heroInk(ui.tone).takeIf { ui.tone != Tone.OK && ui.tone != Tone.MUTED } ?: Ink.heroProgress)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(ui.leftLine ?: "", style = Type.caption().copy(color = Ink.onHeroMuted))
                    BasicText(t(UiKey.CAT_BUDGET_TODAY_LINE, sentenceNumber(ui.dayIndex), sentenceNumber(ui.totalDays)), style = Type.caption().copy(color = Ink.onHeroMuted))
                }
            } else {
                BasicText(t(UiKey.CAT_BUDGET_NO_LIMIT_LINE), style = Type.caption().copy(color = Ink.onHeroMuted))
            }
        }
    }
}

/** شريط على البترولي + خط أبيض رفيع عند «النهارده» (نسبة الأيام اللي عدّت). */
@Composable
private fun HeroBar(percent: Int, todayPercent: Int, fill: Color) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        ProgressBar(percent, fill, track = Ink.heroTrack)
        val x = maxWidth * (todayPercent.coerceIn(0, 100) / 100f)
        Box(Modifier.padding(start = x).width(2.dp).height(14.dp).clip(RoundedCornerShape(1.dp)).background(Color.White).align(Alignment.CenterStart))
    }
}

@Composable
private fun PaceCard(ui: CategoryBudgetUi) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(t(UiKey.CAT_BUDGET_PACE_TITLE), style = Type.of(15, FontWeight.Bold))
            val over = ui.tone == Tone.OVER
            BasicText(ui.pace1, style = Type.bodyBold().copy(color = if (over) Ink.expense else Ink.text))
            if (ui.pace2 != null) BasicText(ui.pace2, style = Type.of(13))
            if (ui.approxNote != null) Gap8Row { ApproxBadge(); BasicText(ui.approxNote, Modifier.weight(1f), style = Type.of(11).copy(color = Ink.muted)) }
        }
    }
}

@Composable
private fun Tiles(ui: CategoryBudgetUi) {
    Gap8Row(Modifier.fillMaxWidth()) {
        FloatingCard(Modifier.weight(1f), shape = RoundedCornerShape(Radius.control), contentPadding = PaddingValues(12.dp)) {
            BasicText(t(UiKey.BUDGETS_AVG_LABEL), style = Type.caption().copy(color = Ink.muted))
            AmountText(ui.averageMinor, ui.currency, size = 17)
            BasicText(ui.averageNote, style = Type.of(11).copy(color = Ink.muted))
        }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(Radius.control)).background(if (ui.anomalyAlert) Ink.alertBg else Color(0x0D193D33)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(t(UiKey.CAT_BUDGET_ANOM_LABEL), style = Type.captionBold().copy(color = if (ui.anomalyAlert) Ink.focus else Ink.muted))
            BasicText(ui.anomalyText, style = Type.of(13))
        }
    }
}

@Composable
private fun LimitSection(ui: CategoryBudgetUi, deps: BudgetsDeps, refresh: () -> Unit) {
    val toaster = LocalToaster.current
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.CAT_BUDGET_LIMIT_TITLE), style = Type.of(17, FontWeight.Bold))
        LimitTrigger(ui.limit, ui.currency) { open = true }
    }
    BudgetLimitSheet(
        visible = open,
        title = t(UiKey.LIMIT_SHEET_TITLE, ui.name),
        monthName = ui.monthName,
        current = ui.limit,
        spentMinor = ui.spentMinor,
        averageMinor = ui.averageMinor,
        currency = ui.currency,
        onDismiss = { open = false },
        onSave = { ok ->
            attempt(t(UiKey.SHELL_LOAD_FAILED)) {
                deps.setBudget.setCategoryLimit(ui.period, ui.categoryId, ok.limitMinor, notifyEnabled = ok.notify, thresholdPercent = ok.thresholdPercent)
            }.also { if (it == null) { open = false; toaster.show(savedToast(ok, ui.currency)); refresh() } }
        },
        onClear = {
            attempt(t(UiKey.SHELL_LOAD_FAILED)) { deps.setBudget.clearCategoryLimit(ui.period, ui.categoryId) }
                .also { if (it == null) { open = false; toaster.show(t(UiKey.CAT_BUDGET_CLEARED)); refresh() } }
        },
    )
}

@Composable
private fun SubsSection(ui: CategoryBudgetUi) {
    val nav = LocalNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.CAT_BUDGET_SUBS_TITLE), style = Type.of(17, FontWeight.Bold))
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            if (ui.subs.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    BasicText(t(UiKey.CAT_BUDGET_NO_SUBS), Modifier.weight(1f), style = Type.of(13).copy(color = Ink.muted))
                    TonalButton(t(UiKey.CAT_BUDGET_ADD_SUB), { nav.push(CategoriesRoute) }, height = 44.dp)
                }
            }
            ui.subs.forEachIndexed { i, sub ->
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(parseHexColor(sub.colorHex))
                    BasicText(sub.name, Modifier.weight(1f), style = Type.bodyBold(), maxLines = 1)
                    AmountText(sub.spentMinor, ui.currency, size = 14, showCurrency = false)
                }
                if (i < ui.subs.lastIndex) app.masroufy.ui.components.Divider()
            }
        }
    }
}

@Composable
private fun TransactionsSection(ui: CategoryBudgetUi) {
    val nav = LocalNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val title = if (ui.txCount == 0) t(UiKey.CAT_BUDGET_TX_TITLE_EMPTY, ui.monthName)
        else t(UiKey.CAT_BUDGET_TX_TITLE, ui.monthName, sentenceNumber(ui.txCount))
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        if (ui.txCount == 0) {
            EmptyState(t(UiKey.CAT_BUDGET_EMPTY_TITLE, ui.name), t(UiKey.CAT_BUDGET_EMPTY_BODY))
            return@Column
        }
        val color = parseHexColor(ui.colorHex)
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            ui.txRows.forEachIndexed { i, tx ->
                OperationRow(
                    tx.title, tx.subtitle, categoryIcon(ui.iconKey), color, tx.amountMinor, tx.currency, AmountTone.EXPENSE,
                    onClick = { nav.push(OperationDetailRoute(tx.id)) },
                )
                if (i < ui.txRows.lastIndex) app.masroufy.ui.components.Divider()
            }
        }
        if (ui.txCount > ui.txRows.size) {
            BasicText(
                t(UiKey.CAT_BUDGET_TX_NOTE, sentenceNumber(ui.txRows.size), sentenceNumber(ui.txCount)),
                style = Type.caption().copy(color = Ink.muted),
            )
        }
    }
}
