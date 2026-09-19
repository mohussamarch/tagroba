import { categorize, matchesText, prepareRules } from '../../src/domain/categorize'
import { merchantIndex } from '../../src/domain/merchantIndex'
import { rememberMerchant } from '../../src/domain/merchantMemory'
import { normalizeText } from '../../src/domain/normalize'
import type { ClassificationRule, Merchant, RuleMatchMode } from '../../src/domain/entities/types'
import { record, seeded } from './goldenKit'

/** التصنيف بالأولوية (spec/05): تأكيد المستخدم ← التاجر المؤكد ← القواعد ← عمود الملف — أسماء وهمية. */
export function categorizeGolden() {
  const rnd = seeded(77)
  const merchants: Merchant[] = [
    { id: 'm1', displayName: 'TEST MART', normalizedName: 'test mart', verifiedCategoryId: 'groceries', aliases: ['تست مارت'] },
    { id: 'm2', displayName: 'مطعم البيت', normalizedName: 'مطعم البيت' },
    { id: 'm3', displayName: 'NEW CAFE', normalizedName: 'NEW CAFE', verifiedCategoryId: 'cafe' },
    { id: 'm4', displayName: 'Dup', normalizedName: 'TEST MART', verifiedCategoryId: 'fuel' },
  ]
  const modes: RuleMatchMode[] = ['contains', 'startsWith', 'exact']
  const rules: ClassificationRule[] = [
    { id: 'r1', priority: 5, matchText: 'mart', matchMode: 'contains', categoryId: 'groceries', enabled: true },
    { id: 'r2', priority: 1, matchText: 'STC', matchMode: 'startsWith', categoryId: 'telecom', enabled: true },
    { id: 'r3', priority: 1, matchText: 'مطعم', matchMode: 'contains', categoryId: 'food', enabled: true },
    { id: 'r4', priority: 0, matchText: 'CAFE', matchMode: 'exact', categoryId: 'cafe', enabled: false },
    { id: 'r5', priority: 3, matchText: 'عبد الفتاح', matchMode: 'contains', categoryId: 'people', enabled: true },
    { id: 'r6', priority: 9, matchText: '', matchMode: 'contains', categoryId: 'never', enabled: true },
    { id: 'r7', priority: 2, matchText: 'uber', matchMode: 'exact', categoryId: 'transport', enabled: true },
  ]
  const categoryIdByName = new Map([['بقالة', 'groceries'], ['مطاعم وقهوة', 'food'], ['اتصالات', 'telecom']].map(([n, id]) => [normalizeText(n!), id!]))
  const deps = { merchantsByNormalizedName: merchantIndex(merchants), rules: prepareRules(rules), categoryIdByName }
  const NAMES = ['TEST MART', 'test-mart', 'تست مارت', 'NEW CAFE', 'new cafe', 'STC PAY', 'pay stc', 'مطعم البيت', 'مطعم تجريبي', 'UBER', 'uber trip', 'عبدالفتاح للتجارة', 'Unknown', '', undefined]
  const inputs = []
  for (const merchantName of NAMES) for (const description of [undefined, '', 'شراء من mart', 'تحويل STC']) {
    const input: Record<string, unknown> = { currentConfirmed: false }
    if (merchantName !== undefined) input.merchantName = merchantName
    if (description !== undefined) input.description = description
    if (rnd.next() < 0.3) input.sourceCategory = rnd.pick(['بقالة', 'مطاعم  وقهوة', 'غير معروف', ''])
    if (rnd.next() < 0.2) { input.currentCategoryId = 'mine'; input.currentConfirmed = rnd.next() < 0.5 }
    inputs.push(input)
  }
  for (const sourceCategory of ['بقالة', 'مطاعم  وقهوة', 'اتصالات', 'غير معروف', '']) inputs.push({ currentConfirmed: false, sourceCategory }, { currentConfirmed: true, sourceCategory, merchantName: 'Unknown' }, { currentConfirmed: true, currentCategoryId: 'mine', sourceCategory })
  const texts = ['TEST MART 12', 'mart', 'STC', 'stc pay', 'pay stc', 'عبد الفتاح', 'عبدالفتاح', '', 'ﻣﻄﻌﻢ']

  return {
    prepareRules: [record(rules.map((r) => r.id), () => prepareRules(rules).map((r) => r.id))],
    merchantIndex: [record(merchants.map((m) => m.id), () => Object.fromEntries([...merchantIndex(merchants)].map(([k, m]) => [k, m.id])))],
    matchesText: texts.flatMap((needle) => modes.flatMap((mode) => texts.slice(0, 5).map((hay) => record({ needle, mode, haystack: hay }, () => matchesText(needle, mode, hay))))),
    categorize: inputs.map((input) => record({ input, merchants, rules, categoryNames: Object.fromEntries(categoryIdByName) }, () => categorize(input as never, deps))),
    rememberMerchant: [['NEW CAFE', 'food'], ['new  cafe', 'food'], ['تست مارت', 'fuel'], ['Brand New Shop', 'food'], ['  spaced   name ', 'food'], ['1234', 'food'], ['', 'food'], ['***', 'food']].map(([name, cat]) =>
      record({ merchants, name, categoryId: cat, newId: 'm-new' }, () => rememberMerchant(merchants, name!, cat!, 'm-new'))),
  }
}
