package app.masroufy.firestore

import app.masroufy.core.Id
import app.masroufy.core.Obligation
import app.masroufy.core.PersonAllocation
import app.masroufy.core.Settlement
import app.masroufy.data.LedgerCodecs
import app.masroufy.port.AllocationRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.SettlementRepository

/**
 * الديون وتسوياتها وتخصيصات الأشخاص — نقل `peopleRepositories.ts`.
 * ⚠️ **التسوية نفسها** (قراية وفحص وكتابة ذرّيًا) مش هنا — دي `SettlementWriter` ومحتاجة معاملة على السيرفر
 * وبتستنى النت (OVERRIDES §51). المستودع ده للقراية والكتابة العادية بس.
 */
class FirestoreObligationRepository(private val space: FirestoreSpace) : ObligationRepository {
    private val codec = LedgerCodecs.obligations

    override suspend fun listByPerson(personId: Id): List<Obligation> =
        codec.decodeAll(space.collection(codec.group).where { "personId" equalTo personId }.get(space.sourceFor(codec.group)))

    override suspend fun listByTransactionIds(ids: List<Id>): List<Obligation> = space.findIn(codec, "originTransactionId", ids)

    override suspend fun saveMany(obligations: List<Obligation>) = space.saveAll(codec, obligations)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}

class FirestoreSettlementRepository(private val space: FirestoreSpace) : SettlementRepository {
    private val codec = LedgerCodecs.settlements

    override suspend fun listByObligations(obligationIds: List<Id>): List<Settlement> = space.findIn(codec, "obligationId", obligationIds)

    override suspend fun listByTransactionIds(ids: List<Id>): List<Settlement> = space.findIn(codec, "transactionId", ids)

    override suspend fun saveMany(settlements: List<Settlement>) = space.saveAll(codec, settlements)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}

class FirestoreAllocationRepository(private val space: FirestoreSpace) : AllocationRepository {
    private val codec = LedgerCodecs.allocations

    override suspend fun listByTransactionIds(ids: List<Id>): List<PersonAllocation> = space.findIn(codec, "transactionId", ids)

    override suspend fun saveMany(allocations: List<PersonAllocation>) = space.saveAll(codec, allocations)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}
