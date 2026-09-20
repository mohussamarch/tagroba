package app.masroufy.port

import app.masroufy.core.Budget
import app.masroufy.core.CategoryBudget
import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.Obligation
import app.masroufy.core.PersonAllocation
import app.masroufy.core.Settlement
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet

/**
 * واجهات المستودعات (ports) — **تعريفات بلا تنفيذ**، نقل `src/application/ports/repositories.ts`.
 * لكل واجهة تنفيذان: واحد للتشغيل وواحد في الذاكرة للاختبار (CLAUDE.md #6).
 *
 * **قيد مشتق من فايربيز:** كل استعلام محدود. مفيش `listAll()` على العمليات — عن قصد.
 *
 * **المساحة (space) مش في التوقيعات دي** (OVERRIDES §41): المستودع بيتعمل **مربوط ببلد**،
 * فحالات الاستخدام والشاشات ما تعرفش عن الموضوع حاجة.
 */

interface TransactionRepository {
    suspend fun listByDateRange(fromIso: String, toIso: String): List<Transaction>

    /** اللي اتسجل بعد لحظة معينة (`createdAt`) — محدود بالوقت؛ لقواعد المشاريع (OVERRIDES §34). */
    suspend fun listCreatedAfter(iso: String): List<Transaction>

    suspend fun listByBatch(batchId: Id): List<Transaction>

    suspend fun findByIds(ids: List<Id>): List<Transaction>

    suspend fun saveMany(transactions: List<Transaction>)

    /** تعديل حقول محددة بس — الباقي زي ما هو. */
    suspend fun update(id: Id, patch: TransactionPatch)

    suspend fun deleteMany(ids: List<Id>)
}

/**
 * تعديل جزئي للعملية. `null` في أي حقل = **ما يتغيرش** (مش «امسحه»)، عشان كده الحقول
 * اللي ممكن تتمسح بتتعلم بـ`clear…`. جافاسكربت بتعمل ده بـ`Partial<Transaction>`،
 * وكوتلن محتاجة تصريح عشان الفرق بين «ما يتغيرش» و«خليه فاضي» يفضل واضح.
 */
data class TransactionPatch(
    val amountMinor: Long? = null,
    val originalAmountMinor: Long? = null,
    val economicKind: app.masroufy.core.EconomicKind? = null,
    val economicKindConfirmed: Boolean? = null,
    val categoryId: Id? = null,
    val clearCategoryId: Boolean = false,
    val categoryConfirmed: Boolean? = null,
    val reviewState: app.masroufy.core.ReviewState? = null,
    val merchantId: Id? = null,
    val note: String? = null,
    val clearNote: Boolean = false,
    val walletId: Id? = null,
    val excludedFromBudget: Boolean? = null,
    val isCashTagged: Boolean? = null,
    val updatedAt: String? = null,
)

interface SourceRecordRepository {
    suspend fun listByBatch(batchId: Id): List<SourceRecord>

    /** كل السجلات بتاعة هوية حساب — أساس فحص التكرار. */
    suspend fun listByAccountIdentity(accountIdentity: String): List<SourceRecord>

    suspend fun listByTransactionIds(ids: List<Id>): List<SourceRecord>

    suspend fun saveMany(records: List<SourceRecord>)

    suspend fun deleteMany(ids: List<Id>)
}

interface ImportBatchRepository {
    suspend fun findById(id: Id): ImportBatch?

    /** الدرجة الأولى من منع التكرار: بصمة ملف اتستورد قبل كده. */
    suspend fun findByFileHash(fileHash: String): ImportBatch?

    suspend fun listRecent(limit: Int): List<ImportBatch>

    suspend fun save(batch: ImportBatch)

    suspend fun updateState(id: Id, state: ImportBatchState)
}

interface WalletRepository {
    suspend fun listAll(): List<Wallet>

    suspend fun findById(id: Id): Wallet?

    suspend fun save(wallet: Wallet)
}

interface ObligationRepository {
    suspend fun listByPerson(personId: Id): List<Obligation>

    suspend fun listByTransactionIds(ids: List<Id>): List<Obligation>

    suspend fun saveMany(obligations: List<Obligation>)

    suspend fun deleteMany(ids: List<Id>)
}

interface SettlementRepository {
    suspend fun listByObligations(obligationIds: List<Id>): List<Settlement>

    suspend fun listByTransactionIds(ids: List<Id>): List<Settlement>

    suspend fun saveMany(settlements: List<Settlement>)

    suspend fun deleteMany(ids: List<Id>)
}

interface AllocationRepository {
    suspend fun listByTransactionIds(ids: List<Id>): List<PersonAllocation>

    suspend fun saveMany(allocations: List<PersonAllocation>)

    suspend fun deleteMany(ids: List<Id>)
}

/** الميزانيات — فترة واحدة = ميزانية واحدة، والقراية بمفتاح الفترة. */
interface BudgetRepository {
    suspend fun findByPeriod(periodKey: String): Budget?

    suspend fun save(budget: Budget)

    /** مسح السقف الإجمالي وسقوف تصنيفاته مع بعض. */
    suspend fun remove(periodKey: String)

    suspend fun listCategoryBudgets(budgetId: Id): List<CategoryBudget>

    suspend fun saveCategoryBudget(line: CategoryBudget)

    suspend fun removeCategoryBudget(id: Id)
}
