import { describe, it, expect } from 'vitest'
import { makeManagePeople } from '../../src/application/useCases/managePeople'
import { makeManageProfile } from '../../src/application/useCases/manageProfile'
import { makeOnboardNewAccount } from '../../src/application/useCases/onboardNewAccount'
import {
  MemoryAllocationRepository, MemoryObligationRepository, MemoryPersonRepository, MemorySettlementRepository,
  MemoryTransactionRepository, MemoryWalletRepository, PassthroughUnitOfWork, SequentialIdGenerator, FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryProfileRepository } from '../../src/infrastructure/memory/memoryProfileRepository'
import { memoryAccount } from '../../src/infrastructure/memory/memoryAccount'
import { emptyProfile } from '../../src/domain/userProfile'
import { checkFullBackupData } from '../../src/domain/checkFullBackup'
import { checkBackupFinance } from '../../src/domain/checkBackupFinance'
import { emptyBackupData } from '../../src/domain/fullBackup'
import type { Wallet } from '../../src/domain/entities/types'

/** أسئلة البداية + الدين القديم من غير عملية — OVERRIDES §26–27. */

const BANK: Wallet = { id: 'wallet-bank', name: 'الراجحي', currency: 'SAR', kind: 'bank', openingBalanceMinor: 0, openingAt: '2026-09-13' }
const CASH: Wallet = { id: 'wallet-cash', name: 'كاش', currency: 'SAR', kind: 'cash', openingBalanceMinor: 0, openingAt: '2026-09-13' }

function system() {
  const clock = new FixedClock('2026-09-13T15:00:00.000Z')
  const txns = new MemoryTransactionRepository()
  const wallets = new MemoryWalletRepository([BANK, CASH])
  const obligations = new MemoryObligationRepository()
  const profiles = new MemoryProfileRepository()
  const profile = makeManageProfile({ profiles, account: memoryAccount(), clock })
  const people = makeManagePeople({
    people: new MemoryPersonRepository(), obligations, settlements: new MemorySettlementRepository(),
    allocations: new MemoryAllocationRepository(), txns, uow: new PassthroughUnitOfWork(), ids: new SequentialIdGenerator(), clock,
  })
  return { txns, wallets, obligations, profiles, profile, people, onboard: makeOnboardNewAccount({ profile, people, wallets, clock }) }
}

describe('الدين القديم من غير عملية', () => {
  it('بيظهر في رصيد الشخص ويتسدد، ومفيش عملية وهمية', async () => {
    const s = system()
    const person = await s.people.addPerson('أحمد')
    const debt = await s.people.addOpeningDebt({ personId: person.id, kind: 'receivable', amountMinor: 50_000 })
    expect(debt.originTransactionId).toBeNull()
    const [row] = await s.people.listWithBalances()
    expect(row.balance.receivableMinor).toBe(50_000)
    await s.people.settle({ obligationId: debt.id, personId: person.id, amountMinor: 20_000 })
    expect((await s.people.listWithBalances())[0].balance.receivableMinor).toBe(30_000)
    expect(await s.txns.listByDateRange('2000-01-01', '2100-01-01')).toEqual([])
    expect(await s.obligations.listByTransactionIds(['anything'])).toEqual([])
  })

  it('يرفض مبلغ صفر أو شخص مش موجود', async () => {
    const s = system()
    const person = await s.people.addPerson('أحمد')
    await expect(s.people.addOpeningDebt({ personId: person.id, kind: 'receivable', amountMinor: 0 })).rejects.toThrow('أكبر من صفر')
    await expect(s.people.addOpeningDebt({ personId: 'nobody', kind: 'loan_payable', amountMinor: 100 })).rejects.toThrow('مش موجود')
  })

  it('النسخة الشاملة بتقبل الدين القديم، وبترفضه لو مبلغه مش موجب', () => {
    const data = emptyBackupData()
    data.people.push({ id: 'p1', name: 'أحمد', archived: false })
    data.obligations.push({ id: 'o1', personId: 'p1', originTransactionId: null, kind: 'receivable', originalMinor: 50_000, currency: 'SAR' })
    expect(() => { checkFullBackupData(data); checkBackupFinance(data) }).not.toThrow()
    data.obligations[0].originalMinor = 0
    expect(() => checkBackupFinance(data)).toThrow('موجب')
  })
})

describe('أسئلة البداية', () => {
  it('الكاش رصيد افتتاح بتاريخ النهارده، والديون في الأشخاص، والملف بيتعلّم خلصان', async () => {
    const s = system()
    await s.profile.markOnboardingPending()
    const result = await s.onboard({
      profile: { ...emptyProfile(), displayName: 'منى', payday: 25, salaryMinor: 900_000, onboardingPending: true },
      cashMinor: 75_050,
      debts: [
        { name: 'أحمد', kind: 'receivable', amountMinor: 50_000 },
        { name: 'خالد', kind: 'loan_payable', amountMinor: 20_000 },
      ],
    })
    expect(result).toEqual({ ok: true })
    expect(await s.wallets.findById('wallet-cash')).toMatchObject({ openingBalanceMinor: 75_050, openingAt: '2026-09-13' })
    expect(await s.wallets.findById('wallet-bank')).toEqual(BANK)
    const rows = await s.people.listWithBalances()
    expect(rows.map((r) => [r.person.name, r.balance.receivableMinor, r.balance.payableLoanMinor]).sort())
      .toEqual([['أحمد', 50_000, 0], ['خالد', 0, 20_000]])
    const saved = await s.profile.load()
    expect(saved).toMatchObject({ displayName: 'منى', payday: 25, salaryMinor: 900_000, onboardingPending: false, onboardedAt: '2026-09-13T15:00:00.000Z' })
    expect(s.profile.needsOnboarding(saved)).toBe(false)
    expect(await s.txns.listByDateRange('2000-01-01', '2100-01-01')).toEqual([])
  })

  it('غلطة في أي خطوة ⇒ مفيش ولا كتابة، والخطأ بيقول الخطوة', async () => {
    const s = system()
    const bad = await s.onboard({ profile: emptyProfile(), cashMinor: 1000, debts: [{ name: 'أحمد', kind: 'receivable', amountMinor: 0 }] })
    expect(bad).toMatchObject({ ok: false, step: 'debts' })
    expect(await s.wallets.findById('wallet-cash')).toEqual(CASH)
    expect(await s.people.listWithBalances()).toEqual([])
    expect(await s.profiles.load()).toBeNull()
    expect(await s.onboard({ profile: { ...emptyProfile(), payday: 40 }, cashMinor: null, debts: [] })).toMatchObject({ ok: false, step: 'profile' })
    expect(await s.onboard({ profile: emptyProfile(), cashMinor: -5, debts: [] })).toMatchObject({ ok: false, step: 'cash' })
  })

  it('تخطي الكاش ما يلمسش محفظة الكاش، والتكرار بعد انقطاع ما يضاعفش شخص ولا دين', async () => {
    const s = system()
    const input = { profile: emptyProfile(), cashMinor: null, debts: [{ name: ' أحمد ', kind: 'receivable' as const, amountMinor: 50_000 }] }
    expect(await s.onboard(input)).toEqual({ ok: true })
    expect(await s.wallets.findById('wallet-cash')).toEqual(CASH)
    expect(await s.onboard(input)).toEqual({ ok: true })
    const rows = await s.people.listWithBalances()
    expect(rows).toHaveLength(1)
    expect(rows[0].obligations).toHaveLength(1)
    expect(rows[0].balance.receivableMinor).toBe(50_000)
  })
})
