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
): CategoryMigrationPlan {
  const accountCategories = stored.categories.map((row) => ({
    id: row.docId,
    name: typeof row.data.name === 'string' ? row.data.name : row.docId,
  }))
  const accountIds = new Set(accountCategories.map((c) => c.id))
  const treeById = new Map(tree.categories.map((c) => [c.id, c]))

  const create = tree.categories.filter((c) => !accountIds.has(c.id))

  // تصنيف قديم (من المرجع) اسمه اتغير في الشجرة ⇒ وجهة واحدة أو غامض
  const destination = new Map<Id, Id>()
  const legacyIds = new Set<Id>()
  const ambiguousIds = new Set<Id>()
  legacyNames.forEach((name, index) => {
    const oldId = legacyCategoryId(name, index)
    legacyIds.add(oldId)
    if (!accountIds.has(oldId) || treeById.has(oldId)) return
    const target = tree.aliases.get(normalizeText(name))
    if (target && target !== oldId && treeById.has(target)) destination.set(oldId, target)
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
  const moves: CategoryMove[] = [...destination.entries()].map(([fromId, toId]) => ({
    fromId, fromName: nameOf(fromId), toId, toName: treeById.get(toId)!.name,
    ...(counts.get(fromId) ?? { transactions: 0, rules: 0, merchants: 0, budgets: 0 }),
  }))
  const ambiguous = [...ambiguousIds].map((id) => ({
    id, name: nameOf(id), ...(counts.get(id) ?? { transactions: 0, rules: 0, merchants: 0, budgets: 0 }),
  }))
  const untouched = accountCategories.filter((c) => !legacyIds.has(c.id) && !treeById.has(c.id))

  return { create, moves, patches, ambiguous, untouched, budgetConflicts }
}
