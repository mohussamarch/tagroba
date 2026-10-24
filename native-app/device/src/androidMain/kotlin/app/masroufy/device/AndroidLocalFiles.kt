package app.masroufy.device

import app.masroufy.core.ProblemCode
import app.masroufy.problems.Problems
import android.content.Context
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.AppLockSettingsPort
import app.masroufy.port.RepairBackupPort
import app.masroufy.port.SavedBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * حفظ نسخة «قبل الإصلاح» **من غير نافذة نظام** — نقل `LocalFilesPlugin.writeAppFile` + `appFileSize`:
 * المجلد **الداخلي الخاص** (`noBackupFilesDir/backups` — PRIVACY-4): مش بيتشاف من الكمبيوتر ولا من تطبيق تاني، ومش بيدخل
 * نسخ أندرويد التلقائية. النسخة اللي المستخدم ياخدها معاه بتطلع بنافذة «احفظ في» (CreateDocument) بس.
 * المجلد الخارجي القديم (`Android/data/<التطبيق>/files/backups`) بيتمسح مرة واحدة أول حفظ بعد التحديث.
 * الحجم **بيتقرا من القرص بعد الكتابة** (مش طول النص) — عشان الكتابة الناقصة تتكشف (`saveVerifiedBackup`).
 * بيتمسح مع إلغاء تثبيت التطبيق.
 */
class AndroidRepairBackup(private val context: Context) : RepairBackupPort {
    override suspend fun save(fileName: String, content: String): SavedBackup = withContext(Dispatchers.IO) {
        if (!SAFE_NAME.matches(fileName)) throw IllegalArgumentException(uiText(TextKey.FILE_NAME_INVALID))
        val file = File(backupDir(), fileName)
        try {
            FileOutputStream(file).use { out ->
                out.write(content.encodeToByteArray())
                out.fd.sync()
            }
        } catch (e: Exception) {
            Problems.report(ProblemCode.BACKUP_DEVICE_COPY_FAILED, e)
            throw IllegalStateException(uiText(TextKey.BACKUP_DEVICE_SAVE_FAILED), e)
        }
        val size = if (file.isFile) file.length() else 0
        // قرار المالك: النسخ الداخلية آخر ملفين بس — بعد ما النسخة الجديدة اتكتبت وانقاست، الزيادة بتتمسح (فشل المسح ما بيفشلش الحفظ)
        if (size > 0) pruneOld(file)
        SavedBackup(file.absolutePath, size)
    }

    /** r06-links2 (§118-ج): نسخ «قبل الإصلاح» في `noBackupFilesDir/backups` (والقديم الخارجي لو فضل) — الأحدث الأول. */
    override suspend fun list(): List<app.masroufy.port.SavedBackupFile> = withContext(Dispatchers.IO) {
        dirs().flatMap { d -> d.listFiles { f -> f.isFile }.orEmpty().toList() }
            .map { app.masroufy.port.SavedBackupFile(it.name, it.length(), it.lastModified()) }
            .sortedByDescending { it.modifiedMillis }
    }

    /** مسح ملف بالاسم (اسم بسيط بس — لا مسارات). ملف ⇒ مالوش تراجع. */
    override suspend fun delete(fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (!SAFE_NAME.matches(fileName)) return@withContext false
        dirs().map { File(it, fileName) }.filter { it.isFile }.count { it.delete() } > 0
    }

    private fun dirs(): List<File> = listOfNotNull(
        File(context.noBackupFilesDir, DIR).takeIf { it.isDirectory },
        context.getExternalFilesDir(null)?.let { File(it, DIR) }?.takeIf { it.isDirectory },
    )

    private fun pruneOld(written: File) = runCatching {
        val siblings = written.parentFile?.listFiles { f -> f.isFile }.orEmpty().map { BackupFileInfo(it.name, it.lastModified()) }
        for (name in BackupPruning.toDelete(siblings, written.name)) File(written.parentFile, name).delete()
    }

    private fun backupDir(): File {
        wipeLegacyExternal(context)
        return File(context.noBackupFilesDir, DIR).apply { if (!isDirectory) mkdirs() }
    }

    companion object {
        private const val DIR = "backups"
        private const val LEGACY_WIPED = "legacyExternalWiped.v1"

        /** اسم ملف بسيط بس — لا مسارات ولا «..». */
        private val SAFE_NAME = Regex("^[A-Za-z0-9._-]{1,120}$")

        /** مسح النسخ القديمة اللي كانت على التخزين الخارجي — مرة واحدة (علامة في الإعدادات). الفشل ما بيوقفش الحفظ. */
        fun wipeLegacyExternal(context: Context) {
            runCatching {
                val prefs = context.getSharedPreferences("repair-backup", Context.MODE_PRIVATE)
                if (prefs.getBoolean(LEGACY_WIPED, false)) return
                context.getExternalFilesDir(null)?.let { File(it, DIR) }?.takeIf { it.isDirectory }?.let { deleteFilesIn(it) }
                prefs.edit().putBoolean(LEGACY_WIPED, true).apply()
            }
        }

        /** تسجيل الخروج (AUTH-4): كل نسخ «قبل الإصلاح» على الجهاز (الداخلي والخارجي القديم). بيرجّع كام ملف اتمسح. */
        fun wipeAll(context: Context): Int = runCatching {
            deleteFilesIn(File(context.noBackupFilesDir, DIR)) +
                (context.getExternalFilesDir(null)?.let { deleteFilesIn(File(it, DIR)) } ?: 0)
        }.getOrDefault(0)

        private fun deleteFilesIn(dir: File): Int = dir.listFiles { f -> f.isFile }.orEmpty().count { it.delete() }
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

/**
 * البلد الشغالة على الجهاز ده (§64) — إعداد جهاز مش بيانات حساب، ومش بيتزامن. لكل حساب ([uid]) مفتاح لوحده،
 * والقيمة البايظة = مفيش (⇒ السعودية — `activeSpaceOf`).
 */
class AndroidActiveSpaceStore(context: Context, uid: String) : app.masroufy.port.ActiveSpaceStore {
    private val prefs = context.getSharedPreferences("active-space", Context.MODE_PRIVATE)
    private val key = "masroufy-space-v1:$uid"

    override fun read(): String? = runCatching { prefs.getString(key, null)?.takeIf { app.masroufy.core.isSpaceId(it) } }.getOrNull()

    override fun write(spaceId: String) {
        runCatching { prefs.edit().putString(key, spaceId).apply() }
    }
}
