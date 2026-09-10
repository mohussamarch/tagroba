import { describe, it, expect } from 'vitest'
import { planIdRepair, emptyStoredData, type StoredData } from '../../src/domain/idRepair'
import { checkFullBackupData } from '../../src/domain/checkFullBackup'
import { checkBackupFinance } from '../../src/domain/checkBackupFinance'
import { BACKUP_GROUPS, type BackupGroup, type BackupRow, type FullBackupData } from '../../src/domain/fullBackup'
import { makeRepairStoredIds } from '../../src/application/useCases/repairStoredIds'
import { memoryIdRepair } from '../../src/infrastructure/memory/idRepair'
import { sanitizeAccountNumbers as redact } from '../../src/infrastructure/firestore/firestoreRepositories'

/**
 * إصلاح البيانات التي كتبها الحفظ القديم (HANDOVER §23). التلف هنا مصنوع
 * بنفس دالة القص الحقيقية على معرّفات بصيغة RandomIdGenerator الحقيقية.
 */

const now = '2026-09-10T00:00:00.000Z'
const TXN_A = 'txn-0mtqh4w00000-4n444r1n275n' // فيه «00000» ⇒ اتقص
const TXN_B = 'txn-0mtqh4wabcde-zzyyxxwwvvuu' // بلا 5 أرقام ⇒ سليم
const SRC = 'src-0mtqh4w00001-2b5v2e2b6v5s'
const BATCH = 'batch-0mtqh4w00002-4p1j0b3p083e'

function txn(id: string): BackupRow {
  return { id: redact(id), occurredAt: '2026-09-01', datePrecision: 'day', sourceOrder: 1,
    economicKind: 'purchase', economicKindConfirmed: false, observedDirection: 'out', amountMinor: 2999,
    currency: 'SAR', walletId: 'wallet-bank', categoryId: 'cat-food', merchantId: redact('merch-00001'),
    categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested', isCashTagged: false,
    createdAt: now, updatedAt: now }
}

/** شكل الحساب كما هو في Firestore بعد الحفظ القديم. */
function damagedAccount(): StoredData {
  const s = emptyStoredData()
  const put = (group: BackupGroup, docId: string, data: BackupRow) => s[group].push({ docId, data })
  put('wallets', 'wallet-bank', { id: 'wallet-bank', name: 'الراجحي', kind: 'bank', currency: 'SAR', openingBalanceMinor: 483783, openingAt: '2025-01-01' })
  put('categories', 'cat-food', { id: 'cat-food', parentId: null, name: 'مطاعم', iconKey: 'food', lightColor: '#fff', darkColor: '#000', active: true, order: 1 })
  put('merchants', 'merch-00001', { id: 'merch-00001', displayName: 'ALBAIK', normalizedName: 'albaik', verifiedCategoryId: 'cat-food' })
  put('people', 'person-a', { id: 'person-a', name: 'شخص', archived: false })
  put('transactions', TXN_A, txn(TXN_A))
  put('transactions', TXN_B, txn(TXN_B))
  put('importBatches', BATCH, { id: redact(BATCH), sourceType: 'csv_legacy', fileHash: 'h', fileName: 'statement.csv',
    importedAt: now, state: 'committed', counts: { total: 2, imported: 2, duplicates: 0, similar: 0, conflicts: 0, invalid: 0 } })
  put('sourceRecords', SRC, { id: redact(SRC), batchId: redact(BATCH), accountIdentity: 'الراجحي', sourceReference: null,
    sourceHash: 'h', originalRowIndex: 1, rawLine: 'TOACCT/****1127', transactionId: redact(TXN_A), matchingState: 'new', reason: 'جديد' })
  // التخصيص اتعمل من الشاشة بالمعرّف اللي رجع من القراءة — المقصوص
  put('allocations', 'alloc-1', { id: 'alloc-1', transactionId: redact(TXN_A), personId: 'person-a', allocationKind: 'receivable', amountMinor: 1000, currency: 'SAR' })
  return s
}

const asBackup = (s: StoredData) =>
  Object.fromEntries(BACKUP_GROUPS.map((g) => [g, s[g].map((r) => r.data)])) as FullBackupData

describe('إصلاح المعرّفات التالفة', () => {
  it('يرد حقل id والروابط لأصلها بيقين، ولا يترك روابط مجهولة', () => {
    const plan = planIdRepair(damagedAccount(), redact)
    const fields = (group: BackupGroup, docId: string) => plan.patches.find((p) => p.group === group && p.docId === docId)?.fields
    expect(fields('transactions', TXN_A)).toEqual({ id: TXN_A, merchantId: 'merch-00001' })
    expect(fields('transactions', TXN_B)).toEqual({ merchantId: 'merch-00001' })
    expect(fields('sourceRecords', SRC)).toEqual({ id: SRC, batchId: BATCH, transactionId: TXN_A })
    expect(fields('importBatches', BATCH)).toEqual({ id: BATCH })
    expect(fields('allocations', 'alloc-1')).toEqual({ transactionId: TXN_A })
    expect(plan.affected).toEqual({ transactions: 2, importBatches: 1, allocations: 1, sourceRecords: 1 })
    expect(plan.unresolved).toEqual([])
  })

  it('بعد التطبيق: لا شيء يحتاج إصلاحًا، والنسخة الشاملة تقبل الحساب — وكانت ترفضه قبله', async () => {
    const store = memoryIdRepair(damagedAccount())
    expect(() => { const d = asBackup(store.snapshot()); checkFullBackupData(d); checkBackupFinance(d) }).toThrow()
    const repair = makeRepairStoredIds(store, redact)
    const preview = await repair.preview()
    expect(preview.before).toHaveLength(5)
    expect((await repair.apply(preview.plan)).written).toBe(5)
    expect(planIdRepair(store.snapshot(), redact).patches).toEqual([])
    const repaired = asBackup(store.snapshot())
    expect(() => { checkFullBackupData(repaired); checkBackupFinance(repaired) }).not.toThrow()
  })

  it('لا يخمّن: رابط يطابق أكثر من معرّف بعد القص يبقى كما هو ويُعرض', () => {
    const s = damagedAccount()
    s.merchants.push({ docId: 'merch-10001', data: { id: 'merch-10001', displayName: 'X', normalizedName: 'x' } }) // نفس «****0001»
    const plan = planIdRepair(s, redact)
    expect(plan.patches.some((p) => 'merchantId' in p.fields)).toBe(false)
    expect(plan.unresolved.filter((u) => u.field === 'merchantId')).toHaveLength(2)
  })

  it('لا يلمس id مختلفًا عن مسار الوثيقة لسبب غير القص', () => {
    const s = damagedAccount()
    s.transactions[1].data.id = 'something-else'
    expect(planIdRepair(s, redact).patches.find((p) => p.docId === TXN_B)?.fields.id).toBeUndefined()
  })

  it('يعرض دفعات الاستيراد المعلّقة قبل التأكيد', () => {
    const s = damagedAccount()
    s.importBatches[0].data.state = 'staged'
    expect(planIdRepair(s, redact).stagedBatches).toEqual([{ docId: BATCH, fileName: 'statement.csv', imported: 2 }])
  })

  it('يكتب ما ظهر في المعاينة فقط؛ تلف ظهر بعدها يتخطى', async () => {
    const store = memoryIdRepair(damagedAccount())
    const repair = makeRepairStoredIds(store, redact)
    const preview = await repair.preview()
    const later = 'txn-0mtqh4w99999-aaaaaaaaaaaa'
    await store.apply([]) // لا شيء
    const raw = store.snapshot(); raw.transactions.push({ docId: later, data: txn(later) })
    const changed = memoryIdRepair(raw)
    const outcome = await makeRepairStoredIds(changed, redact).apply(preview.plan)
    expect(outcome.written).toBe(5)
    expect(outcome.skipped).toBe(1)
  })
})
