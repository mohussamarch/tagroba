package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportSourceType
import app.masroufy.core.IsoDate
import app.masroufy.core.MatchingState
import app.masroufy.core.SourceRecord
import app.masroufy.core.TextKey
import app.masroufy.core.isSmsReference
import app.masroufy.core.uiText
import app.masroufy.port.ImportBatchRepository

/**
 * sms-fix (HANDOVER §6.5-3): **رسالة بنك يومها في آخر يوم في الكشف اللي اتستورد للمحفظة دي أو قبله ما بتعملش عملية جديدة أبدًا.** الكشف هو
 * المرجع للأيام اللي بيغطيها: الرسالة يا إما بتندمج مع سطر الكشف (نفس الاتجاه والمبلغ جوه النافذة — `applyCrossSource` قبل ده)، يا إما بتتعلّم
 * «الكشف غطّاها» (مكررة — ما بتتضافش). اللي اتسجل يوم «اقرأ اللي فات؟» فوق سطور الكشف كان بيعدّ الحركة مرتين.
 * المختلف عليه («شبه عملية» بأكتر من احتمال) بيفضل زي ما هو — المالك بيختار الدمج.
 */
internal suspend fun markStatementCovered(
    deps: ImportStatementDeps,
    request: ImportRequest,
    lines: List<ImportPreviewLine>,
    ledger: ExistingLedger,
): List<ImportPreviewLine> {
    if (request.sourceType != ImportSourceType.SMS || lines.isEmpty()) return lines
    val lastDay = lastStatementDay(deps.batches, ledger) ?: return lines
    return lines.map { line ->
        if (line.state != MatchingState.NEW || line.mergeInto != null || line.row.date > lastDay) line
        else line.copy(state = MatchingState.DUPLICATE, selectedByDefault = false, reason = uiText(TextKey.SMS_COVERED_BY_STATEMENT, lastDay))
    }
}

/** آخر يوم فيه عملية من **كشف** (مش رسالة) في دفاتر الحساب ده — دفعات مقفولة بس (المعلّقة والمتراجع عنها ما بتغطيش حاجة). */
internal suspend fun lastStatementDay(batches: ImportBatchRepository, ledger: ExistingLedger): IsoDate? {
    val statement = statementRecords(batches, ledger.records)
    return statement.mapNotNull { r -> r.transactionId?.let { ledger.byId[it]?.occurredAt } }.maxOrNull()
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
