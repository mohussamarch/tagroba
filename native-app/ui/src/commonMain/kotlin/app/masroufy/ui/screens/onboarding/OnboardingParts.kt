package app.masroufy.ui.screens.onboarding

import app.masroufy.core.UiKey
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * قطع «أول تشغيل» من النموذج: الشريط فوق (رجوع · ٦ أجزاء · تخطَّ) · سطر الاختيار (البلد والمصدر) · لوحة الرد الخضرا تحت · الحلقة في «جاهز» ·
 * مكان الرسمة (الرسمة بخط واحد لسه بتتصمم — عدسة بأيقونة الكارت لحد ما تيجي).
 */
@Composable
internal fun OnbTopBar(s: OnbState, onBack: () -> Unit, onSkip: () -> Unit) {
    val filled = filledSegments(s)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (canBack(s)) Box(Modifier.mirrorInLtr()) { SurfaceIconButton(Lucide.CHEVRON_RIGHT, t(UiKey.SHELL_BACK), onBack) }
        val label = t(UiKey.ONB_STEP_OF, sentenceNumber(s.step.position), sentenceNumber(ONB_SEGMENTS))
        Row(
            Modifier.weight(1f).semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(filled.toFloat(), 0f..ONB_SEGMENTS.toFloat(), ONB_SEGMENTS)
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (i in 1..ONB_SEGMENTS) Segment(i <= filled, Modifier.weight(1f))
        }
        if (canSkip(s)) {
            val press = rememberPress()
            Box(
                Modifier.height(44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).tap(press, onClick = onSkip).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(t(UiKey.ONB_SKIP), style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary)) }
        }
    }
}

@Composable
private fun Segment(on: Boolean, modifier: Modifier) {
    val fill by animateFloatAsState(if (on) 1f else 0f, app.masroufy.ui.theme.motion(app.masroufy.ui.theme.Springs.GENTLE))
    Box(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x1A193D33))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).clip(RoundedCornerShape(3.dp)).background(Glass.primary))
    }
}

/** مكان الرسمة (120×80 بزاوية 16): عدسة فاتحة بأيقونة الكارت — الرسمة النهائية مستنية تصميم المالك. */
@Composable
internal fun OnbIllustration(step: OnbStep) {
    val icon = when (step) {
        OnbStep.LOOK -> Lucide.USER
        OnbStep.COUNTRY -> Lucide.GLOBE
        OnbStep.PAYDAY -> Lucide.CALENDAR
        OnbStep.SOURCE -> Lucide.MESSAGE_SQUARE_TEXT
        OnbStep.READY -> Lucide.CHECK
    }
    Box(
        Modifier.size(width = 120.dp, height = 80.dp).clip(RoundedCornerShape(16.dp)).background(Glass.lensOnLight)
            .insetRing(RoundedCornerShape(16.dp), 1.5.dp, Color(0x5908634F)),
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 32.dp, tint = Ink.primary) }
}

/** سطر اختيار (النموذج: ٦٠ ارتفاع، زاوية 18، عدسة 40 فيها رمز العملة أو أيقونة، والمختار أخضر فاتح بحلقة خضرا وعلامة ✓). */
@Composable
internal fun OnbOptionRow(o: OnbOption, on: Boolean, icon: Lucide?, onPick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).alpha(if (o.off) 0.45f else 1f).pressScale(press, !o.off).clip(shape)
            .background(if (on) Ink.selected else Color.White)
            .insetRing(shape, if (on) 1.5.dp else 1.dp, if (on) Ink.primary else Ink.fieldEdge)
            .tap(press, !o.off, role = Role.RadioButton, onClick = onPick).semantics { selected = on }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1408634F)), contentAlignment = Alignment.Center) {
            if (o.mark != null) BasicText(o.mark, style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
            else if (icon != null) LucideIcon(icon, size = 20.dp, tint = Ink.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(o.label, style = Type.of(16, FontWeight.Bold))
            BasicText(o.sub, style = Type.of(12).copy(color = Ink.muted))
        }
        if (on) LucideIcon(Lucide.CHECK, size = 20.dp, tint = Ink.primary)
    }
}

/** لوحة الرد الخضرا (`#DCEBD6` بزاوية 28 فوق): ✓ في دايرة بيضا · العنوان أخضر عريض · السطر · زرار «متابعة». */
@Composable
internal fun OnbReplyPanel(title: String, line: String, nextLabel: String, bottomPad: androidx.compose.ui.unit.Dp, onNext: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Ink.selected)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp + bottomPad),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                LucideIcon(Lucide.CHECK, size = 20.dp, tint = Ink.income)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(title, style = Type.of(16, FontWeight.Bold).copy(color = Ink.primary))
                BasicText(line, style = Type.of(14))
            }
        }
        PrimaryButton(nextLabel, onClick = onNext, height = 52.dp, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * الحلقة في «جاهز» (132، خط 8): النموذج بيرسم «ملفك ٪» — الحسبة لسه مالهاش منطق (`MeInfo.profilePercent` = null) ⇒ المسار الفاضي بس
 * والعلامة وسطر «غير متاحة بعد»، **مش** حلقة مليانة بنسبة مألّفة (القاعدة 10).
 */
@Composable
internal fun OnbRing() {
    val label = t(UiKey.ONB_RING_NO_PERCENT)
    Box(Modifier.size(132.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            drawCircle(Color(0x1A193D33), radius = size.minDimension / 2 - stroke / 2 - 2.dp.toPx(), center = Offset(size.width / 2, size.height / 2), style = Stroke(stroke))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LucideIcon(Lucide.CHECK, size = 26.dp, tint = Ink.income)
            BasicText(label, Modifier.padding(horizontal = 18.dp), style = Type.of(11).copy(color = Ink.muted, textAlign = TextAlign.Center))
        }
    }
}
