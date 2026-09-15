import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import tree from '../../src/infrastructure/import/categoryTree.json'
import { buildCategoryTree } from '../../src/infrastructure/import/categoryTreeLoader'
import { loadReferences } from '../../src/infrastructure/import/referenceLoader'
import { planReferenceRestore } from '../../src/domain/referenceRestore'
import type { ClassificationRule, Transaction } from '../../src/domain/entities/types'

/** رجوع القواعد والتجار الافتراضيين بمعاينة — OVERRIDES §28.1 (قراءة بس). */

const FIX = resolve(__dirname, '../../design-source/masroofi-claude-code/fixtures')
const readJson = <T>(p: string): T => JSON.parse(readFileSync(p, 'utf8')) as T
const rawRules = readJson<{ word: string; cat: string }[]>(resolve(FIX, 'rule-reference.json'))
const rawMerchants = readJson<{ name: string; cat: string; confidence: string }[]>(resolve(FIX, 'merchant-reference.json'))
const built = buildCategoryTree(tree)
const defaults = loadReferences(rawRules, rawMerchants, built.categories, built)
const idOf = (main: string, sub?: string) => {
  const parent = built.categories.find((c) => c.name === main && !c.parentId)!
  return sub ? built.categories.find((c) => c.name === sub && c.parentId === parent.id)!.id : parent.id
}

const txn = (id: string, extra: Partial<Transaction> = {}): Transaction => ({
  id, occurredAt: '2026-09-01', datePrecision: 'day', sourceOrder: 1, economicKind: 'purchase', economicKindConfirmed: false,
  observedDirection: 'out', amountMinor: 1000, currency: 'SAR', categoryConfirmed: false, excludedFromBudget: false,
  reviewState: 'needs_review', isCashTagged: false, createdAt: '', updatedAt: '', ...extra,
})

describe('رجوع القواعد والتجار الافتراضيين', () => {
  const transactions = [
    txn('barq', { rawMerchantName: 'BARQ TRANSFER' }),
    txn('fuel', { rawDescription: 'POS ALDREES STATION 12' }),
    txn('mine', { rawMerchantName: 'UBER TRIP', categoryId: idOf('ترفيه'), categoryConfirmed: true }),
    txn('set', { rawMerchantName: 'CAREEM', categoryId: idOf('متفرقات') }),
    txn('unknown', { rawMerchantName: 'محل مش معروف' }),
  ]
  const plan = planReferenceRestore({ rules: [], merchants: [], categories: built.categories, transactions }, defaults)

  it('حساب فاضي ⇒ كل الافتراضيين هيتضافوا', () => {
    expect(plan.rules).toHaveLength(defaults.rules.length)
    expect(plan.merchants).toHaveLength(defaults.merchants.length)
  })

  it('الاقتراح للعمليات اللي مالهاش تصنيف ومش مؤكدة بس', () => {
    expect(plan.uncategorized).toBe(3)
    expect(plan.suggestions.map((s) => [s.transactionId, s.categoryId])).toEqual([
      ['barq', idOf('استثمار', 'محافظ رقمية')],
      ['fuel', idOf('السيارة', 'وقود')],
    ])
    expect(plan.byCategory).toHaveLength(2)
  })

  it('الموجود ما بيتكتبش فوقه: نفس النص والطريقة أو نفس الاسم', () => {
    const mine: ClassificationRule = { id: 'rule-mine', priority: 1, matchText: 'barq', matchMode: 'contains', categoryId: idOf('تحويلات'), enabled: true }
    const merchant = { ...defaults.merchants[0], id: 'merch-mine' }
    const again = planReferenceRestore({ rules: [mine], merchants: [merchant], categories: built.categories, transactions }, defaults)
    expect(again.rules.some((r) => normalizeUpper(r.matchText) === 'BARQ')).toBe(false)
    expect(again.rules).toHaveLength(defaults.rules.length - 1)
    expect(again.merchants).toHaveLength(defaults.merchants.length - 1)
    // قاعدة المستخدم هي اللي بتتطبق
    expect(again.suggestions.find((s) => s.transactionId === 'barq')?.categoryId).toBe(idOf('تحويلات'))
  })

  it('بعد الإضافة الفحص التاني مالوش حاجة يضيفها', () => {
    const after = planReferenceRestore({ rules: plan.rules, merchants: plan.merchants, categories: built.categories, transactions }, defaults)
    expect(after.rules).toEqual([])
    expect(after.merchants).toEqual([])
  })

  it('قاعدة تصنيفها مش في الحساب بتتشال، وتاجر تصنيفه مش موجود بيتضاف من غير تصنيف مؤكد', () => {
    const few = built.categories.filter((c) => c.id !== idOf('استثمار', 'محافظ رقمية'))
    const partial = planReferenceRestore({ rules: [], merchants: [], categories: few, transactions }, defaults)
    expect(partial.rules.some((r) => r.categoryId === idOf('استثمار', 'محافظ رقمية'))).toBe(false)
    const ids = new Set(few.map((c) => c.id))
    expect(partial.merchants.every((m) => m.verifiedCategoryId === undefined || ids.has(m.verifiedCategoryId))).toBe(true)
  })
})

function normalizeUpper(text: string) {
  return text.trim().toUpperCase()
}
