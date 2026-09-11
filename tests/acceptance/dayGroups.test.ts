import { describe, it, expect } from 'vitest'
import { groupByDay } from '../../src/domain/dayGroups'
import { parseMoney } from '../../src/domain/money'
import type { Transaction } from '../../src/domain/entities/types'

function txn(
  id: string,
  occurredAt: string,
  amount: string,
  direction: 'in' | 'out',
  sourceOrder = 0,
): Transaction {
  return {
    id,
    occurredAt,
    datePrecision: 'day',
    sourceOrder,
    economicKind: 'unclassified',
    economicKindConfirmed: false,
    observedDirection: direction,
    amountMinor: parseMoney(amount),
    currency: 'SAR',
    categoryConfirmed: false,
    excludedFromBudget: false,
    reviewState: 'needs_review',
    isCashTagged: false,
    createdAt: '2026-09-11T00:00:00.000Z',
    updatedAt: '2026-09-11T00:00:00.000Z',
  }
}

describe('تجميع العمليات بالأيام — شاشة العمليات', () => {
  it('بيجمّع كل يوم لوحده والأحدث فوق', () => {
    const groups = groupByDay([
      txn('a', '2026-09-09', '10.00', 'out'),
      txn('b', '2026-09-11', '20.00', 'out'),
      txn('c', '2026-09-10', '30.00', 'out'),
    ])
    expect(groups.map((g) => g.date)).toEqual(['2026-09-11', '2026-09-10', '2026-09-09'])
  })

  it('مجموع الصادر والوارد منفصلين — مفيش رقم بيخلطهم', () => {
    const groups = groupByDay([
      txn('a', '2026-09-11', '128.50', 'out'),
      txn('b', '2026-09-11', '32.00', 'out'),
      txn('c', '2026-09-11', '1500.00', 'in'),
    ])
    expect(groups).toHaveLength(1)
    expect(groups[0].outgoingMinor).toBe(parseMoney('160.50'))
    expect(groups[0].incomingMinor).toBe(parseMoney('1500.00'))
    expect(groups[0].transactions).toHaveLength(3)
  })

  it('جوه اليوم الواحد: آخر سطر في الكشف يظهر فوق', () => {
    const groups = groupByDay([
      txn('first', '2026-09-11', '10.00', 'out', 1),
      txn('last', '2026-09-11', '20.00', 'out', 9),
    ])
    expect(groups[0].transactions.map((t) => t.id)).toEqual(['last', 'first'])
  })

  it('قايمة فاضية ⇒ مفيش مجموعات، ومفيش أصفار مخترعة', () => {
    expect(groupByDay([])).toEqual([])
  })
})
