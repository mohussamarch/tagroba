package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.Transaction
import app.masroufy.port.AllocationRepository
import app.masroufy.port.AssetRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.DebtTermsRepository
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.MerchantRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.RecurringRepository
import app.masroufy.port.RepairBackupPort
import app.masroufy.port.RoscaRepository
import app.masroufy.port.RuleRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork
import app.masroufy.port.WalletRepository

/**
 * r06-delete (OVERRIDES §118-1 — قاعدة المالك): مسح أي حاجة بلوحة واحدة مشتركة.
 * - **مالهاش عمليات** ⇒ [RemoveChoice.DELETE] بس («امسح <الاسم>؟»).
 * - **ليها عمليات** ⇒ «<الاسم> عليه N عملية» والاختيارات: [RemoveChoice.ARCHIVE] (لو الحاجة ليها أرشفة) · [RemoveChoice.KEEP_OPERATIONS]
 *   (العمليات بتفضل من غير ربط وعليها علامة [Transaction.sourceRemoved] ⇒ المجاميع ما بتتغيرش) · [RemoveChoice.WITH_OPERATIONS]
 *   (هي وعملياتها) · [RemoveChoice.MOVE_OPERATIONS] (التصنيف بس: العمليات تتنقل لتصنيف تاني بدل ما تتمسح).
 * - **كل طريق ليه «تراجع»** ([RemovalDone.undo]) بيرجّع نفس المستندات بنفس معرّفاتها بالظبط. مسح العمليات قبله نسخة متأكدة الحجم
 *   (`saveVerifiedBackup`) لو الجهاز يقدر يحفظ — زي «راجع المكرر».
 */
enum class RemoveChoice { DELETE, ARCHIVE, KEEP_OPERATIONS, WITH_OPERATIONS, MOVE_OPERATIONS }

/** تصنيف ممكن العمليات تتنقل له («انقل العمليات لتصنيف تاني»). */
data class RemovalTarget(val id: Id, val name: String)

/** اللي اللوحة بتعرضه — **من غير أي كتابة**. */
data class RemovalAsk(
    val name: String,
    /** عدد العمليات المربوطة (0 ⇒ مسح على طول بتأكيد واحد). */
    val linkedCount: Int,
    val choices: List<RemoveChoice>,
    /** «امسحه وسيب العمليات» مقفول وده السبب بكلام المالك (مثلًا ديون مفتوحة). null = مفتوح. */
    val keepBlocked: String? = null,
    /** المسح كله مرفوض وده السبب (مثلًا تصنيف تحته فرعيات). */
    val refused: String? = null,
    val targets: List<RemovalTarget> = emptyList(),
)

/** اللي اتعمل — و«تراجع» بيرجّعه زي ما كان. */
class RemovalDone(val choice: RemoveChoice, val name: String, val backup: VerifiedBackup? = null, private val undoWork: suspend () -> Unit) {
    suspend fun undo() = undoWork()
}

/** لوحة المسح الواحدة بتكلّم أي حاجة من دول. */
interface EntityRemover {
    suspend fun ask(id: Id): RemovalAsk

    /** [moveTo] لـ[RemoveChoice.MOVE_OPERATIONS] بس. */
    suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id? = null): RemovalDone
}

/** الاختيارات لما فيه عمليات: الأرشفة لو موجودة + الاتنين التانيين. */
internal fun linkedChoices(archive: Boolean, withOps: Boolean = true): List<RemoveChoice> = buildList {
    if (archive) add(RemoveChoice.ARCHIVE)
    add(RemoveChoice.KEEP_OPERATIONS)
    if (withOps) add(RemoveChoice.WITH_OPERATIONS)
}

data class RemovalDeps(
    val txns: TransactionRepository,
    val sources: SourceRecordRepository,
    val uow: UnitOfWork,
    val clock: Clock,
    /** نفس روابط «امسح» للعملية (المشاريع · الأحداث · الوسوم · الأقساط · الجمعيات · الزكاة · الاستثمار · الأشخاص). */
    val links: RevertLinkDeps,
    val allocations: AllocationRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val wallets: WalletRepository,
    val assets: AssetRepository,
    val roscas: RoscaRepository,
    val recurring: RecurringRepository,
    val people: PersonRepository,
    val merchants: MerchantRepository,
    val incomeSources: IncomeSourceRepository,
    val categories: CategoryRepository,
    val rules: RuleRepository? = null,
    val debtTerms: DebtTermsRepository? = null,
    /** ربط مرسلي رسايل البنك بالحسابات (على الجهاز) — null = مفيش صندوق. */
    val smsInbox: SmsInboxPort? = null,
    val spaceId: String = "",
    /** نسخة قبل مسح العمليات — null = الجهاز ما يقدرش يحفظ (التراجع في الذاكرة بس). */
    val backup: RepairBackupPort? = null,
    /** r06-links2: خطط الادخار وإيداعاتها (مسح الخطة). null ⇒ اللوحة بتقول «مش موجودة». */
    val goals: app.masroufy.port.SavingsGoalRepository? = null,
    val goalContributions: app.masroufy.port.GoalContributionRepository? = null,
)

/** العمليات اللي اتمسحت وكل اللي كان بيشاور عليها — للتراجع بالظبط. */
internal class ErasedOps(
    val rows: List<Transaction> = emptyList(),
    val docs: List<Pair<String, List<Any>>> = emptyList(),
    val partners: List<Transaction> = emptyList(),
    val sources: List<app.masroufy.core.SourceRecord> = emptyList(),
    val backup: VerifiedBackup? = null,
)

/** أدوات العمليات المشتركة بين كل الحاجات. */
internal class OpsKit(private val d: RemovalDeps) {
    suspend fun all(): List<Transaction> = d.txns.listByDateRange("0000-01-01", "9999-12-31T23:59:59Z")

    /** «امسحه وسيب العمليات»: العملية بتفضل (نفس النوع والمبلغ) — الربط بيتفك ([unlink]) وعليها علامة المصدر. بيرجّع اللي كان قبل. */
    suspend fun keep(rows: List<Transaction>, marker: String, unlink: (Transaction) -> Transaction = { it }): List<Transaction> {
        if (rows.isEmpty()) return rows
        val now = d.clock.nowIso()
        d.txns.saveMany(rows.map { unlink(it).copy(sourceRemoved = marker, updatedAt = now) })
        return rows
    }

    /** «تراجع» عن [keep]. */
    suspend fun unkeep(before: List<Transaction>) {
        if (before.isNotEmpty()) d.txns.saveMany(before)
    }

    /** «امسحه هو وعملياته»: العمليات وكل اللي بيشاور عليها بيتشال ذرّيًا (بعد نسخة متأكدة لو ممكن). */
    suspend fun erase(ids: List<Id>, kind: String): ErasedOps {
        val unique = ids.distinct()
        if (unique.isEmpty()) return ErasedOps()
        val rows = d.txns.findByIds(unique)
        val l = d.links
        val set = unique.toSet()
        val sources = d.sources.listByTransactionIds(unique)
        val tags = l.transactionTags.listByTransactionIds(unique)
        val projects = unique.flatMap { l.projectLinks.listByTransaction(it) }
        val events = l.eventLinks.listByTransactionIds(unique)
        val allocations = d.allocations.listByTransactionIds(unique)
        val settlements = d.settlements.listByTransactionIds(unique)
        val entries = l.roscaEntries.listByTransactionIds(unique)
        val payments = l.installmentPayments.listByTransactionIds(unique)
        val zakat = l.zakatPayments.listByTransactionIds(unique)
        val personLinks = l.personLinks?.listByTransactionIds(unique).orEmpty()
        val lots = l.assetLots.listAll().filter { it.transactionId in set }
        val sales = l.assetSales.listAll().filter { it.transactionId in set }
        val origins = d.obligations.listByTransactionIds(unique)
        val plans = l.installmentPlans.listAll().filter { it.receivedTransactionId in set }
        val backup = d.backup?.let { port ->
            val bySource = sources.groupBy { it.transactionId }
            saveVerifiedBackup(port, d.clock, "remove-$kind", rows.map { mergeDoc("removed", it, bySource[it.id].orEmpty()) })
        }
        val now = d.clock.nowIso()
        return d.uow.run {
            if (tags.isNotEmpty()) l.transactionTags.deleteMany(tags.map { it.id })
            if (projects.isNotEmpty()) l.projectLinks.deleteMany(projects.map { it.id })
            if (events.isNotEmpty()) l.eventLinks.deleteMany(events.map { it.id })
            if (allocations.isNotEmpty()) d.allocations.deleteMany(allocations.map { it.id })
            if (settlements.isNotEmpty()) d.settlements.deleteMany(settlements.map { it.id })
            if (entries.isNotEmpty()) l.roscaEntries.deleteMany(entries.map { it.id })
            if (payments.isNotEmpty()) l.installmentPayments.deleteMany(payments.map { it.id })
            if (zakat.isNotEmpty()) l.zakatPayments.deleteMany(zakat.map { it.id })
            if (personLinks.isNotEmpty()) l.personLinks?.deleteMany(personLinks.map { it.transactionId })
            // الأصل أو الدين أو الخطة بيفضلوا (لو مش هما اللي بيتمسحوا) — من غير عملية بيشاوروا عليها
            if (lots.isNotEmpty()) l.assetLots.saveMany(lots.map { it.copy(transactionId = null) })
            if (sales.isNotEmpty()) l.assetSales.saveMany(sales.map { it.copy(transactionId = null) })
            if (origins.isNotEmpty()) d.obligations.saveMany(origins.map { it.copy(originTransactionId = null) })
            for (p in plans) l.installmentPlans.save(p.copy(receivedTransactionId = null))
            if (sources.isNotEmpty()) d.sources.saveMany(sources.map { it.copy(transactionId = null, ownerDeletedAt = now) })
            val partners = unlinkBeforeDelete(d.txns, d.clock, unique)
            d.txns.deleteMany(unique)
            ErasedOps(
                rows, listOf("tags" to tags, "projects" to projects, "events" to events, "allocations" to allocations, "settlements" to settlements,
                    "entries" to entries, "payments" to payments, "zakat" to zakat, "personLinks" to personLinks, "lots" to lots, "sales" to sales,
                    "origins" to origins, "plans" to plans),
                partners, sources, backup,
            )
        }
    }

    /** «تراجع» عن [erase]: كل مستند بيرجع زي ما كان. */
    @Suppress("UNCHECKED_CAST")
    suspend fun restore(e: ErasedOps) {
        if (e.rows.isEmpty()) return
        val l = d.links
        val m = e.docs.toMap()
        fun <T> of(key: String): List<T> = (m[key] ?: emptyList<Any>()) as List<T>
        d.uow.run {
            d.txns.saveMany(e.rows + e.partners)
            if (e.sources.isNotEmpty()) d.sources.saveMany(e.sources)
            of<app.masroufy.core.TransactionTag>("tags").takeIf { it.isNotEmpty() }?.let { l.transactionTags.saveMany(it) }
            of<app.masroufy.core.ProjectLink>("projects").takeIf { it.isNotEmpty() }?.let { l.projectLinks.saveMany(it) }
            of<app.masroufy.core.EventLink>("events").takeIf { it.isNotEmpty() }?.let { l.eventLinks.saveMany(it) }
            of<app.masroufy.core.PersonAllocation>("allocations").takeIf { it.isNotEmpty() }?.let { d.allocations.saveMany(it) }
            of<app.masroufy.core.Settlement>("settlements").takeIf { it.isNotEmpty() }?.let { d.settlements.saveMany(it) }
            of<app.masroufy.core.RoscaEntry>("entries").takeIf { it.isNotEmpty() }?.let { l.roscaEntries.saveMany(it) }
            of<app.masroufy.core.InstallmentPayment>("payments").takeIf { it.isNotEmpty() }?.let { l.installmentPayments.saveMany(it) }
            of<app.masroufy.core.ZakatPayment>("zakat").takeIf { it.isNotEmpty() }?.let { l.zakatPayments.saveMany(it) }
            of<app.masroufy.core.PersonLink>("personLinks").takeIf { it.isNotEmpty() }?.let { l.personLinks?.saveMany(it) }
            of<app.masroufy.core.AssetLot>("lots").takeIf { it.isNotEmpty() }?.let { l.assetLots.saveMany(it) }
            of<app.masroufy.core.AssetSale>("sales").takeIf { it.isNotEmpty() }?.let { l.assetSales.saveMany(it) }
            of<app.masroufy.core.Obligation>("origins").takeIf { it.isNotEmpty() }?.let { d.obligations.saveMany(it) }
            for (p in of<app.masroufy.core.InstallmentPlan>("plans")) l.installmentPlans.save(p)
        }
    }
}

/** علامة المصدر على العملية: «النوع:الاسم». */
internal fun removedMarker(kind: String, name: String): String = "$kind:$name"
