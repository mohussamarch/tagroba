package app.masroufy.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import app.masroufy.ui.theme.motion
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.getValue

/**
 * قطع مشتركة بين شاشات المنطقة (بقيم النموذج نفسها — كلها مشتقة من رموز `Ink`): الشريحة 44 · المفتاح 44×26 · دايرة الحرف ·
 * شبكة الشهور · خانة المبلغ بعملتها · سطر الخطأ · زرار «+ … (اختياري)».
 */
internal object PeopleInk {
    /** خلفية الشريحة غير المختارة `rgba(25,61,51,0.06)`. */
    val chip = Ink.text.copy(alpha = 0.06f)
    /** خلفية الأزرار الهادية `rgba(8,99,79,0.08)`. */
    val tonal = Ink.primary.copy(alpha = 0.08f)
    val tonalSoft = Ink.primary.copy(alpha = 0.06f)
    val expenseSoft = Ink.expense.copy(alpha = 0.10f)
    val transferSoft = Ink.transfer.copy(alpha = 0.10f)
    val mutedSoft = Ink.muted.copy(alpha = 0.10f)
    val track = Ink.text.copy(alpha = 0.16f)
    val rowBg = Ink.text.copy(alpha = 0.04f)
}

/** الشريحة 44 (زاوية 14): غير المختارة رمادي خفيف · المختارة `#DCEBD6` بحد أخضر 1.5 ([filled] = أخضر متدرج بنص أبيض). */
@Composable
internal fun Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    height: Dp = 44.dp,
    textSize: Int = 14,
) {
    val shape = RoundedCornerShape(14.dp)
    val press = rememberPress()
    val surface = when {
        selected && filled -> Modifier.background(Glass.primary)
        selected -> Modifier.background(Ink.selected).insetRing(shape, 1.5.dp, Ink.primary)
        else -> Modifier.background(PeopleInk.chip)
    }
    val ink = when {
        selected && filled -> Ink.onPrimary
        selected -> Ink.primary
        else -> Ink.text
    }
    Box(
        modifier.heightIn(min = height).alpha(if (enabled) 1f else 0.45f).pressScale(press, enabled).clip(shape).then(surface)
            .tap(press, enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Type.of(textSize, if (selected) FontWeight.Bold else FontWeight.Normal).copy(color = ink, textAlign = TextAlign.Center), maxLines = 2)
    }
}

/** صف شرايح بيلف لسطر جديد. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoiceFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** المفتاح 44×26: شغّال = أخضر متدرج والدايرة على الشمال (في العربي)، مقفول = رمادي. */
@Composable
internal fun ToggleSwitch(checked: Boolean, label: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberPress()
    val knob by animateDpAsState(if (checked) 18.dp else 0.dp, motion(Springs.SNAPPY), label = "knob")
    Box(
        modifier.size(48.dp).tap(press, label = label, role = Role.Switch, onClick = onToggle)
            .semantics {
                contentDescription = label
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                stateDescription = t(if (checked) TextKey.PPL_SWITCH_ON else TextKey.PPL_SWITCH_OFF)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(13.dp)).then(if (checked) Modifier.background(Glass.primary) else Modifier.background(PeopleInk.track))) {
            Box(Modifier.padding(2.dp).offset(x = knob).size(22.dp).clip(CircleShape).background(Color.White))
        }
    }
}

/** دايرة أول حرف من الاسم. [onHero] ⇒ أبيض دافي على البترولي. [ring] = حلقة 3 (المُعال). */
@Composable
internal fun InitialCircle(name: String, size: Dp, modifier: Modifier = Modifier, onHero: Boolean = false, ring: Color? = null) {
    val bg = if (onHero) Ink.surface else Ink.selected
    Box(
        modifier.size(size).then(if (ring != null) Modifier.border(3.dp, ring, CircleShape) else Modifier).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(initialOf(name), style = Type.of((size.value * 0.36f).toInt().coerceAtLeast(14), FontWeight.Bold).copy(color = Ink.primary))
    }
}

/** عنوان اللوحة (17 عريض). */
@Composable
internal fun SheetHeading(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = Type.of(17, FontWeight.Bold))
}

/** سطر الخطأ تحت الخانات (12 عريض أحمر) — فاضي ⇒ مفيش حاجة. */
@Composable
internal fun ErrorLine(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    BasicText(text, modifier.semantics { contentDescription = text }, style = Type.of(12, FontWeight.Bold, 1.7).copy(color = Ink.expense))
}

/** سطر شرح رمادي 12. */
@Composable
internal fun Note(text: String, modifier: Modifier = Modifier, color: Color = Ink.muted) {
    BasicText(text, modifier, style = Type.caption().copy(color = color))
}

/** «+ مناسبة له  ·  اختياري» — زرار يفتح ويقفل جزء اختياري. */
@Composable
internal fun ExpandRow(label: String, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).pressScale(press).clip(shape).background(PeopleInk.tonalSoft)
            .tap(press, role = Role.Button, onClick = onToggle).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText((if (open) "− " else "+ ") + label, style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary))
        BasicText(t(TextKey.PPL_OPTIONAL), style = Type.caption().copy(color = Ink.muted))
    }
}

/** شبكة الشهور الميلادية (٤ أعمدة). */
@Composable
internal fun MonthGrid(selected: Int?, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until 3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 1..4) {
                    val m = row * 4 + col
                    Choice(app.masroufy.core.monthName(m), selected == m, { onPick(m) }, Modifier.weight(1f), textSize = 13)
                }
            }
        }
    }
}

/** خانة رقم أو مبلغ من الشمال لليمين (الأرقام العربي مقبولة — `parseMoney` بيقراها) و[currency] جنبها. */
@Composable
internal fun NumberInput(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    currency: Currency? = null,
    decimal: Boolean = true,
    error: String? = null,
) {
    app.masroufy.ui.components.TextInput(
        value, onChange, modifier, label = label, placeholder = placeholder, error = error, ltr = true,
        keyboard = if (decimal) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Number,
        trailing = currency?.let { c -> { BasicText(currencySymbol(c), Modifier.padding(end = 14.dp), style = Type.body().copy(color = Ink.muted)) } },
    )
}

/** زرار مربع 44 هادي (رمز بس) — الحذف والخطوات. */
@Composable
internal fun SquareIcon(icon: Lucide, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tint: Color = Ink.muted, size: Dp = 44.dp) {
    val press = rememberPress()
    Box(
        modifier.size(size).alpha(if (enabled) 1f else 0.45f).pressScale(press, enabled).clip(RoundedCornerShape(14.dp))
            .tap(press, enabled, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 18.dp, tint = tint) }
}

/** شارة صغيرة ملوّنة (11 عريض). */
@Composable
internal fun Pill(text: String, ink: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        BasicText(text, style = Type.of(11, FontWeight.Bold).copy(color = ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** صف بفاصل رفيع تحته (لو مش الأخير). */
@Composable
internal fun Rowed(last: Boolean, modifier: Modifier = Modifier, minHeight: Dp = 56.dp, content: @Composable RowScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = minHeight).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
    }
}

