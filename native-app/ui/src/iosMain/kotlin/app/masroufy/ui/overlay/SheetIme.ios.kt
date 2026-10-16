package app.masroufy.ui.overlay

import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier

/** الآيفون: زي الأول (`imePadding`). ⚠️ ما اتجربش (محتاج ماك). */
@androidx.compose.runtime.Composable
actual fun Modifier.sheetImePadding(): Modifier = imePadding()
