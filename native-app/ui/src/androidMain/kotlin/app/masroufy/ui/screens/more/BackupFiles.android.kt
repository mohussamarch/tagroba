package app.masroufy.ui.screens.more

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** أندرويد: «حفظ في» بنافذة النظام — النص بيتكتب UTF-8 في المكان اللي المستخدم اختاره، وعدد البايتات بيرجع للتأكد. */
@Composable
actual fun rememberFileSaver(mime: String, onResult: (FileSaveResult) -> Unit): (suggestedName: String, content: String) -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri: Uri? ->
        val content = pending
        pending = null
        if (uri == null || content == null) {
            callback(FileSaveResult.Cancelled)
            return@rememberLauncherForActivityResult
        }
        val bytes = content.encodeToByteArray()
        val result = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("no stream")
            FileSaveResult.Saved(bytes.size.toLong())
        }.getOrElse { FileSaveResult.Failed(it.message) }
        callback(result)
    }
    return { name, content ->
        pending = content
        runCatching { launcher.launch(name) }.onFailure { pending = null; callback(FileSaveResult.Failed(it.message)) }
    }
}

/** أندرويد: «فتح ملف» بنافذة النظام — النص كله UTF-8 واسم الملف من مزوّد المحتوى. */
@Composable
actual fun rememberFilePicker(onResult: (FilePickResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            callback(FilePickResult.Cancelled)
            return@rememberLauncherForActivityResult
        }
        val result = runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("no stream")
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment.orEmpty()
            FilePickResult.Picked(name, text)
        }.getOrElse { FilePickResult.Failed(it.message) }
        callback(result)
    }
    return { runCatching { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }.onFailure { callback(FilePickResult.Failed(it.message)) } }
}
