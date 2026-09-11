import { it, expect } from 'vitest'
import { makeImportStatement, type ImportRequest } from '../../src/application/useCases/importStatement'
import { makeRevertImportBatch, REVERT_BLOCKED_MESSAGE } from '../../src/application/useCases/revertImportBatch'
import {
  MemoryAllocationRepository, MemoryCategoryRepository, MemoryImportBatchRepository, MemoryMerchantRepository,
  MemoryObligationRepository, MemoryRuleRepository, MemorySettlementRepository, MemorySourceRecordRepository,
  MemoryTransactionRepository, PassthroughUnitOfWork, SequentialIdGenerator, FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import type { ParsedRow } from '../../src/infrastructure/import/schemas'

/** سجل الاستيرادات + التراجع بمعاينة (HANDOVER §30، بند 1-ب). */

function system() {
  const txns = new MemoryTransactionRepository()
  const sources = new MemorySourceRecordRepository()
  const batches = new MemoryImportBatchRepository()
  const shared = {
    txns, sources, batches, categories: new MemoryCategoryRepository(), merchants: new MemoryMerchantRepository(),
    rules: new MemoryRuleRepository(), uow: new PassthroughUnitOfWork(), ids: new SequentialIdGenerator(),
  }
  const importAt = (iso: string) => makeImportStatement({ ...shared, clock: new FixedClock(iso) })
  const revert = makeRevertImportBatch({
    txns, sources, batches, settlements: new MemorySettlementRepository(),
    allocations: new MemoryAllocationRepository(), obligations: new MemoryObligationRepository(), uow: shared.uow,
  })
  return { txns, sources, batches, importAt, revert }
}

function statement(fileName: string, date: string): ImportRequest {
  const row = (n: number, amountMinor: number, balance: number): ParsedRow => ({
    lineNumber: n, date, amountMinor, direction: 'out', merchantName: `M${n}`, reference: null,
    sourceName: 'الراجحي', statedBalanceMinor: balance, description: `d${n}`, raw: `${fileName}-${n}`,
  })
  return { fileName, content: fileName, parsedRows: [row(1, 1000, 90000), row(2, 2000, 88000)],
    sourceType: 'pdf_alrajhi', accountIdentity: 'الراجحي', walletId: 'wallet-bank' }
}

it('السجل يعرض الاستيرادات الأحدث أولًا', async () => {
  const s = system()
  const older = statement('07.pdf', '2026-08-25')
  await s.importAt('2026-09-07T10:34:00Z').commit(older, await s.importAt('2026-09-07T10:34:00Z').preview(older))
  const newer = statement('08.pdf', '2026-08-26')
  await s.importAt('2026-09-08T06:30:00Z').commit(newer, await s.importAt('2026-09-08T06:30:00Z').preview(newer))
  expect((await s.revert.history()).map((b) => b.fileName)).toEqual(['08.pdf', '07.pdf'])
})

it('المعاينة لا تكتب شيئًا، والتنفيذ يشيل عمليات الدفعة بس ويعلّمها متراجَع عنها', async () => {
  const s = system()
  const keep = statement('keep.pdf', '2026-08-26')
  await s.importAt('2026-09-08T06:30:00Z').commit(keep, await s.importAt('2026-09-08T06:30:00Z').preview(keep))
  const dup = statement('dup.pdf', '2026-08-25')
  const batch = await s.importAt('2026-09-07T10:34:00Z').commit(dup, await s.importAt('2026-09-07T10:34:00Z').preview(dup))

  const plan = await s.revert.plan(batch.id)
  expect(plan).toMatchObject({ expectedCount: 2, recordsFound: 2, blocked: false })
  expect(plan.toDelete).toHaveLength(2)
  expect(await s.txns.listByDateRange('2026-08-01', '2026-08-31')).toHaveLength(4) // المعاينة ما شالتش حاجة

  await s.revert.execute(batch.id)
  const left = await s.txns.listByDateRange('2026-08-01', '2026-08-31')
  expect(left.map((t) => t.occurredAt)).toEqual(['2026-08-26', '2026-08-26'])
  expect((await s.batches.findById(batch.id))?.state).toBe('reverted')
})

it('دفعة سجّلت عمليات ومفيش ولا سجل مصدر ليها ⇒ التراجع مرفوض ولا يعلّمها متراجَع عنها', async () => {
  const s = system()
  const req = statement('broken.pdf', '2026-08-25')
  const batch = await s.importAt('2026-09-07T10:34:00Z').commit(req, await s.importAt('2026-09-07T10:34:00Z').preview(req))
  // محاكاة معرّفات تالفة: سجلات المصدر مش لاقية الدفعة
  await s.sources.deleteMany((await s.sources.listByBatch(batch.id)).map((r) => r.id))

  const plan = await s.revert.plan(batch.id)
  expect(plan).toMatchObject({ blocked: true, expectedCount: 2, recordsFound: 0 })
  await expect(s.revert.execute(batch.id)).rejects.toThrow(REVERT_BLOCKED_MESSAGE)
  expect((await s.batches.findById(batch.id))?.state).toBe('committed')
  expect(await s.txns.listByDateRange('2026-08-01', '2026-08-31')).toHaveLength(2)
})
