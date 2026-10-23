package app.masroufy.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.masroufy.ui.screens.people.PersonPhotos
import java.io.File

/**
 * صورة الشخص (r06-person — OVERRIDES §116-ب): نافذة اختيار الصور بتاعة أندرويد (`PickVisualMedia` — **من غير إذن تخزين**) ⇒ الصورة بتتصغّر
 * لـ[MAX_SIDE] وتتحفظ JPEG في ملفات البرنامج الخاصة (`files/person-photos/<معرّف الشخص>.jpg`). **عمرها ما بتترفع** على فايربيز ولا بتدخل النسخة.
 * لازم يتعمل في `onCreate` (قبل ما الشاشة تبدأ) عشان `registerForActivityResult`.
 */
class AndroidPersonPhotos(private val activity: ComponentActivity) : PersonPhotos {
    private var pending: Pair<String, (Boolean) -> Unit>? = null
    private val cache = HashMap<String, ImageBitmap?>()

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val (personId, done) = pending ?: return@registerForActivityResult
        pending = null
        done(uri != null && runCatching { save(personId, uri) }.getOrDefault(false))
    }

    override val available: Boolean get() = true

    override fun pick(personId: String, onDone: (Boolean) -> Unit) {
        pending = personId to onDone
        runCatching { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure {
            pending = null
            onDone(false)
        }
    }

    override fun load(personId: String): ImageBitmap? = cache.getOrPut(personId) {
        val f = fileOf(personId)
        if (!f.exists()) null else runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
    }

    private fun save(personId: String, uri: Uri): Boolean {
        val resolver = activity.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE && bounds.outHeight / (sample * 2) >= MAX_SIDE) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return false
        val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val f = fileOf(personId)
        f.parentFile?.mkdirs()
        f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        cache.remove(personId)
        return true
    }

    /** اسم الملف من المعرّف بعد ما نشيل أي حرف مش آمن (مفيش مسارات). */
    private fun fileOf(personId: String): File {
        val safe = personId.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
        return File(File(activity.filesDir, "person-photos"), "$safe.jpg")
    }

    private companion object {
        const val MAX_SIDE = 512
    }
}
