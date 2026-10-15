package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.BankLogo
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.ListRow
import app.masroufy.ui.components.NotificationDot
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Anchor
import app.masroufy.ui.overlay.QuickMenuRequest
import app.masroufy.ui.overlay.MenuItem
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * صف العملية في القايمة: أيقونة التصنيف (والنقطة الحمرا «غير مسجّلة» فوق شمالها) · الاسم والسطر التاني · المبلغ · «⋮» على الشمال.
 * الضغط ⇒ التفاصيل · الضغط المطوّل **و**«⋮» ⇒ نفس قايمة الإجراءات (اختيار المالك «الاتنين» §73).
 */
@Composable
internal fun OperationListRow(row: OpRow, onOpen: () -> Unit, onMenu: (Anchor) -> Unit) {
    val anchor = remember { Anchor() }
    Row(Modifier.fillMaxWidth().onGloballyPositioned { anchor.update(it) }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            ListRow(
                title = row.title,
                subtitle = row.subtitle,
                leading = { RowTile(row) },
                trailing = { AmountText(row.amountMinor, row.currency, tone = row.tone, size = 16, showCurrency = false) },
                onClick = onOpen,
                onLongClick = { onMenu(anchor) },
                chevron = false,
            )
        }
        val press = rememberPress()
        val label = t(UiKey.OPERATIONS_ROW_ACTIONS, row.title)
        Box(
            Modifier.size(36.dp, 48.dp).pressScale(press).tap(press, label = label) { onMenu(anchor) }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { LucideIcon(OperationsIcons.DOTS_VERTICAL, size = 20.dp, tint = Ink.muted) }
    }
}

/** أيقونة الصف 36 بزاوية 12 + النقطة الحمرا «غير مسجّلة» (8) فوق شمالها + لوجو بنك المحفظة صغير على الركن التاني تحت (لو له لوجو — §81). */
@Composable
internal fun RowTile(row: OpRow, size: Int = 36) {
    Box {
        IconTile(row.color, size = size.dp) { LucideIcon(row.icon, size = (size / 2).dp, tint = row.color) }
        if (row.unrecorded) {
            val label = t(UiKey.OPERATIONS_UNRECORDED)
            NotificationDot(Modifier.align(AbsoluteAlignment.TopLeft).semantics { contentDescription = label }, size = 8.dp, color = Ink.expense)
        }
        row.bank?.let { BankLogo(it, it.nameAr, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp), size = (size * 0.45f).dp, fallback = false) }
    }
}

/**
 * قايمة إجراءات العملية (`OperationMenu` — جوه `Operations`): القايمة السريعة المشتركة (`QuickMenu` — ستارة + الصف مرفوع + زجاج سائل،
 * ونفسها في كل الشاشات — قايمة المالك §6.5-13). البنود من النموذج: «عرض التفاصيل» ·
 * «تخصيص مبلغ لشخص» (للمصروف بس — بيفتح التفاصيل ولوحة الشخص) · «كل الإجراءات ←».
 */
internal fun operationMenuRequest(
    row: OpRow,
    anchor: Anchor,
    onDetail: (OpRow) -> Unit,
    onPerson: (OpRow) -> Unit,
    /** «تحديد» (قايمة المالك §6.5 بند 15): بيدخل وضع التحديد والصف ده متحدد. `null` ⇒ البند ما بيظهرش. */
    onSelect: ((OpRow) -> Unit)? = null,
): QuickMenuRequest {
    val items = buildList {
        add(MenuItem(t(UiKey.OPERATION_MENU_DETAIL)) { onDetail(row) })
        if (row.tone == app.masroufy.ui.components.AmountTone.EXPENSE) add(MenuItem(t(UiKey.OPERATION_MENU_PERSON)) { onPerson(row) })
        if (onSelect != null) add(MenuItem(t(UiKey.OPS_SELECT)) { onSelect(row) })
        add(MenuItem(t(UiKey.OPERATION_MENU_ALL), highlight = true) { onDetail(row) })
    }
    return QuickMenuRequest(anchor, t(UiKey.OPERATION_MENU_TITLE), items) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RowTile(row)
            Column(Modifier.weight(1f)) {
                BasicText(row.title, style = Type.bodyBold(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText(row.subtitle, style = Type.caption().copy(color = Ink.muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            AmountText(row.amountMinor, row.currency, tone = row.tone, size = 16, showCurrency = false)
        }
    }
}
