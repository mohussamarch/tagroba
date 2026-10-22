package app.masroufy.ui.screens.investment.holdings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.masroufy.ui.theme.Ink

/** قطع صغيرة مشتركة لملفات «حاجاتي» (بنفس قيم النموذج). */
internal val SOFT_FILL = Ink.tint(0x0D)
internal val CHIP_FILL = Ink.tint(0x0F)
internal val ADD_FILL = Color(0x8CFFFFFF)
internal val DASH = Ink.primary.copy(alpha = 0x66 / 255f)
internal val PLAN_THUMB = Color(0xFFB7791F)

/** ضغطة باسم للقارئ (زرار). */
internal fun Modifier.holdTap(label: String, onClick: () -> Unit): Modifier =
    clickable(onClickLabel = label, role = Role.Button, onClick = onClick)

/** حد داخلي (`box-shadow: inset 0 0 0 Wpx C`). */
internal fun Modifier.insetBorder(width: Dp, color: Color, radius: Dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(radius.toPx() - w / 2), style = Stroke(w))
}

/** الحد المتقطع لزرار «ضيف حاجة» (1.5 بلون الأخضر 40%). */
internal fun Modifier.dashedBorder(radius: Dp): Modifier = drawBehind {
    val w = 1.5.dp.toPx()
    drawRoundRect(
        DASH, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/**
 * شريط «خطتي» (النموذج `input[type=range]`): [step] من 0 لـ[steps] — المكان على الشاشة بيتحوّل لرقم خطوة صحيح، والمبلغ = الخطوة ×
 * قيمتها في حالة الاستخدام (مفيش كسر في أي مبلغ). الاتجاه من الشمال لليمين زي النموذج (`dir="ltr"`).
 */
@Composable
internal fun StepSlider(step: Int, steps: Int, label: String, valueText: String, onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = "$label، $valueText" }) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        // مكان اللمس من الشمال دايمًا (إحداثيات الشاشة) ⇒ رقم خطوة صحيح
        fun stepAt(x: Float): Int = ((x / widthPx) * steps + 0.5f).toInt().coerceIn(0, steps)
        Box(
            Modifier.fillMaxWidth().height(28.dp)
                .pointerInput(steps) { detectTapGestures { onStep(stepAt(it.x)) } }
                .pointerInput(steps) { detectHorizontalDragGestures { change, _ -> onStep(stepAt(change.position.x)) } }
                .drawBehind {
                    val track = 6.dp.toPx()
                    val cy = size.height / 2
                    drawRoundRect(Ink.tint(0x1F), Offset(0f, cy - track / 2), Size(size.width, track), CornerRadius(track / 2))
                    val r = 14.dp.toPx()
                    val frac = if (steps == 0) 0f else step.toFloat() / steps
                    // RTL: الصفحة كلها بتتعكس، والنموذج LTR ⇒ نرسم من الشمال دايمًا
                    val cx = (r + frac * (size.width - 2 * r)).coerceIn(r, size.width - r)
                    drawCircle(Color(0x38102D35), r + 1.dp.toPx(), Offset(cx, cy + 3.dp.toPx()))
                    drawCircle(PLAN_THUMB, r, Offset(cx, cy))
                    drawCircle(Color.White, r - 6.dp.toPx(), Offset(cx, cy))
                },
        )
    }
}
