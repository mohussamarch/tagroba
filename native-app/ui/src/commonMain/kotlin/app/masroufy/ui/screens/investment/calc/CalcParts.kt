package app.masroufy.ui.screens.investment.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.TextKey
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** أيقونات الحاسبات اللي مش في المشتركة (مسارات لوسيد من لوحات النموذج). */
internal object CalcIcons {
    val MINUS = Lucide("MINUS", "M5 12h14")
}

/** كهرماني «غير متاح» / ملاحظة (النموذج: `#956000` و`#6B4600` على `#FBF0DD`). */
internal val warnInk = Color(0xFF956000)
internal val warnDeep = Color(0xFF6B4600)

/** خانة رقم أو مبلغ (النموذج: عنوان 13 · خانة بيضا 48 · الوحدة على الشمال · سطر ملاحظة أو خطأ تحتها). */
@Composable
fun CalcField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    note: String? = null,
    error: String? = null,
    placeholder: String? = "0",
    keyboard: KeyboardType = KeyboardType.Decimal,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextInput(
            value, onChange, label = label, placeholder = placeholder, error = error, ltr = true, keyboard = keyboard, textSize = 18,
            trailing = unit?.let { u -> { BasicText(u, Modifier.padding(end = 14.dp), style = Type.of(13).copy(color = Ink.muted)) } },
        )
        if (error == null && !note.isNullOrEmpty()) BasicText(note, style = Type.caption().copy(color = Ink.muted))
    }
}

/** البطاقة البطلة في الحاسبة: عنوان + الرقم الكبير (أو جملة «غير متاح»/«أكمل البيانات») + سطر تحت. */
@Composable
fun CalcHero(label: String, amountMinor: Halalas?, currency: Currency, naText: String, sub: String?, modifier: Modifier = Modifier, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    HeroCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(label, style = Type.of(14).copy(color = Ink.onHeroMuted))
            if (amountMinor != null) HeroAmount(amountMinor, currency, Modifier.fillMaxWidth(), size = 34)
            else BasicText(naText, style = Type.of(22, FontWeight.Bold).copy(color = Ink.onPrimary))
            extra?.invoke(this)
            if (!sub.isNullOrEmpty()) BasicText(sub, style = Type.caption().copy(color = Ink.onHeroMuted))
        }
    }
}

/** سطر نتيجة في كارت (المكافأة · كم تدّخر): العنوان والرقم، وتحتهم شرح (كهرماني لو «غير متاح»). */
data class OutRow(val label: String, val amountMinor: Halalas?, val valueText: String?, val sub: String, val warn: Boolean)

@Composable
fun OutsCard(rows: List<OutRow>, currency: Currency, modifier: Modifier = Modifier, footer: (@Composable ColumnScope.() -> Unit)? = null) {
    FloatingCard(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        rows.forEachIndexed { i, r ->
            if (i > 0) Divider()
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(r.label, Modifier.weight(1f), style = Type.bodyBold())
                    if (r.amountMinor != null) AmountText(r.amountMinor, currency, size = 16, showCurrency = false)
                    else BasicText(r.valueText ?: t(TextKey.NOT_AVAILABLE), style = Type.of(16, FontWeight.Bold).copy(color = Ink.muted))
                }
                if (r.sub.isNotEmpty()) BasicText(r.sub, style = Type.caption().copy(color = if (r.warn) warnInk else Ink.muted))
            }
        }
        footer?.invoke(this)
    }
}

/** «كيف حُسب المعاش؟» — بيفتح سطور الحساب جوه نفس الكارت. */
@Composable
fun HowToggle(open: Boolean, showLabel: String, hideLabel: String, lines: List<String>, onToggle: () -> Unit) {
    Divider()
    val press = rememberPress()
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScale(press).tap(press, label = if (open) hideLabel else showLabel, onClick = onToggle),
        contentAlignment = Alignment.CenterStart,
    ) { BasicText(if (open) hideLabel else showLabel, style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary)) }
    if (open && lines.isNotEmpty()) {
        Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { BasicText(it, style = Type.of(12, lineHeight = 1.7).copy(color = Ink.soft)) }
        }
    }
}

/** مفتاح (النموذج: مسار 44×26 والدايرة 22). */
@Composable
fun CalcSwitchRow(label: String, sub: String?, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, subWarn: Boolean = false) {
    val press = rememberPress()
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).pressScale(press)
            .tap(press, role = Role.Switch, label = label, onClick = onToggle)
            .semantics { toggleableState = ToggleableState(checked) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = Type.bodyBold())
            if (!sub.isNullOrEmpty()) BasicText(sub, style = Type.caption().copy(color = if (subWarn) warnInk else Ink.muted))
        }
        val track = RoundedCornerShape(13.dp)
        Box(
            Modifier.size(44.dp, 26.dp).clip(track).then(if (checked) Modifier.background(Glass.primary) else Modifier.background(Color(0x29193D33))),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) { Box(Modifier.padding(2.dp).size(22.dp).clip(CircleShape).background(Color.White)) }
    }
}

/** عدّاد −/+ (الورثة وأولاد المتوفى قبله): أزرار 44 دايرية والرقم 18 في النص. */
@Composable
fun Stepper(count: Int, onMinus: () -> Unit, onPlus: () -> Unit, minusLabel: String, plusLabel: String, atMin: Boolean, atMax: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton(CalcIcons.MINUS, minusLabel, !atMin, strong = false, onMinus)
        BasicText(
            app.masroufy.core.sentenceNumber(count),
            Modifier.heightIn(min = 28.dp).padding(horizontal = 4.dp),
            style = Type.of(18, FontWeight.Bold).copy(color = if (count > 0) Ink.text else Ink.faded),
        )
        StepButton(Lucide.PLUS, plusLabel, !atMax, strong = true, onPlus)
    }
}

@Composable
private fun StepButton(icon: Lucide, label: String, enabled: Boolean, strong: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    val bg = when {
        !enabled -> Modifier.background(Color(0x0A193D33))
        strong -> Modifier.background(Glass.primary)
        else -> Modifier.background(Color(0x1408634F))
    }
    val ink = when {
        !enabled -> Ink.faded
        strong -> Ink.onPrimary
        else -> Ink.primary
    }
    Box(
        Modifier.size(44.dp).pressScale(press, enabled).clip(CircleShape).then(bg).tap(press, enabled, label = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 18.dp, tint = ink) }
}

/** ملاحظة كهرماني (النموذج: `#6B4600` على `#FBF0DD`، زاوية 14). */
@Composable
fun NoteBox(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.alertBg).padding(horizontal = 12.dp, vertical = 10.dp),
        style = Type.of(12, lineHeight = 1.7).copy(color = warnDeep),
    )
}

/** عنوان قسم 16 عريض فوق كارت. */
@Composable
fun CalcSectionTitle(text: String) {
    BasicText(text, style = Type.of(16, FontWeight.Bold))
}

/** سطر صغير رمادي (الفروض والمصادر). */
@Composable
fun FootNote(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = Type.of(12, lineHeight = 1.7).copy(color = Ink.muted))
}
