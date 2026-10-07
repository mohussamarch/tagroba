package app.masroufy.firestore

import app.masroufy.core.Id
import app.masroufy.core.ZakatFact
import app.masroufy.core.ZakatPayment
import app.masroufy.core.ZakatYear
import app.masroufy.data.ZakatCodecs
import app.masroufy.port.ZakatFactRepository
import app.masroufy.port.ZakatPaymentRepository
import app.masroufy.port.ZakatYearRepository

/** الزكاة (OVERRIDES §62) — مجموعات كوتلن بس، والقواعد بتسمح بيها من غير نشر (`ZakatCodecs`). */
class FirestoreZakatFactRepository(private val space: FirestoreSpace) : ZakatFactRepository {
    private val codec = ZakatCodecs.zakatFacts

    override suspend fun listAll(): List<ZakatFact> = space.select(codec)

    override suspend fun save(fact: ZakatFact) = space.saveAll(codec, listOf(fact))
}

class FirestoreZakatYearRepository(private val space: FirestoreSpace) : ZakatYearRepository {
    private val codec = ZakatCodecs.zakatYears

    override suspend fun listAll(): List<ZakatYear> = space.select(codec)

    override suspend fun save(year: ZakatYear) = space.saveAll(codec, listOf(year))

    override suspend fun remove(id: Id) = space.deleteAll(codec.group, listOf(id))
}

class FirestoreZakatPaymentRepository(private val space: FirestoreSpace) : ZakatPaymentRepository {
    private val codec = ZakatCodecs.zakatPayments

    override suspend fun listByYear(yearId: Id): List<ZakatPayment> = space.select(codec, DocQuery(listOf(Cond.Eq("yearId", yearId))))

    override suspend fun listByTransactionIds(ids: List<Id>): List<ZakatPayment> = space.findIn(codec, "transactionId", ids)

    override suspend fun saveMany(payments: List<ZakatPayment>) = space.saveAll(codec, payments)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}
