import { afterEach, describe, expect, it } from 'vitest'
import { initializeApp, deleteApp, type FirebaseApp } from 'firebase/app'
import { connectFirestoreEmulator, getFirestore, terminate, type Firestore } from 'firebase/firestore'
import { makeFullBackup } from '../../src/application/useCases/fullBackup'
import { firestoreFullBackup } from '../../src/infrastructure/firestore/fullBackupRepository'
import { memoryFullBackup } from '../../src/infrastructure/memory/fullBackup'
import { backupDigest } from '../../src/infrastructure/backupDigest'
import { backupChecksumText } from '../../src/domain/backupProfile'
import { BACKUP_GROUPS, LATER_BACKUP_GROUPS, backupRowId, emptyBackupData, type BackupGroup, type BackupRow, type FullBackupData } from '../../src/domain/fullBackup'

/**
 * النسخة الشاملة على Firestore Emulator حقيقي (مشروع demo بس) — بند Codex §38-4 «حفظ/استعادة نسخة شاملة على حساب تجريبي».
 * حساب تجريبي فاضي بيستقبل نسخة فيها كل المجموعات الـ24 (وأكتر من 200 دفعة عشان الصفحات)، وبيتقري تاني من السيرفر.
 * التشغيل: HANDOVER §38 (`MASROUFY_TEST_FIRESTORE=127.0.0.1:8088`). بيانات وهمية.
 */
const enabled = process.env.MASROUFY_TEST_FIRESTORE === '127.0.0.1:8088'
const clients: { app: FirebaseApp; db: Firestore }[] = []
function account() {
  const uid = 'backup-' + crypto.randomUUID()
  const app = initializeApp({ projectId: 'demo-masroufy-audit', apiKey: 'fake-test-key', appId: 'backup-test' }, crypto.randomUUID())
  const db = getFirestore(app)
  connectFirestoreEmulator(db, '127.0.0.1', 8088, { mockUserToken: { sub: uid } })
  clients.push({ app, db })
  return firestoreFullBackup(db, uid)
}
afterEach(async () => { for (const { db, app } of clients.splice(0)) { await terminate(db); await deleteApp(app) } })

const now = '2026-09-10T00:00:00Z'
function fixture(): FullBackupData {
  const data = emptyBackupData()
  data.wallets = [{ id: 'w', name: 'بنك', kind: 'bank', currency: 'SAR', openingBalanceMinor: 0, openingAt: '2020-01-01' }]
  data.categories = [{ id: 'c', parentId: null, name: 'مطاعم', iconKey: 'food', lightColor: '#fff', darkColor: '#000', active: true, order: 1 }]
  data.merchants = [{ id: 'm', displayName: 'Store', normalizedName: 'store', verifiedCategoryId: 'c', aliases: ['shop'] }]
  data.rules = [{ id: 'r', priority: 1, matchText: 'shop', matchMode: 'contains', categoryId: 'c', enabled: true }]
  data.people = [{ id: 'p', name: 'شخص تجريبي', archived: false }]
  data.tags = [{ id: 'tag', displayName: 'هدية', normalizedName: 'هدية' }]
  data.transactions = [{ id: 't', occurredAt: '2020-01-02', datePrecision: 'day', sourceOrder: 1, economicKind: 'purchase', economicKindConfirmed: true, observedDirection: 'out', amountMinor: 5000, currency: 'SAR', walletId: 'w', merchantId: 'm', categoryId: 'c', categoryConfirmed: true, excludedFromBudget: false, reviewState: 'confirmed', isCashTagged: false, createdAt: now, updatedAt: now }]
  data.obligations = [{ id: 'o', personId: 'p', originTransactionId: 't', kind: 'receivable', originalMinor: 2000, currency: 'SAR' }]
  data.allocations = [{ id: 'a', transactionId: 't', personId: 'p', allocationKind: 'receivable', amountMinor: 2000, currency: 'SAR' }]
  data.settlements = [{ id: 's', transactionId: 't', obligationId: 'o', amountMinor: 1000 }]
  data.transactionTags = [{ id: 'tt', transactionId: 't', tagId: 'tag' }]
  data.budgets = [{ id: '2030-01', periodKey: '2030-01', periodStart: '2030-01-28', periodEnd: '2030-02-27', totalLimitMinor: 80000, thresholdPercent: 80, createdAt: now, updatedAt: now }]
  data.categoryBudgets = [{ id: 'cb', budgetId: '2030-01', categoryId: 'c', limitMinor: 20000, notifyEnabled: true, thresholdPercent: 80 }]
  data.assets = [{ id: 'gold', name: 'ذهب', kind: 'gold', unitLabel: 'جرام', currency: 'SAR', archived: false }]
  data.assetLots = [{ id: 'lot', assetId: 'gold', purchasedAt: '2020-01-02', quantity: 100000000, principalMinor: 30000, feeMinor: 0 }]
  data.assetSales = [{ id: 'sale', assetId: 'gold', soldAt: '2021-01-02', quantity: 50000000, grossProceedsMinor: 20000, feeMinor: 0 }]
  data.assetPrices = [{ assetId: 'gold', pricePerUnitMinor: 40000, asOf: '2026-09-10', source: 'manual' }]
  data.recurringItems = [{ id: 'sub', name: 'اشتراك', merchantKey: 'shop', kind: 'subscription', cycleMonths: 1, expectedMinor: 1000, currency: 'SAR', nextDueAt: '2026-10-01', active: true, confirmed: true }]
  const batch = { id: 'batch', sourceType: 'sms', fileHash: 'test', fileName: 'test', importedAt: now, state: 'committed', counts: { total: 1, imported: 1, duplicates: 0, similar: 0, conflicts: 0, invalid: 0 } }
  // أكتر من 200 عشان قراية السيرفر بصفحات 200 تتجرب
  data.importBatches = Array.from({ length: 205 }, (_, i) => ({ ...batch, id: i === 0 ? 'batch' : 'batch' + i }))
  data.sourceRecords = [{ id: 'source', batchId: 'batch', accountIdentity: 'bank', sourceReference: null, sourceHash: 'hash', originalRowIndex: 1, rawLine: 'redacted', transactionId: 't', matchingState: 'new', reason: 'new' }]
  data.notificationReceipts = [{ eventKey: 'budget|2030-01', threshold: 80, periodStart: '2030-01-28', sentAt: now }]
  data.projects = [{ id: 'proj', name: 'مشروع', normalizedName: 'مشروع', archived: false, createdAt: now }]
  data.projectLinks = [{ id: 'plink-proj-t', projectId: 'proj', transactionId: 't', source: 'manual', createdAt: now }]
  data.projectRules = [{ id: 'prule', projectId: 'proj', matchText: 'shop', matchMode: 'contains', direction: 'out', enabled: true, createdAt: now }]
  return data
}

const byId = (group: BackupGroup, rows: readonly BackupRow[]) =>
  [...rows].sort((a, b) => backupRowId(group, a).localeCompare(backupRowId(group, b)))
const total = (data: FullBackupData) => BACKUP_GROUPS.reduce((sum, group) => sum + data[group].length, 0)

describe.skipIf(!enabled)('Full backup round trip on Firestore Emulator', () => {
  it('restores all 24 groups into an empty account, reads back the same rows, and a second restore adds nothing', async () => {
    const data = fixture()
    const file = await makeFullBackup(memoryFullBackup(data), backupDigest).create(now)
    const target = account()
    const service = makeFullBackup(target, backupDigest)
    const plan = await service.plan(JSON.stringify(file))
    expect((await service.apply(plan.file)).totalAdded).toBe(total(data))
    const back = await target.read()
    for (const group of BACKUP_GROUPS) expect(byId(group, back[group]), group).toEqual(byId(group, data[group]))
    expect((await service.apply(file)).totalAdded).toBe(0)
    // نسخة من الحساب المستعاد نفسه بتتعمل وتتفحص من غير أخطاء
    const again = await service.create(now)
    expect(again.counts).toEqual(file.counts)
  }, 60000)

  it('a backup made before projects existed restores on real Firestore with empty project groups', async () => {
    const file = await makeFullBackup(memoryFullBackup(fixture()), backupDigest).create(now)
    for (const group of LATER_BACKUP_GROUPS) {
      delete (file.data as Record<string, unknown>)[group]
      delete (file.counts as Record<string, unknown>)[group]
    }
    file.checksum = await backupDigest(backupChecksumText(file.data, file.profile))
    const target = account()
    const service = makeFullBackup(target, backupDigest)
    await service.apply((await service.plan(JSON.stringify(file))).file)
    const back = await target.read()
    expect(back.transactions).toHaveLength(1)
    expect(back.projects).toEqual([])
    expect(back.projectLinks).toEqual([])
  }, 60000)
})
