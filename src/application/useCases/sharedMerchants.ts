import type { Id, Merchant, Transaction } from '../../domain/entities/types'
import {
  baselineCatalog,
  contributionFor,
  effectiveEntry,
  latestUpdate,
  planAccountMerchantSync,
  shareableName,
  sharedMerchantKey,
  type SharedMerchantEntry,
} from '../../domain/sharedMerchantCatalog'
import type { Clock, MerchantRepository } from '../ports/repositories'
import type { SharedMerchantCatalogPort, SyncCursorPort } from '../ports/SharedMerchantCatalogPort'

export interface SharedMerchantsDeps {
  catalog: SharedMerchantCatalogPort
  merchants: MerchantRepository
  /** تجار المرجع اللي جوه التطبيق — أساس القاعدة (OVERRIDES §25.1). */
  baseline: readonly Merchant[]
  /** معرّفات شجرة التصنيفات بس — نفس المعرّف في كل الحسابات. */
  treeCategoryIds: ReadonlySet<Id>
  cursor: SyncCursorPort
  /** آخر مراجعة لكل المؤكدين — التأكيد من لوحة فايربيز ممكن ما يغيّرش `updatedAt` فما يوصلش بالتغييرات لوحدها. */
  confirmedCursor: SyncCursorPort
  clock: Clock
}

/** كل المؤكدين بيتقروا مرة في اليوم على الأكتر (حد القراية المجانية 50 ألف في اليوم). */
const CONFIRMED_REFRESH_MS = 24 * 60 * 60 * 1000

/**
 * قاعدة التجار المشتركة — OVERRIDES §25 و§25.1.
 * - `sync`: بيقرا التغييرات من آخر مرة، **وكل المؤكدين مرة في اليوم** (عشان تأكيد اتعمل من اللوحة من غير ما
 *   `updatedAt` يتغير يوصل برضه)، وبيضيف للحساب المؤكد الناقص من غير ما يكتب فوق تصنيف المستخدم.
 * - `contribute`: لما المستخدم يأكد تصنيف عملية شراء، اسم المحل وتصنيفه بيتبعتوا مش مؤكدين.
 */
export function makeSharedMerchants(deps: SharedMerchantsDeps) {
  const baseline = baselineCatalog(deps.baseline)

  async function sync(): Promise<{ changes: number; added: number; filled: number }> {
    const since = deps.cursor.read()
    const now = deps.clock.nowIso()
    const last = deps.confirmedCursor.read()
    // أول مزامنة بتقرا كله أصلًا؛ بعدها كل المؤكدين مرة في اليوم
    const refresh = since !== null && (!last || Date.parse(now) - Date.parse(last) >= CONFIRMED_REFRESH_MS)
    const [changed, confirmed] = await Promise.all([
      deps.catalog.listChangedSince(since),
      refresh ? deps.catalog.listConfirmed() : Promise.resolve([] as SharedMerchantEntry[]),
    ])
    const byKey = new Map(confirmed.map((e) => [sharedMerchantKey(e.normalizedName), e]))
    for (const e of changed) byKey.set(sharedMerchantKey(e.normalizedName), e)
    const remote = [...byKey.values()]
    let added = 0
    let filled = 0
    if (remote.length > 0) {
      const effective = remote.map((r) => effectiveEntry(baseline.get(sharedMerchantKey(r.normalizedName)), r)!)
      const plan = planAccountMerchantSync(effective, await deps.merchants.listAll(), deps.treeCategoryIds)
      const writes = [...plan.add, ...plan.fill]
      if (writes.length > 0) await deps.merchants.saveMany(writes)
      added = plan.add.length
      filled = plan.fill.length
    }
    // المؤشرات بتتقدم بعد الكتابة بس — انقطاع في النص بيعيد نفس التغييرات (المعرّفات ثابتة فمفيش تكرار)
    const latest = latestUpdate(changed, since)
    if (latest && latest !== since) deps.cursor.write(latest)
    if (since === null || refresh) deps.confirmedCursor.write(now)
    return { changes: remote.length, added, filled }
  }

  async function contribute(
    transaction: Pick<Transaction, 'economicKind' | 'observedDirection' | 'rawMerchantName'>,
    categoryId: Id,
  ): Promise<'shared' | 'skipped'> {
    // الفحص الرخيص الأول: مفيش قراءة من السيرفر لعملية مش هتتبعت أصلًا
    const probe = contributionFor({ transaction, categoryId, treeCategoryIds: deps.treeCategoryIds, current: undefined })
    const normalized = shareableName(transaction.rawMerchantName)
    if (!probe || !normalized) return 'skipped'
    const current = effectiveEntry(baseline.get(sharedMerchantKey(normalized)), await deps.catalog.get(normalized))
    const entry = contributionFor({ transaction, categoryId, treeCategoryIds: deps.treeCategoryIds, current })
    if (!entry) return 'skipped'
    await deps.catalog.save(entry)
    return 'shared'
  }

  return { sync, contribute }
}
