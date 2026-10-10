package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.ApproxBadge
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «الميزانيات» (`Budgets`) — **تاني خانة في مبدّل «العمليات»** ([app.masroufy.ui.nav.Slots.BUDGETS]): السقف الإجمالي (أو «لم تحدّد سقفًا»
 * + نسخ سقوف الشهر اللي فات) · المتوسط **معلومة مش سقف** · الصرف غير المعتاد · سطر لكل تصنيف بلونه وشريطه ⇒ «ميزانية تصنيف» ·
 * المواعيد الجاية و«احسبه من فلوسي» · «المتبقي تقريبًا بعد المحجوز». الحالات: بيحمّل · خطأ · فاضي (مفيش سقف) · غير متاح · تقريبي.
 * ⚠️ الشهر = الشهر المالي الحالي (اختيار الشهر من رأس «العمليات» بيتوصّل وقت الدمج — الخانة مالهاش مُدخلات).
 */
@Composable
fun BudgetsPanel() {
    val space = LocalSpace.current
    val deps = space.budgets
    val today = space.shell.today()
    var reload by remember { mutableIntStateOf(0) }
    val state = rememberLoad(space, reload) { loadBudgets(deps, today, space.space.currency) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (state) {
            Load.Loading -> {
                Skeleton(Modifier.fillMaxWidth().height(168.dp), strong = true)
                Skeleton(Modifier.fillMaxWidth().height(240.dp))
            }
            is Load.Failed -> ErrorCard(t(UiKey.BUDGETS_ERROR_TITLE), t(UiKey.BUDGETS_ERROR_BODY), { reload++ })
            is Load.Ready -> BudgetsContent(state.value, deps, today) { reload++ }
        }
    }
}

@Composable
private fun BudgetsContent(ui: BudgetsUi, deps: BudgetsDeps, today: String, refresh: () -> Unit) {
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var totalSheet by remember { mutableStateOf(false) }
    var reserveFor by remember { mutableStateOf<UpcomingUi?>(null) }
    TotalSection(
        ui,
        onEdit = { totalSheet = true },
        onCopy = {
            scope.launch {
                val error = attempt(t(UiKey.SHELL_LOAD_FAILED)) { deps.setBudget.copyFrom(ui.prevPeriodKey, ui.period) }
                toaster.show(error ?: t(UiKey.BUDGETS_COPIED, ui.prevMonthName))
                if (error == null) refresh()
            }
        },
    )
    InfoTiles(ui)
    BasicText(t(UiKey.BUDGETS_CATS_TITLE), style = Type.section())
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        if (ui.lines.isEmpty()) BasicText(t(UiKey.BUDGETS_CATS_EMPTY), Modifier.padding(vertical = 12.dp), style = Type.of(13).copy(color = Ink.muted))
        ui.lines.forEachIndexed { i, line ->
            CategoryLineRow(line, ui) { nav.push(CategoryBudgetRoute(line.categoryId)) }
            if (i < ui.lines.lastIndex) app.masroufy.ui.components.Divider()
        }
    }
    UpcomingSection(
        ui,
        onToggle = { u ->
            val known = u.item.amountMinor
            when {
                u.reserved -> scope.launch {
                    val error = attempt(t(UiKey.SHELL_LOAD_FAILED)) { deps.reservations.uncount(u.item.type, u.item.sourceId, u.item.date) }
                    toaster.show(error ?: t(UiKey.BUDGETS_UNRESERVED_TOAST))
                    if (error == null) refresh()
                }
                // ميعاد من غير مبلغ معروف ⇒ المستخدم بيكتب المبلغ (`RESERVATION_AMOUNT_NEEDED`)
                known == null -> reserveFor = u
                else -> scope.launch {
                    val error = attempt(t(UiKey.SHELL_LOAD_FAILED)) { deps.reservations.countUpcomingItem(u.item.type, u.item.sourceId, u.item.date, today) }
                    toaster.show(error ?: t(UiKey.BUDGETS_RESERVED_TOAST, plain(known, u.item.currency), u.item.title))
                    if (error == null) refresh()
                }
            }
        },
    )
    BudgetLimitSheet(
        visible = totalSheet,
        title = t(UiKey.BUDGETS_TOTAL_SHEET_TITLE),
        monthName = ui.monthName,
        current = ui.totalLimit,
        spentMinor = ui.spentMinor,
        averageMinor = ui.averageMinor,
        currency = ui.currency,
        onDismiss = { totalSheet = false },
        onSave = { ok ->
            attempt(t(UiKey.SHELL_LOAD_FAILED)) { deps.setBudget.setTotalLimit(ui.period, ok.limitMinor, ok.thresholdPercent.takeIf { ok.notify }) }
                .also { if (it == null) { totalSheet = false; toaster.show(savedToast(ok, ui.currency)); refresh() } }
        },
        onClear = {
            attempt(t(UiKey.SHELL_LOAD_FAILED)) { deps.setBudget.clearTotalLimit(ui.period) }
                .also { if (it == null) { totalSheet = false; toaster.show(t(UiKey.CAT_BUDGET_CLEARED)); refresh() } }
        },
    )
    ReserveAmountSheet(reserveFor, onDismiss = { reserveFor = null }) { u, minor ->
        attempt(t(UiKey.SHELL_LOAD_FAILED)) { deps.reservations.countUpcomingItem(u.item.type, u.item.sourceId, u.item.date, today, minor) }
            .also { if (it == null) { reserveFor = null; toaster.show(t(UiKey.BUDGETS_RESERVED_TOAST, plain(minor, u.item.currency), u.item.title)); refresh() } }
    }
}

/** رسالة «حُفظ السقف …» بعد الحفظ (بالتنبيه أو من غيره). */
internal fun savedToast(ok: LimitCheck.Ok, currency: app.masroufy.core.Currency): String =
    if (ok.notify && ok.thresholdPercent != null) t(UiKey.CAT_BUDGET_SAVED_ALERT, plain(ok.limitMinor, currency), app.masroufy.core.sentenceNumber(ok.thresholdPercent))
    else t(UiKey.CAT_BUDGET_SAVED_NO_ALERT, plain(ok.limitMinor, currency))

@Composable
private fun TotalSection(ui: BudgetsUi, onEdit: () -> Unit, onCopy: () -> Unit) {
    when (val total = ui.total) {
        is TotalCard.NoLimit -> FloatingCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(total.title, style = Type.bodyBold())
                BasicText(total.body, style = Type.of(13).copy(color = Ink.muted))
                Gap8Row(Modifier.fillMaxWidth()) {
                    PrimaryButton(t(UiKey.BUDGETS_SET_CAP), onEdit, Modifier.weight(1f))
                    SecondaryButton(t(UiKey.BUDGETS_COPY, ui.prevMonthName), onCopy, Modifier.weight(1f))
                }
            }
        }
        is TotalCard.Unknown -> FloatingCard(Modifier.fillMaxWidth(), onClick = onEdit, clickLabel = t(UiKey.BUDGETS_TOTAL_SHEET_TITLE)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TitleWithPill(total.title, t(TextKey.NOT_AVAILABLE), Tone.MUTED)
                BasicText(t(TextKey.NOT_AVAILABLE), style = Type.of(22, FontWeight.Bold).copy(color = Ink.muted))
                BasicText(total.reason, style = Type.caption().copy(color = Ink.muted))
            }
        }
        is TotalCard.Known -> FloatingCard(Modifier.fillMaxWidth(), onClick = onEdit, clickLabel = t(UiKey.BUDGETS_TOTAL_SHEET_TITLE)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TitleWithPill(total.title, total.chip, total.tone)
                SpentOfLimit(total.spentMinor, total.limitMinor, ui.currency)
                ProgressBar(total.percent, total.tone.bar)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(total.leftLine, style = Type.caption().copy(color = Ink.muted))
                    if (total.dailyLine != null) BasicText(total.dailyLine, style = Type.caption().copy(color = Ink.muted))
                }
                if (total.approxNote != null) {
                    Gap8Row { ApproxBadge(); BasicText(total.approxNote, Modifier.weight(1f), style = Type.caption().copy(color = Ink.muted)) }
                }
            }
        }
    }
}

@Composable
private fun TitleWithPill(title: String, pill: String, tone: Tone) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, style = Type.bodyBold())
        StatusPill(pill, tone)
    }
}

/** «665,000.00 / 800,000.00 ر.س» من الشمال لليمين (النموذج: الرقم 28 والسقف 14 رمادي). */
@Composable
private fun SpentOfLimit(spent: Long, limit: Long, currency: app.masroufy.core.Currency) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.Bottom) {
            AmountText(spent, currency, size = 28, showCurrency = false)
            BasicText(t(UiKey.CAT_BUDGET_OF, plain(limit, currency)), style = Type.body().copy(color = Ink.muted))
        }
    }
}

@Composable
private fun InfoTiles(ui: BudgetsUi) {
    Gap8Row(Modifier.fillMaxWidth()) {
        FloatingCard(Modifier.weight(1f), shape = RoundedCornerShape(Radius.control), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            BasicText(t(UiKey.BUDGETS_AVG_LABEL), style = Type.caption().copy(color = Ink.muted))
            AmountText(ui.averageMinor, ui.currency, size = 17)
            BasicText(if (ui.averageMinor != null) t(UiKey.BUDGETS_AVG_NOTE) else ui.averageReason, style = Type.of(11).copy(color = Ink.muted))
        }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(Radius.control)).background(if (ui.anomalyAlert) Ink.alertBg else androidx.compose.ui.graphics.Color(0x0D193D33))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(t(UiKey.BUDGETS_ANOMALY_LABEL), style = Type.captionBold().copy(color = if (ui.anomalyAlert) Ink.focus else Ink.muted))
            BasicText(ui.anomalyText, style = Type.of(13).copy(color = if (ui.anomalyAlert) Ink.focus else Ink.text))
        }
    }
}
