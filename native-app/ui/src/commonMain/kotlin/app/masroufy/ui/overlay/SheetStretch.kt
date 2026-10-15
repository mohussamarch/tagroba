package app.masroufy.ui.overlay

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import kotlin.math.exp

/**
 * الشد لفوق في اللوحة (مطاط — [Sheet]): كام بكسل اللوحة بتطوّل لفوق لما الصباع اتحرك [total] لفوق (سالب). أقصى [max] ومقاومة [scale]:
 * `max·(1−e^(total/scale))`.
 */
internal fun rubberStretch(total: Float, max: Float, scale: Float): Float = max * (1f - exp(total / scale))

/**
 * المسافة تحت المحتوى اللي بتطوّل اللوحة لفوق وقت الشد (`padding(bottom)`). **مقصوصة على الصفر:** رجوع الشد بالنابض المرن
 * بيعدّي الصفر شوية لتحت، و`padding` السالب بيرمي `IllegalArgumentException` وقت الرسم ⇒ البرنامج كان بيقفل مع كل شدة لفوق
 * (HANDOVER §6.5-2). كل اللوحات بتمر من [Sheet] (و`RouteSheet` جواها) فالقص هنا بيغطيهم كلهم.
 */
internal fun Modifier.sheetStretchPadding(stretchPx: Float, density: Density): Modifier =
    padding(bottom = with(density) { stretchPx.coerceAtLeast(0f).toDp() })
