package app.masroufy.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.PayFrequency
import app.masroufy.core.TextKey
import app.masroufy.core.normalizeDigits
import app.masroufy.core.sentenceNumber
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
import app.masroufy.usecase.IncomeSourceInput
import kotlinx.coroutines.launch

private val WEEK_FROM_SATURDAY = listOf(6, 7, 1, 2, 3, 4, 5)

/**
 * «تعديل» مصدر الدخل (`IncomeSourceEditSheet` — `ManageIncomeSources.edit` + `applyMonthStart` لو وافق): الاسم · يوم القبض (أو يوم الأسبوع
 * للأسبوعي) · المتوقع (اختياري — فاضي = «غير متاح» مش صفر). «تغيّر بداية شهرك المالي؟» للوظيفة الشغالة بس لما اليوم يتغير لغير بداية الشهر.
 * **النهاية مش من هنا** («إنهاء المصدر» — `edit` ما بيغيّرش تاريخ النهاية). الأخطاء من `checkIncomeSource` نفسها.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncomeSourceEditSheet(visible: Boolean, s: IncomeSource, monthStart: Int, onClose: () -> Unit, onSaved: () -> Unit) {
    val deps = LocalSpace.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(s.name) }
    var dayText by remember { mutableStateOf("") }
    var weekday by remember { mutableStateOf(s.payWeekday) }
    var expectedText by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<Boolean?>(null) }
    var tried by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        name = s.name; dayText = s.expectedDayOfMonth?.toString().orEmpty(); weekday = s.payWeekday
        expectedText = s.expectedMinor?.let { amountLabel(it, s.currency, showCurrency = false) }.orEmpty()
        answer = null; tried = false; error = null; busy = false
    }
    val weekly = s.payFrequency == PayFrequency.WEEKLY
    val day = normalizeDigits(dayText).filter { it.isDigit() }.toIntOrNull()
    val dayBad = dayText.isNotBlank() && (day == null || day !in 1..31)
    val expected = expectedText.trim().takeIf { it.isNotEmpty() }?.let { tryParseMoney(it, s.currency) }
    val expectedBad = expectedText.isNotBlank() && (expected == null || expected <= 0)
    val job = s.kind == IncomeSourceKind.JOB && s.endedAt == null && !weekly
    val dayChanged = !weekly && day != s.expectedDayOfMonth
    val ask = job && dayChanged && day != null && day in 1..31 && day != monthStart
    val changed = name.trim() != s.name || dayChanged || (weekly && weekday != s.payWeekday) || expected != s.expectedMinor
    val problem = when {
        name.isBlank() -> t(TextKey.INCEDIT_ERR_NAME)
        dayBad -> t(TextKey.INCEDIT_ERR_DAY)
        ask && answer == null -> t(TextKey.INCEDIT_ERR_MONTH)
        expectedBad -> t(TextKey.INCEDIT_ERR_EXPECTED)
        else -> null
    }
    val period = periodText(s)
    Sheet(visible, onClose, title = t(TextKey.INCEDIT_TITLE), closeLabel = t(TextKey.SHELL_CLOSE), corner = 28.dp, spacing = 10.dp) {
        BasicText(t(TextKey.INCEDIT_TITLE), style = Type.of(17, FontWeight.Bold))
        BasicText(t(TextKey.INCSRC_META, t(incomeKindLabel(s.kind)), period), style = Type.of(13).copy(color = Ink.muted))
        TextInput(name, { name = it; error = null }, label = t(TextKey.INCEDIT_NAME), error = if (tried && name.isBlank()) t(TextKey.INCEDIT_ERR_NAME) else null)
        if (weekly) {
            BasicText(t(TextKey.INCEDIT_WEEKDAY), style = Type.of(13, FontWeight.Bold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in WEEK_FROM_SATURDAY) SelectChip(t(weekdayLabel(d)), weekday == d, onClick = { weekday = d }, height = 44.dp)
            }
        } else {
            TextInput(dayText, { dayText = it; answer = null }, label = t(TextKey.INCEDIT_DAY), placeholder = "1–31", ltr = true, keyboard = KeyboardType.Number,
                error = if (dayBad) t(TextKey.INCEDIT_ERR_DAY) else null)
            BasicText(t(if (day == null) TextKey.INCEDIT_DAY_EMPTY else TextKey.INCEDIT_DAY_NOTE), style = Type.caption().copy(color = Ink.muted))
        }
        if (job) MonthStartBox(ask, day, monthStart, answer) { answer = it }
        TextInput(expectedText, { expectedText = it }, label = t(TextKey.INCEDIT_EXPECTED), placeholder = t(TextKey.INCEDIT_EXPECTED_PH), ltr = true,
            keyboard = KeyboardType.Decimal, error = if (expectedBad) t(TextKey.INCEDIT_ERR_EXPECTED) else null)
        BasicText(t(if (expected == null) TextKey.INCEDIT_EXPECTED_EMPTY else TextKey.INCEDIT_EXPECTED_NOTE), style = Type.caption().copy(color = Ink.muted))
        s.endedAt?.let { NoteBox(t(TextKey.INCEDIT_PAST_NOTE), title = t(TextKey.INCEDIT_PAST_HEAD, fullDate(it) ?: it)) }
        val shown = error ?: if (tried && !changed) t(TextKey.INCEDIT_UNCHANGED) else if (tried) problem else null
        if (shown != null) FieldError(shown)
        PrimaryButton(
            if (changed) t(TextKey.INCEDIT_SAVE) else t(TextKey.INCEDIT_UNCHANGED),
            onClick = {
                tried = true
                if (!changed || problem != null || busy) return@PrimaryButton
                busy = true
                scope.launch {
                    val input = IncomeSourceInput(
                        name.trim(), s.startedAt, s.kind, s.currency,
                        expectedDayOfMonth = if (weekly) null else day, expectedMinor = expected,
                        payFrequency = s.payFrequency, payWeekday = if (weekly) weekday else null,
                    )
                    runCatching {
                        deps.more.incomeSources.edit(s.id, input)
                        if (ask && answer == true && day != null) deps.more.incomeSources.applyMonthStart(day)
                    }.onSuccess {
                        toaster.show(if (ask && answer == true && day != null) t(TextKey.ACC_PAYDAY_SAVED, sentenceNumber(day)) else t(TextKey.INCEDIT_SAVED), dark = true)
                        onSaved()
                        onClose()
                    }.onFailure { error = it.message ?: t(TextKey.MORE_SAVE_FAILED) }
                    busy = false
                }
            },
            enabled = !busy, loading = busy, height = 52.dp, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** كارت بداية الشهر: معلومة لما ما فيش تغيير، وسؤال كهرماني لما اليوم يتغير. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthStartBox(ask: Boolean, day: Int?, monthStart: Int, answer: Boolean?, onAnswer: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (ask) Ink.alertBg else Color(0x0F08634F)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val head = when {
            ask -> t(TextKey.JOB_Q_MONTH_START, sentenceNumber(day ?: monthStart))
            day == monthStart -> t(TextKey.INCEDIT_MONTH_SAME, sentenceNumber(monthStart))
            else -> t(TextKey.INCEDIT_MONTH_INFO, sentenceNumber(monthStart))
        }
        BasicText(head, style = Type.of(14, FontWeight.Bold).copy(color = if (ask) Color(0xFF6B4600) else Ink.primary))
        if (ask) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip(t(TextKey.JOB_MONTH_YES), answer == true, onClick = { onAnswer(true) }, height = 48.dp)
                SelectChip(t(TextKey.JOB_MONTH_NO, sentenceNumber(monthStart)), answer == false, onClick = { onAnswer(false) }, height = 48.dp)
            }
            NotYetLine(t(TextKey.ACC_PAYDAY_NEXT_ONLY))
        } else BasicText(t(TextKey.INCEDIT_MONTH_NOTE), style = Type.caption().copy(color = Ink.soft))
    }
}
