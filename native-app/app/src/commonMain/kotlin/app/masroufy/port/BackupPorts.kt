package app.masroufy.port

import app.masroufy.core.BackupRow
import app.masroufy.core.FullBackupData

/** النسخة الشاملة ونسخة ما قبل الصيانة — نقل `FullBackupPort.ts` و`RepairBackupPort.ts`. */

interface FullBackupPort {
    /** قراية كاملة من السيرفر على صفحات — **عمرها ما ترجع لنسخة الجهاز الناقصة بصمت**. */
    suspend fun read(): FullBackupData

    /** بيضيف المعرّفات الناقصة بس. ممكن يتقسم دفعات؛ إعادته بعد انقطاع آمنة، وعمره ما يستبدل الموجود. */
    suspend fun addMissing(data: FullBackupData): Map<String, Int>

    /** ملف الحساب (مستند واحد)، أو null لو لسه ما اتحفظش — OVERRIDES §26. */
    suspend fun readProfile(): BackupRow?

    /** بيكتب ملف الحساب **لو الحساب مالوش ملف بس**، وعمره ما يكتب فوق الموجود. `true` = اتكتب. */
    suspend fun addProfileIfMissing(profile: BackupRow): Boolean
}

/**
 * البلاد غير السعودية في النسخة الشاملة (الإصدار 3 — §41.1 · §64). السعودية والحساب في [FullBackupPort] زي الإصدار 2.
 * كل قراية **من السيرفر** وكل كتابة **بتضيف الناقص بس** — زي [FullBackupPort] بالظبط.
 */
interface SpacesBackupPort {
    /** سجل البلاد كله (المؤرشف كمان — النسخة للحساب كله). */
    suspend fun registry(): List<app.masroufy.core.Space>

    suspend fun addSpaceIfMissing(space: app.masroufy.core.Space): Boolean

    /** بيانات بلد: مجموعاتها بس (مجموعات الحساب فاضية). */
    fun dataOf(spaceId: String): FullBackupPort

    suspend fun readSpaceTransfers(): List<BackupRow>

    /** بيضيف الأزواج اللي معرّفها مش موجود بس. بيرجّع كام اتضاف. */
    suspend fun addMissingSpaceTransfers(rows: List<BackupRow>): Int
}

/** `bytes` = الحجم الفعلي بالبايت كما التخزين قراه **بعد** الكتابة؛ null = التخزين ما يقدرش يأكد (ويتقال للمستخدم). */
data class SavedBackup(val location: String, val bytes: Long?)

/** حفظ نسخة «قبل الإصلاح» **من غير نافذة نظام** — النافذة على أندرويد بترمي التطبيق للخلفية وتقطع الشغل. */
interface RepairBackupPort {
    suspend fun save(fileName: String, content: String): SavedBackup
}
