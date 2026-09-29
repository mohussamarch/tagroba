import { makeSetBudget } from '../../src/application/useCases/setBudget'
import { makeManageCategories } from '../../src/application/useCases/manageCategories'
import { makeLoadHomeScreen } from '../../src/application/useCases/loadHomeScreen'
import { makeLoadHomeHistory } from '../../src/application/useCases/loadHomeHistory'
import { MemoryTransactionRepository, MemoryAllocationRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryCategoryRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryBudgetRepository } from '../../src/infrastructure/memory/memoryBudgetRepository'
import { FixedClock, MemoryUnitOfWork, SequentialIdGenerator } from '../../src/infrastructure/memory/memorySupport'
import { buildPeriod } from '../../src/domain/period'
import type { CategorySaveInput } from '../../src/domain/categoryEdit'
import type { Budget, CategoryBudget } from '../../src/domain/entities/budgetEntities'
import type { Category, PersonAllocation, Transaction } from '../../src/domain/entities/types'
import { recordAsync, type GoldenCase } from './goldenKit'

/**
 * ضبط الميزانية + إدارة التصنيفات + تاريخ الرئيسية. ⚠️ بيانات وهمية بالكامل.
 * قواعد التصنيف نفسها متغطية في `categories.json` (planCategorySave)؛ هنا ترتيب الحفظ والمعرّفات بس.
 */

const NOW = '2026-09-22T10:00:00.000Z'

type BudgetAction =
  | { kind: 'setTotal'; month: number; limitMinor: number; thresholdPercent: number | null }
  | { kind: 'clearTotal' | 'clearAll'; month: number }
  | { kind: 'setCategory'; month: number; categoryId: string; limitMinor: number; notifyEnabled?: boolean; thresholdPercent?: number | null }
  | { kind: 'clearCategory'; month: number; categoryId: string }
  | { kind: 'copyFrom'; sourceKey: string; month: number }

async function setBudgetCases() {
  const cases: GoldenCase[] = []
  const august = buildPeriod(2026, 8, 28)
  const baseBudgets: Budget[] = [
    { id: august.key, periodKey: august.key, periodStart: august.start, periodEnd: august.end, totalLimitMinor: 800_000, thresholdPercent: 80, createdAt: '2026-08-28T08:00:00.000Z', updatedAt: '2026-08-28T08:00:00.000Z' },
  ]
  const baseLines: CategoryBudget[] = [
    { id: 'cb-food', budgetId: august.key, categoryId: 'food', limitMinor: 150_000, notifyEnabled: true, thresholdPercent: 90 },
    { id: 'cb-fuel', budgetId: august.key, categoryId: 'fuel', limitMinor: 60_000, notifyEnabled: false, thresholdPercent: null },
  ]
  const september = buildPeriod(2026, 9, 28)
  const withSeptember: Budget[] = [
    ...baseBudgets,
    { id: september.key, periodKey: september.key, periodStart: september.start, periodEnd: september.end, totalLimitMinor: null, thresholdPercent: null, createdAt: '2026-09-28T08:00:00.000Z', updatedAt: '2026-09-28T08:00:00.000Z' },
  ]
  const septemberLine: CategoryBudget = { id: 'cb-sep-food', budgetId: september.key, categoryId: 'food', limitMinor: 90_000, notifyEnabled: false, thresholdPercent: null }

  async function run(budgets: Budget[], lines: CategoryBudget[], action: BudgetAction) {
    cases.push(await recordAsync({ budgets, lines, payday: 28, action }, async () => {
      const repo = new MemoryBudgetRepository()
      for (const b of budgets) await repo.save(b)
      for (const l of lines) await repo.saveCategoryBudget(l)
      const set = makeSetBudget({ budgets: repo, uow: new MemoryUnitOfWork([repo]), ids: new SequentialIdGenerator(), clock: new FixedClock(NOW) })
      const period = buildPeriod(2026, action.month, 28)
      let result: unknown = null
      switch (action.kind) {
        case 'setTotal': result = await set.setTotalLimit(period, action.limitMinor, action.thresholdPercent); break
        case 'clearTotal': await set.clearTotalLimit(period); break
        case 'clearAll': await set.clearAll(period); break
        case 'setCategory': {
          const options: { notifyEnabled?: boolean; thresholdPercent?: number | null } = {}
          if (action.notifyEnabled !== undefined) options.notifyEnabled = action.notifyEnabled
          if (action.thresholdPercent !== undefined) options.thresholdPercent = action.thresholdPercent
          result = await set.setCategoryLimit(period, action.categoryId, action.limitMinor, options)
          break
        }
        case 'clearCategory': await set.clearCategoryLimit(period, action.categoryId); break
        case 'copyFrom': result = await set.copyFrom(action.sourceKey, period); break
      }
      const stored = repo.snapshot()
      return { result, storedBudgets: stored.budgets, storedLines: stored.lines }
    }))
  }

  // السقف الإجمالي: فترة جديدة ⇒ ميزانية بمعرّف الفترة نفسها · موجودة ⇒ تاريخ الإنشاء بيفضل
  await run(baseBudgets, baseLines, { kind: 'setTotal', month: 9, limitMinor: 500_000, thresholdPercent: 80 })
  await run(baseBudgets, baseLines, { kind: 'setTotal', month: 8, limitMinor: 900_000, thresholdPercent: null })
  await run(baseBudgets, baseLines, { kind: 'setTotal', month: 9, limitMinor: 0, thresholdPercent: null })
  await run(baseBudgets, baseLines, { kind: 'setTotal', month: 9, limitMinor: -100, thresholdPercent: null })
  await run(baseBudgets, baseLines, { kind: 'setTotal', month: 9, limitMinor: 100, thresholdPercent: 0 })
  await run(baseBudgets, baseLines, { kind: 'setTotal', month: 9, limitMinor: 100, thresholdPercent: 101 })
  await run(baseBudgets, baseLines, { kind: 'setTotal', month: 9, limitMinor: 100, thresholdPercent: 100 })
  // المسح بيرجّع null مش صفر، وسقوف التصنيفات بتفضل
  await run(baseBudgets, baseLines, { kind: 'clearTotal', month: 8 })
  await run(baseBudgets, baseLines, { kind: 'clearTotal', month: 9 })
  // سقف تصنيف: جديد من غير اختيارات · بعتبة ⇒ التنبيه شغال · تنبيه مقفول بعتبة · تصنيف ليه سطر ⇒ نفس المعرّف
  await run(baseBudgets, baseLines, { kind: 'setCategory', month: 9, categoryId: 'rent', limitMinor: 300_000 })
  await run(baseBudgets, baseLines, { kind: 'setCategory', month: 9, categoryId: 'rent', limitMinor: 300_000, thresholdPercent: 75 })
  await run(baseBudgets, baseLines, { kind: 'setCategory', month: 9, categoryId: 'rent', limitMinor: 300_000, notifyEnabled: false, thresholdPercent: 75 })
  await run(baseBudgets, baseLines, { kind: 'setCategory', month: 8, categoryId: 'food', limitMinor: 170_000 })
  await run(baseBudgets, baseLines, { kind: 'setCategory', month: 8, categoryId: 'food', limitMinor: 0 })
  await run(baseBudgets, baseLines, { kind: 'setCategory', month: 8, categoryId: 'food', limitMinor: 100, thresholdPercent: 150 })
  await run(baseBudgets, baseLines, { kind: 'clearCategory', month: 8, categoryId: 'food' })
  await run(baseBudgets, baseLines, { kind: 'clearCategory', month: 8, categoryId: 'rent' })
  await run(baseBudgets, baseLines, { kind: 'clearCategory', month: 9, categoryId: 'food' })
  // مسح الكل بيشيل سطور الفترة دي بس
  await run(withSeptember, [...baseLines, septemberLine], { kind: 'clearAll', month: 8 })
  await run(baseBudgets, baseLines, { kind: 'clearAll', month: 9 })
  // النسخ: لفترة فاضية · من فترة مش موجودة. النسخ لفترة فيها سقوف **مش هنا عن قصد**: كوتلن بتسيب الموجود
  // (قرار المالك، OVERRIDES §49) والتطبيق الحالي بيضيف سقف تاني جنبه — اختباره مكتوب بالإيد في `SetBudgetCopyTest.kt`.
  await run(baseBudgets, baseLines, { kind: 'copyFrom', sourceKey: august.key, month: 9 })
  await run(baseBudgets, baseLines, { kind: 'copyFrom', sourceKey: '2026-07', month: 9 })
  return cases
}

async function manageCategoriesCases() {
  const cases: GoldenCase[] = []
  const seed: Category[] = [
    { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
    { id: 'food-out', parentId: 'food', name: 'مطاعم', iconKey: 'utensils', lightColor: '#b8572f', darkColor: '#f3b59e', active: true, order: 1 },
    { id: 'food-home', parentId: 'food', name: 'بقالة', iconKey: 'shopping-basket', lightColor: '#c46a44', darkColor: '#f5c2af', active: true, order: 2 },
    { id: 'car', parentId: null, name: 'السيارة', iconKey: 'car', lightColor: '#1f5aa4', darkColor: '#8cbcf0', active: true, order: 2, groupKey: 'transport', noCarName: 'المواصلات', noCarIconKey: 'bus' },
    { id: 'kids', parentId: null, name: 'الأولاد', iconKey: 'baby', lightColor: '#1f7a43', darkColor: '#8cd5a8', active: true, order: 3, groupKey: 'personal', requires: 'dependents' },
  ]

  async function run(action: { kind: 'list' } | { kind: 'save'; input: CategorySaveInput }) {
    cases.push(await recordAsync({ categories: seed, action }, async () => {
      const repo = new MemoryCategoryRepository(seed)
      const manage = makeManageCategories({ categories: repo, ids: new SequentialIdGenerator() })
      if (action.kind === 'list') return { listed: await manage.list() }
      const saved = await manage.save(action.input)
      return { saved, storedCategories: await repo.listAll() }
    }))
  }

  await run({ kind: 'list' })
  await run({ kind: 'save', input: { name: 'هدايا', active: true } })
  await run({ kind: 'save', input: { name: ' كافيهات ', active: true, iconKey: 'coffee', parentId: 'food' } })
  // تغيير لون الأساسي ⇒ فرعياته بتتلوّن معاه وبتتحفظ قبله
  await run({ kind: 'save', input: { id: 'food', name: 'أكل وشرب', active: true, swatchKey: 'teal' } })
  await run({ kind: 'save', input: { id: 'food-out', name: 'مطاعم برّه', active: false } })
  // اللي الشاشة ما بتعدلهوش (شرط الظهور، اسم «مالوش سيارة») بيفضل
  await run({ kind: 'save', input: { id: 'car', name: 'عربيتي', active: true } })
  await run({ kind: 'save', input: { id: 'kids', name: 'العيال', active: true, parentId: 'car' } })
  await run({ kind: 'save', input: { id: 'food', name: 'أكل', active: true, parentId: 'car' } })
  await run({ kind: 'save', input: { name: 'أكل', active: true } })
  await run({ kind: 'save', input: { name: '   ', active: true } })
  await run({ kind: 'save', input: { id: 'ghost', name: 'مش موجود', active: true } })
  return cases
}

async function loadHomeHistoryCases() {
  const cases: GoldenCase[] = []
  const cats: Category[] = [
    { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
    { id: 'transfers', parentId: null, name: 'تحويلات', iconKey: 'arrow-left-right', lightColor: '#6b1fa4', darkColor: '#c08cf0', active: true, order: 2, groupKey: 'movement' },
  ]
  let n = 0
  const txn = (occurredAt: string, over: Partial<Transaction> = {}): Transaction => ({
    id: `h-${n++}`, occurredAt, datePrecision: 'day', sourceOrder: n % 3,
    economicKind: 'purchase', economicKindConfirmed: true, observedDirection: 'out',
    amountMinor: 10_000 + n * 1_111, currency: 'SAR', categoryId: 'food',
    categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested', isCashTagged: false,
    createdAt: '2026-09-01T00:00:00.000Z', updatedAt: '2026-09-01T00:00:00.000Z',
    ...over,
  })

  /*
   * يوم راتب 28: فترة «2026-09» = 09-28←10-27، واللي قبلها لحد «2026-04».
   * كل فترة شكل: شراء + راتب · كلها غير محددة ومؤكدة (⇒ «غير متاح») · فاضية · تحويل غامض بيتقدّر.
   */
  const transactions: Transaction[] = [
    txn('2026-09-30'), txn('2026-10-05', { economicKind: 'salary', observedDirection: 'in', amountMinor: 900_000, categoryId: undefined }),
    txn('2026-09-02'), txn('2026-09-10', { economicKind: 'salary', observedDirection: 'in', amountMinor: 900_000, categoryId: undefined }),
    txn('2026-08-05', { economicKind: 'unclassified', economicKindConfirmed: true, observedDirection: 'in', categoryId: undefined }),
    txn('2026-08-20', { economicKind: 'unclassified', economicKindConfirmed: true, observedDirection: 'in', categoryId: undefined }),
    // «2026-06» فاضية عن قصد
    txn('2026-05-30', { economicKind: 'unclassified', economicKindConfirmed: false, observedDirection: 'out', categoryId: 'transfers' }),
    txn('2026-06-10'),
    txn('2026-04-29', { excludedFromBudget: true }), txn('2026-05-01', { economicKind: 'personal_sale', observedDirection: 'in' }),
    txn('2026-03-30'),
  ]
  const allocations: PersonAllocation[] = [
    { id: 'ha-1', transactionId: 'h-2', personId: 'p-1', allocationKind: 'receivable', amountMinor: 5_000, currency: 'SAR' },
  ]

  for (const [payday, withCategoriesRepo] of [[28, true], [28, false], [1, true], [31, true], [15, false]] as const) {
    cases.push(await recordAsync({ transactions, allocations, categories: cats, payday, month: 9, today: '2026-10-01', withCategoriesRepo }, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(transactions)
      const allocationRepo = new MemoryAllocationRepository()
      await allocationRepo.saveMany(allocations)
      const categories = new MemoryCategoryRepository(cats)
      const period = buildPeriod(2026, 9, payday)
      const current = await makeLoadHomeScreen({ txns, categories, allocations: allocationRepo })({
        period, today: '2026-10-01', payday, budgetLimitMinor: null, includeHistory: false,
      })
      const history = await makeLoadHomeHistory({ txns, allocations: allocationRepo, categories: withCategoriesRepo ? categories : undefined })({ period, payday, current })
      return history.map((p) => ({ period: p.period, expenseMinor: p.expenseMinor, incomeMinor: p.incomeMinor, transactionCount: p.transactionCount }))
    }))
  }
  return cases
}

export async function budgetEditGolden() {
  return {
    setBudget: await setBudgetCases(),
    manageCategories: await manageCategoriesCases(),
    loadHomeHistory: await loadHomeHistoryCases(),
  }
}
