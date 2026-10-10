package app.masroufy.ui.screens.investment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * قطع مشتركة بين شاشات الاستثمار (بنفس قيم النموذج): شريط التنبيه الكهرماني · الشارة الصغيرة · سطر «الاسم ← المبلغ» · خانة الإحصاء ·
 * أيقونة النوع · الصندوق الهادي. ألوان من `Ink` بس (الشفافيات مشتقة منها).
 */

/** خلفية هادية `rgba(25,61,51,0.05)` (الخانات · الصفوف الثانوية). */
internal val QuietFill = Ink.text.copy(alpha = 0.05f)

/** شريط تنبيه كهرماني (`role=alert`): عنوان + سطر + زرار اختياري («أعد المحاولة»). */
@Composable
internal fun AlertBanner(title: String, body: String?, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.alertBg).semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Type.bodyBold().copy(color = Ink.focus))
            if (body != null) BasicText(body, style = Type.caption().copy(color = Ink.focus))
        }
        if (action != null && onAction != null) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White)) { TonalButton(action, onAction) }
        }
    }
}

/** شارة صغيرة (11 عريض، زاوية 10). */
@Composable
internal fun ToneChip(text: String, ink: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        BasicText(text, style = Type.of(11, FontWeight.Bold).copy(color = ink), maxLines = 1)
    }
}

/** سطر «الاسم … المبلغ» (نتيجة التوقّع · صفوف البطل). [amount] null ⇒ «غير متاح». */
@Composable
internal fun AmountLine(
    label: String,
    amount: Halalas?,
    currency: Currency,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    tone: AmountTone = AmountTone.PLAIN,
    ink: Color = Ink.text,
    labelInk: Color = ink,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = Type.of(if (bold) 15 else 13, if (bold) FontWeight.Bold else FontWeight.Normal).copy(color = labelInk))
        AmountText(amount, currency, tone = tone, size = if (bold) 15 else 13, weight = if (bold) FontWeight.Bold else FontWeight.Normal,
            showCurrency = false, color = if (tone == AmountTone.PLAIN) ink else null)
    }
}

/** خانة إحصاء (2×2 في تفاصيل الأصل): العنوان 12 · القيمة 17 عريض · سطر 11. */
@Composable
internal fun StatTile(label: String, sub: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    FloatingCard(modifier, shape = RoundedCornerShape(18.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(label, style = Type.caption().copy(color = Ink.muted))
            value()
            BasicText(sub, style = Type.of(11).copy(color = Ink.muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** أيقونة النوع (36 زاوية 12) بلونه. */
@Composable
internal fun KindTile(kind: String, size: Int = 36) {
    val ink = when (kindTone(kind)) {
        KindTone.AMBER -> Ink.focus
        KindTone.BLUE -> Ink.transfer
        KindTone.GREEN -> Ink.primary
    }
    IconTile(ink, size = size.dp) { LucideIcon(kindIcon(kind), size = (size / 2).dp, tint = ink) }
}

/** صف قايمة بكلامه (الأدوات · الأصول): ارتفاع 56، وخط فاصل بعده لو مش الأخير. */
@Composable
internal fun ToolRow(
    title: String,
    sub: String?,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val press = rememberPress()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(press).tap(press, label = title, onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Type.of(15, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) BasicText(sub, style = Type.caption().copy(color = Ink.muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
    }
}

/** صندوق هادي (`rgba(25,61,51,0.04)`) — «خارج الحساب» والدفعات. */
@Composable
internal fun QuietBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.text.copy(alpha = 0.04f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** فاصل بين صفوف جوه كارت. */
@Composable
internal fun RowGap() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
}
