import { describe, it, expect } from 'vitest'
import { reportChainBreaks } from '../../src/domain/chainBreakReport'
import { emptyStoredData, type StoredData } from '../../src/domain/idRepair'
import type { BackupGroup, BackupRow } from '../../src/domain/fullBackup'
import { sanitizeAccountNumbers as redact } from '../../src/infrastructure/firestore/firestoreRepositories'

/** الكسور الباقية بعد التنظيف — جاية منين (قراءة بس، أعداد بس). */

const PDF_AT = '2026-09-12T12:16:00.000Z'
const EXCEL_AT = '2026-09-07T10:30:00.000Z'

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

describe('تقرير الكسور الباقية', () => {
  it('كشف متسق ⇒ مفيش كسور', () => {
    expect(reportChainBreaks(account(), redact, new Set())).toEqual([])
  })

  it('بقايا إكسل مكررة بالكامل في نفس اليوم ⇒ «بقايا متراجَع عنها» ليها توأم كامل', () => {
    const s = account()
    s.transactions.push({ docId: 'x2', data: { id: 'x2', ...line('2026-08-02', 1, 500, 8500, '2026-09-07T10:34:00.000Z') } })
    expect(reportChainBreaks(s, redact, new Set())).toEqual([
      { origin: 'revertedLeftover', previousOrigin: 'statement', batch: null, sameDayAsPrevious: true, hasExactTwin: true, count: 1 },
    ])
  })

  it('عملية حقيقية ناقصة من الكشف ⇒ الكسر على السطر اللي بعدها، من كشف مسجّل ومن غير توأم', () => {
    const s = account()
    s.transactions = s.transactions.filter((r) => r.docId !== 'p2')
    expect(reportChainBreaks(s, redact, new Set())).toEqual([
      { origin: 'statement', previousOrigin: 'statement', batch: 'pdf_alrajhi 2026-09-12', sameDayAsPrevious: false, hasExactTwin: false, count: 1 },
    ])
  })

  it('عملية يدوية متسقة مع رصيدها ما بتنكسرش هي — اللي بعدها هو اللي بينكسر، واللي قبله «من غير مصدر»', () => {
    const s = account()
    // 8500 − 100 = 8400 ⇒ السطر اليدوي نفسه سليم، بس سطر الكشف اللي بعده متوقع يبدأ من 8500
    s.transactions.push({ docId: 'manual', data: { id: 'manual', ...line('2026-08-02', 2, 100, 8400, '2026-09-01T09:00:00.000Z') } })
    expect(reportChainBreaks(s, redact, new Set())).toEqual([
      { origin: 'statement', previousOrigin: 'noSource', batch: 'pdf_alrajhi 2026-09-12', sameDayAsPrevious: false, hasExactTwin: false, count: 1 },
    ])
    expect(reportChainBreaks(s, redact, new Set(['manual']))).toEqual([])
  })

  it('ما يرجعش مبالغ ولا معرّفات', () => {
    const s = account()
    s.transactions.push({ docId: 'x2', data: { id: 'x2', ...line('2026-08-02', 1, 500, 8500, '2026-09-07T10:34:00.000Z') } })
    const text = JSON.stringify(reportChainBreaks(s, redact, new Set()))
    expect(text).not.toMatch(/8500|500|x2|p2/)
  })
})
