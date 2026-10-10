package app.masroufy.ui.screens.more

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.DEPENDENT_KINDS
import app.masroufy.core.MAX_NAME_LENGTH
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalApp
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** اللوحة المفتوحة في «ملفك»: سؤال بمسودته (وغلطه من `checkProfile`) · «هل تذهب بها للدوام؟» · تأكيد الخروج. */
sealed interface AccountEdit {
    data class Field(val field: AccountField, val draft: Any?, val error: String? = null) : AccountEdit

    data object CarToWork : AccountEdit

    data object SignOut : AccountEdit

    fun withError(message: String): AccountEdit = if (this is Field) copy(error = message) else this
}

@Composable
fun AccountSheet(
    edit: AccountEdit?,
    rangeEditable: Boolean,
    onDraft: (AccountEdit) -> Unit,
    onClose: () -> Unit,
    onSave: (AccountField, Any?) -> Unit,
    onRange: (SalaryRange) -> Unit,
    onCarToWork: (Boolean) -> Unit,
) {
    // اللوحة بتفضل مرسومة وهي بتنزل (حركة الخروج) ⇒ آخر محتوى محفوظ
    var shown by remember { mutableStateOf<AccountEdit?>(null) }
    if (edit != null) shown = edit
    val current = shown ?: return
    val currency = LocalSpace.current.space.currency
    Sheet(edit != null, onClose, title = sheetTitle(current, currency), closeLabel = t(UiKey.SHELL_CLOSE), corner = 28.dp, spacing = 12.dp) {
        BasicText(sheetTitle(current, currency), style = Type.of(17, FontWeight.Bold))
        when (current) {
            is AccountEdit.Field -> FieldBody(current, rangeEditable, currency, onDraft, onSave, onRange)
            AccountEdit.CarToWork -> {
                BasicText(t(UiKey.ACC_CAR_TO_WORK_BODY), style = Type.of(13).copy(color = Ink.muted))
                TwoActions(t(UiKey.MORE_YES), danger = false, onFirst = { onCarToWork(true) }, second = t(UiKey.MORE_NO), onSecond = { onCarToWork(false) })
            }
            AccountEdit.SignOut -> {
                BasicText(t(UiKey.ACC_SIGN_OUT_BODY), style = Type.of(13).copy(color = Ink.muted))
                val app = LocalApp.current
                val scope = rememberCoroutineScope()
                TwoActions(t(UiKey.ACC_SIGN_OUT_GO), danger = true, onFirst = { scope.launch { runCatching { app.signIn.signOut() } } }, second = t(UiKey.MORE_CANCEL), onSecond = onClose)
            }
        }
    }
}

private fun sheetTitle(e: AccountEdit, currency: Currency): String = when (e) {
    AccountEdit.CarToWork -> t(UiKey.ACC_CAR_TO_WORK_Q)
    AccountEdit.SignOut -> t(UiKey.ACC_SIGN_OUT_Q)
    is AccountEdit.Field -> when (e.field) {
        AccountField.NAME -> t(UiKey.ACC_NAME)
        AccountField.GENDER -> t(UiKey.ACC_GENDER)
        AccountField.DEPENDENTS -> t(UiKey.ACC_DEP_TITLE)
        AccountField.SALARY -> t(UiKey.ACC_SALARY_TITLE, currencySymbol(currency))
        AccountField.PAYDAY -> t(UiKey.ACC_PAYDAY_TITLE)
        AccountField.CAR -> t(UiKey.ACC_CAR)
        AccountField.RENTER -> t(UiKey.ACC_RENTER)
        AccountField.MAID -> t(UiKey.ACC_MAID)
        AccountField.BUSINESS -> t(UiKey.ACC_BUSINESS)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldBody(
    e: AccountEdit.Field,
    rangeEditable: Boolean,
    currency: Currency,
    onDraft: (AccountEdit) -> Unit,
    onSave: (AccountField, Any?) -> Unit,
    onRange: (SalaryRange) -> Unit,
) {
    val body = when (e.field) {
        AccountField.NAME -> UiKey.ACC_NAME_BODY
        AccountField.GENDER -> UiKey.ACC_GENDER_BODY
        AccountField.DEPENDENTS -> UiKey.ACC_DEP_BODY
        AccountField.SALARY -> UiKey.ACC_SALARY_BODY
        AccountField.PAYDAY -> UiKey.ACC_PAYDAY_BODY
        else -> null
    }
    if (body != null) BasicText(t(body), style = Type.of(13).copy(color = Ink.muted))
    var ok = e.draft != null
    var saveLabel = t(UiKey.MORE_SAVE)
    when (e.field) {
        AccountField.NAME -> {
            val text = e.draft as? String ?: ""
            val long = nameTooLong(text)
            ok = !long
            TextInput(text, { onDraft(e.copy(draft = it, error = null)) }, error = if (long) t(TextKey.PROFILE_NAME_TOO_LONG, sentenceNumber(MAX_NAME_LENGTH)) else null)
        }
        AccountField.GENDER -> {
            ok = true
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (g in listOf("male", "female")) FillChip(genderLabel(g)!!, e.draft == g) { onDraft(e.copy(draft = g)) }
            }
        }
        AccountField.DEPENDENTS -> {
            val kinds = (e.draft as? List<*>)?.filterIsInstance<String>().orEmpty()
            ok = true
            if (kinds.isEmpty()) saveLabel = t(UiKey.ACC_SAVE_WITH, t(UiKey.ACC_DEP_NONE))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in DEPENDENT_KINDS) FillChip(dependentLabel(k), k in kinds) {
                    onDraft(e.copy(draft = if (k in kinds) kinds - k else kinds + k))
                }
            }
        }
        AccountField.SALARY -> {
            ok = rangeEditable && e.draft is SalaryRange
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in SalaryRange.entries) FillChip(salaryRangeLabel(r, currency), e.draft == r, enabled = rangeEditable) { onDraft(e.copy(draft = r)) }
            }
            if (!rangeEditable) NotYetLine(t(UiKey.ACC_SALARY_NOT_YET))
        }
        AccountField.PAYDAY -> {
            DayGrid(e.draft as? Int, { onDraft(e.copy(draft = it)) })
            BasicText(t(UiKey.ACC_PAYDAY_NOTE), style = Type.caption().copy(color = Ink.muted))
            // قرار المالك 2026-10-09: التغيير «من الشهر القادم بس» — كوتلن عنده يوم راتب واحد بيحرّك كل الشهور (OVERRIDES §76 ⚠️)
            NotYetLine(t(UiKey.ACC_PAYDAY_NEXT_ONLY))
        }
        else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (v in listOf(true, false)) FillChip(t(if (v) UiKey.MORE_YES else UiKey.MORE_NO), e.draft == v) { onDraft(e.copy(draft = v)) }
        }
    }
    if (e.error != null) FieldError(e.error)
    PrimaryButton(
        saveLabel,
        onClick = { if (e.field == AccountField.SALARY) (e.draft as? SalaryRange)?.let(onRange) else onSave(e.field, e.draft) },
        enabled = ok, modifier = Modifier.fillMaxWidth(),
    )
}

/** زرارين جنب بعض: الأول أساسي (أو أحمر للخروج) والتاني رمادي. */
@Composable
fun TwoActions(first: String, danger: Boolean, onFirst: () -> Unit, second: String, onSecond: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (danger) {
            val press = rememberPress()
            Box(
                Modifier.weight(1f).height(48.dp).pressScale(press).clip(RoundedCornerShape(16.dp)).background(Ink.expense).tap(press, onClick = onFirst),
                contentAlignment = Alignment.Center,
            ) { BasicText(first, style = Type.of(15, FontWeight.Bold).copy(color = Color.White)) }
        } else PrimaryButton(first, onClick = onFirst, modifier = Modifier.weight(1f))
        TonalButton(second, onClick = onSecond, modifier = Modifier.weight(1f), muted = true)
    }
}
