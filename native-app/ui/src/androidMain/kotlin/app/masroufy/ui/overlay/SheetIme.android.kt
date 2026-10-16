package app.masroufy.ui.overlay

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier

/** أندرويد: مكان الكيبورد **الأخير** (مش اللي بيتحرك) ⇒ اللوحة بتتنقل مرة واحدة أول الحركة، مش فريم بفريم. */
@OptIn(ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
actual fun Modifier.sheetImePadding(): Modifier = windowInsetsPadding(WindowInsets.imeAnimationTarget)
