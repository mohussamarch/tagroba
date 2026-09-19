import { createHash } from 'node:crypto'
import { BACKUP_GROUPS, canonicalBackup, emptyBackupData, type BackupRow, type FullBackupData } from '../../src/domain/fullBackup'
import { checkFullBackupData } from '../../src/domain/checkFullBackup'
import { checkBackupFinance } from '../../src/domain/checkBackupFinance'
import { mergeFullBackup } from '../../src/domain/mergeFullBackup'
import { backupChecksumText, checkBackupProfile } from '../../src/domain/backupProfile'
import { normalizeBudgetIds, pointLinesAtLiveBudgets } from '../../src/domain/backupBudgetIds'
import { record, seeded } from './goldenKit'

/** النسخة الشاملة: الفحص والدمج وبصمة السلامة — حساب وهمي كامل بكل الـ24 مجموعة. */
export function backupGolden() {
  const rnd = seeded(2424)
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
  // كل تعديل بيكسر حاجة واحدة — عشان كل رسالة رفض تتجرب
  const breaks: [string, (d: FullBackupData) => unknown][] = [
    ['ok', (d) => d], ['not object', () => [1]], ['null', () => null], ['missing group', (d) => { const x: Record<string, unknown> = { ...d }; delete x.tags; return x }],
    ['row not object', (d) => ({ ...d, people: [...d.people, 5] })], ['empty id', (d) => ({ ...d, people: [{ ...d.people[0]!, id: '' }] })],
    ['slash id', (d) => ({ ...d, people: [{ ...d.people[0]!, id: 'a/b' }] })], ['dup id', (d) => ({ ...d, people: [...d.people, d.people[0]!] })],
    ['receipt slash ok', (d) => ({ ...d, notificationReceipts: [{ ...d.notificationReceipts[0]!, eventKey: 'a/b' }] })],
    ['missing field', (d) => { const w = { ...d.wallets[0]! }; delete w.kind; return { ...d, wallets: [w, d.wallets[1]!] } }],
    ['bad text', (d) => ({ ...d, people: [{ ...d.people[0]!, name: 5 }] })], ['float amount', (d) => ({ ...d, transactions: [{ ...d.transactions[0]!, amountMinor: 10.5 }, ...d.transactions.slice(1)] })],
    ['negative amount', (d) => ({ ...d, allocations: [{ ...d.allocations[0]!, amountMinor: -1 }] })], ['null limit ok', (d) => d],
    ['bad bool', (d) => ({ ...d, people: [{ ...d.people[0]!, archived: 'no' }] })], ['bad date', (d) => ({ ...d, wallets: [{ ...d.wallets[0]!, openingAt: '2026-02-30' }, d.wallets[1]!] })],
    ['bad currency', (d) => ({ ...d, assets: [{ ...d.assets[0]!, currency: 'sar' }] })], ['full account', (d) => ({ ...d, wallets: [{ ...d.wallets[0]!, accountLast4: '12345678' }, d.wallets[1]!] })],
    ['bad direction', (d) => ({ ...d, transactions: [{ ...d.transactions[0]!, observedDirection: 'up' }, ...d.transactions.slice(1)] })],
    ['zero amount', (d) => ({ ...d, transactions: [{ ...d.transactions[0]!, amountMinor: 0 }, ...d.transactions.slice(1)] })],
    ['bad cycle', (d) => ({ ...d, recurringItems: [{ ...d.recurringItems[0]!, cycleMonths: 2 }] })], ['bad kind', (d) => ({ ...d, transactions: [{ ...d.transactions[0]!, economicKind: 'gift' }, ...d.transactions.slice(1)] })],
    ['bad enum', (d) => ({ ...d, rules: [{ ...d.rules[0]!, matchMode: 'regex' }] })], ['bad counts', (d) => ({ ...d, importBatches: [{ ...d.importBatches[0]!, counts: { total: -1 } }] })],
    ['staged', (d) => ({ ...d, importBatches: [{ ...d.importBatches[0]!, state: 'staged' }] })], ['budget id', (d) => ({ ...d, budgets: [{ ...d.budgets[0]!, id: 'rand' }], categoryBudgets: [{ ...d.categoryBudgets[0]!, budgetId: 'rand' }] })],
    ['broken relation', (d) => ({ ...d, allocations: [{ ...d.allocations[0]!, personId: 'ghost' }] })], ['old debt ok', (d) => d],
    ['over allocation', (d) => ({ ...d, allocations: [...d.allocations, { ...d.allocations[0]!, id: 'al2', amountMinor: 6_001 }] })],
    ['alloc currency', (d) => ({ ...d, allocations: [{ ...d.allocations[0]!, currency: 'USD' }] })], ['debt too big', (d) => ({ ...d, obligations: [{ ...d.obligations[0]!, originalMinor: 20_000 }, d.obligations[1]!] })],
    ['old debt zero', (d) => ({ ...d, obligations: [d.obligations[0]!, { ...d.obligations[1]!, originalMinor: 0 }] })],
    ['over settle', (d) => ({ ...d, settlements: [...d.settlements, { id: 's2', transactionId: 't2', obligationId: 'o1', amountMinor: 3_001 }] })],
  ]
  const profiles: unknown[] = [undefined, null, 5, [], {}, { displayName: 'محمد', payday: 28, salaryMinor: 900_000, gender: 'male', dependentKinds: ['children'], hasCar: true, onboardedAt: 'x' },
    { displayName: 'x'.repeat(61) }, { salaryMinor: -1 }, { salaryMinor: 1.5 }, { payday: 0 }, { payday: null }, { gender: 'x' }, { hasCar: 'yes' }, { dependentKinds: ['cousin'] }, { onboardedAt: 5 }, { displayName: null, salaryMinor: null }]
  const incomingFor = (d: FullBackupData): FullBackupData => ({
    ...d,
    transactions: [...d.transactions.map((row, i) => i === 1 ? { ...row, id: 't2-other-device' } : row), { ...d.transactions[0]!, id: 't9', occurredAt: '2026-09-11' }],
    allocations: [{ ...d.allocations[0]!, id: 'al-x', transactionId: 't2-other-device' }],
    obligations: [...d.obligations, { ...d.obligations[0]!, id: 'o9', originTransactionId: 't9' }],
    settlements: [{ id: 's9', transactionId: 't2-other-device', obligationId: 'o9', amountMinor: 5 }],
    projectLinks: [{ ...d.projectLinks[0]!, id: 'pl9', transactionId: 't2-other-device' }],
    budgets: [{ ...d.budgets[0]!, id: 'rand-2026-08' }], categoryBudgets: [{ ...d.categoryBudgets[0]!, id: 'cb9', budgetId: 'rand-2026-08' }],
  })
  const texts = ['', 'abc', 'مصروفي', 'emoji 🙂', 'x'.repeat(200)]
  for (let i = 0; i < 20; i++) texts.push(canonicalBackup({ n: rnd.int(0, 1e9), s: 'نص ' + i, a: [rnd.next() < 0.5, null, 'q"\\\n'] }))

  return {
    checkFullBackupData: breaks.map(([name, apply]) => { const data = apply(base()); return record({ name, data }, () => { checkFullBackupData(data); return true }) }),
    // الفحص المالي بيشتغل بعد فحص الشكل بس (زي `useCases/fullBackup`) — على النسخ السليمة الشكل
    checkBackupFinance: breaks.map(([name, apply]) => ({ name, data: apply(base()) })).filter(({ data }) => { try { checkFullBackupData(data); return true } catch { return false } })
      .map(({ name, data }) => record({ name, data }, () => { checkBackupFinance(data as FullBackupData); return true })),
    canonicalBackup: [base(), { b: 1, a: [3, 2, { z: null, y: 'ي', x: false }], 'é': 1.5, A: 1e21, n: -0.000001, m: 0.1 }, [], {}, 'x', 5, null].map((v) => record(v, () => canonicalBackup(v))),
    sha256: texts.map((text) => record(text, () => createHash('sha256').update(text, 'utf8').digest('hex'))),
    backupChecksum: profiles.slice(0, 6).map((p) => record({ profile: p === undefined ? '__absent__' : p }, () => {
      const text = backupChecksumText(base(), p as BackupRow | null | undefined)
      return { length: text.length, checksum: createHash('sha256').update(text, 'utf8').digest('hex') }
    })),
    checkBackupProfile: profiles.map((p) => record(p === undefined ? '__absent__' : p, () => { checkBackupProfile(p); return true })),
    normalizeBudgetIds: [base(), incomingFor(base())].map((d) => record(d, () => normalizeBudgetIds(d))),
    pointLinesAtLiveBudgets: [[[{ budgetId: '2026-08', x: 1 }, { budgetId: 'other' }], [{ id: 'rand', periodKey: '2026-08' }, { id: '2026-07', periodKey: '2026-07' }]], [[{ budgetId: '2026-08' }], [{ id: '2026-08', periodKey: '2026-08' }]]]
      .map(([additions, live]) => record({ additions, live }, () => pointLinesAtLiveBudgets(additions as BackupRow[], live as BackupRow[]))),
    mergeFullBackup: [[incomingFor(base()), base()], [base(), base()], [incomingFor(base()), emptyBackupData()], [base(), { ...emptyBackupData(), transactions: [{ ...base().transactions[0]!, id: 'same-content' }] }]]
      .map(([incoming, existing]) => record({ incoming, existing }, () => mergeFullBackup(incoming as FullBackupData, existing as FullBackupData))),
    groups: [record(null, () => BACKUP_GROUPS)],
  }
}
