import { describe, it, expect } from 'vitest'
import { AmountEditError, planAmountEdit, sourceAmountMinor } from '../../src/domain/amountEdit'
import { transactionContentKey } from '../../src/domain/mergeBackup'
import { makeEditTransaction } from '../../src/application/useCases/editTransaction'
import {
  MemoryAllocationRepository,
  MemoryCategoryRepository,
  MemorySettlementRepository,
  MemoryTransactionRepository,
  PassthroughUnitOfWork,
  SequentialIdGenerator,
  FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryTagRepository, MemoryTransactionTagRepository } from '../../src/infrastructure/memory/memoryTagRepositories'
import { parseMoney } from '../../src/domain/money'
import type { Transaction } from '../../src/domain/entities/types'

/** تعديل مبلغ العملية — OVERRIDES §32. */

const base: Transaction = {
  id: 't1', occurredAt: '2026-09-01', datePrecision: 'day', sourceOrder: 1,
  economicKind: 'purchase', economicKindConfirmed: true, observedDirection: 'out',
  amountMinor: parseMoney('200.00'), currency: 'SAR', categoryConfirmed: false, excludedFromBudget: false,
  reviewState: 'confirmed', isCashTagged: false, createdAt: '', updatedAt: '', rawMerchantName: 'Demo Shop',
}

describe('خطة تعديل المبلغ', () => {
  it('200 ⇒ 180: الجديد بيتحسب، والأصلي بيتحفظ مرة واحدة بس', () => {
    const first = planAmountEdit(base, parseMoney('180.00'), [], [])
    expect(first).toEqual({ amountMinor: parseMoney('180.00'), originalAmountMinor: parseMoney('200.00') })
    const second = planAmountEdit({ ...base, ...first }, parseMoney('150.00'), [], [])
    expect(second.originalAmountMinor).toBe(parseMoney('200.00'))
  })

  it('صفر أو سالب أو كسور هللة ⇒ رفض بتفسير', () => {
    expect(() => planAmountEdit(base, 0, [], [])).toThrow(AmountEditError)
    expect(() => planAmountEdit(base, -100, [], [])).toThrow(AmountEditError)
    expect(() => planAmountEdit(base, 10.5, [], [])).toThrow(AmountEditError)
  })

  it('ما ينزلش تحت المتوزع على أشخاص أو المتسوّى بيه دين', () => {
    expect(() => planAmountEdit(base, parseMoney('50.00'), [{ amountMinor: parseMoney('60.00') }], [])).toThrow(/متوزع/)
    expect(() => planAmountEdit(base, parseMoney('50.00'), [], [{ amountMinor: parseMoney('70.00') }])).toThrow(/اتسدد/)
    expect(planAmountEdit(base, parseMoney('60.00'), [{ amountMinor: parseMoney('60.00') }], []).amountMinor).toBe(parseMoney('60.00'))
  })

  it('مطابقة التكرار بتستعمل الأصلي: نفس سطر الكشف بعد التعديل لسه «نفس العملية»', () => {
    const edited = { ...base, ...planAmountEdit(base, parseMoney('180.00'), [], []) }
    expect(sourceAmountMinor(edited)).toBe(parseMoney('200.00'))
    expect(transactionContentKey(edited)).toBe(transactionContentKey(base))
  })
})

describe('تعديل المبلغ من صفحة العملية', () => {
  it('بيتحفظ بالمبلغ الجديد والأصلي، وبيرفض أقل من المتوزع', async () => {
    const txns = new MemoryTransactionRepository()
    await txns.saveMany([base])
    const allocations = new MemoryAllocationRepository()
    const edit = makeEditTransaction({
      txns, categories: new MemoryCategoryRepository(), tags: new MemoryTagRepository(), transactionTags: new MemoryTransactionTagRepository(),
      uow: new PassthroughUnitOfWork(), ids: new SequentialIdGenerator(), clock: new FixedClock('2026-09-15T10:00:00.000Z'),
      allocations, settlements: new MemorySettlementRepository(),
    })
    await edit.setAmount('t1', parseMoney('180.00'))
    const [saved] = await txns.findByIds(['t1'])
    expect(saved.amountMinor).toBe(parseMoney('180.00'))
    expect(saved.originalAmountMinor).toBe(parseMoney('200.00'))

    await allocations.saveMany([{ id: 'a1', transactionId: 't1', personId: 'p', allocationKind: 'receivable', amountMinor: parseMoney('100.00'), currency: 'SAR' }])
    await expect(edit.setAmount('t1', parseMoney('90.00'))).rejects.toThrow(/متوزع/)
    expect((await txns.findByIds(['t1']))[0].amountMinor).toBe(parseMoney('180.00'))
  })
})
