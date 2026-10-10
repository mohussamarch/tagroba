package app.masroufy.ui.screens.more

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.Badge
import app.masroufy.ui.components.BadgeKind
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import app.masroufy.ui.theme.motion

/**
 * قطع مشتركة بين شاشات «المزيد» بنفس قيم النموذج: عنوان المجموعة 13 رمادي · كارت المجموعة (`padding 4 8`) · صف قايمة 56 بأيقونة 36 ·
 * المفتاح (المسار 44×26 · الدايرة 22 · منطقة اللمس 56×48) · شرائح الاختيار المليانة · شبكة أيام الشهر · صناديق الملاحظة والتنبيه.
 */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier.padding(horizontal = 4.dp), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
}

/** كارت مجموعة صفوف (الفواصل بين الصفوف من [MenuRow]/[FactRow] نفسهم). */
@Composable
fun GroupCard(modifier: Modifier = Modifier, horizontal: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
    FloatingCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = horizontal, vertical = 4.dp), content = content)
}

/** خط رفيع تحت الصف (`0 1px 0 rgba(204,216,204,0.55)`). */
@Composable
fun RowRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x8CCCD8CC)))
}

/** صف في قايمة «المزيد»: أيقونة 36 · الاسم 15 · السطر التاني 12 · سهم 16. صفوف القوايم بتفضل بكلامها (KOTLIN-MAP §٣). */
@Composable
fun MenuRow(icon: Lucide?, label: String, hint: String?, last: Boolean, onClick: () -> Unit, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val press = rememberPress()
    Column {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).pressScale(press).clip(RoundedCornerShape(16.dp))
                .tap(press, label = label, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) IconTile(Ink.primary) { LucideIcon(icon, size = 18.dp, tint = Ink.primary) }
            Column(Modifier.weight(1f)) {
                BasicText(label, style = Type.of(15, FontWeight.Medium))
                if (!hint.isNullOrEmpty()) BasicText(hint, style = Type.caption().copy(color = Ink.muted))
            }
            trailing?.invoke(this)
            LucideIcon(Lucide.CHEVRON_LEFT, size = 16.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
        }
        if (!last) RowRule()
    }
}

/** صف حقيقة («النوع» ⇐ «حساب بنكي»): الاسم 13 رمادي يمين، والقيمة 14 عريض شمال. */
@Composable
fun FactRow(label: String, value: String, last: Boolean, muted: Boolean = false, valueContent: (@Composable () -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, Modifier.weight(1f), style = Type.of(13).copy(color = Ink.muted))
            if (valueContent != null) valueContent()
            else BasicText(value, style = Type.of(14, FontWeight.Bold).copy(color = if (muted) Ink.muted else Ink.text, textAlign = TextAlign.End))
        }
        if (!last) RowRule()
    }
}

/** المفتاح (`role=switch`): المسار 44×26 أخضر لما يشتغل، والدايرة 22 بتتحرك بالنابض المرن. [enabled] = false ⇒ شفافية 0.45. */
@Composable
fun ToggleSwitch(checked: Boolean, label: String, enabled: Boolean = true, onToggle: () -> Unit) {
    val press = rememberPress()
    val knob by animateDpAsState(if (checked) 20.dp else 2.dp, motion(Springs.BOUNCY), label = "knob")
    Box(
        Modifier.size(56.dp, 48.dp).tap(press, enabled, role = Role.Switch, label = label, onClick = onToggle)
            .semantics { contentDescription = label; stateDescription = if (checked) "on" else "off" },
        contentAlignment = Alignment.Center,
    ) {
        val track = if (checked) Modifier.background(Glass.primary) else Modifier.background(Color(0x29193D33))
        Box(Modifier.width(44.dp).height(26.dp).alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(13.dp)).then(track)) {
            // «من اليمين» في النموذج (dir=rtl): الدايرة يمين وهو مقفول وشمال وهو شغال ⇒ بالبداية/النهاية بتتقلب لوحدها في الإنجليزي
            Box(Modifier.align(Alignment.CenterStart).offset(x = knob).size(22.dp).clip(CircleShape).background(Color.White))
        }
    }
}

/** شريحة اختيار مليانة (النموذج: `Account` · `Onboarding` · `JobChangeSheet`): المختارة أخضر متدرج بخط أبيض عريض، والباقي رمادي خفيف. */
@Composable
fun FillChip(label: String, on: Boolean, modifier: Modifier = Modifier, height: Dp = 44.dp, enabled: Boolean = true, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.height(height).alpha(if (enabled) 1f else 0.45f).pressScale(press, enabled).clip(shape)
            .background(if (on) Glass.primary else androidx.compose.ui.graphics.SolidColor(Color(0x0F193D33)))
            .tap(press, enabled, role = Role.RadioButton, onClick = onClick).semantics { selected = on }.padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Type.of(14, if (on) FontWeight.Bold else FontWeight.Normal).copy(color = if (on) Color.White else Ink.text), maxLines = 1)
    }
}

/** شبكة أيام الشهر ١–٣١ (٧ أعمدة، الخانة 44 بزاوية 12) — يوم الراتب في «ملفك» و«أول مرة» و«غيّرت شغلي». */
@Composable
fun DayGrid(selected: Int?, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in (1..31).chunked(7)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in row) DayCell(d, d == selected, Modifier.weight(1f)) { onPick(d) }
                repeat(7 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        modifier.height(44.dp).pressScale(press).clip(RoundedCornerShape(12.dp))
            .background(if (on) Glass.primary else androidx.compose.ui.graphics.SolidColor(Color(0x0D193D33)))
            .tap(press, role = Role.RadioButton, onClick = onClick).semantics { selected = on },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(app.masroufy.core.sentenceNumber(day), style = Type.of(14, if (on) FontWeight.Bold else FontWeight.Normal).copy(color = if (on) Color.White else Ink.text))
    }
}

/** صندوق ملاحظة هادي (`rgba(25,61,51,0.04)` بزاوية 18). */
@Composable
fun NoteBox(body: String, modifier: Modifier = Modifier, title: String? = null) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x0A193D33)).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (title != null) BasicText(title, style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
        BasicText(body, style = Type.caption().copy(color = Ink.muted))
    }
}

/** تنبيه كهرماني (`#FBF0DD`): عنوان + سطر + فعل اختياري («أعد المحاولة»). */
@Composable
fun WarnBox(title: String?, body: String?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.alertBg).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) BasicText(title, style = Type.of(14, FontWeight.Bold).copy(color = Ink.focus))
        if (body != null) BasicText(body, style = Type.of(13).copy(color = Color(0xFF6B4600)))
        action?.invoke()
    }
}

/** «غير متاح بعد» — المنطق لسه ما اتبناش في كوتلن ([MoreHooks]). شارة رمادي مش رقم ولا زرار ميت من غير سبب. */
@Composable
fun NotYetBadge(modifier: Modifier = Modifier) = Badge(t(TextKey.MORE_NOT_YET), BadgeKind.NOT_AVAILABLE, modifier)

/** سطر «غير متاح بعد» بسبب — تحت زرار مقفول. */
@Composable
fun NotYetLine(reason: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NotYetBadge()
        BasicText(reason, Modifier.weight(1f), style = Type.caption().copy(color = Ink.muted))
    }
}
