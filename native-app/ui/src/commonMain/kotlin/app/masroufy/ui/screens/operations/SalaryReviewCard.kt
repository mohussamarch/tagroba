package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.PayerQuestion
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceDigits
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * قرار المالك (OVERRIDES §112-6): سؤال «ده مرتب من «…»؟» على إيداع من طرف جديد **اتنقل من «مصادر الدخل»** لـ«محتاجة مراجعة»
 * (`ReviewQueueScreen` — اللي شريط المراجعة في «العمليات» بيفتحها). المنطق نفسه ما اتغيّرش: `IncomeSourceSignals.payerQuestions`
 * (اكتشاف المرتب §110 ±10% / ±3 أيام والتعديل اليدوي) و`answerPayer`.
 */
@Composable
fun SalaryReviewCard() {
    val deps = LocalSpace.current
    val signals = deps.more.incomeSignals
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var question by remember(deps) { mutableStateOf<PayerQuestion?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(deps, tick) { question = runCatching { signals.payerQuestions().firstOrNull() }.getOrNull() }
    val q = question ?: return
    PayerCard(q) { yes ->
        scope.launch {
            val ok = runCatching { signals.answerPayer(q, yes) }.isSuccess
            toaster.show(t(if (!ok) UiKey.MORE_SAVE_FAILED else if (yes) UiKey.INCSRC_PAYER_YES_DONE else UiKey.INCSRC_PAYER_NO_DONE), dark = true)
            tick++
        }
    }
}

/** «هل هذا راتب من «…»؟» — كارت كهرماني بزرارين (من غير المبلغ: السؤال من `payerQuestions` مالوش مبلغ). */
@Composable
private fun PayerCard(q: PayerQuestion, onAnswer: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.alertBg).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(t(UiKey.INCSRC_PAYER_Q, q.sourceName), style = Type.of(15, FontWeight.Bold).copy(color = Color(0xFF6B4600)))
        val from = q.party.last4?.let { t(UiKey.INCSRC_PAYER_FROM_LAST4, dayMonth(q.date), q.party.label, sentenceDigits(it)) } ?: t(UiKey.INCSRC_PAYER_FROM, dayMonth(q.date), q.party.label)
        BasicText(from, style = Type.caption().copy(color = Color(0xFF6B4600)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(t(UiKey.MORE_YES), onClick = { onAnswer(true) }, modifier = Modifier.weight(1f))
            TonalButton(t(UiKey.MORE_NO), onClick = { onAnswer(false) }, modifier = Modifier.weight(1f), muted = true)
        }
    }
}
