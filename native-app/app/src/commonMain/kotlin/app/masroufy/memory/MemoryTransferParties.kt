package app.masroufy.memory

import app.masroufy.core.TransferParty
import app.masroufy.port.TransferPartyRepository

/** `Snapshotable`: القرار وتصحيح العمليات بيتكتبوا مع بعض — لو حاجة فشلت يرجعوا مع بعض. */
class MemoryTransferPartyRepository(seed: List<TransferParty> = emptyList()) : TransferPartyRepository, Snapshotable {
    private var items = LinkedHashMap<String, TransferParty>().apply { seed.forEach { put(it.key, it) } }

    override suspend fun listAll(): List<TransferParty> = items.values.toList()

    override suspend fun save(party: TransferParty) {
        items[party.key] = party
    }

    override suspend fun remove(key: String) {
        items.remove(key)
    }

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as Map<String, TransferParty>)
    }
}
