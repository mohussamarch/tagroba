import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import tree from '../../src/infrastructure/import/categoryTree.json'
import { buildCategoryTree } from '../../src/infrastructure/import/categoryTreeLoader'
import { buildCategories, loadReferences } from '../../src/infrastructure/import/referenceLoader'
import { CATEGORY_GROUPS } from '../../src/domain/categoryTree'
import { CATEGORY_ICONS } from '../../src/presentation/components/CategoryIcon'

/** شجرة التصنيفات الجديدة — OVERRIDES §28–28.1. */

const FIX = resolve(__dirname, '../../design-source/masroofi-claude-code/fixtures')
const DESIGN = resolve(__dirname, '../../design-source/masroofi-claude-code/design')
const readJson = <T>(p: string): T => JSON.parse(readFileSync(p, 'utf8')) as T
const tokens = readJson<{ categories: { name: string; baseColor: string; icon: string }[] }>(resolve(DESIGN, 'tokens.json'))
const rawRules = readJson<{ word: string; cat: string }[]>(resolve(FIX, 'rule-reference.json'))
const rawMerchants = readJson<{ name: string; cat: string; confidence: string }[]>(resolve(FIX, 'merchant-reference.json'))

const built = buildCategoryTree(tree)
const byId = new Map(built.categories.map((c) => [c.id, c]))
const mains = built.categories.filter((c) => c.parentId === null)
const subs = built.categories.filter((c) => c.parentId !== null)
const find = (main: string, sub?: string) =>
  built.categories.find((c) => (sub ? c.name === sub && byId.get(c.parentId ?? '')?.name === main : c.name === main && !c.parentId))!

describe('بناء الشجرة', () => {
  it('مجموعة ← أساسي ← فرعي، والمعرّفات فريدة، وكل فرعي أبوه أساسي', () => {
    expect(mains).toHaveLength(21)
    expect(subs).toHaveLength(70)
    expect(new Set(built.categories.map((c) => c.id)).size).toBe(built.categories.length)
    for (const sub of subs) {
      const parent = byId.get(sub.parentId!)
      expect(parent?.parentId).toBeNull()
      expect(sub.groupKey).toBeUndefined() // الفرعي بياخد مجموعته من أبوه
    }
    const groupKeys = new Set(CATEGORY_GROUPS.map((g) => g.key))
    for (const main of mains) expect(groupKeys.has(main.groupKey!)).toBe(true)
    expect(new Set(mains.map((m) => m.groupKey))).toEqual(groupKeys)
  })

  it('كل أساسي لونه مختلف، والفرعي درجة من أبوه، والغامق غير الفاتح', () => {
    expect(new Set(mains.map((m) => m.lightColor)).size).toBe(mains.length)
    for (const c of built.categories) {
      expect(c.lightColor).toMatch(/^#[0-9A-F]{6}$/)
      expect(c.darkColor).not.toBe(c.lightColor)
    }
    const food = find('مطاعم وقهوة')
    expect(find('مطاعم وقهوة', 'مطاعم').lightColor).not.toBe(food.lightColor)
  })

  it('السيارة تصنيف واحد: اسمه «المواصلات» لو مالوش سيارة، وحاجات العربية بس هي المشروطة', () => {
    const car = find('السيارة')
    expect(car).toMatchObject({ noCarName: 'المواصلات', noCarIconKey: 'bus-front', groupKey: 'transport' })
    expect(car.requires).toBeUndefined()
    const carSubs = subs.filter((s) => s.parentId === car.id)
    expect(carSubs.filter((s) => !s.requires).map((s) => s.name)).toEqual(['تاكسي وتطبيقات', 'مواصلات عامة'])
    expect(carSubs.filter((s) => s.requires).every((s) => s.requires === 'hasCar')).toBe(true)
    expect(find('الأسرة والأطفال').requires).toBe('dependents')
    expect(find('المنزل', 'إيجار').requires).toBe('renter')
    expect(find('المنزل', 'عمالة منزلية').requires).toBe('domesticWorker')
    expect(find('مصاريف الشغل').requires).toBe('business')
  })

  it('التصنيف اللي اسمه ما اتغيرش بيفضل بنفس معرّفه القديم (عشان نقل العمليات)', () => {
    const oldIds = new Map(buildCategories(tokens.categories).map((c) => [c.name, c.id]))
    for (const main of mains) if (oldIds.has(main.name)) expect(main.id).toBe(oldIds.get(main.name))
    expect(find('مطاعم وقهوة').id).toBe(oldIds.get('مطاعم وقهوة'))
  })

  it('كل رمز في الشجرة والمجموعات ليه رسمة', () => {
    for (const c of built.categories) expect(CATEGORY_ICONS[c.iconKey]).toBeDefined()
    for (const c of mains) if (c.noCarIconKey) expect(CATEGORY_ICONS[c.noCarIconKey]).toBeDefined()
    for (const g of CATEGORY_GROUPS) expect(CATEGORY_ICONS[g.iconKey]).toBeDefined()
  })

  it('بيانات غلط بتقف فورًا', () => {
    expect(() => buildCategoryTree({ groups: [{ key: 'x', mains: [] }] })).toThrow('مجموعة')
    const main = { name: 'أ', icon: 'tag', h: 1, s: 1, l: 30, subs: [['ب', 'tag', 'wings']] }
    expect(() => buildCategoryTree({ groups: [{ key: 'food', mains: [main] }] })).toThrow('شرط')
    expect(() => buildCategoryTree({ groups: [{ key: 'food', mains: [{ ...main, subs: [] }] }], ruleWordOverrides: { X: ['أ', 'مش موجود'] } })).toThrow('مش موجود')
  })
})

describe('القواعد والتجار بالأسماء القديمة', () => {
  const refs = loadReferences(rawRules, rawMerchants, built.categories, built)
  const ruleFor = (word: string) => refs.rules.find((r) => r.matchText === word)

  it('كل قاعدة بتوصل لتصنيف موجود، و«تأمين» بس مجهول لأن ليه أكتر من وجهة', () => {
    for (const rule of refs.rules) expect(byId.has(rule.categoryId)).toBe(true)
    expect(refs.unknownCategoryNames).toEqual(['تأمين'])
    expect(ruleFor('TAWUNIYA')).toBeUndefined()
  })

  it('الأسماء القديمة بتروح للتصنيف اللي اتدمجت فيه أو لفرعي بنفس اسمها', () => {
    expect(ruleFor('BARQ')!.categoryId).toBe(find('استثمار', 'محافظ رقمية').id)
    expect(ruleFor('TABBY')!.categoryId).toBe(find('الالتزامات', 'تقسيط').id)
    expect(ruleFor('LAUNDRY')!.categoryId).toBe(find('المنزل').id)
    expect(ruleFor('ELECTRICITY')!.categoryId).toBe(find('المنزل').id)
    expect(ruleFor('PRINT')!.categoryId).toBe(find('التسوق').id)
  })

  it('كلمات بعينها بتروح لفرعيها', () => {
    expect(ruleFor('SHORY')!.categoryId).toBe(find('السيارة', 'تأمين السيارة').id)
    expect(ruleFor('BUPA')!.categoryId).toBe(find('صحة وصيدليات', 'تأمين صحي').id)
    expect(ruleFor('ALDREES')!.categoryId).toBe(find('السيارة', 'وقود').id)
    expect(ruleFor('PARKING')!.categoryId).toBe(find('السيارة', 'مواقف وسايس').id)
    expect(ruleFor('UBER')!.categoryId).toBe(find('السيارة', 'تاكسي وتطبيقات').id)
  })
})
