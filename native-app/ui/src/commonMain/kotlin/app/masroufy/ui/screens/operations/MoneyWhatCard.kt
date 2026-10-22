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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.PAYER_CUSTOM_EMOJIS
import app.masroufy.core.PayerCustomKind
import app.masroufy.core.R06Key
import app.masroufy.core.dayMonth
import app.masroufy.core.formatMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.AnswerUndo
import app.masroufy.usecase.PayerAskView
import app.masroufy.usecase.RefundPicks
import kotlinx.coroutines.launch

/** الاختيار في الكارت: الأربعة الجاهزة أو اختيار محفوظ للحساب ده ([CUSTOM] + رقمه). */
internal enum class MwPick { REFUND, BONUS, LOAN, OTHER, CUSTOM }

/** بعد الحفظ: السطر الأخضر + اللي يلزم لـ«تراجع». */
private data class MwDone(val text: String, val undo: AnswerUndo)

/**
 * r06-1b3 — كارت «الفلوس دي إيه؟» (التصميم `MoneyWhat.dc.html` — قرارات المالك 2026-10-17 وتعديلين 2026-10-22): تحويل داخل من حساب جهة شغل
 * ومش شبه المرتب. سطر أصفر بالسبب، و4 مربعات (+ اختيارات «حاجة تانية» المحفوظة **للحساب ده بس**)، وكل اختيار بيبين أثره قبل الحفظ،
 * وتحته «خليها قاعدة للمرات الجاية» **فاضية من الأول**. بعد الحفظ «— والقاعدة اتحفظت» أو «— وهيسألك تاني المرة الجاية» و«تراجع».
 * الشاشة ما بتحسبش فلوس: المجموع من `PayerKindAnswers.refundSum` والتوزيع والربط من `answerRefund`.
 */
@Composable
fun MoneyWhatCard(transactionId: Id, view: PayerAskView, onChanged: () -> Unit) {
    val payer = LocalSpace.current.operations.payerKinds ?: return
    val scope = rememberCoroutineScope()
    var pick by remember(transactionId) { mutableStateOf<MwPick?>(null) }
    var customAt by remember(transactionId) { mutableStateOf(-1) }
    var name by remember(transactionId) { mutableStateOf("") }
    var emoji by remember(transactionId) { mutableStateOf(PAYER_CUSTOM_EMOJIS.first()) }
    var rule by remember(transactionId) { mutableStateOf(false) }
    var picks by remember(transactionId) { mutableStateOf<RefundPicks?>(null) }
    var chosen by remember(transactionId) { mutableStateOf<Set<Id>>(emptySet()) }
    var customs by remember(transactionId) { mutableStateOf(view.customs) }
    var done by remember(transactionId) { mutableStateOf<MwDone?>(null) }
    var saving by remember(transactionId) { mutableStateOf(false) }
    var error by remember(transactionId) { mutableStateOf<String?>(null) }
    LaunchedEffect(transactionId, pick) {
        if (pick == MwPick.REFUND && picks == null) {
            picks = attempt { payer.refundPicks(transactionId) }?.also { chosen = it.preselected }
        }
    }
    fun reset() { pick = null; customAt = -1; name = ""; rule = false; error = null }
    val custom = customs.getOrNull(customAt).takeIf { pick == MwPick.CUSTOM }
    val typed = PayerCustomKind(emoji, name.trim())
    val kindName = when (pick) {
        MwPick.REFUND -> t(R06Key.PAYER_ASK_REFUND)
        MwPick.BONUS -> t(R06Key.PAYER_ASK_BONUS)
        MwPick.LOAN -> t(R06Key.PAYER_ASK_ADVANCE)
        MwPick.OTHER -> "$emoji ${name.trim().ifEmpty { "…" }}"
        MwPick.CUSTOM -> custom?.label.orEmpty()
        null -> ""
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Ink.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(
                t(R06Key.MW_CHIP),
                Modifier.clip(RoundedCornerShape(8.dp)).background(Ink.transfer).padding(horizontal = 8.dp, vertical = 2.dp),
                style = Type.of(11, FontWeight.Bold).copy(color = Ink.onPrimary),
            )
            BasicText(t(R06Key.PAYER_ASK_TITLE), style = Type.of(16, FontWeight.Bold))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(R06Key.MW_LINE, view.employerName, dayMonth(view.date)), Modifier.weight(1f), style = Type.of(14))
            BasicText("+" + formatMoney(view.amountMinor, view.currency), style = Type.of(17, FontWeight.Bold).copy(color = Ink.primary))
        }
        val d = done
        if (d != null) {
            MwDoneBox(d.text) {
                scope.launch {
                    val err = failureOf { payer.undo(transactionId, d.undo) }
                    if (err == null) { done = null; reset(); onChanged() } else error = err
                }
            }
            error?.let { FieldError(it) }
            return@Column
        }
        when (pick) {
            null -> {
                view.why?.let { MwWhyLine(it) }
                MwTiles(customs, view.employerName) { p, i -> pick = p; customAt = i; error = null }
            }
            MwPick.REFUND -> picks?.let { p ->
                MwRefundList(p, chosen, payer.refundSum(view.amountMinor, p.candidates.filter { it.expense.id in chosen })) { id ->
                    chosen = if (id in chosen) chosen - id else chosen + id
                }
            }
            MwPick.OTHER -> MwOtherFields(name, { name = it }, emoji) { emoji = it }
            else -> Unit
        }
        if (pick == null) return@Column
        val effect = when (pick) {
            MwPick.REFUND -> t(R06Key.MW_EFF_REFUND)
            MwPick.BONUS -> t(R06Key.MW_EFF_BONUS)
            MwPick.LOAN -> t(R06Key.MW_EFF_LOAN, view.employerName, formatMoney(view.amountMinor, view.currency))
            MwPick.OTHER -> if (name.isBlank()) t(R06Key.MW_EFF_OTHER_EMPTY) else t(R06Key.MW_EFF_OTHER, kindName, view.employerName)
            else -> t(R06Key.MW_EFF_CUSTOM, kindName)
        }
        MwEffect(effect)
        val ruleText = when {
            !rule -> t(R06Key.MW_RULE_OFF, view.employerName)
            pick == MwPick.REFUND -> t(R06Key.MW_RULE_ON_REFUND, view.employerName, kindName)
            else -> t(R06Key.MW_RULE_ON, view.employerName, kindName)
        }
        MwRuleBox(rule, ruleText) { rule = !rule }
        error?.let { FieldError(it) }
        val off = (pick == MwPick.REFUND && chosen.isEmpty()) || (pick == MwPick.OTHER && name.isBlank())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TonalButton(t(R06Key.MW_BACK), { reset() }, Modifier.weight(1f))
            PrimaryButton(
                t(if (pick == MwPick.REFUND) R06Key.MW_LINK_SAVE else R06Key.MW_SAVE), modifier = Modifier.weight(1.4f), enabled = !off, loading = saving,
                onClick = {
                    scope.launch {
                        saving = true
                        var undo: AnswerUndo? = null
                        val isNew = pick == MwPick.OTHER && customs.none { it.name == typed.name }
                        error = failureOf {
                            undo = when (pick) {
                                MwPick.REFUND -> {
                                    val ids = picks?.candidates.orEmpty().map { it.expense.id }.filter { it in chosen }
                                    val r = payer.answerRefund(transactionId, ids, rule)
                                    r.refusal?.let { throw IllegalStateException(t(linkRefusalKey(it))) }
                                    r.undo
                                }
                                MwPick.BONUS -> payer.answer(transactionId, EconomicKind.BONUS, rule)
                                MwPick.LOAN -> payer.answer(transactionId, EconomicKind.ADVANCE_RECEIVED, rule)
                                MwPick.OTHER -> payer.answerCustom(transactionId, typed, rule)
                                else -> custom?.let { payer.answerCustom(transactionId, it, rule) }
                            }
                        }
                        saving = false
                        val u = undo ?: return@launch
                        if (isNew) customs = customs + typed
                        val tail = (if (isNew) t(R06Key.MW_DONE_CUSTOM, view.employerName) else "") +
                            t(if (rule) R06Key.MW_DONE_RULE else R06Key.MW_DONE_ASK)
                        done = MwDone(t(R06Key.MW_DONE, kindName) + tail, u)
                        onChanged()
                    }
                },
            )
        }
    }
}
