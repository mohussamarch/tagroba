package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.MIN_PERIODS_FOR_AVERAGE
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * لوحة السقف (`BudgetLimitSheet` — جوه «ميزانية تصنيف»، ونفسها لسقف الشهر كله في «الميزانيات»): السقف · «استخدم المتوسط» (اختيار
 * المستخدم — مش سقف من نفسه، spec/01) · التنبيه شغال؟ · ٧٠/٨٠/٩٠ أو نسبة تانية · «احفظ» و«إزالة السقف».
 * الحفظ والإزالة بيروحوا لـ`SetBudget` من الشاشة ([onSave]/[onClear] بيرجّعوا رسالة الخطأ أو null).
 * ⚠️ سطر «صُرف منه X٪ … أعلى من متوسطك بـY٪» و«ننبّهك عند صرف X» اللي بيتحسبوا وإنت بتكتب **مش هنا**: مالهمش حالة استخدام (missingLogic).
 */
@Composable
fun BudgetLimitSheet(
    visible: Boolean,
    title: String,
    monthName: String,
    current: LimitCurrent,
    spentMinor: Halalas?,
    averageMinor: Halalas?,
    currency: Currency,
    onDismiss: () -> Unit,
    onSave: suspend (LimitCheck.Ok) -> String?,
    onClear: suspend () -> String?,
) {
    var draft by remember(visible, current) { mutableStateOf(LimitDraft.from(current, currency)) }
    var tried by remember(visible) { mutableStateOf(false) }
    var serverError by remember(visible) { mutableStateOf<String?>(null) }
    var busy by remember(visible) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Sheet(visible, onDismiss, title, spacing = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = Type.of(17, FontWeight.Bold))
            BasicText(
                monthName,
                Modifier.clip(RoundedCornerShape(10.dp)).background(Ink.selected).padding(horizontal = 10.dp, vertical = 2.dp),
                style = Type.of(12, FontWeight.Bold).copy(color = Ink.primary),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val spent = spentMinor?.let { plain(it, currency) } ?: t(TextKey.NOT_AVAILABLE)
            BasicText(t(UiKey.LIMIT_SHEET_SPENT, spent), style = Type.caption().copy(color = Ink.muted))
            BasicText(
                if (averageMinor != null) t(UiKey.LIMIT_SHEET_AVG, plain(averageMinor, currency))
                else t(UiKey.LIMIT_SHEET_AVG_NA, sentenceNumber(MIN_PERIODS_FOR_AVERAGE)),
                style = Type.caption().copy(color = Ink.muted),
            )
        }
        val check = checkLimit(draft, currency)
        val bad = check as? LimitCheck.Bad
        TextInput(
            value = draft.limitText,
            onChange = { draft = draft.copy(limitText = it); serverError = null },
            label = t(UiKey.LIMIT_SHEET_LABEL),
            placeholder = "0",
            error = (if (tried) bad?.limitError else null) ?: serverError,
            ltr = true,
            keyboard = KeyboardType.Decimal,
            height = 56.dp,
            textSize = 24,
            trailing = { BasicText(currencySymbol(currency), Modifier.padding(end = 16.dp), style = Type.body().copy(color = Ink.muted)) },
        )
        if (averageMinor != null && averageMinor > 0) {
            TonalButton(t(UiKey.LIMIT_SHEET_USE_AVG, plain(averageMinor, currency)), { draft = draft.copy(limitText = inputText(averageMinor, currency)) }, height = 44.dp)
        }
        Divider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(t(UiKey.LIMIT_SHEET_ALERT), style = Type.bodyBold())
                BasicText(t(if (draft.alertOn) UiKey.LIMIT_SHEET_ALERT_ON else UiKey.LIMIT_SHEET_ALERT_OFF), style = Type.caption().copy(color = Ink.muted))
            }
            Switch(draft.alertOn, t(UiKey.LIMIT_SHEET_ALERT), { draft = draft.copy(alertOn = !draft.alertOn) })
        }
        if (draft.alertOn) {
            FieldLabel(t(UiKey.LIMIT_SHEET_PCT))
            Gap8Row(Modifier.fillMaxWidth()) {
                for (p in LimitDraft.PRESETS) {
                    SelectChip(
                        t(UiKey.LIMIT_SHEET_PERCENT, sentenceNumber(p)),
                        selected = draft.customText.isBlank() && draft.preset == p,
                        onClick = { draft = draft.copy(preset = p, customText = "") },
                        modifier = Modifier.weight(1f),
                        height = 44.dp,
                    )
                }
                TextInput(
                    value = draft.customText,
                    onChange = { draft = draft.copy(customText = it) },
                    modifier = Modifier.weight(1f),
                    placeholder = t(UiKey.LIMIT_SHEET_OTHER),
                    ltr = true,
                    keyboard = KeyboardType.Number,
                    height = 44.dp,
                    textSize = 14,
                )
            }
            val pctError = bad?.percentError?.takeIf { tried || draft.customText.isNotBlank() }
            if (pctError != null) FieldError(pctError)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                t(UiKey.LIMIT_SHEET_SAVE),
                onClick = {
                    tried = true
                    val ok = check as? LimitCheck.Ok ?: return@PrimaryButton
                    busy = true
                    scope.launch {
                        serverError = onSave(ok)
                        busy = false
                    }
                },
                modifier = Modifier.weight(2f),
                loading = busy,
                height = 52.dp,
            )
            if (current.limitMinor != null) {
                DangerButton(t(UiKey.LIMIT_SHEET_CLEAR), { scope.launch { serverError = onClear() } }, Modifier.weight(1f), height = 52.dp)
            }
        }
    }
}

/** زرار فتح لوحة السقف تحت «السقف والتنبيه» + سطر الحالة تحته (النموذج: `BudgetLimitSheet` المقفولة 350×76). */
@Composable
fun LimitTrigger(current: LimitCurrent, currency: Currency, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val limit = current.limitMinor
        if (limit != null) SecondaryButton(t(UiKey.LIMIT_SHEET_EDIT), onOpen, Modifier.fillMaxWidth())
        else PrimaryButton(t(UiKey.BUDGETS_SET_CAP), onOpen, Modifier.fillMaxWidth())
        val note = when {
            limit == null -> t(UiKey.LIMIT_SHEET_NOTE_NONE)
            current.notify && current.thresholdPercent != null -> t(UiKey.LIMIT_SHEET_NOTE_SET, plain(limit, currency), sentenceNumber(current.thresholdPercent))
            else -> t(UiKey.LIMIT_SHEET_NOTE_OFF, plain(limit, currency))
        }
        BasicText(note, style = Type.caption().copy(color = Ink.muted))
    }
}
