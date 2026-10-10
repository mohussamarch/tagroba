package app.masroufy.ui.screens.imports

import androidx.compose.runtime.Composable

/** ملف اختاره المالك من منتقي ملفات الجوال. [tooLarge] = أكبر من [MAX_FILE_BYTES] ⇒ ما اتقراش. [bytes] فاضية لو القراية فشلت. */
class PickedFile(val name: String, val bytes: ByteArray, val tooLarge: Boolean = false)

/** أكبر ملف كشف بنقراه (٢٥ ميجا — كشف سنة كاملة PDF أقل من كده بكتير). */
const val MAX_FILE_BYTES = 25L * 1024 * 1024

/**
 * منتقي ملفات الجوال نفسه (أندرويد: `OpenDocument` لـPDF وCSV) — [onPicked] بتاخد null لو المالك رجع من غير ما يختار.
 * بيرجّع دالة الفتح، أو null لو الجهاز مالوش منتقي (اختبار الكمبيوتر).
 */
@Composable
expect fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): (() -> Unit)?
