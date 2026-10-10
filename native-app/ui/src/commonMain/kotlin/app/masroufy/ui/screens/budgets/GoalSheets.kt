package app.masroufy.ui.screens.budgets

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.currencySymbol
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Confetti
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «تفاصيل الخطة» (`GoalDetail` — لوحة فوق «خطط الادخار»): المدّخر بشريطه · الأربع أرقام · الحساب المربوط **أو** الإيداع اليدوي ·
 * «تعديل» (`GoalEditSheet`) و«أرشفة». الوصول للهدف بعد إيداع ⇒ قصاصات مرة واحدة (`Confetti` — مستوى 3).
 * ⚠️ **قايمة الإيداعات وشيل إيداع مش هنا**: `ManageSavingsGoals` مالهاش قراية لإيداعات خطة (missingLogic) — الإيداع نفسه شغال.
 */
@Composable
fun GoalDetailSheet(card: GoalCardUi?, deps: BudgetsDeps, today: String, onDismiss: () -> Unit, refresh: () -> Unit) {
    var last by remember { mutableStateOf(card) }
    if (card != null) last = card
    val shown = last ?: return
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var deposit by remember(shown.id) { mutableStateOf("") }
    var error by remember(shown.id) { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var confetti by remember { mutableIntStateOf(0) }
    var editOpen by remember { mutableStateOf(false) }
    OnceWhen(pending == shown.id && shown.reached) {
        confetti++
        toaster.show(t(UiKey.GOAL_DETAIL_DONE, shown.name))
        pending = null
    }
    val title = t(if (shown.starred) UiKey.GOAL_DETAIL_TITLE_STAR else UiKey.GOAL_DETAIL_TITLE, shown.name)
    Sheet(card != null, onDismiss, title, spacing = 10.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        val how = shown.walletLabel ?: t(UiKey.GOALS_MANUAL)
        BasicText(
            t(UiKey.GOAL_DETAIL_SUB, amountLabel(shown.targetMinor, shown.currency), t(UiKey.BUDGETS_UP_WHEN, shown.untilText, how)),
            style = Type.of(13).copy(color = Ink.muted),
        )
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink.selected).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(t(if (shown.linked) UiKey.GOAL_DETAIL_SAVED_LINKED else UiKey.GOAL_DETAIL_SAVED), style = Type.caption().copy(color = Ink.soft))
                AmountText(shown.savedMinor, shown.currency, size = 26)
                ProgressBar(shown.percent ?: 0, if (shown.reached) Ink.income else Ink.primary, track = Color(0x2408634F))
                BasicText(detailProgressLine(shown), style = Type.captionBold().copy(color = Ink.primary))
            }
            if (confetti > 0) Confetti(Modifier.matchParentSize(), key = confetti)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x0A193D33)).padding(horizontal = 14.dp, vertical = 2.dp)) {
            shown.stats.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(s.label, style = Type.of(13).copy(color = Ink.soft))
                    BasicText(s.value, style = Type.of(14, FontWeight.Bold).copy(fontFeatureSettings = "tnum", color = statColor(s.tone)))
                }
                if (i < shown.stats.lastIndex) Divider()
            }
        }
        if (shown.linked) {
            BasicText(
                t(UiKey.GOAL_DETAIL_LINKED, shown.walletLabel ?: ""),
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x142469BA)).padding(horizontal = 12.dp, vertical = 10.dp),
                style = Type.of(13).copy(color = Ink.transfer),
            )
        } else {
            Gap8Row(Modifier.fillMaxWidth()) {
                TextInput(
                    value = deposit, onChange = { deposit = it; error = null }, modifier = Modifier.weight(1f), placeholder = "0.00", ltr = true,
                    keyboard = KeyboardType.Decimal, textSize = 17,
                    trailing = { BasicText(currencySymbol(shown.currency), Modifier.padding(end = 12.dp), style = Type.caption().copy(color = Ink.muted)) },
                )
                PrimaryButton(t(UiKey.GOAL_DETAIL_DEPOSIT), {
                    val minor = tryParseMoney(deposit, shown.currency)?.takeIf { it > 0 }
                    if (minor == null) error = t(TextKey.GOAL_CONTRIBUTION_POSITIVE)
                    else scope.launch {
                        error = attempt(t(UiKey.SHELL_LOAD_FAILED)) { deps.goals.recordContribution(shown.id, today, minor) }
                        if (error == null) {
                            deposit = ""
                            toaster.show(t(UiKey.GOAL_DETAIL_DEPOSITED, amountLabel(minor, shown.currency), shown.name))
                            if (!shown.reached) pending = shown.id
                            refresh()
                        }
                    }
                })
            }
        }
        Gap8Row(Modifier.fillMaxWidth()) {
            TonalButton(t(UiKey.GOAL_DETAIL_EDIT), { editOpen = true }, Modifier.weight(1f))
            TonalButton(t(UiKey.GOAL_DETAIL_ARCHIVE), {
                scope.launch {
                    error = attempt(t(UiKey.SHELL_LOAD_FAILED)) { deps.goals.archive(shown.id, true) }
                    if (error == null) {
                        toaster.show(t(UiKey.GOAL_DETAIL_ARCHIVED_TOAST, shown.name))
                        onDismiss()
                        refresh()
                    }
                }
            }, Modifier.weight(1f), muted = true)
        }
        error?.let { FieldError(it) }
    }
    GoalEditSheet(editOpen, shown, deps, today, onDismiss = { editOpen = false }, onArchived = onDismiss, refresh = refresh)
}

internal fun statColor(tone: StatTone): Color = when (tone) {
    StatTone.GOOD -> Ink.income
    StatTone.BAD -> Ink.expense
    StatTone.WARN -> Ink.focus
    StatTone.MUTED -> Ink.muted
    StatTone.PLAIN -> Ink.text
}

/**
 * «خطة جديدة»: الاسم · المبلغ · «حتى متى؟» (آخر السنة · بعد سنة · بعد سنتين) · «كيف تدّخر؟» (يدوي · حساب مخصص ⇒ أي حساب) ⇒
 * `ManageSavingsGoals.create`. الخطة بتتأرشف بس، عمرها ما بتتمسح.
 */
@Composable
fun GoalNewSheet(
    visible: Boolean,
    deps: BudgetsDeps,
    today: String,
    currency: Currency,
    spaceId: String,
    wallets: List<Wallet>,
    onDismiss: () -> Unit,
    refresh: () -> Unit,
) {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var d by remember(visible) { mutableStateOf(GoalNewDraft()) }
    var error by remember(visible) { mutableStateOf<String?>(null) }
    val title = t(UiKey.GOALS_NEW)
    Sheet(visible, onDismiss, title, spacing = 10.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(t(UiKey.GOAL_NEW_SUB), style = Type.of(13).copy(color = Ink.muted))
        TextInput(d.name, { d = d.copy(name = it.take(40)); error = null }, label = t(UiKey.GOAL_NEW_NAME), placeholder = t(UiKey.GOAL_NEW_NAME_HINT))
        TextInput(
            d.targetText, { d = d.copy(targetText = it); error = null }, label = t(UiKey.GOAL_NEW_TARGET, currencySymbol(currency)), placeholder = "0",
            ltr = true, keyboard = KeyboardType.Decimal,
        )
        ChoiceRow(t(UiKey.GOAL_NEW_WHEN), GoalWhen.entries.map { it to t(whenKey(it)) }, d.whenChoice) { d = d.copy(whenChoice = it) }
        ChoiceRow(
            t(UiKey.GOAL_NEW_HOW), listOf(GoalKind.MANUAL to t(UiKey.GOAL_NEW_MANUAL), GoalKind.LINKED to t(UiKey.GOAL_NEW_LINKED)), d.kind,
        ) { d = d.copy(kind = it, walletId = null) }
        if (d.kind == GoalKind.LINKED) {
            val choices = wallets.filter { it.currency == currency && it.kind != "cash" }
            if (choices.isEmpty()) BasicText(t(UiKey.GOAL_NEW_NO_WALLETS), style = Type.caption().copy(color = Ink.muted))
            else ChoiceRow(t(UiKey.GOAL_NEW_WHICH), choices.map { it.id to it.name }, d.walletId) { d = d.copy(walletId = it); error = null }
        }
        BasicText(t(if (d.kind == GoalKind.LINKED) UiKey.GOAL_NEW_NOTE_LINKED else UiKey.GOAL_NEW_NOTE_MANUAL), style = Type.caption().copy(color = Ink.muted))
        PrimaryButton(t(UiKey.GOAL_NEW_CREATE), {
            when (val c = checkNewGoal(d, today, currency, spaceId)) {
                is GoalCheck.Bad -> error = c.message
                is GoalCheck.Ok -> scope.launch {
                    error = attempt(t(UiKey.SHELL_LOAD_FAILED)) { deps.goals.create(c.input) }
                    if (error == null) {
                        toaster.show(t(UiKey.GOAL_NEW_CREATED, c.input.name))
                        onDismiss()
                        refresh()
                    }
                }
            }
        }, Modifier.fillMaxWidth())
        error?.let { FieldError(it) }
    }
}

private fun whenKey(w: GoalWhen): TextRef = when (w) {
    GoalWhen.YEAR_END -> UiKey.GOAL_NEW_YEAR_END
    GoalWhen.ONE_YEAR -> UiKey.GOAL_NEW_ONE_YEAR
    GoalWhen.TWO_YEARS -> UiKey.GOAL_NEW_TWO_YEARS
}

/** عنوان صغير + شرايح اختيار (44) بتلف لو كترت. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T?, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label, style = Type.of(13, FontWeight.Bold))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, text) in options) app.masroufy.ui.components.SelectChip(text, value == selected, { onPick(value) }, height = 44.dp)
        }
    }
}

