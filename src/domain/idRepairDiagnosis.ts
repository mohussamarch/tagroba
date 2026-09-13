import { BACKUP_GROUPS, BACKUP_RELATIONS, type BackupGroup, type BackupRow } from './fullBackup'
import { buildIdIndex, type StoredData } from './idRepair'

/**
 * تشخيص ما لا يرده الإصلاح بيقين — قراءة فقط (قرار المالك 2026-09-13: «ممنوع نتخلى عنهم»).
 *
 * يرجع **أعدادًا وحالات وأوقاتًا** بس، من غير مبالغ ولا أوصاف ولا معرّفات، عشان
 * النتيجة تتعرض على الشاشة وتتكتب في HANDOVER (المستودع علني) من غير ما تكشف الحساب.
 *
 * سؤالين:
 *   1. الروابط التايهة: حقل بيشاور على مستند مش موجود — متجمّعة بالمكان والحقل وشكل
 *      القيمة، ولسجلات المصدر بحالة دفعتها وحالة مطابقتها.
 *   2. العمليات اليتيمة: عملية مفيش ولا سجل مصدر بيشاور عليها لا بمعرّفها ولا بشكله
 *      المقصوص. التراجع عن دفعة كان بيحذف بالمعرّف المخزن في سجل المصدر، فلو كان
 *      مقصوصًا الحذف بيروح لمسار مش موجود وينجح بصمت وتفضل العملية.
 */

export interface BatchInfo { state: string; sourceType: string; day: string }

export interface UnresolvedGroup {
  group: BackupGroup
  field: string
  target: BackupGroup
  /** القيمة فيها «****» (اتقصت قبل كده) ولا شكلها سليم. */
  shape: 'damaged' | 'intact'
  /** أكتر من معرّف حقيقي بيدي نفس الشكل المقصوص. */
  ambiguous: boolean
  /** دفعة سجل المصدر: حالتها ونوعها ويوم استيرادها. */
  batch: BatchInfo | null
  /** حالة مطابقة سجل المصدر وقت الاستيراد (جديد/مكرر/…). */
  matchingState: string | null
  count: number
}

export interface OrphanGroup {
  /** دقيقة الإنشاء «YYYY-MM-DDTHH:MM» بتوقيت جرينتش. */
  createdMinute: string
  count: number
  /** منها ليها عملية تانية بنفس اليوم والمبلغ والاتجاه **وعليها مصدر**. */
  withTwin: number
  /** أقرب دفعة اتسجلت في حدود 10 دقايق من الإنشاء. */
  nearBatch: BatchInfo | null
}

export interface LinkDiagnosis {
  unresolved: UnresolvedGroup[]
  orphans: OrphanGroup[]
  orphanTotal: number
}

const NEAR_BATCH_MS = 10 * 60_000

export function diagnoseLinks(stored: StoredData, redact: (text: string) => string): LinkDiagnosis {
  const { trueIds, byDamaged } = buildIdIndex(stored, redact)

  const batchById = new Map<string, BatchInfo>()
  const batchTimes: { at: number; info: BatchInfo }[] = []
  for (const row of stored.importBatches) {
    const importedAt = String(row.data.importedAt ?? '')
    const info = { state: String(row.data.state ?? ''), sourceType: String(row.data.sourceType ?? ''), day: importedAt.slice(0, 10) }
    batchById.set(row.docId, info)
    const damaged = redact(row.docId)
    if (damaged !== row.docId && !batchById.has(damaged)) batchById.set(damaged, info)
    const at = Date.parse(importedAt)
    if (!Number.isNaN(at)) batchTimes.push({ at, info })
  }

  const unresolved = new Map<string, UnresolvedGroup>()
  for (const group of BACKUP_GROUPS) {
    for (const row of stored[group]) {
      for (const [field, target] of Object.entries(BACKUP_RELATIONS[group] ?? {})) {
        const value = row.data[field]
        if (typeof value !== 'string' || trueIds[target].has(value) || byDamaged[target].get(value)) continue
        const isSource = group === 'sourceRecords'
        const batchId = isSource ? row.data.batchId : undefined
        const entry: UnresolvedGroup = {
          group, field, target,
          shape: value.includes('****') ? 'damaged' : 'intact',
          ambiguous: byDamaged[target].has(value),
          batch: typeof batchId === 'string' ? batchById.get(batchId) ?? null : null,
          matchingState: isSource ? String(row.data.matchingState ?? '') : null,
          count: 0,
        }
        const key = JSON.stringify([group, field, entry.shape, entry.ambiguous, entry.batch, entry.matchingState])
        const found = unresolved.get(key) ?? entry
        found.count++
        unresolved.set(key, found)
      }
    }
  }

  const referenced = new Set<string>()
  for (const row of stored.sourceRecords) {
    if (typeof row.data.transactionId === 'string') referenced.add(row.data.transactionId)
  }
  const isReferenced = (docId: string) => referenced.has(docId) || referenced.has(redact(docId))
  const twinKey = (data: BackupRow) => `${data.occurredAt}|${data.amountMinor}|${data.observedDirection}`
  const sourcedKeys = new Set(stored.transactions.filter((row) => isReferenced(row.docId)).map((row) => twinKey(row.data)))

  const nearestBatch = (at: number): BatchInfo | null => {
    let best: { diff: number; info: BatchInfo } | null = null
    for (const batch of batchTimes) {
      const diff = Math.abs(at - batch.at)
      if (diff <= NEAR_BATCH_MS && (!best || diff < best.diff)) best = { diff, info: batch.info }
    }
    return best?.info ?? null
  }

  const orphans = new Map<string, OrphanGroup>()
  let orphanTotal = 0
  for (const row of stored.transactions) {
    if (isReferenced(row.docId)) continue
    orphanTotal++
    const createdAt = String(row.data.createdAt ?? '')
    const minute = createdAt.slice(0, 16)
    const entry = orphans.get(minute) ?? { createdMinute: minute, count: 0, withTwin: 0, nearBatch: nearestBatch(Date.parse(createdAt)) }
    entry.count++
    if (sourcedKeys.has(twinKey(row.data))) entry.withTwin++
    orphans.set(minute, entry)
  }

  const byCount = <T extends { count: number }>(a: T, b: T) => b.count - a.count
  return { unresolved: [...unresolved.values()].sort(byCount), orphans: [...orphans.values()].sort(byCount), orphanTotal }
}
