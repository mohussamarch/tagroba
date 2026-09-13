import { describe, it, expect } from 'vitest'
import { reportChainBreaks } from '../../src/domain/chainBreakReport'
import { emptyStoredData, type StoredData } from '../../src/domain/idRepair'
import type { BackupGroup, BackupRow } from '../../src/domain/fullBackup'
import { makeCleanupOrphans } from '../../src/application/useCases/cleanupOrphans'
import { memoryIdRepair } from '../../src/infrastructure/memory/idRepair'
import { memoryRepairBackup } from '../../src/infrastructure/memory/repairBackup'
import { sanitizeAccountNumbers as redact } from '../../src/infrastructure/firestore/firestoreRepositories'

/** الكسور الباقية بعد التنظيف — جاية منين (قراءة بس، أعداد بس). */

const PDF_AT = '2026-09-12T12:16:00.000Z'
const EXCEL_AT = '2026-09-07T10:30:00.000Z'
const LEFTOVER_AT = '2026-09-07T10:34:00.000Z'

function line(day: string, order: number, amount: number, stated: number, createdAt: string): BackupRow {
  return { occurredAt: day, sourceOrder: order, amountMinor: amount, observedDirection: 'out', statedBalanceMinor: stated, createdAt }
}

function account(): StoredData {
  const s = emptyStoredData()
  const put = (group: BackupGroup, docId: string, data: BackupRow) => s[group].push({ docId, data: { id: docId, ...data } })
  put('importBatches', 'b-pdf', { state: 'committed', sourceType: 'pdf_alrajhi', importedAt: PDF_AT })
  put('importBatches', 'b-excel', { state: 'reverted', sourceType: 'csv_legacy', importedAt: EXCEL_AT })
  // كشف متسق: 10000 ⇒ 9000 ⇒ 8500 ⇒ 8000
  put('transactions', 'p1', line('2026-08-01', 1, 1000, 9000, PDF_AT))
  put('transactions', 'p2', line('2026-08-02', 1, 500, 8500, PDF_AT))
  put('transactions', 'p3', line('2026-08-03', 1, 500, 8000, PDF_AT))
  for (const id of ['p1', 'p2', 'p3']) put('sourceRecords', `r-${id}`, { batchId: 'b-pdf', transactionId: id })
  return s
}

const base = { cleanupStatus: null, twinCleanupStatus: null }

describe('تقرير الكسور الباقية', () => {
  it('كشف متسق ⇒ مفيش كسور', () => {
    expect(reportChainBreaks(account(), redact, new Set())).toEqual([])
  })

  it('بقايا إكسل مكررة بالكامل في نفس اليوم ⇒ «بقايا متراجَع عنها» نسختها من الكشف', () => {
    const s = account()
    s.transactions.push({ docId: 'x2', data: { id: 'x2', ...line('2026-08-02', 1, 500, 8500, LEFTOVER_AT) } })
    expect(reportChainBreaks(s, redact, new Set())).toEqual([
      { ...base, origin: 'revertedLeftover', previousOrigin: 'statement', batch: null, sameDayAsPrevious: true, hasExactTwin: true, twinOrigin: 'statement', count: 1 },
    ])
  })

  it('عملية حقيقية ناقصة من الكشف ⇒ الكسر على السطر اللي بعدها، من كشف مسجّل ومن غير توأم', () => {
    const s = account()
    s.transactions = s.transactions.filter((r) => r.docId !== 'p2')
    expect(reportChainBreaks(s, redact, new Set())).toEqual([
      { ...base, origin: 'statement', previousOrigin: 'statement', batch: 'pdf_alrajhi 2026-09-12', sameDayAsPrevious: false, hasExactTwin: false, twinOrigin: null, count: 1 },
    ])
  })

  it('عملية يدوية متسقة مع رصيدها ما بتنكسرش هي — اللي بعدها هو اللي بينكسر، واللي قبله «من غير مصدر»', () => {
    const s = account()
    // 8500 − 100 = 8400 ⇒ السطر اليدوي نفسه سليم، بس سطر الكشف اللي بعده متوقع يبدأ من 8500
    s.transactions.push({ docId: 'manual', data: { id: 'manual', ...line('2026-08-02', 2, 100, 8400, '2026-09-01T09:00:00.000Z') } })
    expect(reportChainBreaks(s, redact, new Set())).toEqual([
      { ...base, origin: 'statement', previousOrigin: 'noSource', batch: 'pdf_alrajhi 2026-09-12', sameDayAsPrevious: false, hasExactTwin: false, twinOrigin: null, count: 1 },
    ])
    expect(reportChainBreaks(s, redact, new Set(['manual']))).toEqual([])
  })

  it('من المعاينة: مكرر إكسل متربط بشخص بيبان «متربط» عشان يبان ليه ما اتمسحش', async () => {
    const s = account()
    s.transactions.push({ docId: 'x2', data: { id: 'x2', ...line('2026-08-02', 1, 500, 8500, LEFTOVER_AT) } })
    s.allocations.push({ docId: 'a1', data: { id: 'a1', transactionId: 'x2', personId: 'person' } })
    const store = memoryIdRepair(s)
    const preview = await makeCleanupOrphans({ port: store, remover: store, redact, backup: memoryRepairBackup(), clock: { nowIso: () => PDF_AT } }).preview()
    expect(preview.transactions).toEqual([])
    expect(preview.linked.map((i) => i.docId)).toEqual(['x2'])
    expect(preview.remainingBreaks).toEqual([
      { origin: 'revertedLeftover', previousOrigin: 'statement', batch: null, sameDayAsPrevious: true, hasExactTwin: true,
        cleanupStatus: 'linked', twinOrigin: 'statement', twinCleanupStatus: 'notLeftover', count: 1 },
    ])
  })

  it('ما يرجعش مبالغ ولا معرّفات', () => {
    const s = account()
    s.transactions.push({ docId: 'x2', data: { id: 'x2', ...line('2026-08-02', 1, 500, 8500, LEFTOVER_AT) } })
    const text = JSON.stringify(reportChainBreaks(s, redact, new Set()))
    expect(text).not.toMatch(/8500|500|x2|p2/)
  })
})
