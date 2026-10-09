package app.masroufy.ui.screens.more

import androidx.compose.runtime.Composable

/** الآيفون: نافذة الملفات (`UIDocumentPickerViewController`) لسه ما اتربطتش ⇒ «غير متاح» بصراحة. ⚠️ ما اتجربش (محتاج ماك). */
@Composable
actual fun rememberFileSaver(mime: String, onResult: (FileSaveResult) -> Unit): (suggestedName: String, content: String) -> Unit =
    { _, _ -> onResult(FileSaveResult.Unavailable) }

@Composable
actual fun rememberFilePicker(onResult: (FilePickResult) -> Unit): () -> Unit = { onResult(FilePickResult.Unavailable) }
