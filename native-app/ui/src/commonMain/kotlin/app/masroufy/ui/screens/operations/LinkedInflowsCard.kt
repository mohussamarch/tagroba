package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.LinkRefusal
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.home.transactionTitle
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LinkedInflowsView
import kotlinx.coroutines.launch

/** رسالة رفض الربط (حالة الاستخدام بترجع السبب، والشاشة بتختار النص بس). */
internal fun linkRefusalKey(r: LinkRefusal): UiKey = when (r) {
    LinkRefusal.OVER_EXPENSE -> UiKey.LINKED_ERR_OVER
    LinkRefusal.OTHER_CURRENCY -> UiKey.LINKED_ERR_CURRENCY
    LinkRefusal.LEDGER_LINKED -> UiKey.LINKED_ERR_LEDGER
    LinkRefusal.NOT_EXPENSE, LinkRefusal.NOT_INFLOW, LinkRefusal.ALREADY_LINKED -> UiKey.LINKED_ERR_OTHER
}

/** سطر عملية في الكارت واللوحة: الاسم والتاريخ · المبلغ — من غير «·» جنب رقم (§ نصوص العمليات). */
internal fun linkedLine(t: Transaction): String = transactionTitle(t) + "، " + dayMonth(t.occurredAt.take(10))

/**
 * «عمليات تابعة» (§6.5-14) في تفاصيل العملية: على **المصروف** ⇒ الأصل والصافي وشارة «فيه عملية تابعة» + التابعة (بـ«فك الربط») + «اختار»
 * بيفتح لوحة الفلوس اللي دخلت (تحديد أكتر من واحدة). على **الداخلة التابعة** ⇒ شارة بترجّع للمصروف + «فك الربط».
 * كل الحساب والفحص في `LinkInflows` — الشاشة بتعرض وبتنادي وبس. أي عملية تانية ⇒ ولا حاجة.
 */
@Composable
fun LinkedInflowsCard(transactionId: Id, reloadKey: Int, onChanged: () -> Unit) {
    val links = LocalSpace.current.operations.linkedInflows ?: return
    val toaster = LocalToaster.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var view by remember(transactionId) { mutableStateOf<LinkedInflowsView>(LinkedInflowsView.None) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(transactionId, reloadKey) { view = attempt { links.load(transactionId) } ?: LinkedInflowsView.None }
    fun unlink(inflowId: Id) = scope.launch {
        val err = failureOf { links.unlink(inflowId) }
        toaster.show(err ?: t(UiKey.LINKED_UNLINKED))
        onChanged()
    }
    when (val v = view) {
        LinkedInflowsView.None -> Unit
        is LinkedInflowsView.Inflow -> {
            val expense = v.expense
            LinkCard(
                Lucide.RECEIPT, t(UiKey.LINKED_INFLOW_TITLE), t(if (expense == null) UiKey.LINKED_INFLOW_GONE else UiKey.LINKED_INFLOW_SUB),
                t(UiKey.LINKED_UNLINK), enabled = true, onOpen = { unlink(v.inflow.id) },
            ) {
                if (expense != null) {
                    Divider()
                    LineRow(expense, AmountTone.EXPENSE) { SmallAction(t(UiKey.LINKED_INFLOW_OPEN)) { nav.push(OperationDetailRoute(expense.id)) } }
                }
            }
        }
        is LinkedInflowsView.Expense -> {
            val e = v.expense
            val sub = if (v.linked.isEmpty()) t(UiKey.LINKED_CARD_SUB) else t(UiKey.LINKED_BADGE)
            LinkCard(Lucide.RECEIPT, t(UiKey.LINKED_CARD_TITLE), sub, t(UiKey.LINKED_CARD_PICK), enabled = v.candidates.isNotEmpty(), onOpen = { picking = true }) {
                if (v.linked.isNotEmpty()) {
                    Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(t(UiKey.LINKED_BADGE), ChipInk.blue, ChipInk.blueBg)
                        BasicText(t(UiKey.LINKED_ORIGINAL), Modifier.weight(1f), style = Type.caption().copy(color = Ink.muted))
                        AmountText(e.amountMinor, e.currency, tone = AmountTone.EXPENSE, size = 13)
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(t(UiKey.LINKED_NET), Modifier.weight(1f), style = Type.of(13, FontWeight.Bold))
                        AmountText(v.netMinor, e.currency, tone = AmountTone.EXPENSE, size = 16)
                    }
                    for (l in v.linked) {
                        Divider()
                        LineRow(l, AmountTone.INCOME) { SmallAction(t(UiKey.LINKED_UNLINK)) { unlink(l.id) } }
                    }
                }
            }
            PickSheet(picking, v, onDismiss = { picking = false }) { ids ->
                picking = false
                scope.launch {
                    var refusal: LinkRefusal? = null
                    val err = failureOf { refusal = links.link(e.id, ids) }
                    toaster.show(err ?: refusal?.let { t(linkRefusalKey(it)) } ?: t(UiKey.LINKED_DONE))
                    onChanged()
                }
            }
        }
    }
}

@Composable
private fun LineRow(tx: Transaction, tone: AmountTone, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(linkedLine(tx), Modifier.weight(1f), style = Type.of(13, FontWeight.Bold))
        AmountText(tx.amountMinor, tx.currency, tone = tone, size = 14)
        action()
    }
}

/** لوحة الاختيار: الفلوس اللي دخلت (الأقرب لتاريخ المصروف الأول) — تحديد أكتر من واحدة، والحد المتبقي ظاهر فوق. */
@Composable
private fun PickSheet(visible: Boolean, v: LinkedInflowsView.Expense, onDismiss: () -> Unit, onLink: (List<Id>) -> Unit) {
    var chosen by remember(v.expense.id, visible) { mutableStateOf<Set<Id>>(emptySet()) }
    Sheet(visible, onDismiss, title = t(UiKey.LINKED_SHEET_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        BasicText(t(UiKey.LINKED_SHEET_TITLE), style = Type.of(17, FontWeight.Bold))
        BasicText(t(UiKey.LINKED_SHEET_HINT), style = Type.caption().copy(color = Ink.muted))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(t(UiKey.LINKED_SHEET_ROOM), Modifier.weight(1f), style = Type.of(13))
            AmountText(v.roomMinor, v.expense.currency, size = 14)
        }
        if (v.candidates.isEmpty()) BasicText(t(UiKey.LINKED_SHEET_EMPTY), style = Type.of(13).copy(color = Ink.muted))
        Candidates(v.candidates, chosen) { id -> chosen = if (id in chosen) chosen - id else chosen + id }
        PrimaryButton(t(UiKey.LINKED_SHEET_CONFIRM), onClick = { onLink(chosen.toList()) }, modifier = Modifier.fillMaxWidth(), enabled = chosen.isNotEmpty())
    }
}

@Composable
private fun ColumnScope.Candidates(rows: List<Transaction>, chosen: Set<Id>, toggle: (Id) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in rows) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SelectChip(linkedLine(c), c.id in chosen, { toggle(c.id) }, Modifier.weight(1f), height = 44.dp)
            AmountText(c.amountMinor, c.currency, tone = AmountTone.INCOME, size = 14)
        }
    }
}
