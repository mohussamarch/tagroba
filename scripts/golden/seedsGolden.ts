import categoryTreeData from '../../src/infrastructure/import/categoryTree.json'
import rawRules from '../../design-source/masroofi-claude-code/fixtures/rule-reference.json'
import rawMerchants from '../../design-source/masroofi-claude-code/fixtures/merchant-reference.json'
import { buildCategoryTree, type RawCategoryTree } from '../../src/infrastructure/import/categoryTreeLoader'
import { loadReferences } from '../../src/infrastructure/import/referenceLoader'
import { guessSourceType } from '../../src/infrastructure/import/detectSourceType'
import { inspectFile } from '../../src/infrastructure/import/inspectFile'
import { record } from './goldenKit'

/** المراجع الأولية للحساب الجديد (الشجرة والقواعد والتجار المرفوعين أصلًا في المستودع) + كشف نوع الملف. */
export function seedsGolden() {
  const tree = categoryTreeData as unknown as RawCategoryTree
  const built = buildCategoryTree(tree)
  const broken: [string, RawCategoryTree][] = [
    ['bad group', { groups: [{ key: 'zzz', mains: [] }] }],
    ['bad requirement', { groups: [{ key: 'food', mains: [{ name: 'x', icon: 'i', h: 1, s: 1, l: 1, requires: 'rich', subs: [] }] }] }],
    ['missing sub', { groups: [{ key: 'food', mains: [{ name: 'x', icon: 'i', h: 1, s: 1, l: 1, subs: [['', 'i']] }] }] }],
    ['duplicate', { groups: [{ key: 'food', mains: [{ name: 'x', icon: 'i', h: 1, s: 1, l: 1, subs: [] }, { name: 'X', icon: 'i', h: 1, s: 1, l: 1, subs: [] }] }] }],
    ['bad override', { groups: [{ key: 'food', mains: [{ name: 'x', icon: 'i', h: 1, s: 1, l: 1, subs: [['y', 'i']] }] }], ruleWordOverrides: { W: ['x', 'z'] } }],
    ['ambiguous', { groups: [{ key: 'food', mains: [{ name: 'a', icon: 'i', h: 1, s: 1, l: 1, from: ['قديم', 'b'], subs: [['مشترك', 'i']] }, { name: 'b', icon: 'i', h: 5, s: 5, l: 5, subs: [['مشترك', 'i', 'hasCar']] }] }], ruleWordOverrides: { KIWI: ['a', 'مشترك'] } }],
  ]
  const asJson = (t: ReturnType<typeof buildCategoryTree>) => ({ categories: t.categories, aliases: [...t.aliases], wordOverrides: [...t.wordOverrides] })
  const files: [string, string][] = [['a.csv', ''], ['a.csv', '﻿  \n'], ['statement.PDF', 'x'], ['a.csv', '%PDF-1.7 ...'], ['a.csv', 'date,name\n1,2'],
    ['photo.png', 'ok' + String.fromCharCode(0) + 'x'], ['data.bin', String.fromCharCode(1).repeat(10) + 'a'.repeat(400)], ['noext', String.fromCharCode(1).repeat(20) + 'a'.repeat(10)],
    ['a.csv', 'a'.repeat(100) + String.fromCharCode(0xfffd).repeat(3)], ['.hidden', String.fromCharCode(2).repeat(50)]]

  return {
    buildCategoryTree: [record('categoryTree.json', () => asJson(built)), ...broken.map(([name, raw]) => record({ name, raw }, () => asJson(buildCategoryTree(raw))))],
    loadReferences: [
      record('with-tree', () => loadReferences(rawRules, rawMerchants, built.categories, built)),
      record('no-tree', () => loadReferences(rawRules, rawMerchants, built.categories)),
      record('edge', () => loadReferences([{ word: ' ', cat: 'x' }, { word: 'A', cat: ' ' }, { word: 'B', cat: 'مش موجود' }], [{ name: ' ', cat: 'x', confidence: 'مؤكد' }, { name: 'dup', cat: 'سفر', confidence: 'مؤكد' }, { name: 'DUP', cat: 'x', confidence: 'مؤكد' }, { name: 'y', cat: 'يحتاج تأكيد', confidence: 'مؤكد' }, { name: 'z', cat: 'مش موجود', confidence: ' مؤكد ' }], built.categories, built)),
    ],
    guessSourceType: ['date,name,amount,type', '﻿date,name,amount', 'التاريخ,مدين,دائن', 'التاريخ فقط', ''].map((c) => record(c, () => guessSourceType(c))),
    inspectFile: files.map(([name, content]) => record({ name, content }, () => inspectFile(name, content))),
  }
}
