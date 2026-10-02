package app.masroufy.device

import app.masroufy.core.isValidIsoDate
import app.masroufy.port.AppLockSettingsPort
import app.masroufy.port.RepairBackupPort
import app.masroufy.port.SavedBackup
import app.masroufy.port.SyncCursorPort
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

/**
 * نسخة «قبل الإصلاح» على الآيفون **من غير نافذة** — زي [AndroidRepairBackup]: مجلد `Documents/backups` بتاع التطبيق
 * (بيبان في تطبيق «الملفات» لما الإعداد `UIFileSharingEnabled` يتحط في تطبيق الآيفون — لسه مفيش تطبيق). الحجم **من القرص**
 * بعد الكتابة، والكتابة «ذرية» (`NSDataWritingAtomic`: يا الملف كله يا ولا حاجة). بيتمسح مع مسح التطبيق.
 */
@OptIn(ExperimentalForeignApi::class)
class IosRepairBackup : RepairBackupPort {
    override suspend fun save(fileName: String, content: String): SavedBackup = withContext(Dispatchers.IO) {
        if (!SAFE_NAME.matches(fileName)) throw IllegalArgumentException("اسم الملف أو محتواه غير صالح")
        val files = NSFileManager.defaultManager
        val docs = files.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).firstOrNull() as? NSURL
            ?: throw IllegalStateException("تعذر حفظ النسخة على الجهاز")
        val dir = docs.URLByAppendingPathComponent("backups") ?: throw IllegalStateException("تعذر حفظ النسخة على الجهاز")
        val path = dir.URLByAppendingPathComponent(fileName)?.path ?: throw IllegalStateException("تعذر حفظ النسخة على الجهاز")
        val ok = files.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null) &&
            content.encodeToByteArray().toNSData().writeToFile(path, options = NSDataWritingAtomic, error = null)
        if (!ok) throw IllegalStateException("تعذر حفظ النسخة على الجهاز")
        val size = (files.attributesOfItemAtPath(path, error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0
        SavedBackup(path, size)
    }

    private companion object {
        val SAFE_NAME = Regex("^[A-Za-z0-9._-]{1,120}$")
    }
}

/** «القفل متشغّل» — إعداد الجهاز (`NSUserDefaults`)، مش بيانات حساب. نفس سلوك [AndroidAppLockSettings]. */
class IosAppLockSettings(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : AppLockSettingsPort {
    override fun read(): Boolean = runCatching { defaults.boolForKey(KEY) }.getOrDefault(false)

    override fun write(enabled: Boolean) {
        defaults.setBool(enabled, KEY)
        check(defaults.boolForKey(KEY) == enabled) { "app lock setting write failed" }
    }

    private companion object {
        const val KEY = "masroufy.appLock.enabled"
    }
}

/** آخر مزامنة لقاعدة التجار المشتركة — نفس [AndroidSyncCursor]: مفتاح لكل حساب، والقيمة البايظة = مزامنة كاملة. */
class IosSyncCursor(uid: String, name: String = "shared-merchants", private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : SyncCursorPort {
    private val key = "masroufy-sync-v1:$uid:$name"

    override fun read(): String? = runCatching { defaults.stringForKey(key)?.takeIf(::isIsoInstant) }.getOrNull()

    override fun write(iso: String) {
        runCatching { defaults.setObject(iso, key) }
    }

    private companion object {
        val INSTANT = Regex("^(\\d{4}-\\d{2}-\\d{2})T([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d(\\.\\d{1,9})?Z$")

        fun isIsoInstant(text: String): Boolean = INSTANT.matchEntire(text)?.let { isValidIsoDate(it.groupValues[1]) } == true
    }
}
