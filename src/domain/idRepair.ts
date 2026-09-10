import { BACKUP_GROUPS, BACKUP_RELATIONS, type BackupGroup, type BackupRow } from './fullBackup'

/**
 * إصلاح المعرّفات التالفة — لمرة واحدة (HANDOVER §23).
 *
 * قبل التصحيح كانت حماية أرقام الحسابات تقص أي 5 أرقام متتالية في **كل**
 * حقل نصي للعمليات وسجلات المصدر ودفعات الاستيراد، بما فيها `id` والروابط.
 * مسار الوثيقة بقي بالمعرّف الأصلي، فالحقيقة موجودة ويمكن استرجاعها بيقين:
 *   - حقل `id` يُرد لمعرّف الوثيقة **فقط** لو `redact(docId) === id`
 *   - الرابط يُرد لأصله **فقط** لو معرّف واحد بالضبط يعطي نفس القيمة بعد القص
 * أي حالة غير ذلك تُترك كما هي وتُعرض — لا تخمين.
 */

/** المجموعات التي كُتبت عبر القص القديم؛ فيها معرّف الوثيقة هو الحقيقة. */
const SANITIZED_GROUPS: readonly BackupGroup[] = ['transactions', 'sourceRecords', 'importBatches']
const sanitized = new Set<BackupGroup>(SANITIZED_GROUPS)

/** مستند كما هو مخزَّن: معرّف الوثيقة الحقيقي + الحقول كما كُتبت. */
export interface StoredRow {
  docId: string
  data: BackupRow
}
export type StoredData = Record<BackupGroup, StoredRow[]>

export interface IdPatch {
  group: BackupGroup
  docId: string
  fields: Record<string, string>
}

export interface IdRepairPlan {
  patches: IdPatch[]
  /** عدد المستندات التي ستتعدل لكل مجموعة. */
  affected: Partial<Record<BackupGroup, number>>
  /** روابط لا تطابق شيئًا ولا يمكن ردها بيقين — تبقى كما هي. */
  unresolved: { group: BackupGroup; docId: string; field: string }[]
  /** دفعات استيراد لم تكتمل: بعد الإصلاح يقدر التطبيق ينظّفها عند الفتح. */
  stagedBatches: { docId: string; fileName: string; imported: number }[]
}

export function emptyStoredData(): StoredData {
  return Object.fromEntries(BACKUP_GROUPS.map((group) => [group, [] as StoredRow[]])) as StoredData
}

export function planIdRepair(stored: StoredData, redact: (text: string) => string): IdRepairPlan {
  const patches = new Map<string, IdPatch>()
  const setField = (group: BackupGroup, docId: string, field: string, value: string) => {
    const key = `${group}/${docId}`
    const entry = patches.get(key) ?? { group, docId, fields: {} }
    entry.fields[field] = value
    patches.set(key, entry)
  }

  // المعرّفات الحقيقية لكل مجموعة، وفهرس «الشكل المقصوص ← الأصل» (null = غير وحيد)
  const trueIds = {} as Record<BackupGroup, Set<string>>
  const byDamaged = {} as Record<BackupGroup, Map<string, string | null>>
  for (const group of BACKUP_GROUPS) {
    const ids = new Set<string>()
    const index = new Map<string, string | null>()
    for (const row of stored[group]) {
      const id = sanitized.has(group) ? row.docId : String(row.data.id ?? '')
      if (!id) continue
      ids.add(id)
      const damaged = redact(id)
      if (damaged !== id) index.set(damaged, index.has(damaged) ? null : id)
    }
    trueIds[group] = ids
    byDamaged[group] = index
  }

  for (const group of SANITIZED_GROUPS) {
    for (const row of stored[group]) {
      if (row.data.id !== row.docId && redact(row.docId) === row.data.id) {
        setField(group, row.docId, 'id', row.docId)
      }
    }
  }

  const unresolved: IdRepairPlan['unresolved'] = []
  for (const group of BACKUP_GROUPS) {
    for (const row of stored[group]) {
      for (const [field, target] of Object.entries(BACKUP_RELATIONS[group] ?? {})) {
        const value = row.data[field]
        if (typeof value !== 'string' || trueIds[target].has(value)) continue
        const original = byDamaged[target].get(value)
        if (original) setField(group, row.docId, field, original)
        else unresolved.push({ group, docId: row.docId, field })
      }
    }
  }

  const list = [...patches.values()]
  const affected: IdRepairPlan['affected'] = {}
  for (const patch of list) affected[patch.group] = (affected[patch.group] ?? 0) + 1

  const stagedBatches = stored.importBatches
    .filter((row) => row.data.state === 'staged')
    .map((row) => ({
      docId: row.docId,
      fileName: String(row.data.fileName ?? ''),
      imported: Number((row.data.counts as BackupRow | undefined)?.imported ?? 0),
    }))

  return { patches: list, affected, unresolved, stagedBatches }
}
