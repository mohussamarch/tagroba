import { makeFullBackup } from '../../src/application/useCases/fullBackup'
import { saveVerifiedBackup } from '../../src/application/useCases/verifiedBackup'
import { memoryFullBackup } from '../../src/infrastructure/memory/fullBackup'
import { memoryRepairBackup } from '../../src/infrastructure/memory/repairBackup'
import { FixedClock } from '../../src/infrastructure/memory/memorySupport'
import { backupDigest } from '../../src/infrastructure/backupDigest'
import { canonicalBackup, emptyBackupData, LATER_BACKUP_GROUPS, type BackupRow, type FullBackupData, type FullBackupFile } from '../../src/domain/fullBackup'
import { recordAsync, type GoldenCase } from './goldenKit'

/**
 * النسخة الشاملة (الإصدار 2) كحالة استخدام: عمل نسخة · معاينة الاسترجاع · تنفيذه بالدمج · ونسخة ما قبل الصيانة.
 * ⚠️ حساب وهمي بالكامل — نفس حساب `backupGolden.ts` (الـ24 مجموعة كلها سليمة).
 */

const EXPORTED = '2026-09-22T10:00:00.000Z'

const t = (id: string, over: BackupRow = {}): BackupRow => ({
  id, occurredAt: '2026-09-10', datePrecision: 'day', sourceOrder: 1, economicKind: 'purchase', economicKindConfirmed: true, observedDirection: 'out',
  amountMinor: 10_000, currency: 'SAR', categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested', isCashTagged: false,
  createdAt: '2026-09-10T10:00:00Z', updatedAt: '2026-09-10T10:00:00Z', walletId: 'w1', categoryId: 'c1', rawMerchantName: 'TEST MART', ...over,
})
const base = (): FullBackupData => ({
  ...emptyBackupData(),
  wallets: [{ id: 'w1', name: 'بنك', currency: 'SAR', kind: 'bank', openingBalanceMinor: -500, openingAt: '2026-01-01', accountLast4: '1234' }, { id: 'w2', name: 'كاش', currency: 'SAR', kind: 'cash', openingBalanceMinor: 0, openingAt: '2026-01-01' }],
  categories: [{ id: 'c1', parentId: null, name: 'أكل', iconKey: 'tag', lightColor: '#111111', darkColor: '#222222', active: true, order: 1 }, { id: 'c2', parentId: 'c1', name: 'مطاعم', iconKey: 'tag', lightColor: '#111111', darkColor: '#222222', active: true, order: 2 }],
  merchants: [{ id: 'm1', displayName: 'TEST MART', normalizedName: 'TEST MART', verifiedCategoryId: 'c1', aliases: ['تست'] }],
  rules: [{ id: 'r1', priority: 1, matchText: 'mart', matchMode: 'contains', categoryId: 'c1', enabled: true }],
  people: [{ id: 'p1', name: 'أحمد', archived: false }],
  assets: [{ id: 'a1', name: 'ذهب', kind: 'gold', unitLabel: 'جرام', currency: 'SAR', archived: false }],
  tags: [{ id: 'g1', displayName: 'شغل', normalizedName: 'شغل' }],
  budgets: [{ id: '2026-08', periodKey: '2026-08', periodStart: '2026-08-28', periodEnd: '2026-09-27', totalLimitMinor: null, thresholdPercent: 80, createdAt: 'x', updatedAt: 'x' }],
  recurringItems: [{ id: 'i1', name: 'Netflix', merchantKey: 'name:NETFLIX', kind: 'subscription', cycleMonths: 1, expectedMinor: 5600, currency: 'SAR', nextDueAt: '2026-10-01', active: true, confirmed: true }],
  importBatches: [{ id: 'b1', sourceType: 'csv_legacy', fileHash: 'H', fileName: 'f.csv', importedAt: '2026-09-10T00:00:00Z', state: 'committed', counts: { total: 2, imported: 2, duplicates: 0, similar: 0, conflicts: 0, invalid: 0 } }],
  transactions: [t('t1'), t('t2', { amountMinor: 50_000, observedDirection: 'in', economicKind: 'salary', statedBalanceMinor: -20, merchantId: 'm1' }), t('t3', { originalAmountMinor: 12_000, walletId: 'w2', transferToWalletId: 'w1', note: 'نص' })],
  obligations: [{ id: 'o1', personId: 'p1', originTransactionId: 't1', kind: 'receivable', originalMinor: 4000, currency: 'SAR' }, { id: 'o2', personId: 'p1', originTransactionId: null, kind: 'loan_payable', originalMinor: 900, currency: 'SAR' }],
  allocations: [{ id: 'al1', transactionId: 't1', personId: 'p1', allocationKind: 'receivable', amountMinor: 4000, currency: 'SAR' }],
  settlements: [{ id: 's1', transactionId: 't2', obligationId: 'o1', amountMinor: 1000 }],
  sourceRecords: [{ id: 'sr1', batchId: 'b1', accountIdentity: 'بنك', sourceReference: null, sourceHash: 'x', originalRowIndex: 2, rawLine: 'a,b', transactionId: 't1', matchingState: 'new', reason: 'جديد' }],
  transactionTags: [{ id: 'tt1', transactionId: 't1', tagId: 'g1' }],
  categoryBudgets: [{ id: 'cb1', budgetId: '2026-08', categoryId: 'c1', limitMinor: 100_000, notifyEnabled: true, thresholdPercent: null }],
  assetLots: [{ id: 'lot1', assetId: 'a1', purchasedAt: '2026-05-01', quantity: 250_000_000, principalMinor: 90_000, feeMinor: 50 }],
  assetSales: [{ id: 'sale1', assetId: 'a1', soldAt: '2026-06-01', quantity: 50_000_000, grossProceedsMinor: 20_000, feeMinor: 0 }],
  assetPrices: [{ assetId: 'a1', pricePerUnitMinor: 39_500, asOf: '2026-09-19', source: 'feed' }],
  notificationReceipts: [{ eventKey: '2026-08-28|total|100', threshold: 100, periodStart: '2026-08-28', sentAt: 'x' }],
  projects: [{ id: 'pr1', name: 'ماكت', normalizedName: 'ماكت', archived: false, createdAt: 'x' }],
  projectLinks: [{ id: 'pl1', projectId: 'pr1', transactionId: 't1', source: 'manual', createdAt: 'x' }],
  projectRules: [{ id: 'prr1', projectId: 'pr1', matchText: 'mart', matchMode: 'contains', direction: 'out', enabled: true, createdAt: 'x' }],
})
const profile: BackupRow = { displayName: 'مستخدم تجريبي', salaryMinor: 900_000, payday: 25, gender: 'male', dependentKinds: ['children'], hasCar: true, onboardedAt: 'x' }

/** نفس العمليات بمعرّف تاني من جهاز تاني + عملية جديدة وروابطها — الدمج لازم يحوّل الروابط للموجود. */
const otherDevice = (d: FullBackupData): FullBackupData => ({
  ...d,
  transactions: [...d.transactions.map((row, i) => i === 1 ? { ...row, id: 't2-other-device' } : row), { ...d.transactions[0]!, id: 't9', occurredAt: '2026-09-11' }],
  allocations: [{ ...d.allocations[0]!, id: 'al-x', transactionId: 't2-other-device' }],
  obligations: [...d.obligations, { ...d.obligations[0]!, id: 'o9', originTransactionId: 't9' }],
  settlements: [{ id: 's9', transactionId: 't2-other-device', obligationId: 'o9', amountMinor: 5 }],
  projectLinks: [{ ...d.projectLinks[0]!, id: 'pl9', transactionId: 't2-other-device' }],
  categoryBudgets: [{ ...d.categoryBudgets[0]!, id: 'cb9', categoryId: 'c2' }],
})

/** حساب ميزانيته لسه بالمعرّف العشوائي القديم ⇒ السقف المضاف بيشاور على المعرّف الحقيقي المتخزن. */
const liveWithRandomBudget = (): FullBackupData => {
  const d = base()
  return { ...d, budgets: [{ ...d.budgets[0]!, id: 'rand-2026-08' }], categoryBudgets: [{ ...d.categoryBudgets[0]!, budgetId: 'rand-2026-08' }] }
}

async function fileOf(data: FullBackupData, prof: BackupRow | null): Promise<FullBackupFile> {
  return makeFullBackup(memoryFullBackup(data, prof), backupDigest).create(EXPORTED)
}

/** نسخة إصدار 2 قديمة: قبل المشاريع ومن غير ملف حساب — زي ما التطبيق وقتها كان بيعملها. */
async function oldFile(): Promise<string> {
  const data: Record<string, unknown> = { ...base() }
  for (const key of LATER_BACKUP_GROUPS) delete data[key]
  const counts = Object.fromEntries(Object.entries(data).map(([k, v]) => [k, (v as unknown[]).length]))
  return JSON.stringify({ app: 'masroufy', schemaVersion: 2, exportedAt: EXPORTED, data, checksum: await backupDigest(canonicalBackup(data)), counts })
}

type Action =
  | { kind: 'create' }
  | { kind: 'plan'; raw: string }
  | { kind: 'planApply'; raw: string }

async function fullBackupCases() {
  const cases: GoldenCase[] = []
  async function run(account: FullBackupData, accountProfile: BackupRow | null, action: Action) {
    cases.push(await recordAsync({ account, accountProfile, action }, async () => {
      const port = memoryFullBackup(account, accountProfile)
      const backup = makeFullBackup(port, backupDigest)
      if (action.kind === 'create') return { file: await backup.create(EXPORTED) }
      const plan = await backup.plan(action.raw)
      if (action.kind === 'plan') return { plan }
      const outcome = await backup.apply(plan.file)
      return { plan, outcome, storedData: await port.read(), storedProfile: await port.readProfile() }
    }))
  }

  const full = JSON.stringify(await fileOf(base(), profile))
  const noProfile = JSON.stringify(await fileOf(base(), null))
  const fromOther = JSON.stringify(await fileOf(otherDevice(base()), profile))
  const tampered = full.replace('"name":"أحمد"', '"name":"أحمد 2"')
  const badCounts = full.replace('"people":1,', '"people":2,')

  await run(base(), profile, { kind: 'create' })
  await run(base(), null, { kind: 'create' })
  await run(liveWithRandomBudget(), null, { kind: 'create' })
  await run({ ...base(), allocations: [...base().allocations, { ...base().allocations[0]!, id: 'al2', amountMinor: 6_001 }] }, null, { kind: 'create' })

  await run(emptyBackupData(), null, { kind: 'planApply', raw: full })
  await run(base(), profile, { kind: 'planApply', raw: full })
  await run(base(), null, { kind: 'planApply', raw: fromOther })
  await run(liveWithRandomBudget(), null, { kind: 'planApply', raw: fromOther })
  await run(emptyBackupData(), profile, { kind: 'planApply', raw: noProfile })
  await run(emptyBackupData(), null, { kind: 'planApply', raw: await oldFile() })

  for (const raw of ['{', '5', 'null', '[]', '{"app":"masroufy","schemaVersion":1}', '{"app":"other","schemaVersion":2}', '{"app":"masroufy","schemaVersion":"2"}', tampered, badCounts]) {
    await run(emptyBackupData(), null, { kind: 'plan', raw })
  }
  return cases
}

async function verifiedBackupCases() {
  const cases: GoldenCase[] = []
  const documents: unknown[] = [
    { id: 't1', amountMinor: 10_000, note: 'سطر\nتاني "مقتبس"', tags: [], extra: {} },
    { id: 't2', nested: { a: [1, 2, { b: null }], ok: true } },
  ]
  for (const [kind, truncate, docs] of [['repair', false, documents], ['cleanup', true, documents], ['revert', false, []]] as const) {
    cases.push(await recordAsync({ kind, truncate, documents: docs }, async () => {
      const backup = memoryRepairBackup({ truncate })
      const saved = await saveVerifiedBackup(backup, new FixedClock('2026-09-22T10:00:00.123Z'), kind, [...docs])
      return { saved, files: [...backup.files.entries()] }
    }))
  }
  return cases
}

export async function fullBackupFlowGolden() {
  return { fullBackup: await fullBackupCases(), verifiedBackup: await verifiedBackupCases() }
}
