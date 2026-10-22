package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.R06Key
import app.masroufy.core.Transaction
import app.masroufy.core.formatMoney
import app.masroufy.core.ruleFor
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.IncomeAnswers
import app.masroufy.usecase.PayerAskView
import kotlinx.coroutines.launch

/**
 * PLAN-0.6 §7 — جوه [IncomeKindCard]: تحويل من حساب جهة الشغل **مش راتب** ⇒ «الفلوس دي إيه؟» بردود جاهزة (راتب · استرداد مصاريف · مكافأة
 * أو بدل · سلفة · حاجة تانية) بدل الردود العامة، والرد بيتحفظ قاعدة للحساب. الحساب ليه قاعدة ⇒ سطر «بتتسجل … لوحدها» و«الغي الاختيار ده».
 * بيرجّع true لو عرض السؤال (الكارت العام ما يتعرضش). ستايل البرنامج الحالي — التصميم هيرسمه بعدين (PLAN §7 «محتاج رسم»).
 */
@Composable
fun payerAskShown(transactionId: Id, onDone: (IncomeDone) -> Unit, onChanged: () -> Unit): Boolean {
    val payer = LocalSpace.current.operations.payerKinds ?: return false
    var view by remember(transactionId) { mutableStateOf<PayerAskView?>(null) }
    var reload by remember(transactionId) { mutableIntStateOf(0) }
    LaunchedEffect(transactionId, reload) { view = attempt { payer.view(transactionId) } }
    val v = view ?: return false
    val scope = rememberCoroutineScope()
    v.ruleKind?.let { kind ->
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(t(R06Key.PAYER_RULE_LINE, ruleFor(kind).label), style = Type.of(12).copy(color = Ink.muted))
            SelectChip(t(R06Key.PAYER_RULE_FORGET), false, { scope.launch { attempt { payer.forgetRule(transactionId) }; reload++; onChanged() } })
        }
    }
    if (!v.asks) return false
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Ink.alertBg).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(t(R06Key.PAYER_ASK_TITLE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.focus))
        PayerAskChips(transactionId, onDone)
    }
    return true
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PayerAskChips(transactionId: Id, onDone: (IncomeDone) -> Unit) {
    val payer = LocalSpace.current.operations.payerKinds ?: return
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var other by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun answer(kind: EconomicKind) = scope.launch {
        var done: IncomeDone? = null
        error = failureOf {
            val undo = payer.answer(transactionId, kind)
            val label = ruleFor(kind).label
            done = IncomeDone(
                t(app.masroufy.core.UiKey.INCOME_ANS_DONE_INCOME, label), t(R06Key.PAYER_RULE_SAVED, label), undo,
                refund = kind == EconomicKind.REFUND_RECEIVED,
            )
        }
        done?.let(onDone)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectChip(t(R06Key.PAYER_ASK_SALARY), false, { picking = true; error = null })
        SelectChip(t(R06Key.PAYER_ASK_REFUND), false, { answer(EconomicKind.REFUND_RECEIVED) })
        SelectChip(t(R06Key.PAYER_ASK_BONUS), false, { answer(EconomicKind.BONUS) })
        SelectChip(t(R06Key.PAYER_ASK_ADVANCE), false, { answer(EconomicKind.ADVANCE_RECEIVED) })
        SelectChip(t(R06Key.PAYER_ASK_OTHER), other, { other = !other })
    }
    if (other) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in IncomeAnswers.OTHER_KINDS) SelectChip(ruleFor(k).label, false, { answer(k) })
        }
    }
    error?.let { FieldError(it) }
    EmployerPickSheet(picking, transactionId, onDismiss = { picking = false }) { picking = false; onDone(it) }
}

/** «استرداد لأنهي مشترى؟» بعد «استرداد مصاريف»: مشتريات مبلغها قريب في آخر 60 يوم ⇒ ضغطة بتربط (`PayerKindAnswers.linkRefund`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RefundPickList(transactionId: Id, onChanged: () -> Unit) {
    val payer = LocalSpace.current.operations.payerKinds ?: return
    val scope = rememberCoroutineScope()
    var list by remember(transactionId) { mutableStateOf<List<Transaction>?>(null) }
    var linked by remember(transactionId) { mutableStateOf<String?>(null) }
    var error by remember(transactionId) { mutableStateOf<String?>(null) }
    LaunchedEffect(transactionId) { list = attempt { payer.refundCandidates(transactionId) }.orEmpty() }
    val items = list ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        linked?.let { BasicText(t(R06Key.REFUND_LINKED, it), style = Type.of(13, FontWeight.Bold).copy(color = ChipInk.green)); return@Column }
        BasicText(t(R06Key.REFUND_PICK_TITLE), style = Type.of(13, FontWeight.Bold))
        if (items.isEmpty()) BasicText(t(R06Key.REFUND_PICK_EMPTY), style = Type.of(12).copy(color = Ink.muted))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (e in items) {
                val name = refundLabel(e)
                SelectChip(name, false, {
                    scope.launch {
                        val refusal = attempt { payer.linkRefund(transactionId, e.id) }
                        if (refusal == null) { linked = name; onChanged() } else error = t(linkRefusalKey(refusal))
                    }
                })
            }
        }
        error?.let { FieldError(it) }
    }
}

/** اسم المشترى في الاختيار: التاجر أو الوصف · المبلغ · اليوم. */
private fun refundLabel(e: Transaction): String =
    listOfNotNull(e.rawMerchantName ?: e.rawDescription, formatMoney(e.amountMinor, e.currency), e.occurredAt.take(10)).joinToString(" · ")

/** «مستني استرداد» على مشترى (PLAN §7): علامة بتشيله من مصروفك لحد ما الاسترداد يتربط بيه. */
@Composable
fun AwaitRefundToggle(t0: Transaction, onChanged: () -> Unit) {
    val payer = LocalSpace.current.operations.payerKinds ?: return
    val scope = rememberCoroutineScope()
    var error by remember(t0.id) { mutableStateOf<String?>(null) }
    val on = t0.awaitingRefund == true
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (on) BasicText(t(R06Key.AWAIT_REFUND_ON), style = Type.of(12).copy(color = Ink.muted))
        SelectChip(t(if (on) R06Key.AWAIT_REFUND_OFF else R06Key.AWAIT_REFUND_MARK), on, {
            scope.launch { error = failureOf { payer.setAwaitingRefund(t0.id, !on) }; if (error == null) onChanged() }
        })
        error?.let { FieldError(it) }
    }
}
