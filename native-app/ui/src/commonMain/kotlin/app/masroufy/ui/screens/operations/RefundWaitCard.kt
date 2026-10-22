package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.masroufy.core.R06Key
import app.masroufy.core.Transaction
import app.masroufy.core.dayMonth
import app.masroufy.core.formatMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.RefundStatus
import kotlinx.coroutines.launch

/**
 * r06-1b3 — «مستني استرداد» على المشترى (التصميم `RefundWait.dc.html`، فوق «اربط بـ» في تفاصيل أي مشترى): زرار «مصروف شغل؟ هيترد لي» ⇐
 * علامة زرقا «مستني استرداد» + «شيل» (ما يتحسبش من مصروفك لحد ما الفلوس ترجع) ⇐ بعد الربط «اترد — من «الجهة» · التاريخ».
 * الاسم القديم [AwaitRefundToggle] فاضل عشان تفاصيل العملية ما تتغيرش.
 */
@Composable
fun AwaitRefundToggle(t0: Transaction, onChanged: () -> Unit) {
    val payer = LocalSpace.current.operations.payerKinds ?: return
    val scope = rememberCoroutineScope()
    var error by remember(t0.id) { mutableStateOf<String?>(null) }
    var status by remember(t0.id) { mutableStateOf<RefundStatus?>(null) }
    LaunchedEffect(t0.id, t0.updatedAt, t0.linkedInflowMinor, t0.awaitingRefund) { status = attempt { payer.refundStatus(t0.id) } }
    fun set(on: Boolean) = scope.launch { error = failureOf { payer.setAwaitingRefund(t0.id, on) }; if (error == null) onChanged() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (val s = status) {
            is RefundStatus.Refunded -> RefundBackBox(s, t0)
            RefundStatus.Waiting -> RefundWaitBox { set(false) }
            null -> if (t0.awaitingRefund == true) RefundWaitBox { set(false) } else RefundOffButton { set(true) }
        }
        error?.let { FieldError(it) }
    }
}

@Composable
private fun RefundOffButton(onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).pressScale(press).clip(shape).background(Color.White)
            .border(1.5.dp, Ink.transfer.copy(alpha = 0.35f), shape).tap(press, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(ChipInk.blueBg), contentAlignment = Alignment.Center) {
            LucideIcon(MoneyWhatIcons.ROTATE, size = 18.dp, tint = Ink.transfer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(t(R06Key.RW_OFF_TITLE), style = Type.of(14, FontWeight.Bold))
            BasicText(t(R06Key.RW_OFF_HINT), style = Type.of(12).copy(color = Ink.muted))
        }
    }
}

@Composable
private fun RefundWaitBox(onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.transfer.copy(alpha = 0.07f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MwBluePill(t(R06Key.MW_WAITING))
            Spacer(Modifier.weight(1f))
            TonalButton(t(R06Key.RW_REMOVE), onRemove, height = 36.dp)
        }
        BasicText(t(R06Key.RW_BODY), style = Type.of(13, lineHeight = 1.7))
    }
}

@Composable
private fun RefundBackBox(s: RefundStatus.Refunded, t0: Transaction) {
    val body = if (s.full) t(R06Key.RW_BACK_BODY, s.from, dayMonth(s.date))
    else t(R06Key.RW_BACK_PART, s.from, dayMonth(s.date), formatMoney(s.refundedMinor, t0.currency))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.primary.copy(alpha = 0.08f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Ink.primary), contentAlignment = Alignment.Center) {
            LucideIcon(Lucide.CHECK, size = 14.dp, tint = Color.White)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(t(R06Key.RW_BACK_TITLE), style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary))
            BasicText(body, style = Type.of(12).copy(color = Ink.muted))
        }
    }
}
