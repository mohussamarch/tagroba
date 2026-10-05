package app.masroufy.port

import app.masroufy.core.Id
import app.masroufy.core.ZakatFact
import app.masroufy.core.ZakatPayment
import app.masroufy.core.ZakatYear

/**
 * الزكاة (OVERRIDES §62) — مجموعات جديدة في كوتلن بس، مربوطة بالمساحة زي الباقي (§41: كل بلد قواعدها).
 * `listAll` مسموح: العدد صغير بطبيعته (أصول وديون وسنين — مش عمليات).
 */
interface ZakatFactRepository {
    suspend fun listAll(): List<ZakatFact>

    suspend fun save(fact: ZakatFact)
}

interface ZakatYearRepository {
    suspend fun listAll(): List<ZakatYear>

    suspend fun save(year: ZakatYear)

    suspend fun remove(id: Id)
}

interface ZakatPaymentRepository {
    suspend fun listByYear(yearId: Id): List<ZakatPayment>

    suspend fun listByTransactionIds(ids: List<Id>): List<ZakatPayment>

    suspend fun saveMany(payments: List<ZakatPayment>)

    suspend fun deleteMany(ids: List<Id>)
}
