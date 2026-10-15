package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.ListRow
import app.masroufy.ui.components.NotAvailableBadge
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.BulkAction
import app.masroufy.usecase.SelectionSummary

/**
 * قطع وضع «تحديد» في «العمليات» (قايمة المالك §6.5 بند 15): الصف وهو قابل للتحديد · الشريط اللي فوق (العدد والمجموع لكل عملة
 * والأفعال) · شريط «تراجع». **مفيش حساب هنا** — المجموع من `BulkTransactions.summary` بالهللة زي ما هو، و`null` ⇒ «غير متاح».
 */

/** صف العملية في وضع التحديد: دايرة التحديد على اليمين + نفس الأيقونة والاسم والمبلغ. الضغط (العادي أو المطوّل) بيحدد أو بيشيل. */
@Composable
internal fun SelectableOperationRow(row: OpRow, selected: Boolean, onToggle: () -> Unit) {
    val label = t(if (selected) UiKey.OPS_SELECT_ROW_ON else UiKey.OPS_SELECT_ROW_OFF, row.title)
    Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label }) {
        ListRow(
            title = row.title,
            subtitle = row.subtitle,
            leading = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CheckDot(selected)
                    RowTile(row)
                }
            },
            trailing = { AmountText(row.amountMinor, row.currency, tone = row.tone, size = 16, showCurrency = false) },
            onClick = onToggle,
            onLongClick = onToggle,
            chevron = false,
        )
    }
}

@Composable
private fun CheckDot(selected: Boolean) {
    val base = Modifier.size(22.dp)
    Box(
        if (selected) base.background(Ink.primary, CircleShape) else base.border(1.5.dp, Ink.muted, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (selected) LucideIcon(Lucide.CHECK, size = 14.dp, tint = Color.White) }
}

/** الشريط اللي فوق وهو شغال: «المحدّد: 3» · تحديد الكل ⇄ إلغاء تحديد الكل · إلغاء — ثم المجموع لكل عملة — ثم الأفعال. */
@Composable
internal fun SelectionBar(
    selection: OpsSelection,
    summary: SelectionSummary?,
    visibleIds: List<String>,
    onToggleAll: () -> Unit,
    onCancel: () -> Unit,
    onAction: (BulkAction) -> Unit,
) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(UiKey.OPS_SELECT_COUNT, "${selection.count}"), style = Type.bodyBold(), modifier = Modifier.weight(1f))
            val all = selection.allSelected(visibleIds)
            TonalButton(t(if (all) UiKey.OPS_SELECT_NONE else UiKey.OPS_SELECT_ALL), onToggleAll, height = 36.dp)
            TonalButton(t(UiKey.OPS_SELECT_CANCEL), onCancel, height = 36.dp, muted = true)
        }
        if (selection.count > 0) {
            if (summary == null) {
                NotAvailableBadge()
            } else {
                for (total in summary.totals) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (total.outMinor > 0) {
                            BasicText(t(UiKey.OPS_SELECT_OUT), style = Type.caption().copy(color = Ink.muted))
                            AmountText(total.outMinor, total.currency, tone = AmountTone.EXPENSE, size = 14)
                        }
                        if (total.outMinor > 0 && total.inMinor > 0) Spacer(Modifier.width(8.dp))
                        if (total.inMinor > 0) {
                            BasicText(t(UiKey.OPS_SELECT_IN), style = Type.caption().copy(color = Ink.muted))
                            AmountText(total.inMinor, total.currency, tone = AmountTone.INCOME, size = 14)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((action, key) in BULK_ACTION_LABELS) SelectChip(t(key), false, { onAction(action) }, height = 36.dp)
        }
    }
}

private val BULK_ACTION_LABELS = listOf(
    BulkAction.PROJECT to UiKey.OPS_BULK_PROJECT,
    BulkAction.CATEGORY to UiKey.OPS_BULK_CATEGORY,
    BulkAction.KIND to UiKey.OPS_BULK_KIND,
    BulkAction.TAG to UiKey.OPS_BULK_TAG,
    BulkAction.DELETE to UiKey.OPS_BULK_DELETE,
)

/** بعد أي فعل جماعي: الجملة («تم تعديل 3») + «تراجع» واحد للدفعة كلها. بيختفي لوحده بعد شوية (الشاشة بتحدد). */
@Composable
internal fun UndoStrip(text: String, onUndo: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(text, style = Type.of(14), modifier = Modifier.weight(1f))
            TonalButton(t(UiKey.OPS_BULK_UNDO), onUndo, height = 36.dp)
        }
    }
}
