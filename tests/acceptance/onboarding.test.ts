import { describe, it, expect } from 'vitest'
import { makeManagePeople } from '../../src/application/useCases/managePeople'
import { memorySettlementWriter } from '../../src/infrastructure/memory/settlementWriter'
import { makeManageProfile } from '../../src/application/useCases/manageProfile'
import { makeOnboardAccount } from '../../src/application/useCases/onboardAccount'
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

/** أسئلة البداية (لأي حساب ما خلصهاش) + الدين القديم من غير عملية — OVERRIDES §26–27. */

const BANK: Wallet = { id: 'wallet-bank', name: 'الراجحي', currency: 'SAR', kind: 'bank', openingBalanceMinor: 0, openingAt: '2026-09-13' }
const CASH: Wallet = { id: 'wallet-cash', name: 'كاش', currency: 'SAR', kind: 'cash', openingBalanceMinor: 0, openingAt: '2026-09-13' }

function system(cash: Wallet = CASH) {
  const clock = new FixedClock('2026-09-13T15:00:00.000Z')
  const txns = new MemoryTransactionRepository()
  const wallets = new MemoryWalletRepository([BANK, cash])
  const obligations = new MemoryObligationRepository()
  const settlements = new MemorySettlementRepository()
  const profiles = new MemoryProfileRepository()
  const profile = makeManageProfile({ profiles, account: memoryAccount(), clock })
  const people = makeManagePeople({
    people: new MemoryPersonRepository(), obligations, settlements,
    settlementWriter: memorySettlementWriter(obligations, settlements),
    allocations: new MemoryAllocationRepository(), txns, uow: new PassthroughUnitOfWork(), ids: new SequentialIdGenerator(), clock,
  })
  return { txns, wallets, obligations, profiles, profile, people, onboarding: makeOnboardAccount({ profile, people, wallets, clock }) }
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
  it('إعادة شاشة قديمة بعد تسوية الدين بالكامل لا تعيد إنشاء الدين', async () => {
    const s = system()
    const input = { profile: emptyProfile(), cashMinor: null, debts: [{ name: 'شخص تجربة', kind: 'receivable' as const, amountMinor: 10000 }] }
    await s.onboarding.finish(input)
    const [row] = await s.people.listWithBalances()
    await s.people.settle({ obligationId: row.obligations[0].obligation.id, personId: row.person.id, amountMinor: 10000 })
    await s.onboarding.finish(input)
    expect((await s.people.listWithBalances())[0].balance.receivableMinor).toBe(0)
    expect(await s.obligations.listByPerson(row.person.id)).toHaveLength(1)
  })

  it('إعادة إعداد مكتمل لا تكتب فوق تعديلات الحساب والكاش اللاحقة', async () => {
    const s = system()
    const input = { profile: emptyProfile(), cashMinor: 5000, debts: [] }
    await s.onboarding.finish(input)
    const saved = { ...await s.profile.load(), displayName: 'اسم جديد', payday: 25 }
    await s.profile.save(saved)
    const cash = { ...CASH, openingBalanceMinor: 9000, openingAt: '2026-09-16' }
    await s.wallets.save(cash)
    expect(await s.onboarding.finish(input)).toEqual({ ok: true })
    expect(await s.profile.load()).toEqual(saved)
    expect(await s.wallets.findById(CASH.id)).toEqual(cash)
  })

  it('الكاش رصيد افتتاح بتاريخ النهارده، والديون في الأشخاص، والملف بيتعلّم خلصان', async () => {
    const s = system()
    const result = await s.onboarding.finish({
      profile: { ...emptyProfile(), displayName: 'منى', payday: 25, salaryMinor: 900_000 },
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
    expect(saved).toMatchObject({ displayName: 'منى', payday: 25, salaryMinor: 900_000, onboardedAt: '2026-09-13T15:00:00.000Z' })
    expect(s.profile.needsOnboarding(saved)).toBe(false)
    expect(await s.txns.listByDateRange('2000-01-01', '2100-01-01')).toEqual([])
  })

  it('غلطة في أي خطوة ⇒ مفيش ولا كتابة، والخطأ بيقول الخطوة', async () => {
    const s = system()
    const bad = await s.onboarding.finish({ profile: emptyProfile(), cashMinor: 1000, debts: [{ name: 'أحمد', kind: 'receivable', amountMinor: 0 }] })
    expect(bad).toMatchObject({ ok: false, step: 'debts' })
    expect(await s.wallets.findById('wallet-cash')).toEqual(CASH)
    expect(await s.people.listWithBalances()).toEqual([])
    expect(await s.profiles.load()).toBeNull()
    expect(await s.onboarding.finish({ profile: { ...emptyProfile(), payday: 40 }, cashMinor: null, debts: [] })).toMatchObject({ ok: false, step: 'profile' })
    expect(await s.onboarding.finish({ profile: emptyProfile(), cashMinor: -5, debts: [] })).toMatchObject({ ok: false, step: 'cash' })
  })

  it('تخطي الكاش ما يلمسش محفظة الكاش، والتكرار بعد انقطاع ما يضاعفش شخص ولا دين', async () => {
    const s = system()
    const input = { profile: emptyProfile(), cashMinor: null, debts: [{ name: ' أحمد ', kind: 'receivable' as const, amountMinor: 50_000 }] }
    expect(await s.onboarding.finish(input)).toEqual({ ok: true })
    expect(await s.wallets.findById('wallet-cash')).toEqual(CASH)
    expect(await s.onboarding.finish(input)).toEqual({ ok: true })
    const rows = await s.people.listWithBalances()
    expect(rows).toHaveLength(1)
    expect(rows[0].obligations).toHaveLength(1)
    expect(rows[0].balance.receivableMinor).toBe(50_000)
  })
})

describe('أسئلة البداية لحساب قديم', () => {
  const OLD_CASH: Wallet = { ...CASH, openingBalanceMinor: 100_000, openingAt: '2026-09-06' }

  it('بتبدأ بالموجود: بيانات الملف ورصيد بداية الكاش', async () => {
    const s = system(OLD_CASH)
    await s.profile.save({ ...emptyProfile(), displayName: 'محمد', salaryMinor: 1_100_000, payday: 28 })
    expect(await s.onboarding.start()).toEqual({
      profile: { ...emptyProfile(), displayName: 'محمد', salaryMinor: 1_100_000, payday: 28 },
      cashOpening: { amountMinor: 100_000, openingAt: '2026-09-06' },
    })
    expect((await system().onboarding.start()).cashOpening).toBeNull() // حساب جديد: الرصيد لسه صفر
  })

  it('نفس رصيد الكاش بالظبط ⇒ المحفظة ما تتلمسش حتى تاريخها', async () => {
    const s = system(OLD_CASH)
    expect(await s.onboarding.finish({ profile: emptyProfile(), cashMinor: 100_000, debts: [] })).toEqual({ ok: true })
    expect(await s.wallets.findById('wallet-cash')).toEqual(OLD_CASH)
    expect(s.profile.needsOnboarding(await s.profile.load())).toBe(false)
  })

  it('رصيد كاش مختلف ⇒ رصيد بداية جديد من النهارده', async () => {
    const s = system(OLD_CASH)
    expect(await s.onboarding.finish({ profile: emptyProfile(), cashMinor: 40_000, debts: [] })).toEqual({ ok: true })
    expect(await s.wallets.findById('wallet-cash')).toEqual({ ...OLD_CASH, openingBalanceMinor: 40_000, openingAt: '2026-09-13' })
  })
})
