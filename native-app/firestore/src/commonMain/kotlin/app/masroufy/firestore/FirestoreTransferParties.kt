package app.masroufy.firestore

import app.masroufy.core.TransferParty
import app.masroufy.data.TransferCodecs
import app.masroufy.port.TransferPartyRepository

/** قرارات «زون التحويلات» (OVERRIDES §60) — مستند لكل طرف، والمعرّف مفتاحه. */
class FirestoreTransferPartyRepository(private val space: FirestoreSpace) : TransferPartyRepository {
    private val codec = TransferCodecs.transferParties

    override suspend fun listAll(): List<TransferParty> = space.select(codec)

    override suspend fun save(party: TransferParty) = space.saveAll(codec, listOf(party))

    override suspend fun remove(key: String) = space.deleteAll(codec.group, listOf(key))
}
