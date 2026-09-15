import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import tree from '../../src/infrastructure/import/categoryTree.json'
import { buildCategoryTree } from '../../src/infrastructure/import/categoryTreeLoader'
import { buildCategories } from '../../src/infrastructure/import/referenceLoader'
import { legacyCategoryId, planCategoryMigration } from '../../src/domain/categoryMigration'
import { emptyStoredData, type StoredData } from '../../src/domain/idRepair'

/** خطة نقل حساب قديم للشجرة الجديدة — OVERRIDES §28 و§28.1 (قراءة بس). */

const DESIGN = resolve(__dirname, '../../design-source/masroofi-claude-code/design')
const tokens = JSON.parse(readFileSync(resolve(DESIGN, 'tokens.json'), 'utf8')) as { categories: { name: string; baseColor: string; icon: string }[] }
const legacyNames = tokens.categories.map((c) => c.name)
const legacy = buildCategories(tokens.categories)
const built = buildCategoryTree(tree)
const idOf = (name: string) => legacy.find((c) => c.name === name)!.id
const newId = (main: string, sub?: string) => {
  const parent = built.categories.find((c) => c.name === main && !c.parentId)!
  return sub ? built.categories.find((c) => c.name === sub && c.parentId === parent.id)!.id : parent.id
}

/** حساب قديم: التصنيفات القديمة + عمليات وقواعد وتجار وسقوف عليها. */
function oldAccount(): StoredData {
  const data = emptyStoredData()
  data.categories = legacy.map((c) => ({ docId: c.id, data: { ...c } }))
  data.categories.push({ docId: 'cat-custom', data: { id: 'cat-custom', name: 'هدايا العيد' } })
  const txn = (id: string, categoryId: string, extra = {}) =>
    data.transactions.push({ docId: id, data: { id, categoryId, categoryConfirmed: true, amountMinor: 1000, ...extra } })
  txn('t1', idOf('فواتير ومرافق'))
  txn('t2', idOf('خدمات منزلية'))
  txn('t3', idOf('ملابس وعطور'))
  txn('t4', idOf('تقسيط'))
  txn('t5', idOf('وقود ومواصلات'))
  txn('t6', idOf('تأمين'))
  txn('t7', idOf('مطاعم وقهوة')) // الاسم ما اتغيرش
  txn('t8', 'cat-custom')
  data.transactions.push({ docId: 't9', data: { id: 't9', amountMinor: 5 } }) // بلا تصنيف
  data.rules.push({ docId: 'r1', data: { id: 'r1', categoryId: idOf('محافظ رقمية'), matchText: 'BARQ' } })
  data.rules.push({ docId: 'r2', data: { id: 'r2', categoryId: idOf('تأمين'), matchText: 'BUPA' } })
  data.merchants.push({ docId: 'm1', data: { id: 'm1', verifiedCategoryId: idOf('ملابس وعطور') } })
  data.categoryBudgets.push({ docId: 'b1', data: { id: 'b1', budgetId: 'bud', categoryId: idOf('فواتير ومرافق'), limitMinor: 100 } })
  data.categoryBudgets.push({ docId: 'b2', data: { id: 'b2', budgetId: 'bud', categoryId: idOf('خدمات منزلية'), limitMinor: 50 } })
  data.categoryBudgets.push({ docId: 'b3', data: { id: 'b3', budgetId: 'bud', categoryId: idOf('تقسيط'), limitMinor: 70 } })
  return data
}

describe('خطة نقل الحساب القديم', () => {
  const plan = planCategoryMigration(oldAccount(), built, legacyNames)
  const move = (from: string) => plan.moves.find((m) => m.fromId === idOf(from))

  it('بيضيف الناقص بس: التصنيف اللي اسمه ما اتغيرش ما بيتعملش تاني', () => {
    const createdIds = new Set(plan.create.map((c) => c.id))
    expect(createdIds.has(newId('مطاعم وقهوة'))).toBe(false)
    expect(createdIds.has(newId('المنزل'))).toBe(true)
    expect(createdIds.has(newId('مطاعم وقهوة', 'قهوة ومشروبات'))).toBe(true)
  })

  it('كل تصنيف اتدمج ليه وجهة واحدة وأعداد صح', () => {
    expect(move('فواتير ومرافق')).toMatchObject({ toId: newId('المنزل'), transactions: 1, budgets: 0 })
    expect(move('خدمات منزلية')).toMatchObject({ toId: newId('المنزل'), transactions: 1 })
    expect(move('ملابس وعطور')).toMatchObject({ toId: newId('العناية الشخصية'), transactions: 1, merchants: 1 })
    expect(move('تقسيط')).toMatchObject({ toId: newId('الالتزامات', 'تقسيط'), transactions: 1, budgets: 1 })
    expect(move('محافظ رقمية')).toMatchObject({ toId: newId('استثمار', 'محافظ رقمية'), rules: 1 })
    expect(move('وقود ومواصلات')).toMatchObject({ toId: newId('السيارة'), transactions: 1 })
    expect(move('مطاعم وقهوة')).toBeUndefined()
  })

  it('«تأمين» غامض: ما بيتنقلش وبيتعد عشان المالك يقرر', () => {
    expect(plan.ambiguous).toEqual([{ id: idOf('تأمين'), name: 'تأمين', transactions: 1, rules: 1, merchants: 0, budgets: 0 }])
    expect(plan.patches.some((p) => p.docId === 't6' || p.docId === 'r2')).toBe(false)
  })

  it('التعديل على حقل التصنيف بس، والتصنيف اللي عمله المستخدم ما بيتلمسش', () => {
    const t3 = plan.patches.find((p) => p.docId === 't3')!
    expect(t3).toEqual({ group: 'transactions', docId: 't3', fields: { categoryId: newId('العناية الشخصية') } })
    expect(plan.patches.find((p) => p.docId === 'm1')).toEqual({ group: 'merchants', docId: 'm1', fields: { verifiedCategoryId: newId('العناية الشخصية') } })
    expect(plan.patches.some((p) => ['t7', 't8', 't9'].includes(p.docId))).toBe(false)
    expect(plan.untouched).toEqual([{ id: 'cat-custom', name: 'هدايا العيد' }])
  })

  it('سقفين لنفس الميزانية على نفس الوجهة ⇒ تعارض ما يتنقلش', () => {
    expect(plan.budgetConflicts).toEqual([{ budgetId: 'bud', toId: newId('المنزل'), fromIds: [idOf('فواتير ومرافق'), idOf('خدمات منزلية')] }])
    expect(plan.patches.some((p) => p.docId === 'b1' || p.docId === 'b2')).toBe(false)
    expect(plan.patches.find((p) => p.docId === 'b3')).toEqual({ group: 'categoryBudgets', docId: 'b3', fields: { categoryId: newId('الالتزامات', 'تقسيط') } })
  })

  it('التطبيق مرتين ما بيكررش: الخطة بعد التطبيق مالهاش تعديلات ولا إضافات', () => {
    const account = oldAccount()
    for (const patch of plan.patches) {
      const row = account[patch.group].find((r) => r.docId === patch.docId)!
      Object.assign(row.data, patch.fields)
    }
    for (const c of plan.create) account.categories.push({ docId: c.id, data: { ...c } })
    const again = planCategoryMigration(account, built, legacyNames)
    expect(again.create).toEqual([])
    expect(again.patches).toEqual([])
    expect(again.moves.every((m) => m.transactions + m.rules + m.merchants + m.budgets === 0)).toBe(true)
  })

  it('اختيار المستخدم لوجهة الغامض بيحوّله نقل عادي، ووجهة مش في الشجرة بتتجاهل', () => {
    const carInsurance = newId('السيارة', 'تأمين السيارة')
    const chosen = planCategoryMigration(oldAccount(), built, legacyNames, new Map([[idOf('تأمين'), carInsurance]]))
    expect(chosen.ambiguous).toEqual([])
    expect(chosen.moves.find((m) => m.fromId === idOf('تأمين'))).toMatchObject({ toId: carInsurance, toName: 'تأمين السيارة', transactions: 1, rules: 1 })
    expect(chosen.patches.find((p) => p.docId === 't6')).toEqual({ group: 'transactions', docId: 't6', fields: { categoryId: carInsurance } })
    expect(chosen.patches.find((p) => p.docId === 'r2')).toEqual({ group: 'rules', docId: 'r2', fields: { categoryId: carInsurance } })
    const ignored = planCategoryMigration(oldAccount(), built, legacyNames, new Map([[idOf('تأمين'), 'cat-مش-موجود']]))
    expect(ignored.ambiguous.map((a) => a.id)).toEqual([idOf('تأمين')])
  })

  it('معرّف التصنيف القديم بنفس طريقة البناء القديمة', () => {
    for (const [i, c] of tokens.categories.entries()) expect(legacyCategoryId(c.name, i)).toBe(legacy[i].id)
  })
})
