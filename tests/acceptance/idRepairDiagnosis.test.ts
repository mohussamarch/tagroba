import { describe, it, expect } from 'vitest'
import { planIdRepair, emptyStoredData, type StoredData } from '../../src/domain/idRepair'
import { diagnoseLinks } from '../../src/domain/idRepairDiagnosis'
import type { BackupGroup, BackupRow } from '../../src/domain/fullBackup'
import { sanitizeAccountNumbers as redact } from '../../src/infrastructure/firestore/firestoreRepositories'

/**
 * الـ396 رابط التايه — قرار المالك 2026-09-13 «ممنوع نتخلى عنهم». التشخيص قراءة فقط،
 * ويرجع أعداد وحالات من غير مبالغ. الحساب هنا بيعيد تمثيل اللي حصل: كشف اتستورد
 * مرتين، والتراجع عن الأولى حذف بمعرّفات مقصوصة.
 */

const EXCEL = 'batch-0mtqh4wabcde-aaaaaaaaaaaa'
const PDF = 'batch-0mtqh4wfghij-bbbbbbbbbbbb'
const DELETED = 'txn-0mtqh4w67890-cccccccccccc'      // اتحذف فعلًا بالتراجع — وليه شكل مقصوص
const SURVIVOR = 'txn-0mtqh4w12345-dddddddddddd'     // معرّفه اتقص ⇒ الحذف راح لمسار غلط وفضل
const TWIN = 'txn-0mtqh4wpqrst-eeeeeeeeeeee'          // نفس العملية من الـPDF، عليها مصدر

function txn(createdAt: string): BackupRow {
  return { occurredAt: '2026-08-01', amountMinor: 1500, observedDirection: 'out', createdAt }
}

function account(): StoredData {
  const s = emptyStoredData()
  const put = (group: BackupGroup, docId: string, data: BackupRow) => s[group].push({ docId, data })
  put('importBatches', EXCEL, { id: EXCEL, state: 'reverted', sourceType: 'csv_legacy', importedAt: '2026-09-07T10:30:00.000Z' })
  put('importBatches', PDF, { id: PDF, state: 'committed', sourceType: 'pdf_alrajhi', importedAt: '2026-09-08T06:30:00.000Z' })
  put('transactions', SURVIVOR, { id: SURVIVOR, ...txn('2026-09-07T10:34:10.000Z') })
  put('transactions', TWIN, { id: TWIN, ...txn('2026-09-08T06:30:05.000Z') })
  put('transactions', 'txn-manual-ffffffffffff', { id: 'txn-manual-ffffffffffff', occurredAt: '2026-09-02', amountMinor: 700, observedDirection: 'out', createdAt: '2026-09-02T18:00:00.000Z' })
  // سجلات الـPDF: واحد «مكرر» كان متربط بعملية الإكسل اللي اتحذفت، وواحد جديد للتوأم
  put('sourceRecords', 'src-a', { id: 'src-a', batchId: PDF, transactionId: DELETED, matchingState: 'duplicate' })
  put('sourceRecords', 'src-b', { id: 'src-b', batchId: PDF, transactionId: redact(DELETED), matchingState: 'duplicate' })
  put('sourceRecords', 'src-c', { id: 'src-c', batchId: PDF, transactionId: TWIN, matchingState: 'new' })
  return s
}

describe('تشخيص الروابط التايهة', () => {
  it('يجمّع الروابط بالمكان والحقل وشكل القيمة وحالة الدفعة، ومجموعها = اللي الخطة سابتاه', () => {
    const s = account()
    const diagnosis = diagnoseLinks(s, redact)
    expect(diagnosis.unresolved).toEqual([
      { group: 'sourceRecords', field: 'transactionId', target: 'transactions', shape: 'intact', ambiguous: false,
        batch: { state: 'committed', sourceType: 'pdf_alrajhi', day: '2026-09-08' }, matchingState: 'duplicate', count: 1 },
      { group: 'sourceRecords', field: 'transactionId', target: 'transactions', shape: 'damaged', ambiguous: false,
        batch: { state: 'committed', sourceType: 'pdf_alrajhi', day: '2026-09-08' }, matchingState: 'duplicate', count: 1 },
    ])
    const total = diagnosis.unresolved.reduce((sum, u) => sum + u.count, 0)
    expect(total).toBe(planIdRepair(s, redact).unresolved.length)
  })

  it('يلاقي العمليات اليتيمة ويربطها بوقت دفعة متراجَع عنها وبتوأمها', () => {
    const { orphans, orphanTotal } = diagnoseLinks(account(), redact)
    expect(orphanTotal).toBe(2)
    expect(orphans).toContainEqual({ createdMinute: '2026-09-07T10:34', count: 1, withTwin: 1,
      nearBatch: { state: 'reverted', sourceType: 'csv_legacy', day: '2026-09-07' } })
    expect(orphans).toContainEqual({ createdMinute: '2026-09-02T18:00', count: 1, withTwin: 0, nearBatch: null })
  })

  it('العملية اللي عليها سجل بشكل معرّفها المقصوص مش يتيمة', () => {
    const s = account()
    s.sourceRecords.push({ docId: 'src-d', data: { id: 'src-d', batchId: PDF, transactionId: redact(SURVIVOR), matchingState: 'new' } })
    expect(diagnoseLinks(s, redact).orphanTotal).toBe(1)
  })

  it('ما يرجعش مبالغ ولا معرّفات', () => {
    const text = JSON.stringify(diagnoseLinks(account(), redact))
    expect(text).not.toContain('1500')
    expect(text).not.toContain('txn-')
  })
})
