import categoryTreeData from '../../src/infrastructure/import/categoryTree.json'
import { buildCategoryTree } from '../../src/infrastructure/import/categoryTreeLoader'
import { categoryColors, hslToHex, subCategoryColors } from '../../src/domain/categoryColors'
import { CATEGORY_SWATCHES, childColors, firstFreeSwatch, hexToHsl, swatchColors, swatchKeyOf } from '../../src/domain/categoryPalette'
import { groupCategoryOptions } from '../../src/domain/categoryOptions'
import { categoryPlaceChoices, manageCategoryView } from '../../src/domain/categoryManageView'
import { planCategorySave, type CategorySaveInput } from '../../src/domain/categoryEdit'
import { factsFromProfile, presentCategories, UNKNOWN_FACTS, type ProfileFacts } from '../../src/domain/categoryVisibility'
import { groupDistribution } from '../../src/domain/groupDistribution'
import { checkProfile, emptyProfile, parseStoredProfile, type UserProfile } from '../../src/domain/userProfile'
import type { Category } from '../../src/domain/entities/types'
import { record, seeded } from './goldenKit'

/** التصنيفات (شجرة التطبيق الافتراضية + تصنيفات وهمية) والألوان وملف المستخدم. */
export function categoriesGolden() {
  const rnd = seeded(33)
  const tree = buildCategoryTree(categoryTreeData as never).categories
  const extra: Category[] = [
    { id: 'x-legacy', parentId: null, name: 'قديم مخفي', iconKey: 'tag', lightColor: '#123456', darkColor: '#654321', active: false, order: 5 },
    { id: 'x-legacy-kid', parentId: 'x-legacy', name: 'فرعي قديم', iconKey: 'tag', lightColor: '#123456', darkColor: '#654321', active: true, order: 1 },
    { id: 'x-orphan', parentId: 'gone', name: 'يتيم', iconKey: 'tag', lightColor: 'bad', darkColor: 'bad', active: true, order: 2 },
    { id: 'x-loose', parentId: null, name: 'أحمد', iconKey: 'tag', lightColor: '#AA0000', darkColor: '#FF0000', active: true, order: 2 },
    { id: 'x-loose2', parentId: null, name: 'إبراهيم', iconKey: 'tag', lightColor: '#AA0000', darkColor: '#FF0000', active: true, order: 2 },
    { id: 'x-loose3', parentId: null, name: 'آمال', iconKey: 'tag', lightColor: '#AA0000', darkColor: '#FF0000', active: true, order: 2 },
  ]
  const sets = [tree, [...tree, ...extra], extra, []]
  const factsList: ProfileFacts[] = [UNKNOWN_FACTS, { hasCar: true, familyDependents: true, renter: true, domesticWorker: true, business: true }, { hasCar: false, familyDependents: false, renter: null, domesticWorker: false, business: true }]
  const keepIds = [null, 'x-legacy-kid', tree.find((c) => !c.active)?.id ?? null, tree.find((c) => c.parentId && !c.active)?.id ?? null]

  const saves: CategorySaveInput[] = [
    { name: 'جديد', active: true }, { name: '  مسافات  ', active: true, iconKey: 'car' }, { name: '', active: true }, { name: 'x'.repeat(81), active: true },
    { name: tree[0]!.name, active: true }, { name: 'رمز غلط', active: true, iconKey: 'Bad Key' }, { id: 'missing', name: 'x', active: true },
    { name: 'فرعي جديد', active: true, parentId: tree.find((c) => !c.parentId)!.id }, { name: 'تحت فرعي', active: true, parentId: tree.find((c) => c.parentId)!.id },
    { name: 'تحت مش موجود', active: true, parentId: 'nope' }, { name: 'بمجموعة', active: true, groupKey: 'food' }, { name: 'مجموعة غلط', active: true, groupKey: 'zzz' as never },
    { name: 'بلون', active: true, swatchKey: 'teal' }, { name: 'لون غلط', active: true, swatchKey: 'nope' },
    { id: tree.find((c) => !c.parentId)!.id, name: 'أساسي اتغير لونه', active: false, swatchKey: 'rose' },
    { id: tree.find((c) => !c.parentId)!.id, name: 'أساسي من غير مجموعة', active: true, groupKey: null },
    { id: tree.find((c) => c.parentId)!.id, name: 'فرعي بقى أساسي', active: true, parentId: null },
    { id: tree.find((c) => !c.parentId)!.id, name: 'أساسي تحت نفسه', active: true, parentId: tree.find((c) => !c.parentId)!.id },
    { id: tree.find((c) => !c.parentId && tree.some((k) => k.parentId === c.id))!.id, name: 'أبو فرعيات يتنقل', active: true, parentId: tree.filter((c) => !c.parentId)[3]!.id },
    { id: 'x-orphan', name: 'يتيم اتعدل', active: true },
  ]
  const profiles: unknown[] = [null, {}, { displayName: '  محمد  ', salaryMinor: 1_000_000, payday: 25, gender: 'male', hasCar: true, dependentKinds: ['children', 'spouse', 'children'], onboardedAt: '2026-09-01' },
    { displayName: 'x'.repeat(61), salaryMinor: -1, payday: 0, gender: 'other', hasCar: 'yes', dependentKinds: ['cousin'] }, { salaryMinor: 1.5, payday: 31.5, supportsDependents: false, dependentKinds: [] },
    { salaryMinor: 9007199254740992, payday: 28 }, { displayName: '   ', onboardedAt: '' }]
  const checks: UserProfile[] = [emptyProfile(), { ...emptyProfile(), displayName: ' x '.repeat(30) }, { ...emptyProfile(), displayName: '  ' }, { ...emptyProfile(), salaryMinor: -5 },
    { ...emptyProfile(), payday: 32 }, { ...emptyProfile(), supportsDependents: false, dependentKinds: ['spouse'] }, { ...emptyProfile(), supportsDependents: true, dependentKinds: ['parents', 'spouse', 'spouse'] }]
  const slices = (cats: readonly Category[]) => Array.from({ length: rnd.int(0, 12) }, () => ({
    categoryId: rnd.next() < 0.15 ? null : rnd.pick([...cats.map((c) => c.id), 'unknown-id']),
    amountMinor: rnd.int(1, 200_000), count: rnd.int(1, 9), shareTenthPercent: 0,
  }))

  return {
    hslToHex: Array.from({ length: 60 }, () => [rnd.int(0, 359) + rnd.next(), rnd.int(0, 100), rnd.int(0, 100)]).map(([h, s, l]) => record([h, s, l], () => hslToHex(h!, s!, l!))),
    categoryColors: CATEGORY_SWATCHES.flatMap((s) => [record([s.h, s.s, s.l], () => categoryColors(s.h, s.s, s.l)), ...[0, 1, 5, 9].map((i) => record([s.h, s.s, s.l, i], () => subCategoryColors(s.h, s.s, s.l, i)))]),
    swatches: CATEGORY_SWATCHES.map((s) => record(s.key, () => ({ colors: swatchColors(s.key), keyOf: swatchKeyOf(swatchColors(s.key)!.lightColor.toLowerCase()) }))).concat([record('nope', () => ({ colors: swatchColors('nope'), keyOf: swatchKeyOf('#000000') }))]),
    firstFreeSwatch: sets.map((cats) => record(cats, () => firstFreeSwatch(cats))),
    hexToHsl: ['#1E6B4A', '1e6b4a', ' #FFFFFF ', '#000', 'zzzzzz', '#808080', '#FF0000', '#00FF00', '#0000FF', '#FFFF00', '#AA00AA'].map((h) => record(h, () => hexToHsl(h))),
    childColors: [['#1E6B4A', '#8ED3B0'], ['bad', 'bad2'], ['#AA0000', '#FF0000']].flatMap(([l, d]) => [0, 3, 7].map((i) => record({ parent: { lightColor: l, darkColor: d }, index: i }, () => childColors({ lightColor: l!, darkColor: d! }, i)))),
    groupCategoryOptions: sets.flatMap((cats) => keepIds.map((keepId) => record({ categories: cats, keepId }, () => groupCategoryOptions(cats, { keepId })))),
    manageCategoryView: sets.map((cats) => record(cats, () => manageCategoryView(cats))),
    categoryPlaceChoices: sets.flatMap((cats) => [null, cats[0]?.id ?? null, 'x-loose', 'x-legacy-kid'].map((id) => record({ categories: cats, editingId: id }, () => categoryPlaceChoices(cats, id)))),
    presentCategories: factsList.map((facts) => record({ categories: sets[1], facts }, () => presentCategories(sets[1]!, facts))),
    planCategorySave: saves.map((input) => record({ categories: sets[1], input }, () => planCategorySave(sets[1]!, input, () => 'new-id'))),
    parseStoredProfile: profiles.map((p) => record(p, () => parseStoredProfile(p))),
    checkProfile: checks.map((p) => record(p, () => checkProfile(p))),
    factsFromProfile: [null, emptyProfile(), { ...emptyProfile(), supportsDependents: false }, { ...emptyProfile(), dependentKinds: ['parents'] }, { ...emptyProfile(), dependentKinds: ['children'], hasCar: true }]
      .map((p) => record(p, () => factsFromProfile(p as UserProfile | null))),
    groupDistribution: sets.flatMap((cats) => [0, 1, 2].map(() => { const s = slices(cats); return record({ slices: s, categories: cats }, () => groupDistribution(s, cats)) })),
  }
}
