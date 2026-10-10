package app.masroufy.ui.screens.more

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IncomeFollowUp
import app.masroufy.core.PayFrequency
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** ترتيب أيام الأسبوع في الشرائح: من السبت (زي التقويم — §76) بأرقام ISO (الاتنين = 1 … الأحد = 7). */
private val WEEK_FROM_SATURDAY = listOf(6, 7, 1, 2, 3, 4, 5)

/**
 * سؤال واحد من أسئلة «غيّرت عملي» (`IncomeFollowUp` من النتيجة نفسها) — كل سؤال بيتخطّى. الرد بيروح لحالة الاستخدام اللي ليه:
 * يوم الراتب `answerPayday` · الدورية `answerPayFrequency` · بداية الشهر `applyMonthStart` · المتوقع `answerExpectedSalary`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FollowUpQuestion(
    q: IncomeFollowUp,
    step: Int,
    total: Int,
    monthStart: Int,
    busy: Boolean,
    onPayday: (Id, Int) -> Unit,
    onFrequency: (Id, PayFrequency, Int) -> Unit,
    onMonthStart: (Int, Boolean) -> Unit,
    onExpected: (Id, Halalas) -> Unit,
    onSkip: () -> Unit,
) {
    var freq by remember(q) { mutableStateOf<PayFrequency?>(null) }
    var amountText by remember(q) { mutableStateOf("") }
    val currency = LocalSpace.current.space.currency
    val text = when (q) {
        is IncomeFollowUp.AskPayday -> t(TextKey.JOB_Q_PAYDAY)
        is IncomeFollowUp.ChangeMonthStart -> t(TextKey.JOB_Q_MONTH_START, sentenceNumber(q.day))
        is IncomeFollowUp.AskPayFrequency -> when (freq) {
            null -> t(TextKey.JOB_Q_FREQUENCY)
            PayFrequency.WEEKLY -> t(TextKey.JOB_Q_WEEKDAY)
            PayFrequency.MONTHLY -> t(TextKey.JOB_Q_MONTH_DAY)
        }
        is IncomeFollowUp.AskExpectedSalary -> t(TextKey.JOB_Q_EXPECTED)
        IncomeFollowUp.CarToWork -> t(TextKey.ACC_CAR_TO_WORK_Q)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(text, Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
            BasicText(t(TextKey.JOB_Q_STEP, sentenceNumber(step), sentenceNumber(total)), style = Type.caption().copy(color = Ink.muted))
        }
        when (q) {
            is IncomeFollowUp.AskPayday -> DayGrid(null, { if (!busy) onPayday(q.sourceId, it) })
            is IncomeFollowUp.ChangeMonthStart -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FillChip(t(TextKey.JOB_MONTH_YES), false, enabled = !busy) { onMonthStart(q.day, true) }
                    FillChip(t(TextKey.JOB_MONTH_NO, sentenceNumber(monthStart)), false, enabled = !busy) { onMonthStart(q.day, false) }
                }
                // قرار المالك 2026-10-09: «من الشهر القادم بس» — كوتلن بيحرّك كل الشهور
                NotYetLine(t(TextKey.ACC_PAYDAY_NEXT_ONLY))
            }
            is IncomeFollowUp.AskPayFrequency -> when (freq) {
                null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FillChip(t(TextKey.JOB_FREQ_MONTHLY), false) { freq = PayFrequency.MONTHLY }
                    FillChip(t(TextKey.JOB_FREQ_WEEKLY), false) { freq = PayFrequency.WEEKLY }
                }
                PayFrequency.MONTHLY -> DayGrid(null, { if (!busy) onFrequency(q.sourceId, PayFrequency.MONTHLY, it) })
                PayFrequency.WEEKLY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in WEEK_FROM_SATURDAY) FillChip(t(weekdayLabel(d)), false, enabled = !busy) { onFrequency(q.sourceId, PayFrequency.WEEKLY, d) }
                }
            }
            is IncomeFollowUp.AskExpectedSalary -> {
                val minor = amountText.trim().takeIf { it.isNotEmpty() }?.let { tryParseMoney(it, currency) }?.takeIf { it > 0 }
                TextInput(amountText, { amountText = it }, placeholder = "0.00", ltr = true, keyboard = KeyboardType.Decimal,
                    error = if (amountText.isNotBlank() && minor == null) t(TextKey.WADD_ERR_AMOUNT) else null)
                PrimaryButton(t(TextKey.MORE_SAVE), onClick = { minor?.let { onExpected(q.sourceId, it) } }, enabled = minor != null && !busy, modifier = Modifier.fillMaxWidth())
            }
            IncomeFollowUp.CarToWork -> Unit
        }
        TonalButton(t(TextKey.JOB_SKIP), onClick = onSkip, enabled = !busy, muted = true, height = 44.dp)
    }
}
