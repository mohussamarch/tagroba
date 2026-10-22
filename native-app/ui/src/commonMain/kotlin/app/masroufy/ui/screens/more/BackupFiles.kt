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

    /** الملف اتختار بس مش نسخة (الامتداد) أو كبير أوي — ما اتقراش. */
    data class Rejected(val why: PickRejection) : FilePickResult

    data class Failed(val message: String?) : FilePickResult

    data object Unavailable : FilePickResult
}

/** ليه ملف اتختار اترفض قبل ما يتقرا: كبير أوي · مش ملف نسخة (الامتداد). */
enum class PickRejection { TOO_BIG, NOT_BACKUP }

/** أقصى حجم لملف النسخة بيتفتح (50 ميجا) — نسخة المالك بالكتير كام ميجا. */
const val MAX_BACKUP_PICK_BYTES = 50L * 1024 * 1024

/**
 * فحص صافي قبل قراية الملف: النسخة بنكتبها `masroufy-backup-<التاريخ>.json` ⇒ الامتداد `.json` بس (المنتقي بيقبل `application/octet-stream`
 * عشان ملفات درايف — فالاسم هو الحكم). [sizeBytes] = null (المزوّد ما قالش) ⇒ بنقرا بحد أقصى [MAX_BACKUP_PICK_BYTES] وإلا بنرفض.
 */
fun checkBackupPick(name: String, sizeBytes: Long?): PickRejection? = when {
    !name.trim().endsWith(".json", ignoreCase = true) -> PickRejection.NOT_BACKUP
    sizeBytes != null && sizeBytes > MAX_BACKUP_PICK_BYTES -> PickRejection.TOO_BIG
    else -> null
}

/** بيرجّع دالة «احفظ [content] باسم مقترح» بنوع [mime] — النتيجة في [onResult] بعد ما المستخدم يختار المكان. */
@Composable
expect fun rememberFileSaver(mime: String, onResult: (FileSaveResult) -> Unit): (suggestedName: String, content: String) -> Unit

/** بيرجّع دالة «افتح ملف نص» — النتيجة (الاسم والنص) في [onResult]. */
@Composable
expect fun rememberFilePicker(onResult: (FilePickResult) -> Unit): () -> Unit
