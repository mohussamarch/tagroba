package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.Wallet
import app.masroufy.port.WalletRepository

/** محافظ في الذاكرة — نقل `memoryWalletRepository.ts`. */
class MemoryWalletRepository(seed: List<Wallet> = emptyList()) : WalletRepository {
    private val items = LinkedHashMap<Id, Wallet>()

    init {
        for (w in seed) items[w.id] = w
    }

    override suspend fun listAll(): List<Wallet> = items.values.toList()

    override suspend fun findById(id: Id): Wallet? = items[id]

    override suspend fun save(wallet: Wallet) {
        items[wallet.id] = wallet
    }
}
