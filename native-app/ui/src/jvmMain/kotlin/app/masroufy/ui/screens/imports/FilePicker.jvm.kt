package app.masroufy.ui.screens.imports

import androidx.compose.runtime.Composable

/** الكمبيوتر (اختبارات JVM بس) — مفيش منتقي ملفات. */
@Composable
actual fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): (() -> Unit)? = null
