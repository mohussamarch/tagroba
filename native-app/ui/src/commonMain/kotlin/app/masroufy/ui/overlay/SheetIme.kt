package app.masroufy.ui.overlay

import androidx.compose.ui.Modifier

/**
 * r041b P7 (فحص المحاكي r041 — اللقطات 46 · 67): أول ضغطة على شريحة تصنيف بعد ما الكيبورد يتقفل كانت بتضيع. السبب: اللوحة كانت بتنزل
 * **مع** حركة الكيبورد (`imePadding` بيتغير كل فريم ~ربع ثانية) ⇒ الشرايح هدف بيتحرك تحت الصباع، والضغطة بتقع على حاجة تانية أو بتتلغي.
 * دلوقتي اللوحة بتروح **لمكانها الأخير مرة واحدة** أول ما الكيبورد يبدأ يتحرك (أندرويد: `imeAnimationTarget`) — الشرايح ثابتة وقت الضغط.
 * المنصات التانية: `imePadding` زي الأول.
 */
@androidx.compose.runtime.Composable
expect fun Modifier.sheetImePadding(): Modifier
