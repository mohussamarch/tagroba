package app.masroufy.usecase

import app.masroufy.core.BACKUP_GROUPS
import app.masroufy.core.BackupRow
import app.masroufy.core.NEW_APP_BACKUP_GROUPS
import app.masroufy.core.exportedBackupData
import app.masroufy.core.FullBackupData
import app.masroufy.core.Golden
import app.masroufy.core.field
import app.masroufy.core.str
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.memory.MemoryRepairBackup
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test

/** النسخة الشاملة (الإصدار 2) ونسخة ما قبل الصيانة على `fullBackupFlow.json`. */
class FullBackupFlowGoldenTest {
    /** JSON ⇒ Map/List/أرقام/نصوص — زي اللي طبقة البيانات هتدّيه للمنفذ. */
    private fun plain(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonArray -> e.map(::plain)
        is JsonObject -> LinkedHashMap<String, Any?>().apply { for ((k, v) in e) put(k, plain(v)) }
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.booleanOrNull
            e.longOrNull != null -> e.longOrNull
            else -> e.double
        }
    }

    private fun tree(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map(::tree))
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to tree(v) })
        else -> error("نوع مش مدعوم: ${value::class}")
    }

    /** حسابات المرجع من التطبيق الحالي ما فيهاش «المستحقات» (§55) ⇒ فاضية. */
    @Suppress("UNCHECKED_CAST")
    private fun data(e: JsonElement): FullBackupData =
        BACKUP_GROUPS.associateWith { g -> if (g in NEW_APP_BACKUP_GROUPS && g !in e.jsonObject) emptyList() else (plain(e.field(g)) as List<BackupRow>) }

    @Suppress("UNCHECKED_CAST")
    private fun profile(e: JsonElement?): BackupRow? = e?.takeIf { it !is JsonNull }?.let { plain(it) as BackupRow }

    private fun fileJson(file: FullBackupFile) = Json.parseToJsonElement(file.toJsonText())

    private fun planJson(p: FullBackupPlan) = JsonObject(
        mapOf(
            "file" to fileJson(p.file),
            "lines" to JsonArray(
                // سطور «المستحقات» الفاضية مش في التطبيق الحالي
                p.lines.filter { it.key !in NEW_APP_BACKUP_GROUPS || it.incoming > 0 }.map {
                    JsonObject(
                        mapOf(
                            "key" to JsonPrimitive(it.key), "label" to JsonPrimitive(it.label), "incoming" to JsonPrimitive(it.incoming),
                            "toAdd" to JsonPrimitive(it.toAdd), "skipped" to JsonPrimitive(it.skipped), "note" to tree(it.note),
                        ),
                    )
                },
            ),
            "profile" to JsonObject(mapOf("incoming" to JsonPrimitive(p.profile.incoming), "toAdd" to JsonPrimitive(p.profile.toAdd))),
            "totalToAdd" to JsonPrimitive(p.totalToAdd),
            "warnings" to tree(p.warnings),
        ),
    )

    @Test
    fun fullBackup() {
        Golden.check("fullBackupFlow", "fullBackup") { input ->
            val port = MemoryFullBackup(data(input.field("account")), profile(input.jsonObject["accountProfile"]))
            val backup = FullBackup(port)
            val action = input.field("action")
            runBlocking {
                when (action.field("kind").str) {
                    "create" -> JsonObject(mapOf("file" to fileJson(backup.create("2026-09-22T10:00:00.000Z"))))
                    "plan" -> JsonObject(mapOf("plan" to planJson(backup.plan(action.field("raw").str))))
                    else -> {
                        val plan = backup.plan(action.field("raw").str)
                        val outcome = backup.apply(plan.file)
                        JsonObject(
                            mapOf(
                                "plan" to planJson(plan),
                                "outcome" to JsonObject(mapOf("added" to tree(outcome.added.filter { (k, v) -> k !in NEW_APP_BACKUP_GROUPS || v > 0 }), "totalAdded" to JsonPrimitive(outcome.totalAdded))),
                                "storedData" to tree(exportedBackupData(port.read())),
                                "storedProfile" to tree(port.readProfile()),
                            ),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun verifiedBackup() {
        Golden.check("fullBackupFlow", "verifiedBackup") { input ->
            val backup = MemoryRepairBackup(truncate = input.field("truncate").jsonPrimitive.boolean)
            runBlocking {
                val saved = saveVerifiedBackup(
                    backup, FixedClock("2026-09-22T10:00:00.123Z"), input.field("kind").str, input.field("documents").jsonArray.map(::plain),
                )
                JsonObject(
                    mapOf(
                        "saved" to JsonObject(
                            mapOf(
                                "location" to JsonPrimitive(saved.location), "bytes" to tree(saved.bytes),
                                "fileName" to JsonPrimitive(saved.fileName), "expectedBytes" to JsonPrimitive(saved.expectedBytes),
                            ),
                        ),
                        "files" to JsonArray(backup.files.entries.map { (name, content) -> JsonArray(listOf(JsonPrimitive(name), JsonPrimitive(content))) }),
                    ),
                )
            }
        }
    }
}
