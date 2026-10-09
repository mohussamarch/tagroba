package app.masroufy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * صفوف القوايم (DESIGN-SYSTEM «العمليات»): صف 56 فيه أيقونة · الاسم والسطر التاني · المبلغ أو سهم. مجموعات الأيام («اليوم»، «أمس»)
 * = عنوان صغير + كارت طافي فيه الصفوف بفواصل. **صفوف القوايم بتفضل بكلامها** (مش رموز — KOTLIN-MAP §٣).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null && trailing == null,
    padding: PaddingValues = PaddingValues(vertical = 8.dp),
) {
    val press = rememberPress()
    val click = if (onClick != null) Modifier.tap(press, onLongClick = onLongClick, onClick = onClick) else Modifier
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).pressScale(press, onClick != null).then(click).padding(padding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = Type.bodyBold(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) BasicText(subtitle, style = Type.caption().copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
        // سهم «جوه» = ناحية آخر السطر (في العربي الشمال) — `chevron-left` بيتقلب لوحده مع الاتجاه في الإنجليزي
        if (chevron) LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
    }
}

/** صف عملية: أيقونة التصنيف بلونه · الاسم + التصنيف · المبلغ ملوّن بالاتجاه (المبلغ ما بيورثش لون التصنيف). [sheen] = اتسجّلت لوحدها دلوقتي. */
@Composable
fun OperationRow(
    title: String,
    subtitle: String?,
    icon: Lucide,
    categoryColor: Color,
    amountMinor: Halalas?,
    currency: Currency,
    tone: AmountTone,
    modifier: Modifier = Modifier,
    badges: (@Composable RowScope.() -> Unit)? = null,
    sheen: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Box(modifier.mintSheen(sheen)) {
        ListRow(
            title = title,
            subtitle = subtitle,
            leading = { IconTile(categoryColor) { LucideIcon(icon, size = 20.dp, tint = categoryColor) } },
            trailing = {
                badges?.invoke(this)
                AmountText(amountMinor, currency, tone = tone, showCurrency = false)
            },
            onClick = onClick,
            onLongClick = onLongClick,
            chevron = false,
        )
    }
}

/** مجموعة يوم: «اليوم» / «أمس» / التاريخ + الصفوف جوه كارت بفواصل. */
@Composable
fun DayGroup(header: String, modifier: Modifier = Modifier, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(header, style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
            if (trailing != null) BasicText(trailing, style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted).tabular())
        }
        FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), content = content)
    }
}

/** عنوان قسم (18 عريض) مع فعل اختياري على آخر السطر (رمز 44 — «الكلمة والسهم بقوا رمز»). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, style = Type.section())
        action?.invoke()
    }
}
