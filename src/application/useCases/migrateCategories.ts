import { planCategoryMigration, type CategoryMigrationPlan } from '../../domain/categoryMigration'
import type { Category, Id } from '../../domain/entities/types'
import type { IdPatch } from '../../domain/idRepair'
import type { IdRepairPort } from '../ports/IdRepairPort'
import type { RepairBackupPort } from '../ports/RepairBackupPort'
import type { CategoryRepository, Clock } from '../ports/repositories'
import { REPAIR_CHUNK, type RepairProgress } from './repairStoredIds'
import { saveVerifiedBackup, type VerifiedBackup } from './verifiedBackup'

export interface CategoryMigrationOutcome {
  created: number
  written: number
  hidden: number
  /** تعديلات اتغيرت على الخادم بعد المعاينة فاتخطت. */
  skipped: number
  backup: VerifiedBackup | null
}

/** المعاينة + الشجرة كلها (عشان اختيار وجهة الغامض) + الاختيارات اللي اتعملت عليها. */
export interface CategoryMigrationPreview extends CategoryMigrationPlan {
  targets: readonly Category[]
  choices: Readonly<Record<Id, Id>>
}

export interface MigrateCategoriesDeps {
  port: IdRepairPort
  categories: CategoryRepository
  backup: RepairBackupPort
  clock: Clock
  tree: { categories: readonly Category[]; aliases: ReadonlyMap<string, Id> }
  /** أسماء التصنيفات القديمة بترتيبها (`tokens.json`) — منها بتتحسب المعرّفات القديمة. */
  legacyNames: readonly string[]
}

const patchKey = (p: IdPatch) => `${p.group}/${p.docId}`

/**
 * MigrateCategories — نقل حساب قديم لشجرة التصنيفات (OVERRIDES §28 و§28.1).
 * نص المالك: «يضيف الناقص وبس» + «انقلها للتصنيف الجديد» بنسخة ومعاينة بالعدد يوافق عليها.
 *
 * نفس نمط `repairStoredIds`: المعاينة قراءة بس، والتطبيق بيقرا من الخادم تاني ويكتب **المعروض بس**
 * (أي تعديل اتغير بعد المعاينة بيتخطى).
 *
 * الترتيب الملزم: نسخة متأكدة (المستندات اللي هتتعدل + التصنيفات اللي هتتخفي) ← إضافة الناقص ←
 * تعديل حقل التصنيف على دفعات ← **إخفاء المدموج بس بعد ما كل التعديلات تنجح** (`active: false`، مش مسح).
 * لو النسخة ناقصة ما يتكتبش ولا حرف. انقطاع في النص ⇒ المعاينة التانية بتلاقي الباقي بس.
 * التصنيف الغامض («تأمين») ما بيتنقلش إلا لو المستخدم اختار وجهته (`choices`)؛ والتعارضات ما بتتلمسش.
 */
export function makeMigrateCategories(deps: MigrateCategoriesDeps) {
  const planWith = (stored: Awaited<ReturnType<IdRepairPort['readAll']>>, choices: Readonly<Record<Id, Id>>) =>
    planCategoryMigration(stored, deps.tree, deps.legacyNames, new Map(Object.entries(choices)))

  async function preview(choices: Readonly<Record<Id, Id>> = {}): Promise<CategoryMigrationPreview> {
    return { ...planWith(await deps.port.readAll(), choices), targets: deps.tree.categories, choices }
  }

  async function apply(previewed: CategoryMigrationPreview, onProgress?: (progress: RepairProgress) => void): Promise<CategoryMigrationOutcome> {
    const stored = await deps.port.readAll()
    const fresh = planWith(stored, previewed.choices)

    const approved = new Map(previewed.patches.map((p) => [patchKey(p), JSON.stringify(p.fields)]))
    const patches = fresh.patches.filter((p) => approved.get(patchKey(p)) === JSON.stringify(p.fields))
    const skipped = fresh.patches.length - patches.length
    const approvedCreate = new Set(previewed.create.map((c) => c.id))
    const create = fresh.create.filter((c) => approvedCreate.has(c.id))
    const approvedMoves = new Set(previewed.moves.map((m) => `${m.fromId}>${m.toId}`))
    const hideIds = fresh.moves.filter((m) => approvedMoves.has(`${m.fromId}>${m.toId}`)).map((m) => m.fromId)

    const current = await deps.categories.listAll()
    const toHide = current.filter((c) => hideIds.includes(c.id) && c.active)
    if (patches.length === 0 && create.length === 0 && toHide.length === 0) {
      return { created: 0, written: 0, hidden: 0, skipped, backup: null }
    }

    const touched = new Set(patches.map(patchKey))
    const before = [
      ...Object.entries(stored).flatMap(([group, rows]) =>
        rows.filter((row) => touched.has(`${group}/${row.docId}`)).map((row) => ({ group, ...row }))),
      ...toHide.map((category) => ({ group: 'categories', docId: category.id, data: category })),
    ]
    const saved = await saveVerifiedBackup(deps.backup, deps.clock, 'before-category-move', before)

    for (const category of create) await deps.categories.save(category)

    let written = 0
    onProgress?.({ written, total: patches.length })
    for (let i = 0; i < patches.length; i += REPAIR_CHUNK) {
      try {
        written += await deps.port.apply(patches.slice(i, i + REPAIR_CHUNK))
      } catch (error) {
        const cause = error instanceof Error ? error.message : String(error)
        throw new Error(`اتضاف ${create.length} تصنيف واتعدل ${written} من ${patches.length} قبل الانقطاع (${cause}). ما اتخفاش ولا تصنيف قديم.`)
      }
      onProgress?.({ written, total: patches.length })
    }

    for (const category of toHide) await deps.categories.save({ ...category, active: false })

    return { created: create.length, written, hidden: toHide.length, skipped, backup: saved }
  }

  return { preview, apply }
}
