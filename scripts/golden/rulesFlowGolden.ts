import { makeManageRules } from '../../src/application/useCases/manageRules'
import { makeManageRecurring } from '../../src/application/useCases/manageRecurring'
import { MemoryTransactionRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryCategoryRepository, MemoryMerchantRepository, MemoryRuleRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryRecurringRepository } from '../../src/infrastructure/memory/memoryRecurringRepository'
import { SequentialIdGenerator } from '../../src/infrastructure/memory/memorySupport'
import type { RecurringItem } from '../../src/domain/entities/recurring'
import type { Category, ClassificationRule, Merchant, RuleMatchMode, Transaction } from '../../src/domain/entities/types'
import { recordAsync } from './goldenKit'

/**
 * قواعد التصنيف والتجار + الاشتراكات والفواتير — حالات مكتوبة بالإيد. ⚠️ بيانات وهمية بالكامل.
 * القيود الحاكمة: القاعدة بتتقفل ما بتتحذفش، تعديل قاعدة ما بيعيدش تصنيف القديم،
 * والاسم المطبّع للتاجر ما بيتغيّرش مع إعادة التسمية (مفتاح المطابقة).
 */

const cats: Category[] = [
  { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
  { id: 'telecom', parentId: null, name: 'اتصالات', iconKey: 'wifi', lightColor: '#1f5aa4', darkColor: '#8cbcf0', active: true, order: 2, groupKey: 'home' },
]

const rules: ClassificationRule[] = [
  { id: 'r-b', priority: 20, matchText: 'مطعم', matchMode: 'contains', categoryId: 'food', enabled: true },
  { id: 'r-a', priority: 20, matchText: 'اتصالات', matchMode: 'contains', categoryId: 'telecom', enabled: true },
  { id: 'r-first', priority: 5, matchText: 'stc', matchMode: 'contains', categoryId: 'ghost-cat', enabled: false },
]

const merchants: Merchant[] = [
  { id: 'm-b', displayName: 'مطعم البيك', normalizedName: 'مطعم البيك', verifiedCategoryId: 'food' },
  { id: 'm-a', displayName: 'أمازون', normalizedName: 'امازون', aliases: ['amazon'] },
  { id: 'm-c', displayName: 'STC', normalizedName: 'stc', verifiedCategoryId: 'ghost-cat' },
]

type RulesAction =
  | { kind: 'listRules' | 'listMerchants' }
  | { kind: 'addRule'; matchText: string; matchMode: RuleMatchMode; categoryId: string; priority?: number }
  | { kind: 'updateRule'; id: string; patch: Partial<Omit<ClassificationRule, 'id'>> }
  | { kind: 'setRuleEnabled'; id: string; enabled: boolean }
  | { kind: 'setMerchantCategory'; merchantId: string; categoryId: string | null }
  | { kind: 'renameMerchant'; merchantId: string; displayName: string }
  | { kind: 'addAlias'; merchantId: string; raw: string }

export async function rulesFlowGolden() {
  const rulesCases = []
  const recurringCases = []

  async function runRules(action: RulesAction) {
    rulesCases.push(await recordAsync({ rules, merchants, categories: cats, action }, async () => {
      const ruleRepo = new MemoryRuleRepository(rules)
      const merchantRepo = new MemoryMerchantRepository(merchants)
      const manage = makeManageRules({
        rules: ruleRepo,
        merchants: merchantRepo,
        categories: new MemoryCategoryRepository(cats),
        ids: new SequentialIdGenerator(),
      })
      const stored = async () => ({
        storedRules: await ruleRepo.listAll(),
        storedMerchants: await merchantRepo.listAll(),
      })
      switch (action.kind) {
        case 'listRules': return { rows: await manage.listRules() }
        case 'listMerchants': return { rows: await manage.listMerchants() }
        case 'addRule': return { created: await manage.addRule(action), ...(await stored()) }
        case 'updateRule': await manage.updateRule(action.id, action.patch); return await stored()
        case 'setRuleEnabled': await manage.setRuleEnabled(action.id, action.enabled); return await stored()
        case 'setMerchantCategory': await manage.setMerchantCategory(action.merchantId, action.categoryId); return await stored()
        case 'renameMerchant': await manage.renameMerchant(action.merchantId, action.displayName); return await stored()
        case 'addAlias': await manage.addAlias(action.merchantId, action.raw); return await stored()
      }
    }))
  }

  await runRules({ kind: 'listRules' })
  await runRules({ kind: 'listMerchants' })
  await runRules({ kind: 'addRule', matchText: '  بنزين  ', matchMode: 'startsWith', categoryId: 'food' })
  await runRules({ kind: 'addRule', matchText: 'كهربا', matchMode: 'exact', categoryId: 'telecom', priority: 1 })
  await runRules({ kind: 'addRule', matchText: 'مطعم', matchMode: 'contains', categoryId: 'food' })
  await runRules({ kind: 'addRule', matchText: '   ', matchMode: 'contains', categoryId: 'food' })
  await runRules({ kind: 'addRule', matchText: 'ط'.repeat(61), matchMode: 'contains', categoryId: 'food' })
  await runRules({ kind: 'addRule', matchText: 'جديد', matchMode: 'contains', categoryId: 'c-ghost' })
  await runRules({ kind: 'updateRule', id: 'r-b', patch: { matchText: ' مطاعم ', priority: 3 } })
  await runRules({ kind: 'updateRule', id: 'r-ghost', patch: { priority: 1 } })
  await runRules({ kind: 'updateRule', id: 'r-b', patch: { matchText: '  ' } })
  await runRules({ kind: 'setRuleEnabled', id: 'r-a', enabled: false })
  await runRules({ kind: 'setMerchantCategory', merchantId: 'm-a', categoryId: 'telecom' })
  await runRules({ kind: 'setMerchantCategory', merchantId: 'm-b', categoryId: null })
  await runRules({ kind: 'setMerchantCategory', merchantId: 'm-ghost', categoryId: 'food' })
  await runRules({ kind: 'renameMerchant', merchantId: 'm-a', displayName: ' أمازون السعودية ' })
  await runRules({ kind: 'renameMerchant', merchantId: 'm-a', displayName: '  ' })
  await runRules({ kind: 'addAlias', merchantId: 'm-a', raw: ' Amazon.SA ' })
  await runRules({ kind: 'addAlias', merchantId: 'm-a', raw: 'amazon' })
  await runRules({ kind: 'addAlias', merchantId: 'm-b', raw: 'stc' })
  await runRules({ kind: 'addAlias', merchantId: 'm-ghost', raw: 'حاجة' })

  /* ─── الاشتراكات والفواتير ─── */
  function sub(id: string, merchant: string, date: string, amountMinor: number): Transaction {
    return {
      id, occurredAt: date, datePrecision: 'day', sourceOrder: 1,
      economicKind: 'purchase', economicKindConfirmed: true,
      observedDirection: 'out', amountMinor, currency: 'SAR',
      categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested',
      isCashTagged: false, rawMerchantName: merchant,
      createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z',
    }
  }

  // نتفلكس: تلات دورات شهرية متتالية بنفس المبلغ تقريبًا ⇒ مرشح اكتشاف
  // سبوتيفاي: متسجل خلاص كخطة ⇒ مش بيترشح تاني، وبيتحسب ملخصه
  const recurringTxns: Transaction[] = [
    sub('n-1', 'NETFLIX', '2026-06-10', 2999),
    sub('n-2', 'NETFLIX', '2026-07-10', 2999),
    sub('n-3', 'NETFLIX', '2026-08-10', 3050),
    sub('s-1', 'SPOTIFY', '2026-07-01', 1999),
    sub('s-2', 'SPOTIFY', '2026-08-01', 1999),
    sub('s-3', 'SPOTIFY', '2026-09-01', 1999),
    sub('x-1', 'مطعم البيك', '2026-08-15', 5000),
  ]
  const existingItems: RecurringItem[] = [
    { id: 'recurring:name:SPOTIFY|SAR', name: 'سبوتيفاي', merchantKey: 'name:SPOTIFY', kind: 'subscription', cycleMonths: 1, expectedMinor: 1999, currency: 'SAR', nextDueAt: '2026-10-01', active: true, confirmed: true },
  ]

  type RecurringAction =
    | { kind: 'load'; today: string }
    | { kind: 'save'; input: Omit<RecurringItem, 'id' | 'confirmed'> & { id?: string } }

  async function runRecurring(action: RecurringAction) {
    recurringCases.push(await recordAsync({ transactions: recurringTxns, items: existingItems, categories: cats, action }, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(recurringTxns)
      const items = new MemoryRecurringRepository()
      for (const item of existingItems) await items.save(item)
      const manage = makeManageRecurring({
        items, txns,
        categories: new MemoryCategoryRepository(cats),
        ids: new SequentialIdGenerator(),
      })
      if (action.kind === 'load') return { view: await manage.load(action.today) }
      const saved = await manage.save(action.input)
      return { saved, storedItems: await items.listAll() }
    }))
  }

  await runRecurring({ kind: 'load', today: '2026-09-22' })
  await runRecurring({
    kind: 'save',
    input: { name: 'نتفلكس', merchantKey: 'name:NETFLIX', kind: 'subscription', cycleMonths: 1, expectedMinor: 2999, currency: 'SAR', nextDueAt: '2026-09-10', active: true },
  })
  await runRecurring({
    kind: 'save',
    input: { id: 'recurring:name:SPOTIFY|SAR', name: 'سبوتيفاي عائلي', merchantKey: 'name:SPOTIFY', kind: 'subscription', cycleMonths: 1, expectedMinor: 2499, currency: 'SAR', nextDueAt: '2026-10-01', active: true },
  })
  await runRecurring({
    kind: 'save',
    input: { id: 'recurring:ghost|SAR', name: 'مش موجود', merchantKey: 'ghost', kind: 'bill', cycleMonths: 1, expectedMinor: 100, currency: 'SAR', nextDueAt: '2026-10-01', active: true },
  })
  await runRecurring({
    kind: 'save',
    input: { name: 'سبوتيفاي مكرر', merchantKey: 'name:SPOTIFY', kind: 'subscription', cycleMonths: 1, expectedMinor: 1999, currency: 'SAR', nextDueAt: '2026-10-01', active: true },
  })
  await runRecurring({
    kind: 'save',
    input: { name: 'دورة غلط', merchantKey: 'weird', kind: 'subscription', cycleMonths: 5 as RecurringItem['cycleMonths'], expectedMinor: 100, currency: 'SAR', nextDueAt: '2026-10-01', active: true },
  })

  return { manageRules: rulesCases, manageRecurring: recurringCases }
}
