package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.masroufy.core.IsoDate
import app.masroufy.core.Person
import app.masroufy.core.R06Key
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.core.formatAmount
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.AddedDebt
import app.masroufy.usecase.DebtDueChoice
import app.masroufy.usecase.DebtMoney
import app.masroufy.usecase.DebtSideIn
import app.masroufy.usecase.DebtWho
import app.masroufy.usecase.NewDebt
import kotlinx.coroutines.launch

/** «الفلوس اتحركت إزاي؟» — عملية متسجلة · كاش · دين قديم. */
private enum class DebtHow { OP, CASH, OLD }

private const val NEW_PERSON = "+new"

/**
 * لوحة «ضيف دين» (`AddDebtSheet.dc.html` — فرع التصميم `8575ee4b` · OVERRIDES §112-2) من زرار الرأس في «الديون». ليك ولا عليك · الشخص (موجود
 * أو «+ شخص جديد» بخانة الاسم) · المبلغ والسبب · الميعاد (من غير · أسبوع · شهر · تاريخ من [DayPicker] الموجود) · الفلوس اتحركت إزاي.
 * الحفظ كله في حالة الاستخدام `AddDebt` (الشاشة ما بتحسبش — بتقرا الرقم المكتوب بس). [DuesDeps.addDebt] null ⇒ الزرار مش ظاهر أصلًا.
 */
@Composable
internal fun AddDebtSheet(visible: Boolean, initialSide: DebtSide, onDismiss: () -> Unit, onSaved: (AddedDebt) -> Unit) {
    Sheet(visible = visible, onDismiss = onDismiss, title = t(R06Key.AD_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        if (visible) AddDebtForm(if (initialSide == DebtSide.ON_YOU) DebtSideIn.ON_YOU else DebtSideIn.FOR_YOU, onSaved)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddDebtForm(initial: DebtSideIn, onSaved: (AddedDebt) -> Unit) {
    val space = LocalSpace.current
    val add = space.dues.addDebt ?: return
    val currency = space.space.currency
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var side by remember { mutableStateOf(initial) }
    var who by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(DebtDueChoice.NONE) }
    var picked by remember { mutableStateOf<IsoDate?>(null) }
    var how by remember { mutableStateOf(DebtHow.OP) }
    var op by remember { mutableStateOf<Transaction?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    var hasCash by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<Transaction>?>(null) }
    val today = remember { space.shell.today() }
    LaunchedEffect(add) {
        people = runCatching { space.dues.people.listWithBalances().map { it.person }.filter { !it.archived } }.getOrDefault(emptyList())
        hasCash = runCatching { add.cashWallet() != null }.getOrDefault(false)
    }
    LaunchedEffect(add, side) {
        candidates = null
        op = null
        candidates = runCatching { add.candidates(side) }.getOrDefault(emptyList())
    }
    fun touched() { error = null }

    BasicText(t(R06Key.AD_TITLE), style = Type.section())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectChip(t(R06Key.AD_SIDE_FOR), side == DebtSideIn.FOR_YOU, { side = DebtSideIn.FOR_YOU; touched() }, Modifier.weight(1f), dot = Ink.income, height = 52.dp)
        SelectChip(t(R06Key.AD_SIDE_ON), side == DebtSideIn.ON_YOU, { side = DebtSideIn.ON_YOU; touched() }, Modifier.weight(1f), dot = Ink.expense, height = 52.dp)
    }
    FieldLabel(t(if (side == DebtSideIn.FOR_YOU) R06Key.AD_WHO_FOR else R06Key.AD_WHO_ON))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in people) SelectChip(p.name, who == p.id, { who = p.id; touched() })
        SelectChip(t(R06Key.AD_NEW_PERSON), who == NEW_PERSON, { who = NEW_PERSON; touched() })
    }
    if (who == NEW_PERSON) TextInput(newName, { newName = it; touched() }, placeholder = t(R06Key.AD_NAME_PH), height = 48.dp)
    MoneyField(amount, { amount = it; touched() }, t(R06Key.AD_AMOUNT_PH), currency)
    TextInput(reason, { reason = it; touched() }, placeholder = t(R06Key.AD_REASON_PH), height = 48.dp)

    FieldLabel(t(R06Key.AD_DUE))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((c, k) in listOf(DebtDueChoice.NONE to R06Key.AD_DUE_NONE, DebtDueChoice.WEEK to R06Key.AD_DUE_WEEK, DebtDueChoice.MONTH to R06Key.AD_DUE_MONTH, DebtDueChoice.PICK to R06Key.AD_DUE_PICK)) {
            val label = if (c == DebtDueChoice.PICK && picked != null) pickedLabel(picked) else t(k)
            SelectChip(label, due == c, { due = c; touched() })
        }
    }
    if (due == DebtDueChoice.PICK) DayPicker(picked, today, { picked = it; touched() }, cell = 36)

    FieldLabel(t(R06Key.AD_HOW))
    val forYou = side == DebtSideIn.FOR_YOU
    HowTile(t(R06Key.AD_HOW_OP), t(if (forYou) R06Key.AD_HOW_OP_FOR else R06Key.AD_HOW_OP_ON), how == DebtHow.OP) { how = DebtHow.OP; touched() }
    if (how == DebtHow.OP) OperationPick(candidates, op) { picked ->
        op = picked
        amount = formatAmount(picked.amountMinor, picked.currency, grouping = false)
        touched()
    }
    HowTile(t(R06Key.AD_HOW_CASH), t(if (forYou) R06Key.AD_HOW_CASH_FOR else R06Key.AD_HOW_CASH_ON), how == DebtHow.CASH, enabled = hasCash) { how = DebtHow.CASH; touched() }
    HowTile(t(R06Key.AD_HOW_OLD), t(R06Key.AD_HOW_OLD_HINT), how == DebtHow.OLD) { how = DebtHow.OLD; touched() }
    error?.let { FieldError(it) }

    val minor = tryParseMoney(amount, currency)
    val person: DebtWho? = when (who) {
        null -> null
        NEW_PERSON -> newName.trim().takeIf { it.isNotEmpty() }?.let { DebtWho.New(it) }
        else -> DebtWho.Existing(who!!)
    }
    val money: DebtMoney? = when (how) {
        DebtHow.OP -> op?.let { DebtMoney.Operation(it.id) }
        DebtHow.CASH -> DebtMoney.Cash
        DebtHow.OLD -> DebtMoney.Old
    }
    val ready = person != null && money != null && minor != null && minor > 0 && (due != DebtDueChoice.PICK || picked != null)
    PrimaryButton(
        t(R06Key.AD_SAVE),
        onClick = {
            if (!ready) return@PrimaryButton
            saving = true
            scope.launch {
                try {
                    val out = add.add(NewDebt(side, person!!, minor!!, reason, due, picked, money!!, today, currency))
                    toaster.show(t(R06Key.AD_SAVED))
                    DuesChanges.bump()
                    onSaved(out)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    error = failText(e)
                } finally {
                    saving = false
                }
            }
        },
        enabled = ready, loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(),
    )
}

/** سطر «الفلوس اتحركت إزاي؟»: الاسم وتحته الشرح، والمختار بإطار أخضر. */
@Composable
private fun HowTile(label: String, hint: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).pressScale(press).clip(shape)
            .background(if (on) Ink.primary.copy(alpha = 0.08f) else Ink.text.copy(alpha = 0.04f))
            .then(if (on) Modifier.insetRing(shape, 2.dp, Ink.primary) else Modifier)
            .tap(press, enabled = enabled, role = Role.RadioButton, label = label, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(label, style = Type.bodyBold().copy(color = if (enabled) Ink.text else Ink.muted))
        BasicText(hint, style = Type.caption().copy(color = Ink.muted))
    }
}

/** «اختار العملية»: أحدث العمليات المناسبة (من `AddDebt.candidates`) — اختيار واحدة بيملا المبلغ بمبلغها. */
@Composable
private fun OperationPick(candidates: List<Transaction>?, chosen: Transaction?, onPick: (Transaction) -> Unit) {
    when {
        candidates == null -> Unit
        candidates.isEmpty() -> BasicText(t(R06Key.AD_OP_EMPTY), style = Type.caption().copy(color = Ink.muted))
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(t(R06Key.AD_OP_PICK), style = Type.caption().copy(color = Ink.muted))
            for (c in candidates.take(8)) {
                val title = app.masroufy.ui.screens.people.opTitle(c)
                val label = t(UiKey.DUES_COMMA_JOIN, t(UiKey.DUES_COMMA_JOIN, title.ifEmpty { dayMonth(c.occurredAt) }, dayMonth(c.occurredAt)), amountLabel(c.amountMinor, c.currency))
                SelectChip(label, chosen?.id == c.id, { onPick(c) }, Modifier.fillMaxWidth(), height = 44.dp)
            }
        }
    }
}
