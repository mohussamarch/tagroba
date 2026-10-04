package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.AllocationRepository
import app.masroufy.port.AssetLotRepository
import app.masroufy.port.AssetSaleRepository
import app.masroufy.port.EventLinkRepository
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.ProjectLinkRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransactionTagRepository
import app.masroufy.port.UnitOfWork
import app.masroufy.port.ZakatPaymentRepository

/**
 * RevertImportBatch — نقل `revertImportBatch.ts`. قيد spec/03: التراجع **مش حذف أعمى** —
 * العملية اللي تؤيدها مصادر تانية، أو دخلت عليها تسوية، أو متربطة بشخص، بتفضل ويتشرح ليه.
 *
 * **زيادة على التطبيق الحالي** (التطبيق الحالي كان بيمسح العملية ويسيب الوسوم وروابط المشاريع بتشاور عليها — نفس الغلطة اللي
 * اتلقت في جلسة 6): كل حاجة بتشاور على عملية بمعرّفها اتبصّ عليها ([RevertLinkDeps]) —
 * - **فلوس اتسجلت عليها بعد الاستيراد** (زي التسوية بالظبط): جمعية · قسط · مبلغ تمويل مستلم · دفعة زكاة ⇒ العملية **بتفضل**.
 * - **شراء أو بيع أصل** (استثمار) ⇒ بتفضل. · **نقطة في حدث** (متربطة بشخص — زي التخصيص) ⇒ بتفضل.
 * - **علامات بس** (ربط مشروع · مصروف على حدث · وسم · تحويل لنفسك لبلد تانية) ⇒ الربط **بيتشال مع العملية في نفس وحدة العمل** — ولا ربط بيفضل
 *   بيشاور على عملية مش موجودة (النسخة الشاملة كانت بترفض العلاقة).
 * اختيار Claude (OVERRIDES §64 «قرارات تنفيذ») — المالك يقدر يغيّره.
 *
 * ⚠️ مسار «نسخة قبل الحذف» (`RepairBackupPort`, HANDOVER §36) **مش هنا لسه** — محتاج منفذ
 * ملفات الجهاز، وبيتوصّل مع طبقة التخزين. السلوك الحالي = التطبيق الحالي لما `deps.backup` غايبة.
 */

enum class RevertDecision(val wire: String) {
    DELETED("deleted"), KEPT_OTHER_SOURCE("kept_other_source"), KEPT_HAS_SETTLEMENT("kept_has_settlement"), KEPT_HAS_ALLOCATION("kept_has_allocation"),
    /** متربطة بالمستحقات (جمعية · قسط · مبلغ تمويل مستلم) أو بدفعة زكاة. */
    KEPT_HAS_DUE("kept_has_due"),
    /** متربطة بشراء أو بيع أصل. */
    KEPT_HAS_INVESTMENT("kept_has_investment"),
    /** نقطة في حدث متربطة بشخص (§64). */
    KEPT_HAS_GIFT("kept_has_gift"),
}

data class RevertLineOutcome(val transactionId: Id, val decision: RevertDecision, val reason: String)

data class RevertPlan(
    val batchId: Id,
    /** اللي هيتمسح فعلًا. */
    val toDelete: List<Id>,
    /** اللي هيفضل وليه. */
    val toKeep: List<RevertLineOutcome>,
    val outcomes: List<RevertLineOutcome>,
    /** التراجع نظيف تمامًا (مفيش محتفظ بيه)؟ */
    val isClean: Boolean,
    /** عدد العمليات اللي الدفعة سجّلتها وقت الاستيراد. */
    val expectedCount: Int,
    /** عدد سجلات المصدر اللي اتلقت فعلًا للدفعة دي. */
    val recordsFound: Int,
    /** عمليات متسجلة وولا سجل مصدر — معرّفات تالفة غالبًا؛ التراجع بيترفض لحد ما تتصلح. */
    val blocked: Boolean,
    /** روابط (مشروع · مصروف على حدث · وسم) هتتشال مع العمليات اللي هتتمسح. */
    val unlinkCount: Int = 0,
)

/** كل اللي بيشاور على عملية بمعرّفها غير التسويات والتخصيصات والالتزامات — التراجع بيبص عليهم كلهم. */
data class RevertLinkDeps(
    val projectLinks: ProjectLinkRepository,
    val eventLinks: EventLinkRepository,
    val transactionTags: TransactionTagRepository,
    val roscaEntries: RoscaEntryRepository,
    val installmentPayments: InstallmentPaymentRepository,
    val installmentPlans: InstallmentPlanRepository,
    val zakatPayments: ZakatPaymentRepository,
    val assetLots: AssetLotRepository,
    val assetSales: AssetSaleRepository,
    /**
     * رجول التحويل لنفسك في البلد دي (§64): الرجل اللي هتتمسح ⇒ **الزوج بيتفك** والرجل التانية (في البلد التانية) بترجع «لسه ما اتحددش» —
     * ولا زوج بيفضل بيشاور على عملية مش موجودة. التشغيل الحقيقي بيدّيه.
     */
    val spaceLegs: app.masroufy.port.SpaceTransferLegs? = null,
)

/** الروابط اللي هتتشال مع العمليات اللي هتتمسح. */
private data class Detach(val projectLinks: List<Id>, val eventLinks: List<Id>, val tags: List<Id>, val spacePairs: List<app.masroufy.core.SpaceTransfer> = emptyList()) {
    val count: Int get() = projectLinks.size + eventLinks.size + tags.size + spacePairs.size
}

data class RevertDeps(
    val txns: TransactionRepository,
    val sources: SourceRecordRepository,
    val batches: ImportBatchRepository,
    val settlements: SettlementRepository,
    val allocations: AllocationRepository,
    val obligations: ObligationRepository,
    val uow: UnitOfWork,
    val links: RevertLinkDeps,
)

const val REVERT_BLOCKED_MESSAGE =
    "مقدرناش نلاقي عمليات الاستيراد ده. غالبًا معرّفاته اتحفظت ناقصة في نسخة قديمة. " +
        "من الإعدادات دوس «افحص البيانات القديمة» وصلّح، وبعدين جرّب التراجع تاني."

class RevertImportBatch(private val deps: RevertDeps) {
    /** سجل الاستيرادات، الأحدث الأول — للعرض بس. */
    suspend fun history(limit: Int = 50): List<ImportBatch> =
        deps.batches.listRecent(limit).sortedWith(compareByDescending { it.importedAt })

    /** بيحسب الأثر من غير أي كتابة — بيتعرض للمستخدم قبل التأكيد (spec/03). */
    suspend fun plan(batchId: Id): RevertPlan {
        val batch = deps.batches.findById(batchId) ?: throw IllegalStateException("دفعة غير موجودة: $batchId")
        if (batch.state == ImportBatchState.REVERTED) throw IllegalStateException("الدفعة دي متراجَع عنها قبل كده")

        val batchRecords = deps.sources.listByBatch(batchId)
        val expectedCount = batch.counts.imported
        val recordsFound = batchRecords.size
        val blocked = expectedCount > 0 && recordsFound == 0
        val txnIds = batchRecords.mapNotNull { it.transactionId }

        if (txnIds.isEmpty()) {
            return RevertPlan(batchId, emptyList(), emptyList(), emptyList(), isClean = true, expectedCount = expectedCount, recordsFound = recordsFound, blocked = blocked)
        }

        val allSources = deps.sources.listByTransactionIds(txnIds)
        val settlements = deps.settlements.listByTransactionIds(txnIds)
        val allocations = deps.allocations.listByTransactionIds(txnIds)
        val obligations = deps.obligations.listByTransactionIds(txnIds)

        val settledIds = settlements.map { it.transactionId }.toSet()
        val allocatedIds = allocations.map { it.transactionId }.toSet()
        val obligationOriginIds = obligations.mapNotNull { it.originTransactionId }.toSet()
        val l = deps.links
        val dueIds = (
            l.roscaEntries.listByTransactionIds(txnIds).map { it.transactionId } +
                l.installmentPayments.listByTransactionIds(txnIds).map { it.transactionId } +
                l.installmentPlans.listAll().mapNotNull { it.receivedTransactionId } +
                l.zakatPayments.listByTransactionIds(txnIds).mapNotNull { it.transactionId }
            ).toSet()
        val investmentIds = (l.assetLots.listAll().mapNotNull { it.transactionId } + l.assetSales.listAll().mapNotNull { it.transactionId }).toSet()
        val giftIds = l.eventLinks.listByTransactionIds(txnIds).filter { it.role.isGift }.map { it.transactionId }.toSet()

        val sourceCountByTxn = mutableMapOf<Id, Int>()
        for (record in allSources) {
            val id = record.transactionId ?: continue
            sourceCountByTxn[id] = (sourceCountByTxn[id] ?: 0) + 1
        }

        val toDelete = mutableListOf<Id>()
        val toKeep = mutableListOf<RevertLineOutcome>()
        val outcomes = mutableListOf<RevertLineOutcome>()

        for (id in txnIds) {
            val outcome = when {
                id in settledIds -> RevertLineOutcome(id, RevertDecision.KEPT_HAS_SETTLEMENT, "العملية دي دخلت عليها تسوية بعد الاستيراد، فمش هتتحذف")
                id in allocatedIds || id in obligationOriginIds ->
                    RevertLineOutcome(id, RevertDecision.KEPT_HAS_ALLOCATION, "العملية دي متربطة بشخص (تخصيص أو التزام)، فمش هتتحذف")
                id in dueIds -> RevertLineOutcome(id, RevertDecision.KEPT_HAS_DUE, uiText(TextKey.REVERT_KEPT_DUE))
                id in investmentIds -> RevertLineOutcome(id, RevertDecision.KEPT_HAS_INVESTMENT, uiText(TextKey.REVERT_KEPT_INVESTMENT))
                id in giftIds -> RevertLineOutcome(id, RevertDecision.KEPT_HAS_GIFT, uiText(TextKey.REVERT_KEPT_GIFT))
                (sourceCountByTxn[id] ?: 0) > 1 ->
                    RevertLineOutcome(id, RevertDecision.KEPT_OTHER_SOURCE, "العملية دي ليها مصدر تاني غير الدفعة دي (رسالة مثلًا)، فمش هتتحذف")
                else -> {
                    toDelete += id
                    RevertLineOutcome(id, RevertDecision.DELETED, "الدفعة دي هي المصدر الوحيد ومفيش عليها تسويات")
                }
            }
            outcomes += outcome
            if (outcome.decision != RevertDecision.DELETED) toKeep += outcome
        }

        return RevertPlan(
            batchId, toDelete, toKeep, outcomes, isClean = toKeep.isEmpty(), expectedCount = expectedCount, recordsFound = recordsFound, blocked = blocked,
            unlinkCount = detachFor(toDelete).count,
        )
    }

    /** الروابط اللي بتشاور على [ids] (المشاريع: «مئات مش آلاف» ⇒ قراية الكل أرخص من سؤال لكل عملية). */
    private suspend fun detachFor(ids: List<Id>): Detach {
        if (ids.isEmpty()) return Detach(emptyList(), emptyList(), emptyList())
        val wanted = ids.toSet()
        val l = deps.links
        return Detach(
            l.projectLinks.listAll().filter { it.transactionId in wanted }.map { it.id },
            l.eventLinks.listByTransactionIds(ids).map { it.id },
            l.transactionTags.listByTransactionIds(ids).map { it.id },
            l.spaceLegs?.pairsOf(ids).orEmpty(),
        )
    }

    /** بينفّذ الخطة ذريًا. سجلات المصدر بتاعة الدفعة بتتمسح دايمًا. */
    suspend fun execute(batchId: Id): RevertPlan {
        val revertPlan = plan(batchId)
        if (revertPlan.blocked) throw IllegalStateException(REVERT_BLOCKED_MESSAGE)

        return deps.uow.run {
            if (revertPlan.toDelete.isNotEmpty()) {
                // الروابط الأول وبعدين العمليات — كله في نفس وحدة العمل (فشل في النص ⇒ ولا حاجة اتشالت)
                val detach = detachFor(revertPlan.toDelete)
                if (detach.projectLinks.isNotEmpty()) deps.links.projectLinks.deleteMany(detach.projectLinks)
                if (detach.eventLinks.isNotEmpty()) deps.links.eventLinks.deleteMany(detach.eventLinks)
                if (detach.tags.isNotEmpty()) deps.links.transactionTags.deleteMany(detach.tags)
                if (detach.spacePairs.isNotEmpty()) deps.links.spaceLegs?.detach(detach.spacePairs)
                deps.txns.deleteMany(revertPlan.toDelete)
            }
            val batchRecords = deps.sources.listByBatch(batchId)
            deps.sources.deleteMany(batchRecords.map { it.id })
            deps.batches.updateState(batchId, ImportBatchState.REVERTED)
            revertPlan
        }
    }
}
