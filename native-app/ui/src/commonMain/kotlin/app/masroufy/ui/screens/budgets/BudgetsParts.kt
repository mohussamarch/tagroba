package app.masroufy.ui.screens.budgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import app.masroufy.ui.theme.motion

/**
 * قطع مشتركة بين شاشات منطقة الميزانيات (من النموذج): شريط التقدم · شارة الحالة · زرار التشغيل · زرار «خطر» · كارت الخطأ.
 * **عرض بس** — النسب والمبالغ جاية جاهزة من المقدِّم (`*Presenter.kt`).
 */

/** نبرة شارة/شريط بالحالة (النموذج: ضمن الحد أخضر · قرّب كهرماني · عدّى أحمر · غير متاح رمادي · جديدة أزرق). */
enum class Tone(val ink: Color, val bg: Color, val bar: Color) {
    OK(Ink.income, Ink.selected, Ink.primary),
    NEAR(Ink.focus, Ink.alertBg, Ink.focus),
    OVER(Ink.expense, Color(0x1ABE3D48), Ink.expense),
    MUTED(Ink.muted, Color(0x0F193D33), Ink.muted),
    NEW(Ink.transfer, Color(0x1A2469BA), Ink.transfer),
}

/** لون التصنيف من `Category.lightColor` («#RRGGBB») — نص مش صالح ⇒ الرمادي. */
fun parseHexColor(hex: String?): Color {
    val h = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return Ink.muted
    val v = h.toLongOrNull(16) ?: return Ink.muted
    return Color(0xFF000000 or v)
}

/** شريط تقدم (8 للبطاقة · 6 للصفوف): [percent] من 0 لـ100 جاهز من المقدِّم، والعرض بيتحرك بالنابض الهادي. */
@Composable
fun ProgressBar(percent: Int, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp, track: Color = Color(0x14193D33)) {
    val target = percent.coerceIn(0, 100) / 100f
    val fraction by animateFloatAsState(target, motion(Springs.GENTLE), label = "bar")
    val shape = RoundedCornerShape(height / 2)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(track)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(shape).background(color))
    }
}

/** شارة الحالة (12 عريض · حشوة 3/10 · زاوية 12). */
@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(tone.bg).padding(horizontal = 10.dp, vertical = 3.dp)) {
        BasicText(text, style = Type.of(12, FontWeight.Bold).copy(color = tone.ink), maxLines = 1)
    }
}

/** زرار تشغيل/إيقاف (44×26 جوه لمس 56×48) — زي «التنبيه» و«أرشفة الخطة» وتشغيل القاعدة. */
@Composable
fun Switch(checked: Boolean, label: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberPress()
    val knob by animateFloatAsState(if (checked) 1f else 0f, motion(Springs.SNAPPY), label = "knob")
    Box(
        modifier.size(56.dp, 48.dp).tap(press, role = Role.Switch, label = label, onClick = onToggle)
            .semantics { contentDescription = label; toggleableState = ToggleableState(checked) },
        contentAlignment = Alignment.Center,
    ) {
        val track = if (checked) Modifier.background(Glass.primary) else Modifier.background(Color(0x29193D33))
        Box(Modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(13.dp)).then(track)) {
            // اليمين = بداية في العربي: المقفول على اليمين (2) والمفتوح على الشمال (20) — زي النموذج
            Box(
                Modifier.padding(start = (2 + 18 * knob).dp, top = 2.dp).size(22.dp)
                    .shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White),
            )
        }
    }
}

/** زرار «خطر» هادي (أبيض بحد أحمر خفيف) — «بلا سقف» · «إزالة السقف». */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp) {
    val shape = RoundedCornerShape(Radius.control)
    val press = rememberPress()
    Box(
        modifier.height(height).pressScale(press).clip(shape).background(Color.White).insetRing(shape, 1.dp, Color(0x40BE3D48))
            .tap(press, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Type.of(14, FontWeight.Bold).copy(color = Ink.expense, textAlign = TextAlign.Center), maxLines = 1)
    }
}

/** زرار صغير بحالتين (ارتفاع 32 · زاوية 16): «احسبه من فلوسي» أخضر ⇄ «محجوز ✓» فاتح. */
@Composable
fun SmallToggleButton(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(Radius.lensSmall)
    val press = rememberPress()
    val surface = if (on) Modifier.background(Ink.selected) else Modifier.background(Glass.primary)
    Box(
        modifier.height(32.dp).alpha(if (enabled) 1f else 0.45f).pressScale(press, enabled).clip(shape).then(surface)
            .tap(press, enabled, onClick = onClick).semantics { stateDescription = text }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Type.of(12, FontWeight.Bold).copy(color = if (on) Ink.income else Ink.onPrimary), maxLines = 1)
    }
}

/** كارت الخطأ (كهرماني) بزرار «أعد المحاولة» — زي «تعذّر تحميل التصنيفات» في النموذج. */
@Composable
fun ErrorCard(title: String, body: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Ink.alertBg).padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "$title. $body" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(title, style = Type.bodyBold().copy(color = Ink.focus))
        BasicText(body, style = Type.of(13).copy(color = Ink.focus))
        val press = rememberPress()
        Box(
            Modifier.height(44.dp).pressScale(press).clip(RoundedCornerShape(Radius.lensSmall)).background(Color(0xCCFFFFFF))
                .tap(press, onClick = onRetry).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText(t(TextKey.SHELL_RETRY), style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary)) }
    }
}

/** نقطة لون التصنيف (10 في الصفوف · 12 في الرأس). */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** صف «عنوان صغير + قيمة» جوه مربع رمادي خفيف (إحصاءات الخطة 2×2). */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Ink.text, ltr: Boolean = true) {
    Column(modifier.clip(RoundedCornerShape(Radius.lensSmall)).background(Color(0x0A193D33)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        BasicText(label, style = Type.of(11).copy(color = Ink.muted), maxLines = 1)
        val style = Type.of(14, FontWeight.Bold).copy(color = valueColor)
        BasicText(value, style = if (ltr) style.copy(fontFeatureSettings = "tnum") else style, maxLines = 1)
    }
}

/** أيقونات المنطقة اللي مش في المشتركة (ARCHITECTURE §31.31 «الأيقونات») — من `<svg>` لوحات النموذج. */
internal object BudgetsIcons {
    /** النجمة في «خطط الادخار» (`<polygon>` اتحوّل لمسار بنفس الشكل) — المختارة كهرماني على خلفية كهرمانية (لوسيد من غير ملء). */
    val STAR = app.masroufy.ui.icons.Lucide("STAR", "M12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2z")

    /** «قدّمها» في ترتيب القواعد (لوسيد `minus`). */
    val MINUS = app.masroufy.ui.icons.Lucide("MINUS", "M5 12h14")
}

/** صف أفقي بمسافة 8 (للشرايح والأزرار). */
@Composable
fun Gap8Row(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}
