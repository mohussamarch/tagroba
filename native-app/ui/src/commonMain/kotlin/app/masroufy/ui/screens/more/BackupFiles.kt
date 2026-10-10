package app.masroufy.ui.screens.more

import androidx.compose.runtime.Composable

/**
 * ملفات الجوال لشاشة النسخة الاحتياطية — **نافذة النظام نفسها** (أندرويد: «حفظ في» و«فتح ملف» — `CreateDocument`/`OpenDocument`)،
 * فالتطبيق ما بيلمسش ملفات من غير ما المستخدم يختار مكانها. الجهاز اللي مالوش (اختبار JVM) ⇒ [FileSaveResult.Unavailable].
 */
sealed interface FileSaveResult {
    /** [bytes] = عدد البايتات اللي اتكتبت فعلًا (بيتقارن بالمتوقع — زي `saveVerifiedBackup`). */
    data class Saved(val bytes: Long) : FileSaveResult

    data object Cancelled : FileSaveResult

    data class Failed(val message: String?) : FileSaveResult

    data object Unavailable : FileSaveResult
}

sealed interface FilePickResult {
    data class Picked(val name: String, val text: String) : FilePickResult

    data object Cancelled : FilePickResult

    data class Failed(val message: String?) : FilePickResult

    data object Unavailable : FilePickResult
}

/** بيرجّع دالة «احفظ [content] باسم مقترح» بنوع [mime] — النتيجة في [onResult] بعد ما المستخدم يختار المكان. */
@Composable
expect fun rememberFileSaver(mime: String, onResult: (FileSaveResult) -> Unit): (suggestedName: String, content: String) -> Unit

/** بيرجّع دالة «افتح ملف نص» — النتيجة (الاسم والنص) في [onResult]. */
@Composable
expect fun rememberFilePicker(onResult: (FilePickResult) -> Unit): () -> Unit
