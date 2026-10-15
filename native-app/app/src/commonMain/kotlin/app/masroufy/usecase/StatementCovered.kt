package app.masroufy.usecase

import app.masroufy.core.CROSS_SOURCE_WIDE_WINDOW_DAYS
import app.masroufy.core.Id
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportSourceType
import app.masroufy.core.IsoDate
import app.masroufy.core.MatchingState
import app.masroufy.core.SourceRecord
import app.masroufy.core.TextKey
import app.masroufy.core.UiKey
import app.masroufy.core.daysBetween
import app.masroufy.core.isSmsReference
import app.masroufy.core.uiText
import app.masroufy.port.ImportBatchRepository
import kotlin.math.abs

/**
 * sms-fix (HANDOVER §6.5-3) + **إجابة المالك 2026-10-15 (OVERRIDES §91-2)**: رسالة بنك يومها جوه أيام الكشف اللي اتستورد للمحفظة دي:
 * - مبلغها **في الكشف** (نفس الاتجاه والمبلغ جوه النافذة الواسعة) ⇒ زي الأول: يا إما اتدمجت مع سطره (`applyCrossSource` قبل ده)، يا إما
 *   بتتعلّم «الكشف غطّاها» (مكررة — ما بتتضافش). اللي اتسجل يوم «اقرأ اللي فات؟» فوق سطور الكشف كان بيعدّ الحركة مرتين.
 * - مبلغها **مش في الكشف** ⇒ **بتتسجل** وعليها علامة «مش في الكشف» (`Transaction.notInStatement`) — المالك يشوفها ويقرر.
 * المختلف عليه («شبه عملية» بأكتر من احتمال) بيفضل زي ما هو — المالك بيختار الدمج.
 */
internal suspend fun markStatementCovered(
    deps: ImportStatementDeps,
    request: ImportRequest,
    lines: List<ImportPreviewLine>,
    ledger: ExistingLedger,
): List<ImportPreviewLine> {
    if (request.sourceType != ImportSourceType.SMS || lines.isEmpty()) return lines
    val (firstDay, lastDay) = statementDays(deps.batches, ledger) ?: return lines
    val statement = statementRecords(deps.batches, ledger.records).mapNotNull { r -> r.transactionId?.let { ledger.byId[it] } }
    return lines.map { line ->
        // مراجعة r04: آخر يوم في الكشف غالبًا ناقص (الكشف اتصدّر في نصّه) ⇒ الشراء اللي بعد التصدير لازم يتسجل · وقبل أول يوم الكشف ما يعرفش حاجة
        val covered = line.row.date >= firstDay && line.row.date < lastDay
        if (line.state != MatchingState.NEW || line.mergeInto != null || !covered) return@map line
        val inStatement = statement.any { t ->
            t.amountMinor == line.row.amountMinor && t.observedDirection == line.row.direction &&
                abs(daysBetween(line.row.date, t.occurredAt.take(10))) <= CROSS_SOURCE_WIDE_WINDOW_DAYS
        }
        if (inStatement) line.copy(state = MatchingState.DUPLICATE, selectedByDefault = false, reason = uiText(TextKey.SMS_COVERED_BY_STATEMENT, lastDay))
        else line.copy(notInStatement = true, reason = uiText(UiKey.TXN_NOT_IN_STATEMENT_NOTE))
    }
}

/** آخر يوم فيه عملية من **كشف** (مش رسالة) في دفاتر الحساب ده — دفعات مقفولة بس (المعلّقة والمتراجع عنها ما بتغطيش حاجة). */
internal suspend fun lastStatementDay(batches: ImportBatchRepository, ledger: ExistingLedger): IsoDate? = statementDays(batches, ledger)?.second

/** أول وآخر يوم فيهم عملية من **كشف** في دفاتر الحساب ده (نفس سجلات [lastStatementDay])، أو null. */
internal suspend fun statementDays(batches: ImportBatchRepository, ledger: ExistingLedger): Pair<IsoDate, IsoDate>? {
    val days = statementRecords(batches, ledger.records).mapNotNull { r -> r.transactionId?.let { ledger.byId[it]?.occurredAt?.take(10) } }
    return if (days.isEmpty()) null else days.min() to days.max()
}

/** سجلات الكشف بس من [records] — نوع الدفعة بيحكم، ولو الدفعة مش موجودة (بيانات قديمة) شكل المرجع. */
internal suspend fun statementRecords(batches: ImportBatchRepository, records: List<SourceRecord>): List<SourceRecord> {
    val byBatch: Map<Id, Pair<ImportSourceType, ImportBatchState>?> =
        records.map { it.batchId }.distinct().associateWith { id -> batches.findById(id)?.let { it.sourceType to it.state } }
    return records.filter { r ->
        val batch = byBatch[r.batchId]
        when {
            batch == null -> !isSmsReference(r.sourceReference)
            batch.second == ImportBatchState.STAGED || batch.second == ImportBatchState.REVERTED -> false
            else -> batch.first != ImportSourceType.SMS
        }
    }
}
