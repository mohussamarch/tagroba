import { makeManageProfile } from '../../src/application/useCases/manageProfile'
import { makeOnboardAccount, type OnboardingInput } from '../../src/application/useCases/onboardAccount'
import { makeSeedWallets } from '../../src/application/useCases/seedWallets'
import { makeSeedUserReferences, type SeedSource } from '../../src/application/useCases/seedUserReferences'
import { makeManagePeople } from '../../src/application/useCases/managePeople'
import { MemoryProfileRepository } from '../../src/infrastructure/memory/memoryProfileRepository'
import { memoryAccount } from '../../src/infrastructure/memory/memoryAccount'
import { MemoryWalletRepository } from '../../src/infrastructure/memory/memoryWalletRepository'
import { memoryReferenceSeed } from '../../src/infrastructure/memory/referenceSeed'
import { MemoryTransactionRepository } from '../../src/infrastructure/memory/memoryRepositories'
import {
  MemoryAllocationRepository,
  MemoryCategoryRepository,
  MemoryMerchantRepository,
  MemoryObligationRepository,
  MemoryRuleRepository,
  MemorySettlementRepository,
} from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryPersonRepository } from '../../src/infrastructure/memory/memoryPeopleRepositories'
import { memorySettlementWriter } from '../../src/infrastructure/memory/settlementWriter'
import { MemoryUnitOfWork, SequentialIdGenerator, FixedClock } from '../../src/infrastructure/memory/memorySupport'
import { emptyProfile, type UserProfile } from '../../src/domain/userProfile'
import type { Category, ClassificationRule, Merchant, Obligation, Person, Wallet } from '../../src/domain/entities/types'
import { recordAsync, type GoldenCase } from './goldenKit'

/**
 * الحساب: الملف الشخصي · أسئلة البداية · المحافظ الأولى · المراجع الأولى (OVERRIDES §26–27). ⚠️ بيانات وهمية بالكامل.
 * ⚠️ مسار «أرصدة المالك» في `seedWallets` **مش هنا عن قصد**: فيه أرصدة حساب المالك، والمستودع عام،
 * والتطبيق الجديد مش محتاجه (نفس حساب فايربيز ونفس بياناته — KOTLIN_PLAN §1). الموجود: المستخدم الجديد برصيد صفر.
 */

const NOW = '2026-09-22T10:00:00.000Z'

const profileSaved: UserProfile = {
  ...emptyProfile(), displayName: 'مستخدم تجريبي', salaryMinor: 1_000_000, payday: 25, gender: 'male',
  supportsDependents: true, dependentKinds: ['children'], hasCar: true,
}

async function manageProfileCases() {
  const cases: GoldenCase[] = []
  type Step =
    | { kind: 'load' | 'email' | 'sendPasswordReset' }
    | { kind: 'save' | 'completeOnboarding' | 'needsOnboarding'; profile: UserProfile }
  const runs: { stored: UserProfile | null; email: string | null; steps: Step[] }[] = [
    { stored: null, email: 'demo@example.com', steps: [{ kind: 'load' }, { kind: 'email' }, { kind: 'sendPasswordReset' }] },
    { stored: profileSaved, email: null, steps: [{ kind: 'load' }, { kind: 'email' }] },
    { stored: null, email: null, steps: [{ kind: 'sendPasswordReset' }] },
    { stored: null, email: 'demo@example.com', steps: [{ kind: 'save', profile: { ...profileSaved, displayName: '  اسم بمسافات  ' } }, { kind: 'load' }] },
    { stored: profileSaved, email: 'demo@example.com', steps: [{ kind: 'save', profile: { ...profileSaved, salaryMinor: -5 } }, { kind: 'load' }] },
    { stored: profileSaved, email: 'demo@example.com', steps: [{ kind: 'save', profile: { ...profileSaved, payday: 32 } }] },
    { stored: null, email: 'demo@example.com', steps: [{ kind: 'completeOnboarding', profile: profileSaved }, { kind: 'load' }] },
    { stored: null, email: 'demo@example.com', steps: [{ kind: 'completeOnboarding', profile: { ...profileSaved, onboardedAt: '2026-09-01T08:00:00.000Z' } }] },
    { stored: null, email: 'demo@example.com', steps: [{ kind: 'needsOnboarding', profile: profileSaved }, { kind: 'needsOnboarding', profile: { ...profileSaved, onboardedAt: '2026-09-01T08:00:00.000Z' } }, { kind: 'needsOnboarding', profile: { ...profileSaved, onboardedAt: '' } }] },
  ]
  for (const r of runs) {
    cases.push(await recordAsync(r, async () => {
      const account = memoryAccount(r.email)
      const manage = makeManageProfile({ profiles: new MemoryProfileRepository(r.stored), account, clock: new FixedClock(NOW) })
      const out: unknown[] = []
      for (const step of r.steps) {
        switch (step.kind) {
          case 'load': out.push(await manage.load()); break
          case 'email': out.push(manage.email()); break
          case 'sendPasswordReset': await manage.sendPasswordReset(); out.push({ resetsSent: account.resetsSent }); break
          case 'save': out.push(await manage.save(step.profile)); break
          case 'completeOnboarding': out.push(await manage.completeOnboarding(step.profile)); break
          case 'needsOnboarding': out.push(manage.needsOnboarding(step.profile)); break
        }
      }
      return out
    }))
  }
  return cases
}

/** `seedWallets` بيقرا تاريخ الجهاز (`new Date()`) — بيتثبت هنا على `NOW` وقت المولّد بس، وكوتلن بتاخده من `Clock`. */
async function withFrozenDate<T>(run: () => Promise<T>): Promise<T> {
  const RealDate = Date
  class FrozenDate extends RealDate {
    constructor(...args: [] | [string | number | Date]) {
      super(args.length === 0 ? NOW : args[0]!)
    }
    static now() { return new RealDate(NOW).getTime() }
  }
  globalThis.Date = FrozenDate as unknown as DateConstructor
  try {
    return await run()
  } finally {
    globalThis.Date = RealDate
  }
}

async function seedWalletsCases() {
  const cases: GoldenCase[] = []
  const existing: Wallet[] = [{ id: 'w-mine', name: 'محفظتي', currency: 'SAR', kind: 'cash', openingBalanceMinor: 12_300, openingAt: '2026-09-01' }]
  for (const wallets of [[], existing]) {
    cases.push(await recordAsync({ wallets }, () => withFrozenDate(async () => {
      const repo = new MemoryWalletRepository(wallets)
      const outcome = await makeSeedWallets({ wallets: repo })(false)
      return { outcome, storedWallets: await repo.listAll() }
    })))
  }
  return cases
}

async function seedUserReferencesCases() {
  const cases: GoldenCase[] = []
  const source: SeedSource = {
    categories: [
      { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
      { id: 'fuel', parentId: null, name: 'بنزين', iconKey: 'fuel', lightColor: '#1f5aa4', darkColor: '#8cbcf0', active: true, order: 2, groupKey: 'transport' },
    ],
    rules: [
      { id: 'r-1', priority: 10, matchText: 'مطعم', matchMode: 'contains', categoryId: 'food', enabled: true },
      { id: 'r-2', priority: 20, matchText: 'محطة', matchMode: 'contains', categoryId: 'fuel', enabled: true },
    ],
    merchants: [{ id: 'm-1', displayName: 'TEST FUEL', normalizedName: 'test fuel', verifiedCategoryId: 'fuel' }],
  }
  const edited: Category = { ...source.categories[0]!, name: 'أكل معدّل' }
  const oldRule: ClassificationRule = { id: 'r-1', priority: 5, matchText: 'قديمة', matchMode: 'exact', categoryId: 'food', enabled: false }
  const runs: { categories: Category[]; rules: ClassificationRule[]; merchants: Merchant[]; times: number }[] = [
    { categories: [], rules: [], merchants: [], times: 2 },
    // حساب قديم من غير علامة ومعاه تصنيفات ⇒ بيتعتبر خلصان، وما يتلمسش
    { categories: [edited], rules: [], merchants: [], times: 1 },
    // من غير تصنيفات بس فيه قاعدة بنفس المعرّف ⇒ الناقص بس بيتضاف، والموجود ما يتكتبش فوقه
    { categories: [], rules: [oldRule], merchants: [], times: 1 },
  ]
  for (const r of runs) {
    cases.push(await recordAsync({ ...r, source }, async () => {
      const categories = new MemoryCategoryRepository(r.categories)
      const rules = new MemoryRuleRepository(r.rules)
      const merchants = new MemoryMerchantRepository(r.merchants)
      const seed = makeSeedUserReferences({ categories, rules, merchants, progress: memoryReferenceSeed({ categories, rules, merchants }) })
      const outcomes = []
      for (let i = 0; i < r.times; i++) outcomes.push(await seed(source))
      return { outcomes, storedCategories: await categories.listAll(), storedRules: await rules.listAll(), storedMerchants: await merchants.listAll() }
    }))
  }
  return cases
}

async function onboardAccountCases() {
  const cases: GoldenCase[] = []
  const cashWallet: Wallet = { id: 'w-cash', name: 'كاش', currency: 'SAR', kind: 'cash', openingBalanceMinor: 50_000, openingAt: '2026-09-01' }
  const zeroCash: Wallet = { ...cashWallet, openingBalanceMinor: 0 }
  const bankWallet: Wallet = { id: 'w-bank', name: 'بنك تجريبي', currency: 'SAR', kind: 'bank', openingBalanceMinor: 0, openingAt: '2026-09-01' }
  const people: Person[] = [{ id: 'p-ahmed', name: 'أحمد', archived: false }]
  const openingDebt: Obligation = { id: 'ob-old', personId: 'p-ahmed', originTransactionId: null, kind: 'receivable', originalMinor: 20_000, currency: 'SAR' }
  const input = (over: Partial<OnboardingInput> = {}): OnboardingInput => ({ profile: profileSaved, cashMinor: null, debts: [], ...over })

  interface Run { profile: UserProfile | null; wallets: Wallet[]; people: Person[]; obligations: Obligation[]; action: 'start' | OnboardingInput }
  const runs: Run[] = [
    { profile: null, wallets: [cashWallet, bankWallet], people: [], obligations: [], action: 'start' },
    { profile: profileSaved, wallets: [zeroCash], people: [], obligations: [], action: 'start' },
    { profile: null, wallets: [], people: [], obligations: [], action: 'start' },
    // كامل: كاش جديد + دين لشخص موجود + دين لشخص جديد مرتين بنفس الاسم
    {
      profile: null, wallets: [cashWallet], people, obligations: [], action: input({
        cashMinor: 75_000,
        debts: [{ name: ' أحمد ', kind: 'loan_payable', amountMinor: 30_000 }, { name: 'سارة', kind: 'receivable', amountMinor: 10_000 }, { name: 'سارة', kind: 'loan_payable', amountMinor: 5_000 }],
      }),
    },
    // نفس رصيد الكاش بالظبط ⇒ المحفظة ما تتلمسش · نفس الدين القديم ⇒ ما يتكررش
    { profile: null, wallets: [cashWallet], people, obligations: [openingDebt], action: input({ cashMinor: 50_000, debts: [{ name: 'أحمد', kind: 'receivable', amountMinor: 20_000 }] }) },
    // خلص الأسئلة قبل كده ⇒ شاشة قديمة ما تكتبش حاجة
    { profile: { ...profileSaved, onboardedAt: '2026-09-01T08:00:00.000Z' }, wallets: [cashWallet], people: [], obligations: [], action: input({ cashMinor: 1 }) },
    { profile: null, wallets: [cashWallet], people: [], obligations: [], action: input({ profile: { ...profileSaved, payday: 0 } }) },
    { profile: null, wallets: [cashWallet], people: [], obligations: [], action: input({ cashMinor: -1 }) },
    { profile: null, wallets: [], people: [], obligations: [], action: input({ cashMinor: 100 }) },
    { profile: null, wallets: [cashWallet], people: [], obligations: [], action: input({ debts: [{ name: '   ', kind: 'receivable', amountMinor: 100 }] }) },
    { profile: null, wallets: [cashWallet], people: [], obligations: [], action: input({ debts: [{ name: 'م'.repeat(81), kind: 'receivable', amountMinor: 100 }] }) },
    { profile: null, wallets: [cashWallet], people: [], obligations: [], action: input({ debts: [{ name: 'خالد', kind: 'receivable', amountMinor: 0 }] }) },
    // الغلط في دين تاني ⇒ ولا الكاش اتكتب ولا الدين الأول
    { profile: null, wallets: [cashWallet], people: [], obligations: [], action: input({ cashMinor: 90_000, debts: [{ name: 'خالد', kind: 'receivable', amountMinor: 100 }, { name: 'منى', kind: 'receivable', amountMinor: 0 }] }) },
  ]
  for (const r of runs) {
    cases.push(await recordAsync(r, async () => {
      const profiles = new MemoryProfileRepository(r.profile)
      const wallets = new MemoryWalletRepository(r.wallets)
      const personRepo = new MemoryPersonRepository(r.people)
      const obligations = new MemoryObligationRepository()
      await obligations.saveMany(r.obligations)
      const settlements = new MemorySettlementRepository()
      const txns = new MemoryTransactionRepository()
      const ids = new SequentialIdGenerator()
      const clock = new FixedClock(NOW)
      const onboard = makeOnboardAccount({
        profile: makeManageProfile({ profiles, account: memoryAccount(), clock }),
        people: makeManagePeople({
          people: personRepo, obligations, settlements, allocations: new MemoryAllocationRepository(), txns,
          settlementWriter: memorySettlementWriter(obligations, settlements), uow: new MemoryUnitOfWork([txns]), ids, clock,
        }),
        wallets, clock,
      })
      const result = r.action === 'start' ? await onboard.start() : await onboard.finish(r.action)
      return {
        result,
        storedProfile: await profiles.load(),
        storedWallets: await wallets.listAll(),
        storedPeople: await personRepo.listAll(),
        storedObligations: await obligations.all(),
      }
    }))
  }
  return cases
}

export async function accountFlowGolden() {
  return {
    manageProfile: await manageProfileCases(),
    seedWallets: await seedWalletsCases(),
    seedUserReferences: await seedUserReferencesCases(),
    onboardAccount: await onboardAccountCases(),
  }
}
