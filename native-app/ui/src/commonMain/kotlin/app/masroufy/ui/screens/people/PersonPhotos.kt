package app.masroufy.ui.screens.people

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * صورة الشخص (r06-person — OVERRIDES §116-ب): **على الجهاز بس** — عمرها ما بتترفع على فايربيز (بيانات شخصية · قرار مسجّل في OVERRIDES).
 * أندرويد بيقدّمها من `MainActivity` (`AndroidPersonPhotos`: نافذة اختيار الصور بتاعة أندرويد — من غير إذن تخزين — والصورة بتتصغّر وتتحفظ
 * في ملفات البرنامج باسم الشخص). الاختبار والآيفون ⇒ [None] ⇒ رمز الشخص دايمًا.
 */
interface PersonPhotos {
    val available: Boolean

    /** يفتح اختيار الصورة؛ [onDone] بـtrue لو اتحفظت صورة جديدة. */
    fun pick(personId: String, onDone: (Boolean) -> Unit)

    /** الصورة المحفوظة أو null. */
    fun load(personId: String): ImageBitmap?

    object None : PersonPhotos {
        override val available = false

        override fun pick(personId: String, onDone: (Boolean) -> Unit) = onDone(false)

        override fun load(personId: String): ImageBitmap? = null
    }
}

val LocalPersonPhotos = staticCompositionLocalOf<PersonPhotos> { PersonPhotos.None }

/** بيتزوّد كل ما صورة تتحفظ ⇒ الدايرة تقرا تاني. */
internal object PersonPhotoChanges {
    var version by mutableStateOf(0)
        private set

    fun bump() {
        version++
    }
}
