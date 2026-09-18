import { expect, it } from 'vitest'
import { memorySettlementWriter } from '../../src/infrastructure/memory/settlementWriter'
import { MemoryObligationRepository, MemorySettlementRepository } from '../../src/infrastructure/memory/memoryRepositories'
import type { Obligation } from '../../src/domain/entities/types'

const debt: Obligation = { id: 'debt', personId: 'person', kind: 'receivable', originalMinor: 10000, originTransactionId: null, currency: 'SAR' }
const command = { id: 'request', personId: 'person', obligationId: 'debt', transactionId: '', amountMinor: 7000 }
async function system() {
  const obligations = new MemoryObligationRepository(), settlements = new MemorySettlementRepository()
  await obligations.saveMany([debt])
  return { settlements, first: memorySettlementWriter(obligations, settlements), second: memorySettlementWriter(obligations, settlements) }
}
it('independent callers cannot settle 140 against a debt of 100', async () => {
  const sys = await system()
  const results = await Promise.allSettled([sys.first.settle(command), sys.second.settle({ ...command, id: 'second' })])
  expect(results.filter(result => result.status === 'fulfilled')).toHaveLength(1)
  expect((await sys.settlements.listByObligations(['debt'])).reduce((sum, row) => sum + row.amountMinor, 0)).toBe(7000)
})
it('a lost response can be retried with the same request, even after full settlement', async () => {
  const sys = await system(), input = { ...command, amountMinor: 10000 }
  const [a, b] = await Promise.all([sys.first.settle(input), sys.second.settle(input)])
  expect(a).toEqual(b)
  expect(await sys.first.settle(input)).toEqual(a)
  expect(await sys.settlements.listByObligations(['debt'])).toHaveLength(1)
})
it('reusing a request ID with a different payload fails instead of overwriting money', async () => {
  const sys = await system()
  await sys.first.settle(command)
  await expect(sys.second.settle({ ...command, amountMinor: 2000 })).rejects.toThrow('بيانات مختلفة')
  expect((await sys.settlements.listByObligations(['debt']))[0].amountMinor).toBe(7000)
})
it.each([NaN, Infinity, 1.5, 0, -1])('rejects invalid minor units %s', async amountMinor => {
  const sys = await system()
  await expect(sys.first.settle({ ...command, amountMinor })).rejects.toThrow()
  expect(await sys.settlements.listByObligations(['debt'])).toEqual([])
})
it('checks person ownership inside the writer', async () => {
  await expect((await system()).first.settle({ ...command, personId: 'other' })).rejects.toThrow('للشخص')
})
