package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.BankSmsReader
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportSourceType
import app.masroufy.core.IsoDate
import app.masroufy.core.LedgerMove
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsRow
import app.masroufy.core.SmsStatementPair
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.isSmsReference
import app.masroufy.core.lateCardPosting
import app.masroufy.core.smsRepairTarget
import app.masroufy.core.smsStatementDuplicates
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.RepairBackupPort
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/** سطر في «صلّح رسايل البنك القديمة»: قبل ⇒ بعد، بتاريخه ومبلغه (الشاشة بتعرض بس — مفيش حساب فيها). */
data class SmsRepairLine(
    val transactionId: Id,
    val date: IsoDate,
    val amountMinor: Halalas,
    val oldWalletId: Id?,
    val newWalletId: Id,
    val oldDirection: Direction,
    val newDirection: Direction,
)

/** «تراجع» واحد للدفعة كلها: العمليات والسجلات زي ما كانت قبل التصليح — ومعاها اللي «الكشف غطّاها» سجّله ([covered]). */
class SmsRepairUndo internal constructor(
    internal val transactions: List<Transaction>,
    internal val records: List<SourceRecord>,
    internal val covered: CoveredWrite? = null,
) {
    val count: Int get() = transactions.size + (covered?.lineCount ?: 0)
}

class SmsRepairResult(val backup: VerifiedBackup, val undo: SmsRepairUndo)

/**
 * sms-fix (HANDOVER §6.5-3 — بند ٧): **«صلّح رسايل البنك القديمة»** — من «افهم رصيدك» أو المحفظة، **بتأكيد المالك بس**:
 * [preview] بيقرا تاني نص كل رسالة اتسجلت ([SourceRecord.rawLine]) بالقاعدة الجديدة (`SmsRepairRules.kt`) ويطلّع كل عملية اتجاهها أو محفظتها
 * هيتغير · [apply] للي المالك اختاره: نسخة متأكدة الأول ([saveVerifiedBackup]) وبعدين الكتابة، و«تراجع» واحد للدفعة ([undo]).
 * [duplicates] = إعادة المطابقة مرة واحدة (رسالة بس ⇄ كشف بس) في الأيام اللي الكشف بيغطيها — لقايمة «راجع المكرر» (مسار balance-why).
 */
class RepairOldSms(
    private val txns: TransactionRepository,
    private val sources: SourceRecordRepository,
    private val batches: ImportBatchRepository,
    private val wallets: WalletRepository,
    private val readers: List<BankSmsReader>,
    private val backup: RepairBackupPort,
    private val clock: Clock,
    private val uow: app.masroufy.port.UnitOfWork,
    /** r041-B: «الكشف غطّاها» — null = من غير صندوق رسايل (السطور دي ما بتظهرش). */
    private val covered: CoveredSmsRepair? = null,
) {
    private class Recorded(val transaction: Transaction, val records: List<SourceRecord>, val row: SmsRow?)

    suspend fun preview(): List<SmsRepairLine> {
        val all = wallets.listAll()
        val own = ownDigits(all)
        return recorded().mapNotNull { rec ->
            val t = rec.transaction
            val row = rec.row ?: return@mapNotNull null
            val current = all.firstOrNull { it.id == t.walletId }
            val (digits, direction) = smsRepairTarget(row, own, walletLast4(current?.accountLast4)) ?: return@mapNotNull null
            val target = all.filter { it.kind == "bank" && walletLast4(it.accountLast4) == digits }.singleOrNull() ?: return@mapNotNull null
            if (target.id == t.walletId && direction == t.observedDirection) return@mapNotNull null
            SmsRepairLine(t.id, t.occurredAt, t.amountMinor, t.walletId, target.id, t.observedDirection, direction)
        }.sortedWith(compareBy({ it.date }, { it.transactionId }))
    }

    /** الشاشة كلها: النقل و«الكشف غطّاها» (r041-B). قراية بس. */
    suspend fun previewAll(): SmsRepairPreview = SmsRepairPreview(preview(), covered?.preview().orEmpty())

    /** بيطبّق [chosen] من [preview] (اللي لسه زي ما هو بس) — null لو مفيش حاجة تتغير. */
    suspend fun apply(chosen: Set<Id>): SmsRepairResult? = applyAll(chosen, emptySet())

    /**
     * r041-B: النوعين مع بعض — **نسخة متأكدة واحدة · وحدة عمل واحدة · «تراجع» واحد**. [moves] = معرّفات العمليات من [SmsRepairPreview.moves] ·
     * [unrecorded] = معرّفات السجلات من [SmsRepairPreview.unrecorded]. المعاينة بتتعمل تاني: السطر اللي اتغير من ساعتها بيخرج.
     */
    suspend fun applyAll(moves: Set<Id>, unrecorded: Set<Id>): SmsRepairResult? {
        val lines = if (moves.isEmpty()) emptyList() else preview().filter { it.transactionId in moves }
        val fresh = if (unrecorded.isEmpty() || covered == null) emptyList() else covered.candidates().filter { it.line.recordId in unrecorded }
        if (lines.isEmpty() && fresh.isEmpty()) return null
        val byId = txns.findByIds(lines.map { it.transactionId }).associateBy { it.id }
        val records = if (lines.isEmpty()) emptyList() else sources.listByTransactionIds(lines.map { it.transactionId })
        val all = wallets.listAll()
        // النسخة الأول — لو ما اتأكدتش بيطلع خطأ ومفيش ولا كتابة
        val saved = saveVerifiedBackup(
            backup, clock, "sms-repair",
            lines.mapNotNull { line -> byId[line.transactionId]?.let { t -> backupDoc(t, records.filter { it.transactionId == t.id }) } } +
                covered?.backupDocs(fresh).orEmpty(),
        )
        val now = clock.nowIso()
        val fixed = lines.mapNotNull { line ->
            val t = byId[line.transactionId] ?: return@mapNotNull null
            val own = smsKindOf(records.filter { it.transactionId == t.id }) == SmsKind.OWN_TRANSFER
            t.copy(
                walletId = line.newWalletId, observedDirection = line.newDirection, updatedAt = now,
                economicKind = if (own) EconomicKind.INTERNAL_TRANSFER else t.economicKind,
                economicKindConfirmed = if (own) true else t.economicKindConfirmed && line.newDirection == line.oldDirection,
            )
        }
        val moved = lines.filter { it.newWalletId != it.oldWalletId }.associate { it.transactionId to it.newWalletId }
        val movedRecords = records.filter { it.transactionId in moved }.mapNotNull { r ->
            val name = all.firstOrNull { it.id == moved[r.transactionId] }?.name ?: return@mapNotNull null
            r.copy(accountIdentity = name)
        }
        // مراجعة r04: العملية وسجلاتها في وحدة عمل واحدة — ما ينفعش العملية تتنقل وسجلاتها تفضل على الحساب القديم
        val write = uow.run {
            if (fixed.isNotEmpty()) txns.saveMany(fixed)
            if (movedRecords.isNotEmpty()) sources.saveMany(movedRecords)
            if (fresh.isEmpty()) null else covered?.record(fresh, now)
        }
        return SmsRepairResult(saved, SmsRepairUndo(fixed.mapNotNull { byId[it.id] }, records.filter { it.transactionId in moved }, write))
    }

    /**
     * «تراجع» الدفعة كلها في وحدة عمل واحدة. اللي «الكشف غطّاها» سجّله بيتمسح **من غير علامة مسح المالك** (عشان يتعرض تاني) والسجلات والدفعات
     * بترجع بالحرف. عملية منهم اتربطت بعدها (مصروف تابع · مبلغ داخل مربوط) أو اتعدلت (`updatedAt` اتغير) ⇒ رفض زي المسح، ومفيش ولا كتابة.
     */
    suspend fun undo(undo: SmsRepairUndo) {
        val c = undo.covered
        if (c != null && txns.findByIds(c.createdTransactionIds).any {
                it.linkedExpenseId != null || it.linkedInflowMinor != null || c.createdStamps[it.id].let { stamp -> stamp != null && stamp != it.updatedAt }
            }
        ) {
            throw IllegalStateException(uiText(UiKey.ERASE_TXN_BLOCKED_TITLE))
        }
        uow.run {
            if (undo.transactions.isNotEmpty()) txns.saveMany(undo.transactions)
            if (undo.records.isNotEmpty()) sources.saveMany(undo.records)
            if (c != null) {
                if (c.createdRecordIds.isNotEmpty()) sources.deleteMany(c.createdRecordIds)
                sources.saveMany(c.recordsBefore)
                txns.deleteMany(c.createdTransactionIds)
                for (b in c.batchesBefore) batches.save(b)
            }
        }
    }

    /**
     * **نقطة الربط مع «راجع المكرر»** (مسار balance-why): أزواج (رسالة بس ⇄ كشف بس) في الأيام اللي كشف المحفظة بيغطيها — منها التحويل بين
     * حساباتك اللي اتسجل بعكس اتجاه الكشف. قراية بس.
     */
    suspend fun duplicates(): List<SmsStatementPair> {
        val all = recordsWithBatches()
        val ids = all.mapNotNull { it.first.transactionId }.distinct()
        val byTxn = txns.findByIds(ids).associateBy { it.id }
        val grouped = all.filter { it.first.transactionId != null }.groupBy { it.first.transactionId!! }
        val moves = grouped.mapNotNull { (id, recs) ->
            val t = byTxn[id] ?: return@mapNotNull null
            val sms = recs.filter { it.second }.map { it.first }
            LedgerMove(
                id, t.walletId, t.occurredAt, t.amountMinor, t.observedDirection, fromSms = sms.isNotEmpty(), fromStatement = recs.any { !it.second },
                ownTransfer = smsKindOf(sms) == SmsKind.OWN_TRANSFER, wide = sms.any { lateCardPosting(it.rawLine) },
            )
        }
        val lastDay = moves.filter { it.fromStatement && it.walletId != null }.groupBy { it.walletId!! }.mapValues { (_, m) -> m.maxOf { it.date } }
        return smsStatementDuplicates(moves, lastDay)
    }

    /** كل سجل من دفعة مقفولة ومعاه: من رسالة ولا لأ. */
    private suspend fun recordsWithBatches(): List<Pair<SourceRecord, Boolean>> =
        batches.listRecent(ALL_BATCHES).filter { it.state != ImportBatchState.STAGED && it.state != ImportBatchState.REVERTED }
            .flatMap { b -> sources.listByBatch(b.id).map { it to (b.sourceType == ImportSourceType.SMS || isSmsReference(it.sourceReference)) } }

    private suspend fun recorded(): List<Recorded> {
        val all = recordsWithBatches()
        // مراجعة r04: اللي اندمجت مع سطر كشف ما بتتلمسش (الكشف هو المرجع وسلسلة رصيده بالهللة) · ولا طرف في «عمليات تابعة» (قلب اتجاهه يبوّظ الربط)
        val fromStatement = all.filter { !it.second }.mapNotNull { it.first.transactionId }.toSet()
        val sms = all.filter { it.second && it.first.transactionId != null && it.first.transactionId !in fromStatement }.map { it.first }
        val byTxn = sms.groupBy { it.transactionId!! }
        return txns.findByIds(byTxn.keys.toList()).filter { it.linkedExpenseId == null && it.linkedInflowMinor == null }.map { t ->
            val recs = byTxn.getValue(t.id)
            Recorded(t, recs, recs.firstNotNullOfOrNull { parse(it.rawLine, t.occurredAt) })
        }
    }

    private fun parse(body: String, date: IsoDate): SmsRow? =
        // لحظة كاملة بـ«Z» (12:00 جرينتش = نفس اليوم في السعودية ومصر): من غيرها القارئ بيرفض الرسالة «التاريخ مش واضح» والمعاينة كانت فاضية دايمًا
        readers.firstNotNullOfOrNull { (it.parse(BankSmsMessage("", date + "T12:00:00Z", body), 1) as? SmsParseResult.Ok)?.row }

    private fun smsKindOf(records: List<SourceRecord>): SmsKind? = records.firstNotNullOfOrNull { r -> parse(r.rawLine, "2000-01-01")?.kind }

    private fun backupDoc(t: Transaction, records: List<SourceRecord>): Map<String, Any?> = linkedMapOf(
        "id" to t.id, "walletId" to t.walletId, "observedDirection" to t.observedDirection.name, "occurredAt" to t.occurredAt,
        "amountMinor" to t.amountMinor, "economicKind" to t.economicKind.name, "economicKindConfirmed" to t.economicKindConfirmed,
        // مراجعة r04: الحساب القديم لكل سجل — النقل بيغيّره، و«تراجع» في الذاكرة بس
        "sourceRecords" to records.map { linkedMapOf("id" to it.id, "accountIdentity" to it.accountIdentity) },
    )
}

/** كل الدفعات (الإصلاح مرة واحدة على الدفاتر كلها). */
private const val ALL_BATCHES = 100_000
