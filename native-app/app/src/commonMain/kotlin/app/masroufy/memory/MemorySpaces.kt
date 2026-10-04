package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.Space
import app.masroufy.core.SpaceTransfer
import app.masroufy.port.ActiveSpaceStore
import app.masroufy.port.MerchantCategoryRepository
import app.masroufy.port.SpaceLegWrite
import app.masroufy.port.SpaceRegistry
import app.masroufy.port.SpaceTransferRepository
import app.masroufy.port.SpaceTransferWriter

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

class MemorySpaceTransferRepository(seed: List<SpaceTransfer> = emptyList()) : SpaceTransferRepository, Snapshotable {
    private var items = LinkedHashMap<Id, SpaceTransfer>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<SpaceTransfer> = items.values.toList()

    internal fun put(pair: SpaceTransfer) {
        items[pair.id] = pair
    }

    internal fun remove(id: Id) {
        items.remove(id)
    }

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as Map<Id, SpaceTransfer>)
    }
}

/**
 * الكتابة الذرّية في الذاكرة: لقطة من الأزواج وعمليات كل بلد قبل البدء، وأي فشل في النص ⇒ كله يرجع.
 * [failAfterFirstLeg] للاختبار بس: بيقع بعد ما الرجل الأولى اتكتبت — عشان نثبت إن مفيش نص زوج بيفضل.
 */
class MemorySpaceTransferWriter(
    private val pairs: MemorySpaceTransferRepository,
    private val transactions: (spaceId: String) -> MemoryTransactionRepository,
    var failAfterFirstLeg: Boolean = false,
) : SpaceTransferWriter {
    override suspend fun link(pair: SpaceTransfer, legs: List<SpaceLegWrite>) = atomic(legs) {
        pairs.put(pair)
        writeLegs(legs)
    }

    override suspend fun unlink(pair: SpaceTransfer, legs: List<SpaceLegWrite>) = atomic(legs) {
        pairs.remove(pair.id)
        writeLegs(legs)
    }

    private suspend fun writeLegs(legs: List<SpaceLegWrite>) {
        legs.forEachIndexed { i, leg ->
            transactions(leg.spaceId).saveMany(listOf(leg.transaction))
            if (failAfterFirstLeg && i == 0) throw IllegalStateException("انقطع في النص (اختبار)")
        }
    }

    private suspend fun atomic(legs: List<SpaceLegWrite>, work: suspend () -> Unit) {
        MemoryUnitOfWork(listOf(pairs) + legs.map { transactions(it.spaceId) }.distinct()).run(work)
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
