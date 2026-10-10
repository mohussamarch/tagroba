package app.masroufy.ui.screens.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type

/**
 * قطع مشتركة بين شاشات «الاستيراد» بنفس قيم النموذج (`BankSms` · `SmsWaiting` · `ImportReview` · `ImportBatches` …) — الألوان كلها من `Ink`
 * (الشفافيات زي النموذج: الرمادي `Ink.text` ٦٪ · الأخضر `Ink.primary` ٨٪ · الأحمر `Ink.expense` ٨–١٠٪).
 */

/** نبرة الشارة الصغيرة (11 عريض بزاوية 10) — جديد · مكرر · شبيه · تعارض · غير صالح · بمصدرين. */
enum class TagTone(internal val ink: Color, internal val bg: Color) {
    NEW(Ink.primary, Ink.selected),
    MUTED(Ink.muted, Ink.muted.copy(alpha = 0.12f)),
    AMBER(Ink.focus, Ink.alertBg),
    DANGER(Ink.expense, Ink.expense.copy(alpha = 0.10f)),
    BLUE(Ink.transfer, Ink.transfer.copy(alpha = 0.10f)),
}

@Composable
fun Tag(text: String, tone: TagTone, modifier: Modifier = Modifier, bold: Boolean = true) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(tone.bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        BasicText(text, style = Type.of(11, if (bold) FontWeight.Bold else FontWeight.Normal).copy(color = tone.ink), maxLines = 1)
    }
}

/** لوحة ملونة من غير ظل (تنبيه كهرماني · تم نعناعي · رمادي هادي) — زاوية 22 وحشوة 14/16. */
enum class PanelTone(internal val bg: Color, internal val title: Color) {
    AMBER(Ink.alertBg, Ink.focus),
    MINT(Ink.selected, Ink.primary),
    QUIET(Ink.text.copy(alpha = 0.04f), Ink.text),
}

@Composable
fun TintedPanel(tone: PanelTone, modifier: Modifier = Modifier, radius: Dp = Radius.card, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(radius)).background(tone.bg).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun PanelTitle(text: String, tone: PanelTone) {
    BasicText(text, style = Type.of(14, FontWeight.Bold).copy(color = tone.title))
}

/** زرار هادي (رمادي ٦٪ — «إلغاء» · «هي نفسها») أو خطر (أحمر ٨٪ — «أوقف القراءة» · «إرجاع الدفعة»). */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    onTint: Boolean = false,
) {
    val press = rememberPress()
    val bg = when {
        danger -> Ink.expense.copy(alpha = 0.08f)
        onTint -> Color.White.copy(alpha = 0.8f)
        else -> Ink.text.copy(alpha = 0.06f)
    }
    val ink = when {
        danger -> Ink.expense
        onTint -> Ink.primary
        else -> Ink.text
    }
    Box(
        modifier.height(height).alpha(if (enabled) 1f else 0.45f).pressScale(press, enabled).clip(RoundedCornerShape(16.dp)).background(bg)
            .tap(press, enabled, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = Type.of(14, FontWeight.Bold).copy(color = ink), maxLines = 2) }
}

/** عنوان قسم صغير (15 عريض) وعدّه على الطرف التاني. */
@Composable
fun CountTitle(title: String, count: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, style = Type.of(15, FontWeight.Bold))
        if (count != null) BasicText(count, style = Type.caption().copy(color = Ink.muted))
    }
}

/** سطر رابط لشاشة تانية (أخضر على خلفية خضرا ٦٪ + سهم) — «الكشوف المستوردة سابقًا» · «إشعارات رسائل البنك». */
@Composable
fun LinkRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberPress()
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).pressScale(press).clip(RoundedCornerShape(16.dp)).background(Ink.primary.copy(alpha = 0.06f))
            .tap(press, onClick = onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, Modifier.weight(1f), style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary))
        LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.primary, modifier = Modifier.mirrorInLtr())
    }
}

/** خيار في لوحة (محفظة · بنك): الاسم وسطر صغير ونقطة اختيار 20 — المختار أخضر فاتح بحد أخضر. */
@Composable
fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    val surface = if (selected) Modifier.background(Ink.selected).insetRing(shape, 1.5.dp, Ink.primary) else Modifier.background(Ink.text.copy(alpha = 0.05f))
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).pressScale(press).clip(shape).then(surface)
            .semantics { this.selected = selected }.tap(press, role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Type.of(15, FontWeight.Bold))
            if (subtitle != null) BasicText(subtitle, style = Type.caption().copy(color = Ink.muted))
        }
        val dot = if (selected) Modifier.border(6.dp, Ink.primary, CircleShape).background(Color.White, CircleShape)
        else Modifier.border(1.5.dp, Ink.faded, CircleShape)
        Box(Modifier.size(20.dp).clip(CircleShape).then(dot))
    }
}

/** شارة العدد (22 دايرية): كهرماني لو فيه حاجة مستنية، أخضر لو صفر. */
@Composable
fun CountBadge(text: String, waiting: Boolean) {
    Box(
        Modifier.height(22.dp).defaultMinSize(minWidth = 22.dp).clip(RoundedCornerShape(11.dp)).background(if (waiting) Ink.focus else Ink.selected)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = Type.of(12, FontWeight.Bold).copy(color = if (waiting) Color.White else Ink.primary)) }
}

/** نقطة حالة (10) قبل عنوان. */
@Composable
fun StatusDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}
