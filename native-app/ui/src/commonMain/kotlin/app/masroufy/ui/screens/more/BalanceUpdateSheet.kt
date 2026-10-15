package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.BalancePreview
import kotlinx.coroutines.launch

/**
 * «حدّث الرصيد» / «حدّث الكاش» (قايمة المالك 2026-10-15 البنود 4 و5): المالك بيكتب اللي معاه فعلًا دلوقتي (أو آخر يوم فات — مش المستقبل)
 * ⇒ UpdateBalance.record بيسجّل نقطة واحدة والفرق «فرق مش معروف» (مش مصروف ولا دخل). اللوحة مابتحسبش حاجة: المحسوب والفرق من
 * UpdateBalance.preview، والغلط (يوم في المستقبل، قبل بداية المحفظة …) رسالة راجعة من حالة الاستخدام.
 */
@Composable
fun BalanceUpdateSheet(visible: Boolean, walletId: Id?, walletName: String, isCash: Boolean, onClose: () -> Unit, onSaved: () -> Unit) {
    val deps = LocalSpace.current
    val update = deps.more.updateBalance
    val currency = deps.space.currency
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val today = update?.today() ?: deps.shell.today()
    var amountText by remember { mutableStateOf("") }
    var past by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(today) }
    var preview by remember { mutableStateOf<BalancePreview?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var tried by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        amountText = ""; past = false; day = today; preview = null; previewError = null; serverError = null; tried = false; saving = false
    }
    val amount = amountText.trim().takeIf { it.isNotEmpty() }?.let { tryParseMoney(it, currency) }
    val amountBad = amountText.isNotBlank() && (amount == null || amount < 0)
    val dayOk = !past || (isValidIsoDate(day) && day <= today)
    val chosenDay = if (past) day else null
    LaunchedEffect(visible, walletId, amount, chosenDay, dayOk) {
        if (!visible || walletId == null || update == null || !dayOk) return@LaunchedEffect
        runCatching { update.preview(walletId, amount?.takeIf { it >= 0 }, chosenDay) }
            .onSuccess { preview = it; previewError = null }
            .onFailure { preview = null; previewError = it.message }
    }
    val title = t(UiKey.BAL_SHEET_TITLE, walletName)
    Sheet(visible, onClose, title = title, closeLabel = t(UiKey.SHELL_CLOSE), corner = 28.dp, spacing = 10.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(t(UiKey.BAL_SHEET_BODY), style = Type.of(13).copy(color = Ink.muted))
        TextInput(
            amountText, { amountText = it; serverError = null }, label = t(UiKey.BAL_AMOUNT_LABEL), placeholder = "0.00", ltr = true,
            keyboard = KeyboardType.Decimal, error = if (amountBad || (tried && amount == null)) t(UiKey.BAL_ERR_AMOUNT) else null,
            trailing = { BasicText(currencySymbol(currency), Modifier.padding(horizontal = 12.dp), style = Type.caption().copy(color = Ink.muted)) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip(t(UiKey.BAL_WHEN_NOW), !past, onClick = { past = false; serverError = null }, modifier = Modifier.weight(1f), height = 44.dp)
            SelectChip(t(UiKey.BAL_WHEN_DAY), past, onClick = { past = true; serverError = null }, modifier = Modifier.weight(1f), height = 44.dp)
        }
        if (past) {
            TextInput(day, { day = it.trim(); serverError = null }, label = t(UiKey.BAL_DAY_LABEL), placeholder = today, ltr = true,
                error = if (!dayOk) t(UiKey.BAL_ERR_DATE) else null)
            BasicText(t(UiKey.BAL_WHEN_DAY_NOTE), style = Type.caption().copy(color = Ink.muted))
        }
        PreviewLines(preview?.takeIf { dayOk }, amount, currency)
        BasicText(t(UiKey.BAL_NOT_SPEND), style = Type.caption().copy(color = Ink.muted))
        if (isCash) BasicText(t(UiKey.BAL_UPDATE_HINT), style = Type.caption().copy(color = Ink.muted))
        val shown = serverError ?: previewError?.takeIf { dayOk }
        if (shown != null) FieldError(shown)
        PrimaryButton(
            t(UiKey.BAL_SAVE),
            onClick = {
                tried = true
                val stated = amount
                if (update == null || walletId == null || stated == null || stated < 0 || !dayOk || saving) return@PrimaryButton
                saving = true
                scope.launch {
                    val error = runCatching { update.record(walletId, stated, chosenDay) }.fold({ null }, { it.message ?: t(UiKey.MORE_SAVE_FAILED) })
                    saving = false
                    if (error == null) {
                        toaster.show(t(UiKey.BAL_SAVED), dark = true)
                        onSaved()
                        onClose()
                    } else serverError = error
                }
            },
            enabled = update != null && walletId != null, loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** المحسوب والفرق — الاتنين من UpdateBalance.preview (اللوحة بتعرض الإشارة بس). */
@Composable
private fun PreviewLines(p: BalancePreview?, amount: Halalas?, currency: Currency) {
    if (p == null) return
    BasicText(t(UiKey.BAL_COMPUTED, amountLabel(p.computedMinor, currency)), style = Type.of(13))
    val d = p.differenceMinor ?: return
    if (amount == null) return
    val line = when {
        d > 0 -> t(UiKey.BAL_DIFF_MORE, amountLabel(d, currency))
        d < 0 -> t(UiKey.BAL_DIFF_LESS, amountLabel(-d, currency))
        else -> t(UiKey.BAL_DIFF_NONE)
    }
    BasicText(line, style = Type.of(13, FontWeight.Bold).copy(color = if (d == 0L) Ink.muted else Ink.text))
}
