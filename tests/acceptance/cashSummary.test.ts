import { describe, it, expect } from 'vitest'
import { summarizeCash } from '../../src/domain/cashSummary'
import { makeLoadCashSummary } from '../../src/application/useCases/loadCashSummary'
import { MemoryAllocationRepository, MemoryCategoryRepository, MemoryTransactionRepository, MemoryWalletRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { buildPeriod } from '../../src/domain/period'
import { parseMoney } from '../../src/domain/money'
import type { Transaction, Wallet } from '../../src/domain/entities/types'

/** الكاش لوحده — OVERRIDES §32. أرقام وهمية. */
const cash: Wallet = { id: 'wallet-cash', name: 'كاش', currency: 'SAR', kind: 'cash', openingBalanceMinor: parseMoney('1000.00'), openingAt: '2026-09-06' }
const period = buildPeriod(2026, 8, 28) // 2026-08-28 → 2026-09-27
let n = 0
const tx = (over: Partial<Transaction>): Transaction => ({
  id: `t${++n}`, occurredAt: '2026-09-10', datePrecision: 'day', sourceOrder: n, economicKind: 'purchase', economicKindConfirmed: true,
  observedDirection: 'out', amountMinor: parseMoney('50.00'), currency: 'SAR', categoryConfirmed: false, excludedFromBudget: false,
  reviewState: 'confirmed', isCashTagged: false, createdAt: '', updatedAt: '', walletId: 'wallet-cash', ...over,
})

describe('ملخص الكاش', () => {
  it('الرصيد = البداية + الداخل − الخارج من يوم البداية بس', () => {
    const rows = [
      tx({ amountMinor: parseMoney('120.00') }),
      tx({ observedDirection: 'in', economicKind: 'debt_collected', amountMinor: parseMoney('30.00') }),
      tx({ walletId: 'wallet-bank', transferToWalletId: 'wallet-cash', economicKind: 'internal_transfer', amountMinor: parseMoney('200.00') }),
      tx({ occurredAt: '2026-09-01', amountMinor: parseMoney('999.00') }), // قبل البداية ⇒ داخل في رصيد البداية
    ]
    const s = summarizeCash({ wallet: cash, transactions: rows, allocations: [], period })
    expect(s.inSinceOpeningMinor).toBe(parseMoney('230.00'))
    expect(s.outSinceOpeningMinor).toBe(parseMoney('120.00'))
    expect(s.balanceMinor).toBe(parseMoney('1110.00'))
  })

  it('المصروف من الكاش في الفترة: المحفظة أو «اتدفعت كاش»، بعد نصيب الأشخاص، ومن غير المستبعد والتحويلات', () => {
    const rows = [
      tx({ id: 'a', amountMinor: parseMoney('100.00') }),
      tx({ id: 'b', walletId: 'wallet-bank', isCashTagged: true, amountMinor: parseMoney('40.00') }),
      tx({ id: 'c', excludedFromBudget: true, amountMinor: parseMoney('70.00') }),
      tx({ id: 'd', walletId: 'wallet-bank', transferToWalletId: 'wallet-cash', economicKind: 'internal_transfer', amountMinor: parseMoney('500.00') }),
      tx({ id: 'e', walletId: 'wallet-bank', amountMinor: parseMoney('60.00') }), // بنك ومش معلّم ⇒ مش كاش
    ]
    const allocations = [{ id: 'al', transactionId: 'a', personId: 'p', allocationKind: 'receivable' as const, amountMinor: parseMoney('25.00'), currency: 'SAR' as const }]
    const s = summarizeCash({ wallet: cash, transactions: rows, allocations, period })
    expect(s.spentInPeriodMinor).toBe(parseMoney('115.00'))
    expect(s.periodTransactions.map((t) => t.id).sort()).toEqual(['a', 'b', 'c', 'd'])
  })

  it('حساب من غير محفظة كاش ⇒ null مش صفر', async () => {
    const load = makeLoadCashSummary({ wallets: new MemoryWalletRepository(), txns: new MemoryTransactionRepository(), allocations: new MemoryAllocationRepository(), categories: new MemoryCategoryRepository() })
    expect(await load({ period, today: '2026-09-15' })).toBeNull()
  })
})
