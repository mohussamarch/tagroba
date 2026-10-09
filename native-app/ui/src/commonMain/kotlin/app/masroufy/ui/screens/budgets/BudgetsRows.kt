package app.masroufy.ui.screens.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Halalas
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tabular
import app.masroufy.ui.components.tap
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.amount
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** صف تصنيف في «الميزانيات»: النقطة باللون · الاسم · «المصروف / السقف» · الشريط (لو فيه سقف) · السطر تحته ⇒ «ميزانية تصنيف». */
@Composable
fun CategoryLineRow(line: LineUi, ui: BudgetsUi, onOpen: () -> Unit) {
    val press = rememberPress()
    val color = parseHexColor(line.colorHex)
    Column(
        Modifier.fillMaxWidth().pressScale(press).tap(press, label = line.name, onClick = onOpen).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(color)
            BasicText(line.name, Modifier.weight(1f), style = Type.bodyBold(), maxLines = 1)
            val numbers = when {
                line.spentMinor == null -> t(TextKey.NOT_AVAILABLE)
                // «المصروف / السقف» — نفس شكل «المدّخر / الهدف» (`{0} / {1}`)
                line.limitMinor != null -> t(TextKey.BUDGETS_SPENT_OF, amount(line.spentMinor, ui.currency), amount(line.limitMinor, ui.currency))
                else -> amount(line.spentMinor, ui.currency)
            }
            val ink = when {
                line.spentMinor == null -> Ink.muted
                line.over -> Ink.expense
                else -> Ink.text
            }
            BasicText(numbers, style = Type.of(14, FontWeight.Bold).copy(color = ink).tabular(), maxLines = 1)
        }
        if (line.percent != null) ProgressBar(line.percent, if (line.over) Ink.expense else color, height = 6.dp)
        val noteInk = when {
            line.over -> Ink.expense
            line.noLimit -> Ink.primary
            else -> Ink.muted
        }
        BasicText(line.note, style = Type.of(12, if (line.noLimit) FontWeight.Bold else FontWeight.Normal).copy(color = noteInk))
    }
}

/** «مواعيد قادمة»: الاسم وميعاده · المبلغ · «احسبه من فلوسي» ⇄ «محجوز ✓ · إلغاء» — وتحتهم «المتبقي تقريبًا بعد المحجوز». */
@Composable
fun UpcomingSection(ui: BudgetsUi, onToggle: (UpcomingUi) -> Unit) {
    BasicText(t(TextKey.BUDGETS_UP_TITLE), style = Type.section())
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        if (ui.upcoming.isEmpty()) BasicText(t(TextKey.BUDGETS_UP_EMPTY), Modifier.padding(vertical = 12.dp), style = Type.of(13).copy(color = Ink.muted))
        ui.upcoming.forEachIndexed { i, u ->
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(u.item.title, style = Type.bodyBold(), maxLines = 1)
                    BasicText(u.whenText, style = Type.caption().copy(color = Ink.muted))
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val amountMinor = u.item.amountMinor
                    if (amountMinor != null) AmountText(amountMinor, u.item.currency)
                    else BasicText(t(TextKey.UPCOMING_NO_AMOUNT), style = Type.of(13).copy(color = Ink.muted))
                    SmallToggleButton(t(if (u.reserved) TextKey.BUDGETS_RESERVED else TextKey.BUDGETS_RESERVE), u.reserved, { onToggle(u) })
                }
            }
            if (i < ui.upcoming.lastIndex) Divider()
        }
    }
    val left = ui.leftover ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(left.text, Modifier.weight(1f, fill = false), style = Type.of(13, FontWeight.Bold).copy(color = if (left.negative) Ink.expense else Ink.primary))
        if (left.approximate) app.masroufy.ui.components.ApproxBadge()
    }
}

/** «كم تحجز لـ…؟» — ميعاد مالوش مبلغ معروف (`ManageReservations.countUpcomingItem(…, amountMinor)` — المستخدم لازم يكتبه). */
@Composable
fun ReserveAmountSheet(target: UpcomingUi?, onDismiss: () -> Unit, onSave: suspend (UpcomingUi, Halalas) -> String?) {
    var text by remember(target) { mutableStateOf("") }
    var error by remember(target) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val title = target?.let { t(TextKey.BUDGETS_RESERVE_TITLE, it.item.title) } ?: ""
    Sheet(target != null, onDismiss, title) {
        val item = target ?: return@Sheet
        BasicText(title, style = Type.section())
        TextInput(
            value = text,
            onChange = { text = it; error = null },
            label = t(TextKey.BUDGETS_RESERVE_AMOUNT),
            placeholder = "0",
            error = error,
            ltr = true,
            keyboard = KeyboardType.Decimal,
            trailing = { BasicText(currencySymbol(item.item.currency), Modifier.padding(end = 16.dp), style = Type.body().copy(color = Ink.muted)) },
        )
        PrimaryButton(t(TextKey.BUDGETS_RESERVE_SAVE), {
            val minor = tryParseMoney(text, item.item.currency)?.takeIf { it > 0 }
            if (minor == null) error = t(TextKey.RESERVATION_AMOUNT) else scope.launch { error = onSave(item, minor) }
        }, Modifier.fillMaxWidth(), height = 52.dp)
    }
}
