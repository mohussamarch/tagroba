import { buildBudgetNotifications, filterUnseen, receiptFor, staleReceipts, type NotificationReceipt } from '../../src/domain/notifications'
import { budgetStatus } from '../../src/domain/budget'
import { shouldLock, LOCK_AFTER_BACKGROUND_MS } from '../../src/domain/appLock'
import { buildLogoIndex, logoSourceFor, type MerchantLogoEntry } from '../../src/domain/merchantLogo'
import { baselineCatalog, contributionFor, effectiveEntry, latestUpdate, planAccountMerchantSync, shareableName, sharedMerchantKey, type SharedMerchantEntry } from '../../src/domain/sharedMerchantCatalog'
import type { Merchant } from '../../src/domain/entities/types'
import { record, seeded } from './goldenKit'

/** التنبيهات والقفل والشعارات وقاعدة التجار المشتركة — أسماء وأرقام وهمية. */
export function noticeGolden() {
  const rnd = seeded(777)
  const status = () => budgetStatus(rnd.int(1, 100_000), rnd.int(0, 150_000), null)
  const inputs = Array.from({ length: 30 }, (_, i) => ({
    periodStart: '2026-08-28', totalStatus: rnd.next() < 0.7 ? status() : null, totalThresholdPercent: rnd.pick([null, 0, 80, 90, 100, 120]),
    categories: Array.from({ length: rnd.int(0, 3) }, (_, c) => ({ categoryId: `c${c}`, categoryName: `تصنيف ${c}`, status: status(), thresholdPercent: rnd.pick([null, 75, 100]) })),
    spentKnown: i % 9 !== 0,
  }))
  const receipts: NotificationReceipt[] = [{ eventKey: '2026-08-28|total|100', threshold: 100, periodStart: '2026-08-28', sentAt: 'x' }, { eventKey: 'old', threshold: null, periodStart: '2026-07-28', sentAt: 'x' }]
  const logos: MerchantLogoEntry[] = [{ names: ['TEST MART', 'test-mart'], file: 'mart.png', domain: 'testmart.com' }, { names: ['NEW CAFE'], domain: ' NewCafe.SA ' }, { names: ['Bad'], domain: 'not a domain' }, { names: ['TEST MART'], file: 'dup.png' }, { names: ['', '  '] }]
  const merchants: Merchant[] = [
    { id: 'm1', displayName: 'TEST MART', normalizedName: 'TEST MART', verifiedCategoryId: 'cat-food', aliases: ['تست مارت'] },
    { id: 'm2', displayName: 'مطعم', normalizedName: 'مطعم' }, { id: 'm3', displayName: 'X', normalizedName: 'X', verifiedCategoryId: '' },
  ]
  const tree = new Set(['cat-food', 'cat-fuel'])
  const entries: SharedMerchantEntry[] = [
    { normalizedName: 'مطعم', displayName: 'مطعم', aliases: [], categoryId: 'cat-food', confirmed: true, updatedAt: '2026-09-02' },
    { normalizedName: 'NEW SHOP', displayName: '', aliases: ['A', 'B'], categoryId: 'cat-fuel', confirmed: true, updatedAt: '2026-09-05' },
    { normalizedName: 'new  shop', displayName: 'dup', aliases: [], categoryId: 'cat-fuel', confirmed: true },
    { normalizedName: 'UNCONFIRMED', displayName: 'u', aliases: [], categoryId: 'cat-food', confirmed: false, updatedAt: '2026-09-09' },
    { normalizedName: 'NOT TREE', displayName: 'n', aliases: [], categoryId: 'user-cat', confirmed: true }, { normalizedName: 'TEST MART', displayName: 't', aliases: [], categoryId: 'cat-fuel', confirmed: true },
  ]
  const names = [undefined, '', 'TEST MART', 'shop 12345', 'shop ١٢٣٤', 'shop 123', 'x'.repeat(81), 'Branch ①②③④', '  spaced   name ', 'A/B-C']

  return {
    buildBudgetNotifications: inputs.map((input) => record(input, () => buildBudgetNotifications(input))),
    filterAndReceipts: inputs.slice(0, 8).map((input) => record(input, () => {
      const events = buildBudgetNotifications(input)
      return { unseen: filterUnseen(events, receipts).map((e) => e.eventKey), receipts: events.map((e) => receiptFor(e, 'NOW')), stale: staleReceipts(receipts, '2026-08-28').map((r) => r.eventKey) }
    })),
    shouldLock: [[false, null, 0], [true, null, 0], [true, 1000, 999], [true, 1000, 1000 + LOCK_AFTER_BACKGROUND_MS - 1], [true, 1000, 1000 + LOCK_AFTER_BACKGROUND_MS]].map(([enabled, hiddenAt, now]) =>
      record({ enabled, hiddenAt, now }, () => shouldLock({ enabled: enabled as boolean, hiddenAt: hiddenAt as number | null, now: now as number }))),
    logoSourceFor: ['TEST MART', 'test mart', 'NEW CAFE', 'Bad', 'unknown', ''].flatMap((name) => [[true, ['mart.png']], [false, []], [true, []]].map(([online, files]) =>
      record({ name, online, files }, () => logoSourceFor(name, buildLogoIndex(logos), { onlineEnabled: online as boolean, bundledFiles: new Set(files as string[]) }) ?? null))),
    shareableName: names.map((n) => record(n ?? null, () => ({ name: shareableName(n), key: n ? sharedMerchantKey(n) : null }))),
    baselineCatalog: [record(merchants, () => Object.fromEntries(baselineCatalog(merchants)))],
    effectiveEntry: [[undefined, undefined], [entries[0], undefined], [{ ...entries[0]!, confirmed: true }, entries[3]], [entries[3], entries[0]], [undefined, entries[3]]].map(([b, r]) => record({ baseline: b ?? null, remote: r ?? null }, () => effectiveEntry(b, r) ?? null)),
    contributionFor: names.flatMap((name) => [['purchase', 'out', 'cat-food'], ['purchase', 'in', 'cat-food'], ['support_gift', 'out', 'cat-food'], ['purchase', 'out', 'user-cat']].flatMap(([kind, dir, cat]) =>
      [undefined, entries[0], entries[3], { ...entries[3]!, categoryId: cat! }].map((current) => record({ kind, dir, name: name ?? null, cat, current: current ?? null }, () =>
        contributionFor({ transaction: { economicKind: kind as never, observedDirection: dir as never, rawMerchantName: name }, categoryId: cat!, treeCategoryIds: tree, current }))))),
    planAccountMerchantSync: [record({ entries, merchants }, () => planAccountMerchantSync(entries, merchants, tree))],
    latestUpdate: [null, '', '2026-09-03', '2026-12-01'].map((p) => record(p, () => latestUpdate(entries, p))),
  }
}
