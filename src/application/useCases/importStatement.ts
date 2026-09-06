import { hashContent } from '../../domain/dedupe'
import type { Id, ImportBatch, SourceRecord, Transaction } from '../../domain/entities/types'
import { runPreview } from './importPreview'
import type {
  ImportPreview,
  ImportPreviewLine,
  ImportRequest,
  ImportStatementDeps,
} from './importTypes'

export type {
  ImportPreview,
  ImportPreviewLine,
  ImportRequest,
  ImportStatementDeps,
} from './importTypes'

/**
 * ImportStatement — قراءة الكشف، منع التكرار، إنشاء الدفعة.
 *
 * التدفق مرحلتان صراحةً (spec/04):
 *   preview()  → استخراج + مراجعة + ملخص الأثر، **بلا كتابة** (importPreview.ts)
 *   commit()   → تأكيد ذري داخل UnitOfWork
 *
 * السبب في الفصل: «إعادة الضغط أو إعادة فتح التطبيق لا تعيد الالتزام بالدفعة»
 * و«صفر أو كامل الدفعة، لا نصفها» (spec/05 و spec/06).
 */
export function makeImportStatement(deps: ImportStatementDeps) {
  async function preview(request: ImportRequest): Promise<ImportPreview> {
    return runPreview(deps, request)
  }

  function buildTransaction(id: Id, line: ImportPreviewLine, now: string): Transaction {
    const txn: Transaction = {
      id,
      occurredAt: line.row.date,
      datePrecision: 'day',
      sourceOrder: line.row.lineNumber,
      // ⚠️ النوع الاقتصادي **لا يُستنتج من اتجاه السيولة** (spec/02).
      // يبدأ غير محدد وينتظر تأكيد المستخدم في المراجعة.
      economicKind: 'unclassified',
      economicKindConfirmed: false,
      observedDirection: line.row.direction,
      amountMinor: line.row.amountMinor,
      currency: 'SAR',
      categoryConfirmed: false,
      excludedFromBudget: false,
      reviewState: line.categoryId ? 'suggested' : 'needs_review',
      isCashTagged: false,
      rawDescription: line.row.description,
      rawMerchantName: line.row.merchantName,
      createdAt: now,
      updatedAt: now,
    }
    if (line.categoryId) txn.categoryId = line.categoryId
    if (line.row.sourceCategory) txn.sourceCategory = line.row.sourceCategory
    if (line.row.sourceOperationType) txn.sourceOperationType = line.row.sourceOperationType
    return txn
  }

  /**
   * المرحلة الثانية: الالتزام الذري.
   * `selectedLineNumbers` تسمح بتضمين متشابه أو استبعاد جديد **بقرار صريح**.
   */
  async function commit(
    request: ImportRequest,
    previewResult: ImportPreview,
    selectedLineNumbers?: readonly number[],
  ): Promise<ImportBatch> {
    const selection = selectedLineNumbers
      ? new Set(selectedLineNumbers)
      : new Set(previewResult.lines.filter((l) => l.selectedByDefault).map((l) => l.row.lineNumber))

    // التعارض لا يُكتب أبدًا بلا حسم صريح — لا استبدال صامت (spec/05)
    for (const line of previewResult.lines) {
      if (selection.has(line.row.lineNumber) && line.state === 'conflict') {
        throw new Error(`الصف ${line.row.lineNumber} فيه تعارض ولازم تحسمه الأول. ${line.reason}`)
      }
    }

    return deps.uow.run(async () => {
      const batchId = deps.ids.next('batch')
      const now = deps.clock.nowIso()
      const transactions: Transaction[] = []
      const records: SourceRecord[] = []

      for (const line of previewResult.lines) {
        const included = selection.has(line.row.lineNumber)
        const txnId = included ? deps.ids.next('txn') : null
        if (txnId) transactions.push(buildTransaction(txnId, line, now))

        records.push({
          id: deps.ids.next('src'),
          batchId,
          accountIdentity: request.accountIdentity,
          sourceReference: line.row.reference,
          sourceHash: hashContent(line.row.raw),
          originalRowIndex: line.row.lineNumber,
          rawLine: line.row.raw,
          transactionId: txnId,
          matchingState: line.state,
          reason: line.reason,
        })
      }

      await deps.txns.saveMany(transactions)
      await deps.sources.saveMany(records)

      const batch: ImportBatch = {
        id: batchId,
        sourceType: request.sourceType,
        fileHash: previewResult.fileHash,
        fileName: request.fileName,
        importedAt: now,
        state: 'committed',
        counts: {
          total: previewResult.counts.total,
          imported: transactions.length,
          duplicates: previewResult.counts.duplicates,
          similar: previewResult.counts.similar,
          conflicts: previewResult.counts.conflicts,
          invalid: previewResult.counts.invalid,
        },
      }
      await deps.batches.save(batch)
      return batch
    })
  }

  return { preview, commit }
}
