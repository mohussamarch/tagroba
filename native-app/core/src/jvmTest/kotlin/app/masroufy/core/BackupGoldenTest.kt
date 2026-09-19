package app.masroufy.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test

/** النسخة الشاملة: الفحص والدمج وبصمة السلامة (native-app/golden/backup.json). */
class BackupGoldenTest {
    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("backup", fn) { json(run(it)) }

    /** JSON ⇒ بيانات مرنة (Map/List/Long/Double/…) زي اللي طبقة البيانات هتدّيها. */
    private fun plain(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonArray -> e.map(::plain)
        is JsonObject -> LinkedHashMap(e.mapValues { (_, v) -> plain(v) })
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.booleanOrNull
            e.longOrNull != null -> e.longOrNull
            else -> e.double
        }
    }
    /** بيانات مرنة ⇒ JSON للمقارنة. */
    private fun back(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to back(x) })
        is List<*> -> JsonArray(v.map(::back))
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is Double -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }
    @Suppress("UNCHECKED_CAST")
    private fun data(e: JsonElement) = plain(e) as FullBackupData

    @Test fun validation() {
        check("checkFullBackupData") { checkFullBackupData(plain(it.field("data"))); true }
        check("checkBackupFinance") { checkBackupFinance(data(it.field("data"))); true }
        check("checkBackupProfile") { checkBackupProfile(if (it is JsonPrimitive && it.content == "__absent__") null else plain(it)); true }
    }

    @Test fun checksum() {
        check("canonicalBackup") { canonicalBackup(plain(it)) }
        check("sha256") { Sha256.hex(it.str) }
        check("backupChecksum") {
            val raw = it.field("profile")
            val absent = raw is JsonPrimitive && raw.content == "__absent__"
            val profile = if (absent) null else plain(raw)
            val base = Golden.cases("backup", "normalizeBudgetIds").first()["in"]!!
            val text = backupChecksumText(data(base), profile, hasProfile = !absent)
            back(mapOf("length" to text.length.toLong(), "checksum" to backupChecksum(text)))
        }
    }

    @Test fun mergeAndBudgets() {
        check("normalizeBudgetIds") { back(normalizeBudgetIds(data(it))) }
        @Suppress("UNCHECKED_CAST")
        fun rows(e: JsonElement) = plain(e) as List<BackupRow>
        check("pointLinesAtLiveBudgets") { back(pointLinesAtLiveBudgets(rows(it.field("additions")), rows(it.field("live")))) }
        check("mergeFullBackup") { back(mergeFullBackup(data(it.field("incoming")), data(it.field("existing")))) }
        check("groups") { BACKUP_GROUPS }
    }

    @Test fun backupBaseIsTheSameAccount() {
        // الحساب الوهمي في حالة «ok» لازم يعدّي الفحصين — لو اتكسر، كل الحالات التانية بتفقد معناها
        val ok = Golden.cases("backup", "checkFullBackupData").first { it["in"]!!.jsonObject["name"]!!.str == "ok" }
        checkFullBackupData(plain(ok["in"]!!.field("data")))
    }
}
