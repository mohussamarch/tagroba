import { describe, it, expect } from 'vitest'
import { statementChainBreaks } from '../../src/domain/balanceChainCheck'
import { emptyStoredData, type StoredRow } from '../../src/domain/idRepair'
import type { BackupRow } from '../../src/domain/fullBackup'
import { makeCleanupOrphans } from '../../src/application/useCases/cleanupOrphans'
import { memoryIdRepair } from '../../src/infrastructure/memory/idRepair'
import { memoryRepairBackup } from '../../src/infrastructure/memory/repairBackup'
import { sanitizeAccountNumbers as redact } from '../../src/infrastructure/firestore/firestoreRepositories'

/**
 * دليل سلسلة رصيد الكشف قبل تنظيف البقايا (HANDOVER §36): مكرر = كسر، عملية حقيقية
 * اتشالت = كسر، والتنظيف الصح = صفر.
 */

const line = (docId: string, date: string, order: number, amount: number, direction: 'in' | 'out', stated: number,
  extra: BackupRow = {}): StoredRow => ({
  docId, data: { id: docId, occurredAt: date, sourceOrder: order, amountMinor: amount, observedDirection: direction, statedBalanceMinor: stated, ...extra },
})

// رصيد يبدأ 10000: −1500 ⇒ 8500، +3000 ⇒ 11500، −900 ⇒ 10600
const statement = [
  line('a', '2026-08-01', 1, 1500, 'out', 8500),
  line('b', '2026-08-02', 1, 3000, 'in', 11500),
  line('c', '2026-08-03', 1, 900, 'out', 10600),
]

describe('سلسلة رصيد الكشف', () => {
  it('كشف متسق ⇒ صفر كسر، والسطر اللي من غير رصيد مش داخل', () => {
    const manual: StoredRow = { docId: 'm', data: { id: 'm', occurredAt: '2026-08-02', amountMinor: 500, observedDirection: 'out' } }
    expect(statementChainBreaks([...statement, manual], new Set())).toEqual({ checked: 3, breaks: 0, breakMonths: {} })
  })

  it('نسخة مكررة ⇒ كسر واحد، وشيلها ⇒ صفر', () => {
    const rows = [...statement, line('a-dup', '2026-08-01', 1, 1500, 'out', 8500)]
    expect(statementChainBreaks(rows, new Set()).breaks).toBe(1)
    expect(statementChainBreaks(rows, new Set(['a-dup'])).breaks).toBe(0)
  })

  it('شيل عملية حقيقية ⇒ كسر في شهرها — فالتنظيف الغلط بيبان', () => {
    expect(statementChainBreaks(statement, new Set(['b']))).toEqual({ checked: 2, breaks: 1, breakMonths: { '2026-08': 1 } })
  })

  it('ما يرجعش مبالغ', () => {
    const text = JSON.stringify(statementChainBreaks([...statement, line('x', '2026-08-01', 1, 1500, 'out', 8500)], new Set()))
    expect(text).not.toMatch(/1500|8500|11500|10600/)
  })

  it('المعاينة بتدي التلات حالات: المكرر بيتشال والتوأم الحقيقي ما بيتلمسش', async () => {
    const s = emptyStoredData()
    s.importBatches.push({ docId: 'b-excel', data: { id: 'b-excel', state: 'reverted', importedAt: '2026-09-07T10:30:00.000Z' } })
    s.importBatches.push({ docId: 'b-pdf', data: { id: 'b-pdf', state: 'committed', importedAt: '2026-09-12T12:16:00.000Z' } })
    for (const row of statement) {
      s.transactions.push(line(row.docId, row.data.occurredAt as string, 1, row.data.amountMinor as number,
        row.data.observedDirection as 'in' | 'out', row.data.statedBalanceMinor as number, { createdAt: '2026-09-12T12:16:00.000Z' }))
      s.sourceRecords.push({ docId: `r-${row.docId}`, data: { id: `r-${row.docId}`, batchId: 'b-pdf', transactionId: row.docId } })
    }
    // بقايا الإكسل: نسخة من «a» ليها توأم في نفس اليوم، ونسخة من «b» تاريخها في الإكسل متأخر يوم
    // (فمالهاش توأم «نفس اليوم» ومش هتتمسح — بس السلسلة بتكشف إنها مكررة)
    s.transactions.push(line('x-a', '2026-08-01', 1, 1500, 'out', 8500, { createdAt: '2026-09-07T10:34:00.000Z' }))
    s.transactions.push(line('x-b', '2026-08-03', 2, 3000, 'in', 11500, { createdAt: '2026-09-07T10:34:00.000Z' }))
    const store = memoryIdRepair(s)
    const preview = await makeCleanupOrphans({ port: store, remover: store, redact, backup: memoryRepairBackup(), clock: { nowIso: () => '2026-09-13T00:00:00.000Z' } }).preview()
    expect(preview.transactions.map((i) => i.docId)).toEqual(['x-a'])
    expect(preview.chain.now.breaks).toBe(2)
    expect(preview.chain.afterCleanup.breaks).toBe(1)
    expect(preview.chain.ifUnprovenRemovedToo.breaks).toBe(0)
  })
})
