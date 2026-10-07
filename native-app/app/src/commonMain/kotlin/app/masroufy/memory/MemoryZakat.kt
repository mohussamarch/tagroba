package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.ZakatFact
import app.masroufy.core.ZakatPayment
import app.masroufy.core.ZakatYear
import app.masroufy.port.ZakatFactRepository
import app.masroufy.port.ZakatPaymentRepository
import app.masroufy.port.ZakatYearRepository

/** الزكاة في الذاكرة — للاختبار (CLAUDE.md #6). الدفعات بتتكتب مع تعديل العملية في وحدة عمل واحدة ⇒ `Snapshotable`. */
class MemoryZakatFactRepository(seed: List<ZakatFact> = emptyList()) : ZakatFactRepository {
    private val items = LinkedHashMap<Id, ZakatFact>().apply { seed.forEach { put(it.subjectId, it) } }

    override suspend fun listAll(): List<ZakatFact> = items.values.toList()

    override suspend fun save(fact: ZakatFact) {
        items[fact.subjectId] = fact
    }
}

class MemoryZakatYearRepository(seed: List<ZakatYear> = emptyList()) : ZakatYearRepository, Snapshotable {
    private var items = LinkedHashMap<Id, ZakatYear>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<ZakatYear> = items.values.toList()

    override suspend fun save(year: ZakatYear) {
        items[year.id] = year
    }

    override suspend fun remove(id: Id) {
        items.remove(id)
    }

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as Map<Id, ZakatYear>)
    }
}

class MemoryZakatPaymentRepository(seed: List<ZakatPayment> = emptyList()) : ZakatPaymentRepository, Snapshotable {
    private var items = LinkedHashMap<Id, ZakatPayment>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listByYear(yearId: Id): List<ZakatPayment> = items.values.filter { it.yearId == yearId }

    override suspend fun listByTransactionIds(ids: List<Id>): List<ZakatPayment> = ids.toSet().let { s -> items.values.filter { it.transactionId in s } }

    override suspend fun saveMany(payments: List<ZakatPayment>) {
        for (p in payments) items[p.id] = p
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }

    fun all(): List<ZakatPayment> = items.values.toList()

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as Map<Id, ZakatPayment>)
    }
}
