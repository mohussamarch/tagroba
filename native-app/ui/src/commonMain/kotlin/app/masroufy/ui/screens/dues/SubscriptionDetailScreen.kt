package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.formatAmount
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.RecurringSaveInput
import kotlinx.coroutines.launch

/**
 * «تفاصيل الاشتراك» (`SubscriptionDetail`): الموعد القادم بحالته · المدفوع آخر ١٢ شهرًا (من عمليات نفس الجهة — `RecurringItemView.paidMinor`،
 * «غير متاح» للخطة اليدوية) · المتوقع سنويًا · المواعيد · «عدّل المبلغ والدورة والموعد» (لوحة) · إيقاف/استئناف المتابعة — كله `ManageRecurring.save`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionDetailScreen(itemId: String) {
    val space = LocalSpace.current
    val deps = space.dues
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val today = space.shell.today()
    val load = rememberLoad(deps, itemId) { deps.recurring.load(today).items.firstOrNull { it.item.id == itemId }?.let { subDetailUi(it, today) } }
    var sheet by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save(input: RecurringSaveInput, done: TextKey) = scope.launch {
        try {
            deps.recurring.save(input)
            toaster.show(t(done))
            sheet = null
            error = null
            DuesChanges.bump()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = failText(e)
        }
    }
    val ui = (load.value as? Load.Ready)?.value
    DuesScaffold(ui?.view?.item?.name ?: t(TextKey.SUBS_TITLE), ui?.kindLine) {
        when (val s = load.value) {
            Load.Loading -> item { LoadingBlocks(150, 240) }
            Load.Failed -> item { LoadFailed(load::reload) }
            is Load.Ready -> {
                val d = s.value
                if (d == null) {
                    item { EmptyState(t(TextKey.RECURRING_NOT_FOUND)) }
                    return@DuesScaffold
                }
                val item = d.view.item
                item(key = "hero") {
                    HeroCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                BasicText(d.nextLabel, style = Type.body().copy(color = Ink.onHeroMuted))
                                StatusChip(d.chipText, d.chip, onHero = true)
                            }
                            BasicText(d.nextText, style = Type.of(26, FontWeight.Bold).copy(color = Color.White))
                            BasicText(d.nextSub, style = Type.caption().copy(color = Ink.onHeroMuted))
                        }
                    }
                }
                item(key = "numbers") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FloatingCard(Modifier.weight(1f)) {
                            BasicText(t(TextKey.SUBS_PAID_12), style = Type.caption().copy(color = Ink.muted))
                            AmountText(d.view.paidMinor, item.currency, size = 18)
                            BasicText(d.paidCount, style = Type.of(11).copy(color = Ink.muted))
                        }
                        FloatingCard(Modifier.weight(1f)) {
                            BasicText(t(TextKey.SUBS_ANNUAL), style = Type.caption().copy(color = Ink.muted))
                            if (item.active) AmountText(d.view.annualMinor, item.currency, size = 18)
                            else ValueText(t(TextKey.SUBS_CHIP_STOPPED), muted = true)
                            BasicText(d.annualNote, style = Type.of(11).copy(color = Ink.muted))
                        }
                    }
                }
                item(key = "dates") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText(t(TextKey.SUBS_DATES), style = Type.section())
                        FloatingCard(Modifier.fillMaxWidth()) {
                            LabelValue(t(TextKey.SUBS_F_CYCLE)) { ValueText(d.cycle) }
                            Divider()
                            LabelValue(t(TextKey.SUBS_F_EXPECTED)) { AmountText(item.expectedMinor, item.currency) }
                            Divider()
                            LabelValue(t(TextKey.SUBS_F_NEXT)) { ValueText(d.nextText) }
                            TonalButton(
                                t(if (item.active) TextKey.SUBS_EDIT else TextKey.SUBS_EDIT_LOCKED), { if (item.active) sheet = "edit" },
                                Modifier.fillMaxWidth(), enabled = item.active,
                            )
                        }
                        BasicText(t(TextKey.SUBS_PAY_NOTE, item.name), style = Type.caption().copy(color = Ink.muted))
                    }
                }
                item(key = "stop") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.active) DangerButton(t(TextKey.SUBS_STOP), { sheet = "stop" }, Modifier.fillMaxWidth())
                        else PrimaryButton(t(TextKey.SUBS_RESUME), { save(item.withActive(true), TextKey.SUBS_RESUMED) }, Modifier.fillMaxWidth())
                        BasicText(t(TextKey.SUBS_STOP_NOTE), style = Type.caption().copy(color = Ink.muted))
                        error?.let { FieldError(it) }
                    }
                }
            }
        }
    }
    val d = ui ?: return
    ConfirmSheet(
        sheet == "stop", t(TextKey.SUBS_STOP_TITLE, d.view.item.name), t(TextKey.SUBS_STOP_BODY), t(TextKey.SUBS_STOP_YES),
        onConfirm = { save(d.view.item.withActive(false), TextKey.SUBS_STOPPED) }, onDismiss = { sheet = null },
    )
    EditSheet(sheet == "edit", d, onSave = { save(it, TextKey.SUBS_EDIT_SAVED) }, onDismiss = { sheet = null; error = null })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditSheet(visible: Boolean, d: SubDetailUi, onSave: (RecurringSaveInput) -> Unit, onDismiss: () -> Unit) {
    val item = d.view.item
    val today = LocalSpace.current.shell.today()
    var amount by remember(visible) { mutableStateOf(formatAmount(item.expectedMinor, item.currency, grouping = false)) }
    var cycle by remember(visible) { mutableStateOf(item.cycleMonths) }
    var next by remember(visible) { mutableStateOf(item.nextDueAt) }
    var problem by remember(visible) { mutableStateOf<String?>(null) }
    Sheet(visible, onDismiss, t(TextKey.SUBS_EDIT_TITLE, item.name), closeLabel = t(TextKey.SHELL_CLOSE)) {
        BasicText(t(TextKey.SUBS_EDIT_TITLE, item.name), style = Type.section())
        MoneyField(amount, { amount = it; problem = null }, t(TextKey.SUBS_F_AMOUNT), item.currency, problem)
        FieldLabel(t(TextKey.SUBS_F_CYCLE))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in SUB_CYCLES) SelectChip(subCycle(c), cycle == c, { cycle = c }, height = 44.dp)
        }
        FieldLabel(t(TextKey.SUBS_F_NEXT))
        DayPicker(next, today, { next = it }, cell = 36)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(t(TextKey.SUBS_SAVE), {
                val (input, err) = subEditInput(item, amount, cycle, next)
                if (input == null) problem = err else onSave(input)
            }, Modifier.weight(2f))
            TonalButton(t(TextKey.DUES_CANCEL), onDismiss, Modifier.weight(1f), muted = true)
        }
    }
}
