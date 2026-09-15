import { normalizeText } from './normalize'
import type { Category, Id } from './entities/types'
import type { BackupGroup } from './fullBackup'
import type { IdPatch, StoredData } from './idRepair'

/**
 * خطة نقل حساب قديم لشجرة التصنيفات الجديدة — OVERRIDES §28 و§28.1.
 * نص المالك: «يضيف الناقص وبس» + «انقلها للتصنيف الجديد» (بنسخة ومعاينة بالعدد يوافق عليها).
 *
 * **دالة قراءة بس** — مش بتكتب حاجة. بترجع:
 * - `create`: تصنيفات الشجرة اللي مش موجودة في الحساب (بالمعرّف).
 * - `update`: تصنيفات موجودة بنفس معرّف الشجرة، ياخدوا مجموعتها ورمزها وألوانها وشروط ظهورها **والاسم و`active` زي ما هم**
 *   (§28: «يضيف الفروع والرموز والألوان الجديدة، من غير تغيير اسم تصنيف عدّله»).
 * - `moves`: كل تصنيف قديم اتدمج ← وجهته، ومعاه عدد العمليات والقواعد والتجار وسقوف الميزانية اللي هتتنقل.
 * - `patches`: تعديل حقل التصنيف بس (`categoryId` / `verifiedCategoryId`) — **ولا تأكيد ولا مبلغ ولا تاريخ بيتلمس**.
 * - `ambiguous`: اسم قديم ليه أكتر من وجهة («تأمين») — **ما بيتنقلش** وبيتسأل عنه المالك بالأعداد.
 * - `untouched`: تصنيفات الحساب اللي مش من المرجع القديم ولا من الشجرة (عملها المستخدم بنفسه) — ما بتتلمسش.
 * - `budgetConflicts`: سقفين لنفس الميزانية هيقعوا على نفس التصنيف الجديد — ما بيتنقلوش ويتسأل عنهم.
 *
 * الربط بمعرّف التصنيف القديم (`cat-<الاسم القديم مطبعن>`) مش باسمه الحالي، فتصنيف المستخدم غيّر اسمه
 * بيتنقل صح. التطبيق مرتين ما بيكررش: التاني بيلاقي الحقول على المعرّف الجديد خلاص.
 */

export interface MoveCounts {
  transactions: number
  rules: number
  merchants: number
  budgets: number
}

export interface CategoryMove extends MoveCounts {
  fromId: Id
  fromName: string
  toId: Id
  toName: string
}

export interface CategoryMigrationPlan {
  create: Category[]
  update: { id: Id; name: string; category: Category }[]
  moves: CategoryMove[]
  patches: IdPatch[]
  ambiguous: (MoveCounts & { id: Id; name: string })[]
  untouched: { id: Id; name: string }[]
  budgetConflicts: { budgetId: Id; toId: Id; fromIds: Id[] }[]
}

/** نفس معرّف `buildCategories` القديم. */
export function legacyCategoryId(name: string, index: number): Id {
  return `cat-${normalizeText(name).replace(/\s+/g, '-').toLowerCase() || `n${index}`}`
}

const FIELD: Partial<Record<BackupGroup, string>> = {
  transactions: 'categoryId',
  rules: 'categoryId',
  merchants: 'verifiedCategoryId',
  categoryBudgets: 'categoryId',
}
const COUNT_KEY: Record<string, keyof MoveCounts> = {
  transactions: 'transactions', rules: 'rules', merchants: 'merchants', categoryBudgets: 'budgets',
}

export function planCategoryMigration(
  stored: StoredData,
  tree: { categories: readonly Category[]; aliases: ReadonlyMap<string, Id> },
  legacyNames: readonly string[],
  /** وجهة يختارها المستخدم لتصنيف غامض (مثلًا «تأمين» ← تأمين السيارة). وجهة مش في الشجرة بتتجاهل. */
  choices: ReadonlyMap<Id, Id> = new Map(),
): CategoryMigrationPlan {
  const accountCategories = stored.categories.map((row) => ({
    id: row.docId,
    name: typeof row.data.name === 'string' ? row.data.name : row.docId,
  }))
  const accountIds = new Set(accountCategories.map((c) => c.id))
  const treeById = new Map(tree.categories.map((c) => [c.id, c]))

  const create = tree.categories.filter((c) => !accountIds.has(c.id))

  const TREE_FIELDS = ['parentId', 'groupKey', 'iconKey', 'lightColor', 'darkColor', 'requires', 'noCarName', 'noCarIconKey'] as const
  const update = stored.categories.flatMap((row) => {
    const target = treeById.get(row.docId)
    if (!target) return []
    const differs = TREE_FIELDS.some((field) => (row.data[field] ?? null) !== (target[field] ?? null))
    if (!differs) return []
    const current = row.data as Partial<Category>
    const category: Category = {
      ...target,
      name: typeof current.name === 'string' && current.name ? current.name : target.name,
      active: typeof current.active === 'boolean' ? current.active : target.active,
      order: typeof current.order === 'number' ? current.order : target.order,
    }
    return [{ id: row.docId, name: category.name, category }]
  })

  // تصنيف قديم (من المرجع) اسمه اتغير في الشجرة ⇒ وجهة واحدة أو غامض
  const destination = new Map<Id, Id>()
  const legacyIds = new Set<Id>()
  const ambiguousIds = new Set<Id>()
  legacyNames.forEach((name, index) => {
    const oldId = legacyCategoryId(name, index)
    legacyIds.add(oldId)
    if (!accountIds.has(oldId) || treeById.has(oldId)) return
    const target = tree.aliases.get(normalizeText(name))
    const chosen = choices.get(oldId)
    if (target && target !== oldId && treeById.has(target)) destination.set(oldId, target)
    else if (chosen && treeById.has(chosen)) destination.set(oldId, chosen)
    else ambiguousIds.add(oldId)
  })

  const counts = new Map<Id, MoveCounts>()
  const countFor = (id: Id) => {
    const existing = counts.get(id) ?? { transactions: 0, rules: 0, merchants: 0, budgets: 0 }
    counts.set(id, existing)
    return existing
  }

  // سقفين من نفس الميزانية على نفس الوجهة (أو وجهة عليها سقف أصلًا) ⇒ تعارض، ما يتنقلش
  const budgetTargets = new Map<string, Id[]>()
  for (const row of stored.categoryBudgets) {
    const from = row.data.categoryId as Id
    const to = destination.get(from) ?? (treeById.has(from) ? from : null)
    if (!to || typeof row.data.budgetId !== 'string') continue
    const key = `${row.data.budgetId}|${to}`
    budgetTargets.set(key, [...(budgetTargets.get(key) ?? []), from])
  }
  const budgetConflicts = [...budgetTargets.entries()]
    .filter(([, fromIds]) => fromIds.length > 1 && fromIds.some((id) => destination.has(id)))
    .map(([key, fromIds]) => {
      const [budgetId, toId] = key.split('|') as [Id, Id]
      return { budgetId, toId, fromIds }
    })
  const conflicted = new Set(budgetConflicts.map((c) => `${c.budgetId}|${c.toId}`))

  const patches: IdPatch[] = []
  for (const [group, field] of Object.entries(FIELD) as [BackupGroup, string][]) {
    for (const row of stored[group]) {
      const current = row.data[field]
      if (typeof current !== 'string') continue
      if (ambiguousIds.has(current)) { countFor(current)[COUNT_KEY[group]!]++; continue }
      const to = destination.get(current)
      if (!to) continue
      if (group === 'categoryBudgets' && conflicted.has(`${row.data.budgetId}|${to}`)) continue
      countFor(current)[COUNT_KEY[group]!]++
      patches.push({ group, docId: row.docId, fields: { [field]: to } })
    }
  }

  const nameOf = (id: Id) => accountCategories.find((c) => c.id === id)?.name ?? id
  const empty: MoveCounts = { transactions: 0, rules: 0, merchants: 0, budgets: 0 }
  const total = (c: MoveCounts) => c.transactions + c.rules + c.merchants + c.budgets
  // تصنيف قديم اتخفى خلاص ومفيش حاجة عليه ⇒ النقل خلص، ما يتعرضش تاني
  const hidden = new Set(stored.categories.filter((row) => row.data.active === false).map((row) => row.docId))
  const pending = (id: Id) => !hidden.has(id) || total(counts.get(id) ?? empty) > 0
  const moves: CategoryMove[] = [...destination.entries()].filter(([fromId]) => pending(fromId)).map(([fromId, toId]) => ({
    fromId, fromName: nameOf(fromId), toId, toName: treeById.get(toId)!.name, ...(counts.get(fromId) ?? empty),
  }))
  const ambiguous = [...ambiguousIds].filter(pending).map((id) => ({
    id, name: nameOf(id), ...(counts.get(id) ?? empty),
  }))
  const untouched = accountCategories.filter((c) => !legacyIds.has(c.id) && !treeById.has(c.id))

  return { create, update, moves, patches, ambiguous, untouched, budgetConflicts }
}
