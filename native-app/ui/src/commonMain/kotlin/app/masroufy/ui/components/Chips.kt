package app.masroufy.ui.components

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type

/**
 * الشرائح والشارات والنقطة وشريط الخانات — بنفس قيم النموذج (`BottomBar` · `SignInEmail` · `SpaceSwitcher`).
 * المختار: خلفية `#DCEBD6` بحد أخضر 1.5 جوه (DESIGN-SYSTEM «مختار»). غير المختار: أبيض بحد رفيع `rgba(204,216,204,0.9)`.
 */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    height: Dp = 40.dp,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(Radius.control)
    val press = rememberPress()
    val surface = if (selected) Modifier.background(Ink.selected).insetRing(shape, 1.5.dp, Ink.primary)
    else Modifier.background(Color.White).insetRing(shape, 1.dp, Ink.fieldEdge)
    Row(
        modifier
            .height(height)
            .pressScale(press, enabled)
            .clip(shape)
            .then(surface)
            .tap(press, enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        BasicText(label, style = Type.of(13, if (selected) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
    }
}

/** نوع الشارة: «تقريبي» كهرماني · «غير متاح» رمادي · معلومة خضرا («الحالية»). */
enum class BadgeKind(internal val bg: Color, internal val ink: Color) {
    APPROX(Ink.alertBg, Ink.focus),
    NOT_AVAILABLE(Color(0x14193D33), Ink.muted),
    INFO(Ink.selected, Ink.primary),
}

@Composable
fun Badge(text: String, kind: BadgeKind, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(kind.bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        BasicText(text, style = Type.of(11, FontWeight.Bold).copy(color = kind.ink), maxLines = 1)
    }
}

/** شارة «تقريبي» (الرقم مبني على نوع مش مؤكد — §18). */
@Composable
fun ApproxBadge(modifier: Modifier = Modifier) = Badge(t(UiKey.BADGE_APPROX), BadgeKind.APPROX, modifier)

/** شارة «غير متاح» (CLAUDE.md #10). */
@Composable
fun NotAvailableBadge(modifier: Modifier = Modifier) = Badge(t(TextKey.NOT_AVAILABLE), BadgeKind.NOT_AVAILABLE, modifier)

/** نقطة الإشعار (KOTLIN-MAP §٣): ٦×٦ `#D93A47` **من غير إطار أبيض**. */
@Composable
fun NotificationDot(modifier: Modifier = Modifier, size: Dp = 6.dp, color: Color = Ink.dot) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/**
 * شريط خانات (صرف · دخل · تحويل / تسجيل الدخول · حساب جديد): [style] `PRIMARY` = المختار حبّة خضرا بظل (زاوية 14) على كارت أبيض،
 * `QUIET` = المختار أبيض على خلفية رمادي خفيفة (شاشة الدخول).
 */
enum class SegmentStyle { PRIMARY, QUIET }

@Composable
fun <T> SegmentedTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    style: SegmentStyle = SegmentStyle.PRIMARY,
    enabled: Boolean = true,
    height: Dp = 40.dp,
) {
    val outer = RoundedCornerShape(Radius.control)
    val track = if (style == SegmentStyle.PRIMARY) Modifier.layeredShadow(outer, Shadows.chip).clip(outer).background(Glass.card).innerSheen(outer, Shadows.chip)
    else Modifier.clip(outer).background(Color(0x0F193D33))
    Row(modifier.then(track).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((value, label) in options) Segment(label, value == selected, style, enabled, height) { onSelect(value) }
    }
}

@Composable
private fun RowScope.Segment(label: String, on: Boolean, style: SegmentStyle, enabled: Boolean, height: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val press = rememberPress()
    val surface = when {
        !on -> Modifier
        style == SegmentStyle.PRIMARY -> Modifier.layeredShadow(shape, Shadows.segment).clip(shape).background(Glass.primary).innerSheen(shape, Shadows.segment)
        else -> Modifier.layeredShadow(shape, Shadows.chip).clip(shape).background(Color.White)
    }
    val ink = when {
        !on -> Ink.muted
        style == SegmentStyle.PRIMARY -> Ink.onPrimary
        else -> Ink.primary
    }
    Box(
        Modifier.weight(1f).height(height).pressScale(press, enabled).then(surface).clip(shape)
            .tap(press, enabled, role = Role.Tab, onClick = onClick).semantics { selected = on },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Type.of(14, if (on) FontWeight.Bold else FontWeight.Normal).copy(color = ink, textAlign = TextAlign.Center), maxLines = 1)
    }
}
