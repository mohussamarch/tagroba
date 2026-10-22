package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Id
import app.masroufy.core.R06Key
import app.masroufy.core.UiKey
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
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

/** سطر في صفحة التوزيع: خطة موجودة ([planId]) أو مشترى جديد (الاسم + العدد) — والقسط مكتوب نص لحد ما يتقري بالهللة. */
private class SplitRow(val planId: Id?, val name: String, amount: String) {
    var amount by mutableStateOf(amount)
    var what by mutableStateOf("")
    var count by mutableStateOf("4")
}

/**
 * PLAN-0.6 §8 — «مجموع أكتر من قسط»: المالك بيختار الخطط اللي الخصم ده دفعها (أو «+ مشترى جديد») ويكتب قسط كل واحدة. الفرق بيبان تحت
 * (`BnplSplitCharge.diff` — الشاشة ما بتحسبش)، والحفظ مقفول لحد ما يبقى صفر. [transactionId] null = مقفولة. ستايل البرنامج الحالي —
 * التصميم هيرسمه بعدين («محتاج رسم»).
 */
@Composable
fun BnplMultiSheet(transactionId: Id?, onDismiss: () -> Unit, onDone: (BnplSplitUndo, Int) -> Unit) {
    val split = LocalSpace.current.dues.bnplSplit ?: return
    val scope = rememberCoroutineScope()
    var view by remember(transactionId) { mutableStateOf<BnplSplitView?>(null) }
    val rows = remember(transactionId) { mutableStateListOf<SplitRow>() }
    var error by remember(transactionId) { mutableStateOf<String?>(null) }
    var saving by remember(transactionId) { mutableStateOf(false) }
    LaunchedEffect(transactionId) { if (transactionId != null) view = runCatching { split.view(transactionId) }.getOrNull() }
    val v = view
    Sheet(transactionId != null && v != null, onDismiss, title = t(R06Key.BNPL_MULTI_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        val vv = v ?: return@Sheet
        val currency = vv.charge.currency
        BasicText(t(R06Key.BNPL_MULTI_TITLE), style = Type.of(17, FontWeight.Bold))
        BasicText(t(R06Key.BNPL_MULTI_BODY, amountLabel(vv.charge.amountMinor, currency)), style = Type.of(13).copy(color = Ink.muted))
        if (vv.options.isEmpty()) BasicText(t(R06Key.BNPL_MULTI_EMPTY), style = Type.of(12).copy(color = Ink.muted))
        for (o in vv.options) {
            val picked = rows.firstOrNull { it.planId == o.planId }
            SelectChip(o.name + " · " + amountLabel(o.installmentMinor, o.currency), picked != null, {
                if (picked != null) rows.remove(picked) else rows += SplitRow(o.planId, o.name, plainAmount(o.installmentMinor, currency))
                error = null
            }, height = 44.dp)
        }
        SelectChip(t(R06Key.BNPL_MULTI_NEW), false, { rows += SplitRow(null, "", ""); error = null }, height = 44.dp)
        for (r in rows) SplitRowFields(r, currency)
        val parts = rows.map { partOf(it, currency) }
        val diff = if (parts.any { it == null }) null else split.diff(vv.charge.amountMinor, parts.filterNotNull())
        if (diff != null && diff != 0L) BasicText(t(R06Key.BNPL_MULTI_DIFF, amountLabel(diff, currency)), style = Type.of(13, FontWeight.Bold).copy(color = Ink.expense))
        error?.let { FieldError(it) }
        PrimaryButton(t(R06Key.BNPL_MULTI_SAVE), loading = saving, enabled = diff == 0L && rows.size >= 2, height = 52.dp, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                saving = true
                try {
                    val undo = split.save(vv.charge.id, parts.filterNotNull())
                    onDone(undo, parts.size)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = bnplErrorText(e) ?: e.message
                }
                saving = false
            }
        })
    }
}

@Composable
private fun SplitRowFields(r: SplitRow, currency: Currency) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (r.planId == null) {
            TextInput(r.what, { r.what = it }, Modifier.fillMaxWidth(), label = t(R06Key.BNPL_MULTI_NEW_WHAT))
            TextInput(r.count, { r.count = it }, Modifier.fillMaxWidth(), label = t(R06Key.BNPL_MULTI_NEW_COUNT), keyboard = KeyboardType.Number, ltr = true)
        } else {
            BasicText(r.name, style = Type.of(13, FontWeight.Bold))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextInput(r.amount, { r.amount = it }, Modifier.fillMaxWidth(), label = t(R06Key.BNPL_MULTI_AMOUNT), keyboard = KeyboardType.Decimal, ltr = true)
        }
    }
}

/** قراية السطر بالهللة (قراية مش حساب). null = لسه مش مكتوب صح. */
private fun partOf(r: SplitRow, currency: Currency): BnplSplitPart? {
    val amount = tryParseMoney(r.amount.trim(), currency)?.takeIf { it > 0 } ?: return null
    if (r.planId != null) return BnplSplitPart(amount, planId = r.planId)
    val count = r.count.trim().toIntOrNull()?.takeIf { it in 1..60 } ?: return null
    return BnplSplitPart(amount, newPurchase = BnplPurchase(r.what, null, count))
}

/** القسط المقترح في الخانة (من غير علامة العملة). */
private fun plainAmount(minor: Long, currency: Currency): String = app.masroufy.core.formatMoney(minor, currency, showCurrency = false, grouping = false)
