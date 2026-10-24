package app.masroufy.usecase

import app.masroufy.core.SmsStatementPair
import app.masroufy.core.CROSS_SOURCE_WINDOW_DAYS
import app.masroufy.core.Id
import app.masroufy.core.MatchingState
import app.masroufy.core.MergeRestore
import app.masroufy.core.SourceRecord
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.daysBetween
import app.masroufy.core.sourceAmountMinor
import app.masroufy.core.uiText
import app.masroufy.port.AllocationRepository
import app.masroufy.port.Clock
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.RepairBackupPort
import app.masroufy.port.SettlementRepository
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.SpaceTransferRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork
import kotlin.math.abs

/**
 * «راجع المكرر» (HANDOVER §6.5 بند 3): المالك شاف رسالة وسطر كشف جنب بعض وقال **«دول عملية واحدة»** ⇒ بيتدمجوا **بنفس آلية الدمج
 * الموجودة** (§75-10 — `MergeUndo.kt`): عملية واحدة بمصدرين. ولا حاجة بتتدمج أو تتمسح لوحدها — بس بالضغطة دي.
 * - **اللي بيفضل = اللي اتسجل الأول** (`createdAt`؛ التعادل ⇒ الكشف) — زي ما كان الدمج هيعمل وقت التسجيل: سجلات مصدر التانية بتتنقل عليها
 *   «مكرر» (`matchingState`) بسبب «اخترت إنها نفس العملية». لو اللي فاضل **رسالة**: تاريخ الكشف وترتيب سطره ورصيده المعلن بيكسبوا
 *   (سلسلة الرصيد تفضل مطابقة للكشف) والقديم بيتحفظ في سجل الكشف (`MergeRestore` ⇒ التراجع عن دفعة الكشف بيرجّعه).
 * - ⚙️ اختيار Claude (المالك يقدر يغيّره): التصنيف والنوع اللي **المالك أكّدهم** على اللي هتتشال بيتنقلوا لو اللي فاضلة ما اتأكدش فيها حاجة،
 *   والملاحظة لو اللي فاضلة من غير ملاحظة — عشان شغل المالك ما يضيعش.
 * - **قبل أي كتابة نسخة مؤكدة الحجم** (`saveVerifiedBackup` — العمليتين وسجلاتهم)؛ من غير مكان للنسخة ⇒ **رفض** (ولا كتابة).
 * - ⚙️ **اللي هتتشال عليها ربط** (شخص · دين · وسم · مشروع · حدث · مستحقات · زكاة · استثمار · رجوع · تحويل لبلد تانية) ⇒ **رفض** ويتقال
 *   ليه — ما بننقلش الربط من غير ما المالك يقرر (سؤال مفتوح).
 * - **تراجع** ([undo]): بالتذكرة اللي رجعت، ولو مفيش حاجة اتغيرت بعدها بس (نسخة قبله كمان).
 */
data class MergeDuplicatesDeps(
    val txns: TransactionRepository,
    val sources: SourceRecordRepository,
    val batches: ImportBatchRepository,
    val uow: UnitOfWork,
    val clock: Clock,
    /** null = مفيش مكان لنسخة «قبل الإصلاح» (الآيفون · الاختبار) ⇒ الدمج مرفوض. */
    val backup: RepairBackupPort?,
    val allocations: AllocationRepository,
    val settlements: SettlementRepository,
    val obligations: ObligationRepository,
    val links: RevertLinkDeps,
    val spaceTransfers: SpaceTransferRepository? = null,
    val windowDays: Int = CROSS_SOURCE_WINDOW_DAYS,
    /**
     * أزواج «صلّح رسايل البنك القديمة» (`RepairOldSms.duplicates`): زوج **بعكس الاتجاه** بيتقبل بس لو القاعدة دي طلّعته من التخزين دلوقتي،
     * واللي بتفضل بتاخد اتجاه الكشف. null = بعكس الاتجاه مرفوض دايمًا.
     */
    val rematch: (suspend () -> List<SmsStatementPair>)? = null,
)

enum class MergeRefusal { GONE, NOT_A_PAIR, LINKED, NO_BACKUP, CHANGED }

enum class MergeLink { PERSON, DEBT, TAG, PROJECT, EVENT, DUE, ZAKAT, INVESTMENT, REVERSAL, SPACE_TRANSFER, LINKED_INFLOW }

/** التذكرة اللي التراجع بيحتاجها: العملية اللي فضلت قبل وبعد · اللي اتشالت · سجلات مصدرها قبل النقل. */
data class MergeTicket(val keptBefore: Transaction, val keptAfter: Transaction, val removed: Transaction, val movedBefore: List<SourceRecord>)

sealed interface MergeResult {
    data class Done(val keptId: Id, val removedId: Id, val backup: VerifiedBackup, val ticket: MergeTicket) : MergeResult
    data class Refused(val reason: MergeRefusal, val links: Set<MergeLink> = emptySet()) : MergeResult
}

sealed interface UndoResult {
    data class Done(val backup: VerifiedBackup) : UndoResult
    data class Refused(val reason: MergeRefusal) : UndoResult
}

class MergeDuplicates(private val deps: MergeDuplicatesDeps) {
    /** «دول عملية واحدة» للرسالة [smsId] وسطر الكشف [statementId] — كل الشروط بتتفحص من جديد من التخزين (مش من الشاشة). */
    suspend fun merge(smsId: Id, statementId: Id): MergeResult {
        val found = deps.txns.findByIds(listOf(smsId, statementId)).associateBy { it.id }
        val sms = found[smsId] ?: return MergeResult.Refused(MergeRefusal.GONE)
        val statement = found[statementId] ?: return MergeResult.Refused(MergeRefusal.GONE)
        val kinds = sourcesOf(deps.sources, deps.batches, listOf(smsId, statementId))
        val smsOk = kinds[smsId]?.let { it.sms && !it.statement } == true
        val statementOk = kinds[statementId]?.let { it.statement && !it.sms } == true
        val opposite = sms.observedDirection != statement.observedDirection && deps.rematch?.let { f ->
            runCatching { f() }.getOrNull().orEmpty().any { it.oppositeDirection && it.smsTransactionId == smsId && it.statementTransactionId == statementId }
        } == true
        if (smsId == statementId || !smsOk || !statementOk || !samePair(sms, statement, opposite)) return MergeResult.Refused(MergeRefusal.NOT_A_PAIR)
        val smsFirst = sms.createdAt < statement.createdAt
        val keep = if (smsFirst) sms else statement
        val drop = if (smsFirst) statement else sms
        val linked = linksOf(drop)
        if (linked.isNotEmpty()) return MergeResult.Refused(MergeRefusal.LINKED, linked)
        val backupPort = deps.backup ?: return MergeResult.Refused(MergeRefusal.NO_BACKUP)

        val dropRecords = deps.sources.listByTransactionIds(listOf(drop.id))
        val keepRecords = deps.sources.listByTransactionIds(listOf(keep.id))
        val backup = saveVerifiedBackup(
            backupPort, deps.clock, "merge-duplicates",
            listOf(mergeDoc("kept", keep, keepRecords), mergeDoc("removed", drop, dropRecords)),
        )
        val now = deps.clock.nowIso()
        val reason = uiText(TextKey.MATCH_MERGE_CHOSEN, uiText(if (smsFirst) TextKey.MATCH_SOURCE_SMS else TextKey.MATCH_SOURCE_STATEMENT))
        val moved = dropRecords.map { r ->
            r.copy(
                transactionId = keep.id, matchingState = MatchingState.DUPLICATE, reason = reason,
                // سطر الكشف اللي اتنقل على رسالة كتب تاريخه ورصيده فوقها ⇒ القديم بيتحفظ عشان التراجع عن دفعته (`MergeUndo`)
                mergeUndo = if (smsFirst) MergeRestore(keep.occurredAt, keep.sourceOrder, keep.statedBalanceMinor, drop.occurredAt, drop.statedBalanceMinor) else r.mergeUndo,
            )
        }
        val kept = keptAfter(keep, drop, statementWins = smsFirst, now = now).let {
            // بعكس الاتجاه: الكشف هو الصح (الرسالة القديمة اتقرت بعكس اتجاه التحويل بين حساباتك)
            if (opposite && it.observedDirection != statement.observedDirection) it.copy(observedDirection = statement.observedDirection, updatedAt = now) else it
        }
        deps.uow.run {
            deps.sources.saveMany(moved)
            if (kept != keep) deps.txns.saveMany(listOf(kept))
            deps.txns.deleteMany(listOf(drop.id))
        }
        return MergeResult.Done(keep.id, drop.id, backup, MergeTicket(keep, kept, drop, dropRecords))
    }

    /** يرجّع الاتنين زي ما كانوا — بس لو اللي فضلت لسه زي ما الدمج سابها، واللي اتشالت لسه مش موجودة، والسجلات لسه عليها. */
    suspend fun undo(ticket: MergeTicket): UndoResult {
        val ids = listOf(ticket.keptAfter.id, ticket.removed.id)
        val now = deps.txns.findByIds(ids).associateBy { it.id }
        val records = deps.sources.listByTransactionIds(listOf(ticket.keptAfter.id)).associateBy { it.id }
        val intact = now[ticket.keptAfter.id] == ticket.keptAfter && ticket.removed.id !in now &&
            ticket.movedBefore.all { records[it.id]?.transactionId == ticket.keptAfter.id }
        if (!intact) return UndoResult.Refused(MergeRefusal.CHANGED)
        val backupPort = deps.backup ?: return UndoResult.Refused(MergeRefusal.NO_BACKUP)
        val backup = saveVerifiedBackup(backupPort, deps.clock, "merge-duplicates-undo", listOf(mergeDoc("kept", ticket.keptAfter, records.values.toList())))
        deps.uow.run {
            deps.txns.saveMany(listOf(ticket.removed, ticket.keptBefore))
            deps.sources.saveMany(ticket.movedBefore)
        }
        return UndoResult.Done(backup)
    }

    /** نفس المحفظة والعملة والاتجاه والمبلغ زي ما ورد، وجوه النافذة، ومش تحويل — نفس شروط «المكرر المحتمل». */
    private fun samePair(a: Transaction, b: Transaction, opposite: Boolean = false): Boolean =
        a.walletId != null && a.walletId == b.walletId && a.currency == b.currency && (opposite || a.observedDirection == b.observedDirection) &&
            sourceAmountMinor(a.amountMinor, a.originalAmountMinor) == sourceAmountMinor(b.amountMinor, b.originalAmountMinor) &&
            a.transferToWalletId == null && b.transferToWalletId == null && abs(daysBetween(a.occurredAt, b.occurredAt)) <= deps.windowDays

    /** كل ربط بيشاور على [t] (نفس اللي التراجع عن دفعة بيبص عليه — `RevertImportBatch`) + الرجوع والتحويل لبلد تانية. */
    private suspend fun linksOf(t: Transaction): Set<MergeLink> {
        val ids = listOf(t.id)
        val l = deps.links
        val out = mutableSetOf<MergeLink>()
        // R2: رابط شخص (حتى من غير نصيب ولا دين) ⇒ الدمج بيترفض بدل ما الرابط يفضل بيشاور على عملية اتشالت
        if (deps.allocations.listByTransactionIds(ids).isNotEmpty() || l.personLinks?.listByTransactionIds(ids)?.isNotEmpty() == true) out += MergeLink.PERSON
        if (deps.settlements.listByTransactionIds(ids).isNotEmpty() || deps.obligations.listByTransactionIds(ids).isNotEmpty()) out += MergeLink.DEBT
        if (l.transactionTags.listByTransactionIds(ids).isNotEmpty()) out += MergeLink.TAG
        if (l.projectLinks.listByTransaction(t.id).isNotEmpty()) out += MergeLink.PROJECT
        if (l.eventLinks.listByTransactionIds(ids).isNotEmpty()) out += MergeLink.EVENT
        val due = l.roscaEntries.listByTransactionIds(ids).isNotEmpty() || l.installmentPayments.listByTransactionIds(ids).isNotEmpty() ||
            l.installmentPlans.listAll().any { it.receivedTransactionId == t.id }
        if (due) out += MergeLink.DUE
        if (l.zakatPayments.listByTransactionIds(ids).any { it.transactionId == t.id }) out += MergeLink.ZAKAT
        if (l.assetLots.listAll().any { it.transactionId == t.id } || l.assetSales.listAll().any { it.transactionId == t.id }) out += MergeLink.INVESTMENT
        val reversal = t.reversalOfId != null || t.reversedById != null ||
            deps.txns.listReversalLinked().any { it.reversalOfId == t.id || it.reversedById == t.id }
        if (reversal) out += MergeLink.REVERSAL
        if (deps.spaceTransfers?.listAll()?.any { it.fromTransactionId == t.id || it.toTransactionId == t.id } == true) out += MergeLink.SPACE_TRANSFER
        // مراجعة r04: طرف في «عمليات تابعة» — شيله كان بيسيب المصروف منقوص بداخلة مش موجودة (أو الداخلة مستخبية ورا مصروف مش موجود)
        if (t.linkedExpenseId != null || t.linkedInflowMinor != null) out += MergeLink.LINKED_INFLOW
        return out
    }
}

/** اللي فاضلة بعد الدمج: الكشف بيكسب التاريخ والترتيب والرصيد (لو اللي فاضلة رسالة) · تأكيدات المالك اللي على التانية بتتنقل لو ناقصة هنا. */
internal fun keptAfter(keep: Transaction, drop: Transaction, statementWins: Boolean, now: String): Transaction {
    var t = keep
    if (statementWins) t = t.copy(occurredAt = drop.occurredAt, sourceOrder = drop.sourceOrder, statedBalanceMinor = drop.statedBalanceMinor ?: keep.statedBalanceMinor)
    if (!keep.categoryConfirmed && drop.categoryConfirmed && drop.categoryId != null) t = t.copy(categoryId = drop.categoryId, categoryConfirmed = true)
    if (!keep.economicKindConfirmed && drop.economicKindConfirmed) t = t.copy(economicKind = drop.economicKind, economicKindConfirmed = true)
    if (keep.note == null && drop.note != null) t = t.copy(note = drop.note)
    // مراجعة r051 (A2/F3): «فكّيت ربطها» على اللي اتشالت بيفضل على اللي فاضلة — وإلا قواعد الحساب كانت بترجع تربط نفس العملية
    if (keep.personRulesOff != true && drop.personRulesOff == true) t = t.copy(personRulesOff = true)
    return if (t == keep) keep else t.copy(updatedAt = now)
}

/** مستند النسخة: العملية وسجلات مصدرها بأسماء الحقول المتخزنة — للرجوع باليد لو احتاجنا (ولا حاجة بتتقري منه آليًا). */
internal fun mergeDoc(role: String, t: Transaction, records: List<SourceRecord>): Map<String, Any?> =
    linkedMapOf("role" to role, "transaction" to txnDoc(t), "sourceRecords" to records.map(::recordDoc))

private fun txnDoc(t: Transaction): Map<String, Any?> = linkedMapOf(
    "id" to t.id, "occurredAt" to t.occurredAt, "datePrecision" to t.datePrecision, "sourceTime" to t.sourceTime, "sourceOrder" to t.sourceOrder,
    "economicKind" to t.economicKind.wire, "economicKindConfirmed" to t.economicKindConfirmed, "observedDirection" to t.observedDirection.wire,
    "amountMinor" to t.amountMinor, "originalAmountMinor" to t.originalAmountMinor, "currency" to t.currency.name, "merchantId" to t.merchantId,
    "categoryId" to t.categoryId, "categoryConfirmed" to t.categoryConfirmed, "excludedFromBudget" to t.excludedFromBudget,
    "reviewState" to t.reviewState.wire, "note" to t.note, "walletId" to t.walletId, "transferToWalletId" to t.transferToWalletId,
    "statedBalanceMinor" to t.statedBalanceMinor, "rawDescription" to t.rawDescription, "rawMerchantName" to t.rawMerchantName,
    "isCashTagged" to t.isCashTagged, "sourceCategory" to t.sourceCategory, "sourceOperationType" to t.sourceOperationType,
    "createdAt" to t.createdAt, "updatedAt" to t.updatedAt, "suggestedKind" to t.suggestedKind?.wire, "reversalOfId" to t.reversalOfId,
    "reversedById" to t.reversedById, "foreignAmountMinor" to t.foreignAmountMinor, "foreignCurrency" to t.foreignCurrency,
    "kindBeforeReversal" to t.kindBeforeReversal?.wire, "linkedExpenseId" to t.linkedExpenseId, "linkedInflowMinor" to t.linkedInflowMinor,
    "supportAskPending" to t.supportAskPending, "explainsCheckpointId" to t.explainsCheckpointId, "notInStatement" to t.notInStatement,
    "sourceRemoved" to t.sourceRemoved,
)

private fun recordDoc(r: SourceRecord): Map<String, Any?> = linkedMapOf(
    "id" to r.id, "batchId" to r.batchId, "accountIdentity" to r.accountIdentity, "sourceReference" to r.sourceReference, "sourceHash" to r.sourceHash,
    "originalRowIndex" to r.originalRowIndex, "rawLine" to r.rawLine, "transactionId" to r.transactionId, "matchingState" to r.matchingState.wire,
    "reason" to r.reason,
    "mergeUndo" to r.mergeUndo?.let {
        linkedMapOf(
            "occurredAt" to it.occurredAt, "sourceOrder" to it.sourceOrder, "statedBalanceMinor" to it.statedBalanceMinor,
            "mergedOccurredAt" to it.mergedOccurredAt, "mergedStatedBalanceMinor" to it.mergedStatedBalanceMinor,
        )
    },
)
