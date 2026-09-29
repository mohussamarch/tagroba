import { makeExportBackup } from '../../src/application/useCases/exportBackup'
import { MemoryTransactionRepository, MemorySourceRecordRepository, MemoryImportBatchRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryCategoryRepository, MemoryMerchantRepository, MemoryRuleRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryBudgetRepository } from '../../src/infrastructure/memory/memoryBudgetRepository'
import { MemoryWalletRepository } from '../../src/infrastructure/memory/memoryWalletRepository'
import type { Transaction } from '../../src/domain/entities/types'
import { recordAsync, type GoldenCase } from './goldenKit'

/**
 * تصدير العمليات CSV (بمخطط المعاينة، فينفع يتستورد تاني) — الجزء المستعمل من `exportBackup.ts`.
 * نسخة الإصدار 1 نفسها **مش منقولة** بقرار المالك (OVERRIDES §49). ⚠️ بيانات وهمية بالكامل.
 */

let n = 0
function txn(occurredAt: string, over: Partial<Transaction> = {}): Transaction {
  n++
  return {
    id: `t-${String(n).padStart(3, '0')}`, occurredAt, datePrecision: 'day', sourceOrder: n % 3,
    economicKind: 'purchase', economicKindConfirmed: true, observedDirection: 'out', amountMinor: 1_000 * n + 5, currency: 'SAR',
    categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested', isCashTagged: false,
    createdAt: '2026-09-01T00:00:00.000Z', updatedAt: '2026-09-01T00:00:00.000Z',
    ...over,
  }
}

async function exportCsvCases() {
  const cases: GoldenCase[] = []
  n = 0
  const transactions: Transaction[] = [
    txn('2025-12-30', { rawMerchantName: 'TEST MART', walletId: 'w-bank' }),
    txn('2026-01-01', { rawMerchantName: 'Cafe, "Corner"', walletId: 'w-bank', sourceOrder: 2 }),
    txn('2026-01-01', { rawDescription: 'سطر\nتاني', walletId: 'w-cash', sourceOrder: 1 }),
    txn('2026-01-27', { observedDirection: 'in', economicKind: 'salary', amountMinor: 1_250_000_00, rawMerchantName: 'EMPLOYER' }),
    txn('2026-01-28', { rawMerchantName: '', rawDescription: '' }),
    txn('2026-02-15', { amountMinor: 7, rawMerchantName: 'صغير' }),
    txn('2026-03-10', { rawMerchantName: 'carriage\rreturn' }),
    txn('2020-05-05', { rawMerchantName: 'قديم جدًا' }),
  ]
  const runs = [
    { from: '2026-01-01', to: '2026-02-28', payday: 28 },
    { from: '2026-01-01', to: '2026-02-28', payday: 1 },
    { from: '2025-12-29', to: '2026-01-01', payday: 28 },
    { from: '2026-03-11', to: '2026-03-31', payday: 28 },
    // أكتر من 60 فترة ⇒ التصدير بيقف عند 60 (قيد في التطبيق الحالي، متسجل زي ما هو)
    { from: '2020-01-01', to: '2026-12-31', payday: 28 },
  ]
  for (const r of runs) {
    cases.push(await recordAsync({ transactions, ...r }, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(transactions)
      const exporter = makeExportBackup({
        txns, sources: new MemorySourceRecordRepository(), batches: new MemoryImportBatchRepository(), wallets: new MemoryWalletRepository(),
        categories: new MemoryCategoryRepository(), rules: new MemoryRuleRepository(), merchants: new MemoryMerchantRepository(), budgets: new MemoryBudgetRepository(),
      })
      return await exporter.exportCsv(r)
    }))
  }
  return cases
}

export async function csvExportGolden() {
  return { exportCsv: await exportCsvCases() }
}
