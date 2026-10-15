package app.masroufy.ui.screens.more

import androidx.compose.runtime.Composable

/** JVM (اختبارات الكمبيوتر): مفيش نافذة مشاركة ⇒ «غير متاح» بصراحة. */
@Composable
actual fun rememberTextSharer(): ((text: String) -> Boolean)? = null
