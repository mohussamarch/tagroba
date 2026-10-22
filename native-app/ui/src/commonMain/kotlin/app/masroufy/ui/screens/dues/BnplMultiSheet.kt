package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Id
import app.masroufy.core.R06Key
import app.masroufy.core.UiKey
import app.masroufy.core.formatMoney
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.BnplPurchase
import app.masroufy.usecase.BnplSplitPart
import app.masroufy.usecase.BnplSplitUndo
import app.masroufy.usecase.BnplSplitView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** سطر «اتوزّع»: الاسم والمبلغ. */
private class DoneRow(val name: String, val amountMinor: Long)

/**
 * r06-1b4 — «الخصم ده فيه كام قسط؟» (`BnplSplit.dc.html`): الخطط المفتوحة لنفس المزوّد بعلامات اختيار (المقترحة متعلّمة من الأول وفوقها
 * «غالبًا دول …») · «+ مشترى جديد» (الاسم · قيمة الدفعة بالباقي · قسمتها على · الدفعة دي رقم) · سطر المجموع · «وزّع على الأقساط» شغال بس لما
 * المجموع = الخصم بالظبط (`BnplSplitCharge.ready`) · بعد الحفظ «اتوزّع» بكل جزء، و«تمام» بيقفل ويطلّع «تراجع». الشاشة ما بتحسبش: المجموع والباقي
 * من حالة الاستخدام، والمكتوب بيتقري بالهللة (`tryParseMoney` — أعداد صحيحة). [transactionId] null = مقفولة.
 */
@Composable
fun BnplMultiSheet(transactionId: Id?, onDismiss: () -> Unit, onDone: (BnplSplitUndo, Int) -> Unit) {
    val split = LocalSpace.current.dues.bnplSplit ?: return
    val scope = rememberCoroutineScope()
    var view by remember(transactionId) { mutableStateOf<BnplSplitView?>(null) }
    val plans = remember(transactionId) { mutableStateListOf<SplitPlanRow>() }
    val news = remember(transactionId) { mutableStateListOf<SplitNewRow>() }
    var sugg by remember(transactionId) { mutableStateOf(false) }
    var error by remember(transactionId) { mutableStateOf<String?>(null) }
    var saving by remember(transactionId) { mutableStateOf(false) }
    var done by remember(transactionId) { mutableStateOf<Pair<BnplSplitUndo, List<DoneRow>>?>(null) }
    var nextKey by remember(transactionId) { mutableStateOf(0) }
    LaunchedEffect(transactionId) {
        if (transactionId == null) return@LaunchedEffect
        val v = runCatching { split.view(transactionId) }.getOrNull() ?: return@LaunchedEffect
        plans.clear()
        for (o in v.options) plans += SplitPlanRow(o, plainAmount(o.installmentMinor, v.charge.currency), o.planId in v.suggested)
        sugg = v.suggested.size >= 2
        view = v
    }
    val v = view
    val maxBody = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.62f).toDp() }.coerceAtLeast(280.dp)
    Sheet(transactionId != null && v != null, onDismiss, title = t(R06Key.BS_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        val vv = v ?: return@Sheet
        val currency = vv.charge.currency
        val charge = vv.charge.amountMinor
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(t(R06Key.BS_TITLE), style = Type.of(18, FontWeight.Bold))
            AmountText(charge, currency, size = 20)
        }
        val finished = done
        if (finished != null) {
            SplitDoneCard(t(R06Key.BS_DONE), finished.second.map { it.name to amountLabel(it.amountMinor, currency) })
            PrimaryButton(t(R06Key.BS_FINISH), { onDone(finished.first, finished.second.size) }, Modifier.fillMaxWidth(), height = 52.dp)
            return@Sheet
        }
        val picked = plans.filter { it.on }
        val amounts = picked.map { read(it.amount, currency) } + news.map { read(it.amount, currency) }
        val (sum, left) = split.tally(charge, amounts)
        Column(Modifier.weight(1f, fill = false).heightIn(max = maxBody).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(R06Key.BS_LEAD), style = Type.of(13, lineHeight = 1.7).copy(color = Ink.muted))
            if (sugg) {
                val names = plans.filter { it.on }.joinToString(" + ") { "«" + it.option.name + "»" }
                SplitSuggLine(t(R06Key.BS_SUGG, names))
            }
            if (vv.options.isEmpty()) BasicText(t(R06Key.BNPL_MULTI_EMPTY), style = Type.of(12).copy(color = Ink.muted))
            for (row in plans) {
                SplitPlanCard(row, currency, onToggle = { row.on = !row.on; sugg = false; error = null }, onAmount = { row.amount = it; sugg = false })
            }
            for (row in news) SplitNewCard(row, onRemove = { news.remove(row) })
            SplitAddButton(t(R06Key.BNPL_MULTI_NEW)) {
                news += SplitNewRow(nextKey++, if (left > 0) plainAmount(left, currency) else "")
                sugg = false
            }
            val line = when {
                left == 0L -> t(R06Key.BS_SUM_EXACT, amountLabel(sum, currency))
                left > 0 -> t(R06Key.BS_SUM_LEFT, amountLabel(sum, currency), amountLabel(left, currency))
                else -> t(R06Key.BS_SUM_OVER, amountLabel(sum, currency), amountLabel(-left, currency))
            }
            SplitSumLine(line, exact = left == 0L)
            error?.let { FieldError(it) }
        }
        val ready = split.ready(charge, amounts, news.all { it.name.isNotBlank() })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(t(R06Key.BS_SAVE), loading = saving, enabled = ready, height = 52.dp, modifier = Modifier.weight(2f), onClick = {
                val parts = picked.map { BnplSplitPart(read(it.amount, currency) ?: 0, planId = it.option.planId) } +
                    news.map { BnplSplitPart(read(it.amount, currency) ?: 0, newPurchase = BnplPurchase(it.name.trim(), null, it.split, at = it.at)) }
                val rows = picked.map { DoneRow(it.option.name, read(it.amount, currency) ?: 0) } +
                    news.map { DoneRow(t(R06Key.BS_DONE_NEW, it.name.trim(), it.at.toString(), it.split.toString()), read(it.amount, currency) ?: 0) }
                scope.launch {
                    saving = true
                    try {
                        done = split.save(vv.charge.id, parts) to rows
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = bnplErrorText(e) ?: e.message
                    }
                    saving = false
                }
            })
            BnplQuietButton(t(R06Key.BS_BACK), onDismiss, Modifier.weight(1f))
        }
    }
}

/** قراية المبلغ المكتوب بالهللة (قراية مش حساب) — null = لسه مش مكتوب صح أو صفر. */
private fun read(text: String, currency: Currency): Long? = tryParseMoney(text.trim(), currency)?.takeIf { it > 0 }

/** المبلغ في الخانة من غير علامة العملة ولا فواصل. */
private fun plainAmount(minor: Long, currency: Currency): String = formatMoney(minor, currency, showCurrency = false, grouping = false)
