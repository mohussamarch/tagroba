package app.masroufy.ui.screens.imports

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** منتقي ملفات أندرويد (`ACTION_OPEN_DOCUMENT`) — الملف بيتقرا على الجوال ومش بيترفع (spec/05). */
@Composable
actual fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            current(null)
        } else {
            scope.launch { current(withContext(Dispatchers.IO) { readPicked(context, uri) }) }
        }
    }
    return { launcher.launch(TYPES) }
}

/** PDF وCSV (بعض الجوالات بتسمّي CSV نص عادي أو إكسل). */
private val TYPES = arrayOf("application/pdf", "text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel")

private fun readPicked(context: Context, uri: Uri): PickedFile {
    var name = uri.lastPathSegment ?: "file"
    var size = -1L
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
    }
    if (size > MAX_FILE_BYTES) return PickedFile(name, ByteArray(0), tooLarge = true)
    val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: ByteArray(0)
    return PickedFile(name, bytes, tooLarge = bytes.size > MAX_FILE_BYTES)
}
