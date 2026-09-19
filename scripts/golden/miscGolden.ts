import { summarizeCash } from '../../src/domain/cashSummary'
import { groupByDay } from '../../src/domain/dayGroups'
import { parseQuery, searchTransactions, type SearchableTransaction } from '../../src/domain/search'
import { computeExpenseBreakdown, isNonExpenseSourceCategory } from '../../src/domain/expenseClassification'
import { settleableKinds, suggestedSettlementMinor } from '../../src/domain/settlementSuggestion'
import { prepareSettlement } from '../../src/domain/settlementCommand'
import { checkProjectName, checkProjectRule, memberIds, membershipChanges, planSyncLinks, ruleCandidates, summarizeProject, syncStart } from '../../src/domain/projects'
import { detectRecurring, recurringSummary, shiftMonths, validateRecurring } from '../../src/domain/recurring'
import { ALL_ECONOMIC_KINDS } from '../../src/domain/entities/economicKind'
import type { Obligation, Settlement, Transaction, Wallet } from '../../src/domain/entities/types'
import type { Project, ProjectLink, ProjectRule } from '../../src/domain/entities/projectEntities'
import type { RecurringItem } from '../../src/domain/entities/recurring'
import { record, seeded } from './goldenKit'

/** الكاش والأيام والبحث والفصل والتسويات والمشاريع والاشتراكات — بيانات وهمية. */
export function miscGolden() {
  const rnd = seeded(909)
  const DATES = ['2026-06-10', '2026-07-09', '2026-08-11', '2026-08-28', '2026-09-01', '2026-09-01', '2026-09-05', '2026-09-20', '2026-09-27']
  const NAMES = ['TEST MART', 'Netflix', 'NETFLIX.COM', 'stc pay', 'مطعم تجريبي', 'Spotify AB', 'كهرباء', '', 'zainCash']
  const txn = (i: number, over: Partial<Transaction> = {}): Transaction => {
    const t: Transaction = {
      id: `t-${i}`, occurredAt: rnd.pick(DATES), datePrecision: 'day', sourceOrder: rnd.int(0, 5), economicKind: rnd.pick(['purchase', 'unclassified', rnd.pick(ALL_ECONOMIC_KINDS)]),
      economicKindConfirmed: rnd.next() < 0.5, observedDirection: rnd.next() < 0.75 ? 'out' : 'in', amountMinor: rnd.int(1, 90_000), currency: rnd.next() < 0.9 ? 'SAR' : 'USD',
      categoryConfirmed: false, excludedFromBudget: rnd.next() < 0.1, reviewState: 'suggested', isCashTagged: rnd.next() < 0.2,
      createdAt: `2026-09-${String(rnd.int(1, 28)).padStart(2, '0')}T10:00:00Z`, updatedAt: 'x',
    }
    if (rnd.next() < 0.7) t.rawMerchantName = rnd.pick(NAMES)
    if (rnd.next() < 0.4) t.rawDescription = rnd.pick(['اشتراك شهري', 'تحويل', 'CASH', ''])
    if (rnd.next() < 0.3) t.note = rnd.pick(['غدا مع الشغل', 'كاش'])
    if (rnd.next() < 0.5) t.categoryId = rnd.pick(['subs', 'food', 'bills'])
    if (rnd.next() < 0.3) t.merchantId = rnd.pick(['m-netflix', 'm-stc'])
    if (rnd.next() < 0.5) t.walletId = rnd.pick(['w-cash', 'w-bank'])
    if (rnd.next() < 0.1) t.transferToWalletId = 'w-cash'
    return { ...t, ...over }
  }
  const sets = Array.from({ length: 25 }, (_, s) => Array.from({ length: rnd.int(0, 18) }, (_, i) => txn(s * 100 + i)))
  const cash: Wallet = { id: 'w-cash', name: 'كاش', currency: 'SAR', kind: 'cash', openingBalanceMinor: 100_000, openingAt: '2026-08-15' }
  // اشتراكات حقيقية الشكل: نفس المبلغ كل شهر
  const monthly = (name: string, start: [number, number, number], months: number, amount: number, step = 1) => Array.from({ length: months }, (_, i) => {
    const m0 = start[1] - 1 + i * step
    const d = `${start[0] + Math.floor(m0 / 12)}-${String((m0 % 12) + 1).padStart(2, '0')}-${String(start[2] + rnd.int(-3, 3)).padStart(2, '0')}`
    return txn(5000 + i + months * 10, { occurredAt: d, rawMerchantName: name, merchantId: undefined, amountMinor: amount + rnd.int(-50, 50), observedDirection: 'out', economicKind: 'purchase', currency: 'SAR' })
  })
  const recurringSets = [
    [...monthly('Netflix', [2026, 5, 12], 4, 5_600)], [...monthly('STC', [2025, 12, 5], 3, 23_000, 3)], [...monthly('Spotify', [2026, 1, 20], 3, 2_199)],
    [...monthly('Unknown Gym', [2026, 3, 10], 4, 10_000)], [...monthly('icloud+', [2026, 2, 15], 5, 1_299)], ...sets.slice(0, 5),
  ]
  const categories = [{ id: 'subs', parentId: null, name: 'اشتراكات', iconKey: 'tv', lightColor: '#000', darkColor: '#fff', active: true, order: 1 },
    { id: 'bills', parentId: null, name: 'فواتير ومرافق', iconKey: 'zap', lightColor: '#000', darkColor: '#fff', active: true, order: 2 }]
  const projects: Project[] = [{ id: 'p1', name: 'ماكت', normalizedName: 'ماكت', archived: false, createdAt: '2026-09-01' }]
  const rules: ProjectRule[] = [
    { id: 'r1', projectId: 'p1', matchText: 'mart', matchMode: 'contains', direction: 'out', enabled: true, createdAt: '2026-09-10T00:00:00Z' },
    { id: 'r2', projectId: 'p1', matchText: 'TEST', matchMode: 'startsWith', direction: 'any', enabled: true, createdAt: '2026-09-05T00:00:00Z' },
    { id: 'r3', projectId: 'p2', matchText: 'netflix', matchMode: 'exact', direction: 'in', enabled: false, createdAt: '2026-09-01T00:00:00Z' },
  ]
  const links: ProjectLink[] = [{ id: 'l1', projectId: 'p1', transactionId: 't-0', source: 'manual', createdAt: 'x' }, { id: 'l2', projectId: 'p1', transactionId: 't-1', source: 'excluded', createdAt: 'x' }]
  const obligation: Obligation = { id: 'o1', personId: 'p', originTransactionId: null, kind: 'loan_payable', originalMinor: 10_000, currency: 'SAR' }
  const settled: Settlement[] = [{ id: 's1', transactionId: 't1', obligationId: 'o1', amountMinor: 4_000 }]
  const recurringItems: RecurringItem[] = [
    { id: 'i1', name: 'Netflix', merchantKey: 'name:NETFLIX', kind: 'subscription', cycleMonths: 1, expectedMinor: 5_600, currency: 'SAR', nextDueAt: '2026-09-12', active: true, confirmed: true },
    { id: 'i2', name: 'ايجار', merchantKey: 'manual:rent', kind: 'bill', cycleMonths: 3, expectedMinor: 900_000, currency: 'SAR', nextDueAt: '2026-10-01', active: false, confirmed: true },
    { id: 'i3', name: 'STC', merchantKey: 'id:m-stc', kind: 'bill', cycleMonths: 12, expectedMinor: 23_000, currency: 'SAR', nextDueAt: '2026-09-30', active: true, confirmed: false },
  ]
  const badItems = [{ name: ' ' }, { name: 'x'.repeat(121) }, { cycleMonths: 2 }, { kind: 'loan' }, { expectedMinor: 0 }, { nextDueAt: '2026-02-30' }, { merchantKey: '' }]
    .map((over) => ({ ...recurringItems[0]!, ...over }) as RecurringItem)

  return {
    summarizeCash: sets.map((transactions) => record({ transactions, period: { start: '2026-08-28', end: '2026-09-27' } }, () => summarizeCash({ wallet: cash, transactions, allocations: [], period: { start: '2026-08-28', end: '2026-09-27' } }))),
    groupByDay: sets.map((transactions) => record(transactions, () => groupByDay(transactions))),
    parseQuery: ['  ', '85.99', '٨٥٫٩٩', '1,000', '12 3', 'abc 12', '0', '.', 'كاش', ' cash '].map((q) => record(q, () => parseQuery(q))),
    searchTransactions: sets.slice(0, 12).flatMap((transactions) => ['mart', 'كاش', 'مطعم', '56', 'اشتراك', 'غدا', ''].map((q) => {
      const items: SearchableTransaction[] = transactions.map((t) => ({ transaction: t, categoryName: t.categoryId === 'subs' ? 'اشتراكات' : undefined, tagNames: t.id.endsWith('3') ? ['شغل', 'غدا'] : undefined, merchantNames: t.merchantId ? ['Netflix Inc'] : undefined }))
      return record({ items, query: q }, () => searchTransactions(items, parseQuery(q)).map((h) => ({ id: h.transaction.id, matchedFields: h.matchedFields })))
    })),
    computeExpenseBreakdown: Array.from({ length: 10 }, () => Array.from({ length: rnd.int(0, 20) }, () => {
      const out = rnd.next() < 0.7
      return { debitMinor: out ? rnd.int(1, 90_000) : 0, creditMinor: out ? 0 : rnd.int(1, 90_000), sourceCategory: rnd.pick([undefined, 'تحويلات', 'محافظ  رقمية', 'ذهب', 'بقالة', 'سحب نقدي', ''] as const) }
    })).map((rows) => record(rows, () => computeExpenseBreakdown(rows))),
    isNonExpenseSourceCategory: [null, '', 'تحويلات', ' تحويلات ', 'تقسيط', 'بقالة'].map((c) => record(c, () => isNonExpenseSourceCategory(c))),
    settlement: [record('in', () => settleableKinds('in')), record('out', () => settleableKinds('out')), record([5, 9], () => suggestedSettlementMinor(5, 9)), record([9, 5], () => suggestedSettlementMinor(9, 5))],
    prepareSettlement: [
      { id: 's2', obligationId: 'o1', transactionId: 't2', amountMinor: 3_000, personId: 'p' }, { id: 's2', obligationId: 'o1', transactionId: 't2', amountMinor: 7_000, personId: 'p' },
      { id: 's1', obligationId: 'o1', transactionId: 't1', amountMinor: 4_000, personId: 'p' }, { id: 's1', obligationId: 'o1', transactionId: 't1', amountMinor: 1, personId: 'p' },
      { id: 's3', obligationId: 'o1', transactionId: 't3', amountMinor: 0, personId: 'p' }, { id: 's3', obligationId: 'o1', transactionId: 't3', amountMinor: 1, personId: 'other' },
    ].map((input) => record({ input, obligation, settlements: settled }, () => prepareSettlement(input, obligation, settled))),
    checkProjectName: ['  ماكت  ', 'ماكت', 'مشروع   جديد', '', 'x'.repeat(61)].map((n) => record(n, () => checkProjectName(n, projects))),
    checkProjectRule: [['mart', 'contains', 'out'], [' a ', 'exact', 'in'], ['x'.repeat(61), 'contains', 'any'], ['ab', 'startsWith', 'sideways']].map(([t, m, d]) => record({ matchText: t, matchMode: m, direction: d }, () => checkProjectRule({ matchText: t!, matchMode: m as never, direction: d as never }))),
    ruleCandidates: sets.slice(0, 10).flatMap((transactions) => rules.map((rule) => record({ rule, transactions, links }, () => ruleCandidates(rule, transactions, links).map((t) => t.id)))),
    planSyncLinks: sets.slice(0, 10).map((transactions) => record({ rules, transactions, links }, () => planSyncLinks(rules, transactions, links, 'NOW'))),
    syncStart: [null, '', '2026-09-02T00:00:00Z', '2026-09-20T00:00:00Z'].map((c) => record(c, () => syncStart(rules, c))).concat([record('no-rules', () => syncStart([], 'x'))]),
    membership: [[[], true], [[], false], [[links[0]!], true], [[links[0]!], false], [[links[1]!], true], [[links[1]!, { ...links[0]!, id: 'other' }], false]].map(([existing, member]) =>
      record({ existing, member }, () => ({ changes: membershipChanges(existing as ProjectLink[], 'p1', 't-0', member as boolean, 'NOW'), members: [...memberIds(existing as ProjectLink[], 'p1')] }))),
    summarizeProject: sets.slice(0, 10).map((transactions) => record(transactions, () => summarizeProject(transactions, [], new Map([['subs', 'اشتراكات'], ['bills', 'فواتير']])))),
    shiftMonths: [['2026-01-31', 1], ['2028-01-31', 1], ['2026-12-15', 1], ['2026-01-15', -1], ['2026-03-31', -13], ['2026-05-20', 12], ['2026-05-20', 0]].map(([d, n]) => record({ date: d, delta: n }, () => shiftMonths(d as string, n as number))),
    detectRecurring: recurringSets.map((rows) => record({ rows, categories }, () => detectRecurring(rows, categories as never))),
    recurringSummary: recurringItems.flatMap((item) => recurringSets.slice(0, 5).map((rows) => record({ item, rows, today: '2026-09-19' }, () => recurringSummary(item, rows, '2026-09-19')))),
    validateRecurring: [...recurringItems, ...badItems].map((item) => record(item, () => { validateRecurring(item); return true })),
  }
}
