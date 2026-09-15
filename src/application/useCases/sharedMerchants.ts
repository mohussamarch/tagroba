import type { Id, Merchant, Transaction } from '../../domain/entities/types'
import {
  baselineCatalog,
  contributionFor,
  effectiveEntry,
  latestUpdate,
  planAccountMerchantSync,
  shareableName,
  sharedMerchantKey,
} from '../../domain/sharedMerchantCatalog'
import type { MerchantRepository } from '../ports/repositories'
import type { SharedMerchantCatalogPort, SyncCursorPort } from '../ports/SharedMerchantCatalogPort'

export interface SharedMerchantsDeps {
  catalog: SharedMerchantCatalogPort
  merchants: MerchantRepository
  /** تجار المرجع اللي جوه التطبيق — أساس القاعدة (OVERRIDES §25.1). */
  baseline: readonly Merchant[]
  /** معرّفات شجرة التصنيفات بس — نفس المعرّف في كل الحسابات. */
  treeCategoryIds: ReadonlySet<Id>
  cursor: SyncCursorPort
}

/**
 * قاعدة التجار المشتركة — OVERRIDES §25 و§25.1.
 * - `sync`: بيقرا التغييرات من آخر مرة بس، وبيضيف للحساب المؤكد الناقص من غير ما يكتب فوق تصنيف المستخدم.
 * - `contribute`: لما المستخدم يأكد تصنيف عملية شراء، اسم المحل وتصنيفه بيتبعتوا مش مؤكدين.
 */
export function makeSharedMerchants(deps: SharedMerchantsDeps) {
  const baseline = baselineCatalog(deps.baseline)

  async function sync(): Promise<{ changes: number; added: number; filled: number }> {
    const since = deps.cursor.read()
    const remote = await deps.catalog.listChangedSince(since)
    if (remote.length === 0) return { changes: 0, added: 0, filled: 0 }
    const effective = remote.map((r) => effectiveEntry(baseline.get(sharedMerchantKey(r.normalizedName)), r)!)
    const plan = planAccountMerchantSync(effective, await deps.merchants.listAll(), deps.treeCategoryIds)
    const writes = [...plan.add, ...plan.fill]
    if (writes.length > 0) await deps.merchants.saveMany(writes)
    // المؤشر بيتقدم بعد الكتابة بس — انقطاع في النص بيعيد نفس التغييرات (المعرّفات ثابتة فمفيش تكرار)
    const latest = latestUpdate(remote, since)
    if (latest && latest !== since) deps.cursor.write(latest)
    return { changes: remote.length, added: plan.add.length, filled: plan.fill.length }
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
