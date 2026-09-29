package app.masroufy.usecase

import app.masroufy.core.jsonStringifyPretty
import app.masroufy.port.Clock
import app.masroufy.port.RepairBackupPort

/**
 * نسخة قبل أي كتابة صيانة (إصلاح، تنظيف، تراجع) — نقل `verifiedBackup.ts` (HANDOVER §36).
 * بتتحفظ من غير نافذة، والحجم اللي اتقرا من القرص لازم يساوي عدد بايتات UTF-8 المتوقع **بالظبط**،
 * وإلا بترمي قبل ما حالة الاستخدام تلمس أي مستند. `bytes = null` = تخزين ما يقدرش يأكد، ويتقال ده للمستخدم.
 */
data class VerifiedBackup(val location: String, val bytes: Long?, val fileName: String, val expectedBytes: Long)

suspend fun saveVerifiedBackup(backup: RepairBackupPort, clock: Clock, kind: String, documents: List<Any?>): VerifiedBackup {
    val savedAt = clock.nowIso()
    val content = jsonStringifyPretty(linkedMapOf("app" to "masroufy", "kind" to kind, "savedAt" to savedAt, "documents" to documents))
    val fileName = "masroufy-$kind-${savedAt.replace(':', '-').replace('.', '-')}.json"
    val expectedBytes = content.encodeToByteArray().size.toLong()
    val saved = backup.save(fileName, content)
    if (saved.bytes != null && saved.bytes != expectedBytes) {
        throw IllegalStateException("النسخة الاحتياطية اتحفظت ناقصة (${saved.bytes} من $expectedBytes بايت) — ما اتلمسش ولا مستند")
    }
    return VerifiedBackup(saved.location, saved.bytes, fileName, expectedBytes)
}
