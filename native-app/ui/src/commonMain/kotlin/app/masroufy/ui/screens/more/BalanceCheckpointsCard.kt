package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.CheckpointEffect
import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.SplitPart
import app.masroufy.usecase.WalletCheckpoints
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * «تحديثات الرصيد» في شاشة المحفظة: كل نقطة «حدّث الرصيد» بحالتها (محتاجة مراجعة · سبتها · مفيش فرق · اتغيّرت بعمليات أقدم وصلت بعدها)
 * والأفعال: «سيبه زي ما هو» · «وزّع جزء» (الجزء بيبقى عملية عادية بتصنيف) · «رجّعه للفرق» · «تراجع». كل الأرقام من UpdateBalance.history.
 */
@Composable
fun BalanceCheckpointsCard(walletId: Id, reload: Int, onChanged: () -> Unit) {
    val deps = LocalSpace.current
    val update = deps.more.updateBalance ?: return
    val currency = deps.space.currency
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var data by remember(walletId) { mutableStateOf<WalletCheckpoints?>(null) }
    var local by remember { mutableIntStateOf(0) }
    var splitting by remember { mutableStateOf<CheckpointEffect?>(null) }
    LaunchedEffect(walletId, reload, local) { data = runCatching { update.history(walletId) }.getOrNull() }
    fun act(done: UiKey, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onSuccess { toaster.show(t(done), dark = true); local++; onChanged() }
                .onFailure { toaster.show(it.message ?: t(UiKey.MORE_SAVE_FAILED), dark = true) }
        }
    }
    val d = data ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.BAL_HISTORY_TITLE), style = Type.section())
        if (d.effects.isEmpty()) BasicText(t(UiKey.BAL_HISTORY_EMPTY), style = Type.of(13).copy(color = Ink.muted))
        for (e in d.effects) {
            val cp = e.checkpoint
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    BasicText(if (cp.atInstant != null) dayMonth(cp.day) else t(UiKey.BAL_END_OF_DAY, dayMonth(cp.day)), style = Type.of(14, FontWeight.Bold))
                    BasicText(t(UiKey.BAL_ROW_STATED, amountLabel(cp.statedMinor, currency)), style = Type.caption().copy(color = Ink.muted))
                }
                BasicText(t(UiKey.BAL_UNKNOWN_DIFF) + " " + amountLabel(e.adjustmentMinor, currency, showCurrency = false), style = Type.of(13, FontWeight.Bold))
            }
            for (line in stateLines(e, currency)) BasicText(line, style = Type.caption().copy(color = if (e.needsReview) Ink.text else Ink.muted))
            for (p in d.parts[cp.id].orEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(t(UiKey.BAL_PARTS) + " · " + amountLabel(p.amountMinor, currency), Modifier.weight(1f), style = Type.caption())
                    TonalButton(t(UiKey.BAL_PART_REMOVE), onClick = { act(UiKey.BAL_PART_REMOVED) { update.removePart(p.id) } }, height = 36.dp, muted = true)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (e.needsReview) {
                    TonalButton(t(UiKey.BAL_SPLIT), onClick = { splitting = e }, modifier = Modifier.weight(1f), height = 40.dp)
                    TonalButton(t(UiKey.BAL_KEEP), onClick = { act(UiKey.BAL_KEPT) { update.keep(walletId, cp.id) } }, modifier = Modifier.weight(1f), height = 40.dp)
                }
                TonalButton(t(UiKey.BAL_UNDO), onClick = { act(UiKey.BAL_UNDONE) { update.undo(walletId, cp.id) } }, modifier = Modifier.weight(1f), height = 40.dp, muted = true)
            }
        }
        if (d.effects.isNotEmpty()) BasicText(t(UiKey.BAL_UNDO_BODY), style = Type.caption().copy(color = Ink.muted))
    }
    SplitSheet(splitting, walletId, onClose = { splitting = null }, onSaved = { local++; onChanged() })
}

/** سطور الحالة — كلها من أثر النقطة المحسوب في core (CheckpointEffect). */
@Composable
private fun stateLines(e: CheckpointEffect, currency: Currency): List<String> {
    val out = ArrayList<String>(3)
    when {
        e.adjustmentMinor == 0L && e.explainedMinor == 0L -> out += t(UiKey.BAL_STATE_MATCH)
        e.needsReview -> out += t(UiKey.BAL_STATE_REVIEW)
        e.adjustmentMinor != 0L -> out += t(UiKey.BAL_STATE_KEPT)
    }
    if (e.lateMinor != 0L) out += t(UiKey.BAL_STATE_CHANGED, amountLabel(abs(e.lateMinor), currency))
    if (e.explainedMinor != 0L) out += t(UiKey.BAL_STATE_EXPLAINED, amountLabel(abs(e.explainedMinor), currency))
    return out
}

/** «وزّع جزء»: النقص بيتوزّع على تصنيف مصروف، والزيادة على وارد (دخل إضافي · هدية · بيع غرض). الشروط بتتأكد في UpdateBalance.split. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitSheet(effect: CheckpointEffect?, walletId: Id, onClose: () -> Unit, onSaved: () -> Unit) {
    val deps = LocalSpace.current
    val update = deps.more.updateBalance ?: return
    val currency = deps.space.currency
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var amountText by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<EconomicKind?>(null) }
    var category by remember { mutableStateOf<Id?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val less = (effect?.adjustmentMinor ?: 0L) < 0
    LaunchedEffect(effect) {
        amountText = ""; kind = if (less) EconomicKind.PURCHASE else null; category = null; error = null
        if (effect != null && less) categories = runCatching { update.categories().filter { it.active } }.getOrDefault(emptyList())
    }
    val amount = amountText.trim().takeIf { it.isNotEmpty() }?.let { tryParseMoney(it, currency) }
    Sheet(effect != null, onClose, title = t(UiKey.BAL_SPLIT), closeLabel = t(UiKey.SHELL_CLOSE), corner = 28.dp, spacing = 10.dp) {
        val e = effect ?: return@Sheet
        BasicText(t(UiKey.BAL_SPLIT), style = Type.of(17, FontWeight.Bold))
        BasicText(t(UiKey.BAL_LEFT, amountLabel(abs(e.adjustmentMinor), currency)), style = Type.of(13).copy(color = Ink.muted))
        BasicText(t(UiKey.BAL_DETAIL_BODY), style = Type.caption().copy(color = Ink.muted))
        TextInput(amountText, { amountText = it; error = null }, label = t(UiKey.BAL_SPLIT_AMOUNT), placeholder = "0.00", ltr = true, keyboard = KeyboardType.Decimal)
        BasicText(t(if (less) UiKey.BAL_SPLIT_CATEGORY else UiKey.BAL_SPLIT_PICK), style = Type.of(13, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            key(less) { // نفس سبب وقعة «تحويل» في لوحة «+»: كل قائمة في مجموعة بمفتاحها
                if (less) for (c in categories) key(c.id) { SelectChip(c.name, category == c.id, onClick = { category = c.id }, height = 40.dp) }
                else for ((k, label) in INCOME_KINDS) key(k) { SelectChip(t(label), kind == k, onClick = { kind = k }, height = 40.dp) }
            }
        }
        error?.let { FieldError(it) }
        PrimaryButton(
            t(UiKey.BAL_SPLIT_SAVE),
            onClick = {
                val a = amount
                val k = kind
                if (a == null || a <= 0 || k == null) {
                    error = t(UiKey.BAL_ERR_AMOUNT)
                    return@PrimaryButton
                }
                scope.launch {
                    runCatching { update.split(walletId, e.checkpoint.id, SplitPart(a, k, category)) }
                        .onSuccess { toaster.show(t(UiKey.BAL_SPLIT_DONE), dark = true); onSaved(); onClose() }
                        .onFailure { error = it.message ?: t(UiKey.MORE_SAVE_FAILED) }
                }
            },
            height = 52.dp, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** أنواع الوارد اللي الزيادة بتتوزّع عليها («دخل إضافي» ⇒ FREELANCE مؤقتًا — سؤال مفتوح للمالك). */
private val INCOME_KINDS = listOf(
    EconomicKind.FREELANCE to UiKey.BAL_SPLIT_KIND_INCOME,
    EconomicKind.GIFT_RECEIVED to UiKey.BAL_SPLIT_KIND_GIFT,
    EconomicKind.PERSONAL_SALE to UiKey.BAL_SPLIT_KIND_SALE,
)
