package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.SharedMerchantEntry
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAppLockSettings
import app.masroufy.memory.MemoryDeviceLock
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemorySharedMerchantCatalog
import app.masroufy.memory.MemorySyncCursor
import app.masroufy.port.DeviceLockAvailability
import app.masroufy.port.LockResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test

/** قاعدة التجار المشتركة + قفل التطبيق على `settingsFlow.json`. */
class SettingsFlowGoldenTest {
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.orNull(name: String): JsonElement? = jsonObject[name]?.takeIf { it !is JsonNull }

    private fun entry(e: JsonElement) = SharedMerchantEntry(
        e.field("normalizedName").str, e.field("displayName").str, e.field("aliases").jsonArray.map { it.str },
        e.orNull("categoryId")?.str, e.field("confirmed").jsonPrimitive.boolean, e.orNull("updatedAt")?.str,
    )

    private fun entryJson(e: SharedMerchantEntry) = JsonObject(
        buildMap {
            put("normalizedName", json(e.normalizedName)); put("displayName", json(e.displayName)); put("aliases", json(e.aliases))
            put("categoryId", nullable(e.categoryId)); put("confirmed", json(e.confirmed)); e.updatedAt?.let { put("updatedAt", json(it)) }
        },
    )

    @Test
    fun sharedMerchants() {
        Golden.check("settingsFlow", "sharedMerchants") { input ->
            val catalog = MemorySharedMerchantCatalog(input.field("remote").jsonArray.map(::entry))
            val merchants = MemoryMerchantRepository(input.field("account").jsonArray.map(ReferenceJson::merchant))
            val cursor = MemorySyncCursor(input.orNull("since")?.str)
            val confirmedCursor = MemorySyncCursor(input.orNull("confirmedAt")?.str)
            val shared = SharedMerchants(
                SharedMerchantsDeps(
                    catalog = catalog, merchants = merchants,
                    baseline = input.field("baseline").jsonArray.map(ReferenceJson::merchant),
                    treeCategoryIds = input.field("tree").jsonArray.map { it.str }.toSet(),
                    cursor = cursor, confirmedCursor = confirmedCursor, clock = FixedClock("2026-09-22T10:00:00.000Z"),
                ),
            )
            runBlocking {
                val out = input.field("steps").jsonArray.map { step ->
                    if (step.field("kind").str == "sync") {
                        val r = shared.sync()
                        EntityJson.obj("changes" to r.changes, "added" to r.added, "filled" to r.filled)
                    } else {
                        json(
                            shared.contribute(
                                EconomicKind.fromWire(step.field("economicKind").str), Direction.fromWire(step.field("observedDirection").str),
                                step.orNull("rawMerchantName")?.str, step.field("categoryId").str,
                            ).wire,
                        )
                    }
                }
                JsonObject(
                    mapOf(
                        "steps" to JsonArray(out),
                        "cursorAfter" to nullable(cursor.read()),
                        "confirmedCursorAfter" to nullable(confirmedCursor.read()),
                        "storedMerchants" to JsonArray(merchants.listAll().map(ReferenceJson::merchantJson)),
                        "catalogAfter" to JsonArray(catalog.listChangedSince(null).map(::entryJson)),
                    ),
                )
            }
        }
    }

    @Test
    fun appLock() {
        Golden.check("settingsFlow", "appLock") { input ->
            val availability = input.orNull("availability")?.let { DeviceLockAvailability(it.field("available").jsonPrimitive.boolean, it.field("code").str) }
            val results = input.orNull("results")?.jsonArray?.map { r -> LockResult.entries.first { it.wire == r.str } }.orEmpty()
            val device = if (availability != null) MemoryDeviceLock(availability, results) else MemoryDeviceLock(results = results)
            val nowMs = input.field("now").jsonPrimitive.long
            val lock = AppLock(device, MemoryAppLockSettings(input.field("enabled").jsonPrimitive.boolean)) { nowMs }
            fun change(c: LockChange) = when (c) {
                LockChange.Ok -> JsonObject(mapOf("ok" to json(true)))
                is LockChange.Refused -> JsonObject(mapOf("ok" to json(false), "message" to json(c.message)))
            }
            runBlocking {
                val out = input.field("steps").jsonArray.map { step ->
                    when (step.field("kind").str) {
                        "supported" -> json(lock.supported)
                        "isEnabled" -> json(lock.isEnabled())
                        "needsUnlock" -> json(lock.needsUnlock(step.orNull("hiddenAt")?.jsonPrimitive?.long))
                        "enable" -> change(lock.enable())
                        "disable" -> change(lock.disable())
                        "unlock" -> lock.unlock().let { JsonObject(mapOf("result" to json(it.result.wire), "message" to nullable(it.message))) }
                        else -> json(lock.releaseIfDeviceHasNoLock())
                    }
                }
                JsonObject(mapOf("steps" to JsonArray(out), "prompts" to json(device.prompts.toList())))
            }
        }
    }
}
