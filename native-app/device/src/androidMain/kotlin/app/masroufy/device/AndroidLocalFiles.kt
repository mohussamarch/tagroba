package app.masroufy.device

import android.content.Context
import app.masroufy.port.AppLockSettingsPort
import app.masroufy.port.RepairBackupPort
import app.masroufy.port.SavedBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * حفظ نسخة «قبل الإصلاح» **من غير نافذة نظام** — نقل `LocalFilesPlugin.writeAppFile` + `appFileSize`:
 * مجلد التطبيق على التخزين (`Android/data/<التطبيق>/files/backups` — بيتشاف من الكمبيوتر بكابل)، وإلا الداخلي.
 * الحجم **بيتقرا من القرص بعد الكتابة** (مش طول النص) — عشان الكتابة الناقصة تتكشف (`saveVerifiedBackup`).
 * بيتمسح مع إلغاء تثبيت التطبيق.
 */
class AndroidRepairBackup(private val context: Context) : RepairBackupPort {
    override suspend fun save(fileName: String, content: String): SavedBackup = withContext(Dispatchers.IO) {
        if (!SAFE_NAME.matches(fileName)) throw IllegalArgumentException("اسم الملف أو محتواه غير صالح")
        val file = File(backupDir(), fileName)
        try {
            FileOutputStream(file).use { out ->
                out.write(content.encodeToByteArray())
                out.fd.sync()
            }
        } catch (e: Exception) {
            throw IllegalStateException("تعذر حفظ النسخة على الجهاز", e)
        }
        SavedBackup(file.absolutePath, if (file.isFile) file.length() else 0)
    }

    private fun backupDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "backups").apply { if (!isDirectory) mkdirs() }
    }

    private companion object {
        /** اسم ملف بسيط بس — لا مسارات ولا «..». */
        val SAFE_NAME = Regex("^[A-Za-z0-9._-]{1,120}$")
    }
}

/**
 * «القفل متشغّل» — إعداد الجهاز نفسه (مش بيانات حساب، ومش بيتزامن). نقل `localAppLockSettings.ts`:
 * القراية لو فشلت بترجع «مقفول»، والكتابة لو فشلت بترمي عشان الشاشة تقول إن الإعداد ما اتحفظش.
 */
class AndroidAppLockSettings(context: Context) : AppLockSettingsPort {
    private val prefs = context.getSharedPreferences("app-lock", Context.MODE_PRIVATE)

    override fun read(): Boolean = runCatching { prefs.getBoolean(KEY, false) }.getOrDefault(false)

    override fun write(enabled: Boolean) {
        check(prefs.edit().putBoolean(KEY, enabled).commit()) { "app lock setting write failed" }
    }

    private companion object {
        const val KEY = "masroufy.appLock.enabled"
    }
}

/**
 * آخر مزامنة لقاعدة التجار المشتركة على الجهاز ده — نقل `localSyncCursor.ts` (OVERRIDES §25.1). مش بيانات مالية:
 * لو اتمسحت أو فشلت، المزامنة الجاية بتقرا كله (المعرّفات ثابتة فمفيش تكرار). لكل حساب ([uid]) مفتاح لوحده.
 */
class AndroidSyncCursor(context: Context, uid: String, name: String = "shared-merchants") : app.masroufy.port.SyncCursorPort {
    private val prefs = context.getSharedPreferences("sync-cursors", Context.MODE_PRIVATE)
    private val key = "masroufy-sync-v1:$uid:$name"

    override fun read(): String? = runCatching { prefs.getString(key, null)?.takeIf { runCatching { java.time.Instant.parse(it) }.isSuccess } }.getOrNull()

    override fun write(iso: String) {
        runCatching { prefs.edit().putString(key, iso).apply() }
    }
}
