import { describe, it, expect } from 'vitest'
import { emptyStoredData, type StoredData } from '../../src/domain/idRepair'
import { planOrphanCleanup } from '../../src/domain/orphanCleanup'
import type { BackupGroup, BackupRow } from '../../src/domain/fullBackup'
import { makeCleanupOrphans } from '../../src/application/useCases/cleanupOrphans'
import { memoryIdRepair } from '../../src/infrastructure/memory/idRepair'
import { memoryRepairBackup } from '../../src/infrastructure/memory/repairBackup'
import { sanitizeAccountNumbers as redact } from '../../src/infrastructure/firestore/firestoreRepositories'

/**
 * بقايا التراجع عن دفعة الإكسل (قرار المالك 2026-09-13). الحساب هنا بيمثّل اللي حصل:
 * كشف إكسل اتستورد، وبعدين اتراجعنا عنه بحذف بمعرّفات مقصوصة، وبعدين نفس الكشف اتستورد PDF.
 */

const EXCEL = 'batch-excel'
const PDF = 'batch-pdf'
const EXCEL_AT = '2026-09-07T10:30:00.000Z'
const PDF_AT = '2026-09-12T12:16:00.000Z'

function txn(day: string, amount: number, createdAt: string, extra: BackupRow = {}): BackupRow {
  return { occurredAt: day, amountMinor: amount, observedDirection: 'out', walletId: 'wallet-bank', createdAt, ...extra }
}

function account(): StoredData {
  const s = emptyStoredData()
  const put = (group: BackupGroup, docId: string, data: BackupRow) => s[group].push({ docId, data: { id: docId, ...data } })
  put('importBatches', EXCEL, { state: 'reverted', sourceType: 'csv_legacy', importedAt: EXCEL_AT })
  put('importBatches', PDF, { state: 'committed', sourceType: 'pdf_alrajhi', importedAt: PDF_AT })

  // الـPDF: توأمين مسجّلين
  put('transactions', 'pdf-1', txn('2026-08-01', 1500, PDF_AT))
  put('transactions', 'pdf-2', txn('2026-07-15', 900, PDF_AT))
  put('sourceRecords', 'rec-pdf-1', { batchId: PDF, transactionId: 'pdf-1', matchingState: 'new' })
  put('sourceRecords', 'rec-pdf-2', { batchId: PDF, transactionId: 'pdf-2', matchingState: 'new' })

  // بقايا الإكسل: عملية مكررة ليها توأم، وعملية متربطة بشخص، وعملية من غير توأم
  put('transactions', 'excel-dup', txn('2026-08-01', 1500, '2026-09-07T10:34:00.000Z'))
  put('transactions', 'excel-linked', txn('2026-07-15', 900, '2026-09-07T10:34:00.000Z'))
  put('allocations', 'alloc-1', { transactionId: 'excel-linked', personId: 'p1' })
  put('transactions', 'excel-alone', txn('2026-06-01', 700, '2026-09-07T10:35:00.000Z'))
  // سجلات مصدر للدفعة المتراجَع عنها: واحد بيشاور على عملية اتمسحت، وواحد على المكررة
  put('sourceRecords', 'rec-excel-gone', { batchId: EXCEL, transactionId: 'excel-deleted', matchingState: 'new' })
  put('sourceRecords', 'rec-excel-dup', { batchId: EXCEL, transactionId: 'excel-dup', matchingState: 'new' })

  // عملية يدوية — مالهاش علاقة بأي دفعة
  put('transactions', 'manual', txn('2026-08-01', 1500, '2026-09-02T18:00:00.000Z'))
  return s
}

const clock = { nowIso: () => '2026-09-13T11:00:00.000Z' }

describe('تنظيف بقايا دفعة متراجَع عنها', () => {
  it('يمسح سجلات الدفعة المتراجَع عنها والمكررة اللي ليها توأم بس، ويعد اللي مش هيتمسح', () => {
    const plan = planOrphanCleanup(account(), redact)
    expect(plan.transactions.map((i) => i.docId)).toEqual(['excel-dup'])
    expect(plan.records.map((i) => i.docId).sort()).toEqual(['rec-excel-dup', 'rec-excel-gone'])
    expect(plan.byMonth).toEqual({ '2026-08': 1 })
    expect(plan.keptLinked).toBe(1)
    expect(plan.keptNoEvidence).toBe(1)
  })

  it('ما يمسحش أكتر من عدد التوائم: عمليتين مكررتين قصاد توأم واحد ⇒ واحدة بس', () => {
    const s = account()
    s.transactions.push({ docId: 'excel-dup-2', data: { id: 'excel-dup-2', ...txn('2026-08-01', 1500, '2026-09-07T10:34:30.000Z') } })
    const plan = planOrphanCleanup(s, redact)
    expect(plan.transactions).toHaveLength(1)
    expect(plan.keptNoEvidence).toBe(2)
  })

  it('محفظة مختلفة مش توأم', () => {
    const s = account()
    s.transactions.find((r) => r.docId === 'excel-dup')!.data.walletId = 'wallet-cash'
    expect(planOrphanCleanup(s, redact).transactions).toEqual([])
  })

  it('العملية اللي عليها سجل من دفعة سليمة — حتى بشكل معرّفها المقصوص — مش بقايا', () => {
    const s = account()
    const id = 'txn-0mtqh4w12345-aaaaaaaaaaaa'
    s.transactions.push({ docId: id, data: { id, ...txn('2026-08-01', 1500, '2026-09-07T10:34:00.000Z') } })
    s.sourceRecords.push({ docId: 'rec-pdf-3', data: { id: 'rec-pdf-3', batchId: PDF, transactionId: redact(id), matchingState: 'duplicate' } })
    expect(planOrphanCleanup(s, redact).transactions.map((i) => i.docId)).not.toContain(id)
  })

  it('التطبيق: نسخة مؤكدة الحجم فيها المستندات بالظبط ⇒ حذف ⇒ الفحص التاني فاضي', async () => {
    const store = memoryIdRepair(account())
    const backup = memoryRepairBackup()
    const cleanup = makeCleanupOrphans({ port: store, remover: store, redact, backup, clock })
    const outcome = await cleanup.apply(await cleanup.preview())
    expect(outcome.removed).toBe(3)
    expect(outcome.backup?.bytes).toBe(outcome.backup?.expectedBytes)
    const saved = JSON.parse([...backup.files.values()][0])
    expect(saved.documents.map((d: { docId: string }) => d.docId).sort()).toEqual(['excel-dup', 'rec-excel-dup', 'rec-excel-gone'])
    const after = await cleanup.preview()
    expect(after.transactions).toEqual([])
    expect(after.records).toEqual([])
    const left = store.snapshot().transactions.map((r) => r.docId).sort()
    expect(left).toEqual(['excel-alone', 'excel-linked', 'manual', 'pdf-1', 'pdf-2'])
  })

  it('نسخة ناقصة ⇒ ما يتمسحش ولا مستند', async () => {
    const store = memoryIdRepair(account())
    const cleanup = makeCleanupOrphans({ port: store, remover: store, redact, backup: memoryRepairBackup({ truncate: true }), clock })
    await expect(cleanup.apply(await cleanup.preview())).rejects.toThrow('ناقصة')
    expect(store.snapshot().transactions).toHaveLength(6)
    expect(store.snapshot().sourceRecords).toHaveLength(4)
  })

  it('الحذف نجح بس الرد ضاع ⇒ خطأ واضح، والفحص التاني فاضي ومفيش حذف مرتين', async () => {
    const store = memoryIdRepair(account())
    const lostReply = { remove: async (items: Parameters<typeof store.remove>[0]) => { await store.remove(items); throw new Error('انقطع الاتصال') } }
    const first = makeCleanupOrphans({ port: store, remover: lostReply, redact, backup: memoryRepairBackup(), clock })
    await expect(first.apply(await first.preview())).rejects.toThrow('اتمسح 0 من 3 قبل الانقطاع')
    const second = makeCleanupOrphans({ port: store, remover: store, redact, backup: memoryRepairBackup(), clock })
    const rest = await second.preview()
    expect(rest.transactions.length + rest.records.length).toBe(0)
    expect(await second.apply(rest)).toEqual({ removed: 0, skipped: 0, backup: null })
  })
})
