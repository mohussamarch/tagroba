package app.masroufy.ui.overlay

import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier

/** الكمبيوتر (اختبارات JVM ومعاينة الشاشات): مفيش كيبورد على الشاشة — زي الأول. */
@androidx.compose.runtime.Composable
actual fun Modifier.sheetImePadding(): Modifier = imePadding()
