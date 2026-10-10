package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.theme.CategoryInk
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** لون ورمز نوع الميعاد (النموذج: الدين أزرق · الاشتراك وردي · القسط رمادي أزرق · الجمعية أخضر). */
internal fun kindIcon(kind: DueKind): Pair<Lucide, Color> = when (kind) {
    DueKind.DEBT -> Lucide.HAND_COINS to Ink.transfer
    DueKind.SUBSCRIPTION -> DuesIcons.REFRESH_CCW to CategoryInk.gifts
    DueKind.INSTALLMENT -> DuesIcons.CALENDAR_DAYS to CategoryInk.transport
    DueKind.ROSCA -> Lucide.COINS to CategoryInk.savings
}

/** صف ميعاد في «حسب الموعد»: رمز النوع 40 · الاسم والتاريخ والحالة · المبلغ بلون الاتجاه (من غير علامة) و«ستدفع/ستستلم». */
@Composable
internal fun DueRow(row: AgendaRowUi, onClick: () -> Unit) {
    val press = rememberPress()
    val (icon, color) = kindIcon(row.kind)
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).pressScale(press).tap(press, label = row.title, onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(color, size = 40.dp) { LucideIcon(icon, size = 20.dp, tint = color) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(row.title, style = Type.bodyBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(row.sub, style = Type.caption().copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusChip(row.chipText, row.chip)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AmountText(row.amountMinor, row.currency, showCurrency = false, color = if (row.incoming) Ink.income else Ink.expense)
            BasicText(row.dirText, style = Type.of(11).copy(color = Ink.muted))
        }
    }
}

/** دايرة الشخص 40 بأول حرف — أخضر لـ«لك» وأحمر خفيف لـ«عليك». */
@Composable
internal fun PersonInitial(initial: String, forYou: Boolean, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (forYou) Ink.selected else Ink.expense.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) { BasicText(initial, style = Type.of(16, FontWeight.Bold).copy(color = if (forYou) Ink.primary else Ink.expense)) }
}

/** صف دين في «الديون»: دايرة · الاسم والسبب والإشارة · المبلغ والموعد · سهم. */
@Composable
internal fun DebtRow(row: DebtRowUi, onClick: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).pressScale(press).tap(press, onClick = onClick).padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = row.aria },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonInitial(row.initial, row.forYou)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(row.name, style = Type.bodyBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(row.reason, style = Type.caption().copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.chipText != null) StatusChip(row.chipText, row.chip, Modifier.padding(top = 2.dp))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AmountText(row.amountMinor, row.currency, size = 16, showCurrency = false, color = if (row.forYou) Ink.income else Ink.expense)
            BasicText(row.dueText, style = Type.of(11).copy(color = Ink.muted), maxLines = 1)
        }
        LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
    }
}
