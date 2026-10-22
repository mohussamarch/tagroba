package app.masroufy.ui.screens.more

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            callback(FilePickResult.Cancelled)
            return@rememberLauncherForActivityResult
        }
        // القراية على خيط الدخول والخروج (IO) مش الرئيسي — ملف كبير كان بيعلّق الشاشة
        scope.launch {
            val result = withContext(Dispatchers.IO) { readPicked(context.contentResolver, uri) }
            callback(result)
        }
    }
    // نوع نسخة مصروفي JSON ⇒ `application/json`؛ و`octet-stream` لملفات درايف وبعض مديري الملفات — والاسم لازم يخلص بـ .json (`checkBackupPick`)
    return { runCatching { launcher.launch(arrayOf("application/json", "application/octet-stream")) }.onFailure { callback(FilePickResult.Failed(it.message)) } }
}

/** اسم الملف وحجمه من مزوّد المحتوى (من غير ما نقراه). */
private fun nameAndSize(resolver: ContentResolver, uri: Uri): Pair<String, Long?> {
    var name: String? = null
    var size: Long? = null
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
    }
    return (name ?: uri.lastPathSegment.orEmpty()) to size
}

private fun readPicked(resolver: ContentResolver, uri: Uri): FilePickResult = runCatching {
    val (name, size) = nameAndSize(resolver, uri)
    checkBackupPick(name, size)?.let { return@runCatching FilePickResult.Rejected(it) }
    // الحجم ممكن ما يتقالش ⇒ بنقرا لحد الحد الأقصى + بايت واحد، ولو عدّاه نرفض
    val input = resolver.openInputStream(uri) ?: error("no stream")
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    input.use { s ->
        while (true) {
            val n = s.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_BACKUP_PICK_BYTES) return@runCatching FilePickResult.Rejected(PickRejection.TOO_BIG)
            out.write(buffer, 0, n)
        }
    }
    FilePickResult.Picked(name, out.toByteArray().decodeToString())
}.getOrElse { FilePickResult.Failed(it.message) }
