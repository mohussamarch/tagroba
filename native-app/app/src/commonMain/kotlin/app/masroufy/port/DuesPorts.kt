package app.masroufy.port

import app.masroufy.core.DebtTerms
import app.masroufy.core.Id
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry

/**
 * «المستحقات» — الجمعيات والأقساط ومواعيد الديون (OVERRIDES §50). ميزة جديدة في كوتلن بس،
 * فمالهاش مقابل في التطبيق الحالي ولا في نسخته الاحتياطية.
 * `listAll` مسموح هنا: العدد صغير بطبيعته (جمعيات وخطط أقساط، مش عمليات).
 */
interface RoscaRepository {
    suspend fun listAll(): List<Rosca>

    suspend fun save(rosca: Rosca)
}

interface RoscaEntryRepository {
    suspend fun listByRosca(roscaId: Id): List<RoscaEntry>

    suspend fun listByTransactionIds(ids: List<Id>): List<RoscaEntry>

    suspend fun saveMany(entries: List<RoscaEntry>)

    suspend fun deleteMany(ids: List<Id>)
}

interface InstallmentPlanRepository {
    suspend fun listAll(): List<InstallmentPlan>

    suspend fun save(plan: InstallmentPlan)
}

interface InstallmentPaymentRepository {
    suspend fun listByPlan(planId: Id): List<InstallmentPayment>

    suspend fun listByTransactionIds(ids: List<Id>): List<InstallmentPayment>

    suspend fun saveMany(payments: List<InstallmentPayment>)

    suspend fun deleteMany(ids: List<Id>)
}

interface DebtTermsRepository {
    suspend fun listAll(): List<DebtTerms>

    suspend fun save(terms: DebtTerms)

    suspend fun remove(obligationId: Id)
}
