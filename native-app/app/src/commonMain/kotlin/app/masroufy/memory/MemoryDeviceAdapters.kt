package app.masroufy.memory

import app.masroufy.core.SharedMerchantEntry
import app.masroufy.core.sharedMerchantKey
import app.masroufy.port.AppLockSettingsPort
import app.masroufy.port.DeviceLockAvailability
import app.masroufy.port.DeviceLockPort
import app.masroufy.port.LockResult
import app.masroufy.port.SharedMerchantCatalogPort

/**
 * قاعدة التجار المشتركة في الذاكرة — نقل `memorySharedMerchantCatalog.ts`.
 * بتقلّد قواعد الحماية: بتكتب مش مؤكد بس، وبترفض تعديل المؤكد.
 */
class MemorySharedMerchantCatalog(seed: List<SharedMerchantEntry> = emptyList()) : SharedMerchantCatalogPort {
    private val items = LinkedHashMap<String, SharedMerchantEntry>()
    private var tick = 0

    init {
        for (e in seed) items[sharedMerchantKey(e.normalizedName)] = e
    }

    override suspend fun listChangedSince(sinceIso: String?): List<SharedMerchantEntry> =
        items.values.filter { sinceIso.isNullOrEmpty() || (it.updatedAt ?: "") > sinceIso }

    override suspend fun listConfirmed(): List<SharedMerchantEntry> = items.values.filter { it.confirmed }

    override suspend fun get(normalizedName: String): SharedMerchantEntry? = items[sharedMerchantKey(normalizedName)]

    override suspend fun save(entry: SharedMerchantEntry) {
        val key = sharedMerchantKey(entry.normalizedName)
        if (entry.confirmed) throw IllegalStateException("permission-denied: confirmed is written from the Firebase console only")
        if (items[key]?.confirmed == true) throw IllegalStateException("permission-denied: confirmed merchants are read-only")
        tick += 1
        items[key] = entry.copy(updatedAt = "2026-01-01T00:00:${tick.toString().padStart(2, '0')}.000Z")
    }
}

/** قفل جهاز وهمي: النتايج بتتسحب بالترتيب، وكل سؤال بيتسجل. */
class MemoryDeviceLock(
    private val available: DeviceLockAvailability = DeviceLockAvailability(true, "SUCCESS"),
    results: List<LockResult> = emptyList(),
) : DeviceLockPort {
    override val supported = true
    val prompts = mutableListOf<String>()
    private val queue = ArrayDeque(results)

    override suspend fun availability(): DeviceLockAvailability = available

    override suspend fun authenticate(title: String, subtitle: String?): LockResult {
        prompts += title
        return queue.removeFirstOrNull() ?: LockResult.OK
    }
}

class MemoryAppLockSettings(private var enabled: Boolean = false) : AppLockSettingsPort {
    override fun read(): Boolean = enabled

    override fun write(enabled: Boolean) {
        this.enabled = enabled
    }
}
