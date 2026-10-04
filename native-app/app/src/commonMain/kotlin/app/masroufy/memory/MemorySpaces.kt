package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.Space
import app.masroufy.port.ActiveSpaceStore
import app.masroufy.port.MerchantCategoryRepository
import app.masroufy.port.SpaceRegistry

/** سجل المساحات والمساحة الشغالة وتصنيف التاجر جوه البلد — في الذاكرة للاختبار (OVERRIDES §41 · §64). */
class MemorySpaceRegistry(seed: List<Space> = emptyList()) : SpaceRegistry {
    private val items = LinkedHashMap<String, Space>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<Space> = items.values.toList()

    override suspend fun addIfMissing(space: Space): Boolean {
        if (space.id in items) return false
        items[space.id] = space
        return true
    }

    override suspend fun save(space: Space) {
        items[space.id] = space
    }
}

class MemoryActiveSpaceStore(private var value: String? = null) : ActiveSpaceStore {
    override fun read(): String? = value

    override fun write(spaceId: String) {
        value = spaceId
    }
}

class MemoryMerchantCategoryRepository(seed: Map<Id, Id> = emptyMap()) : MerchantCategoryRepository, Snapshotable {
    private var items = LinkedHashMap(seed)

    override suspend fun listAll(): Map<Id, Id> = LinkedHashMap(items)

    override suspend fun set(merchantId: Id, categoryId: Id?) {
        if (categoryId == null) items.remove(merchantId) else items[merchantId] = categoryId
    }

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as Map<Id, Id>)
    }
}
