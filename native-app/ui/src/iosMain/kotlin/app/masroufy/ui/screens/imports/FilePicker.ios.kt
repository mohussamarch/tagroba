package app.masroufy.ui.screens.imports

import androidx.compose.runtime.Composable

/** الآيفون: منتقي الملفات (`UIDocumentPickerViewController`) لسه ما اتوصلش — محتاج ماك للبناء والتجربة. الشاشة بتقول «غير متاح». */
@Composable
actual fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): (() -> Unit)? = null
