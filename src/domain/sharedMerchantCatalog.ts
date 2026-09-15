import type { Id, Merchant, Transaction } from './entities/types'
import { merchantIndex } from './merchantIndex'
import { normalizeText } from './normalize'

/**
 * قاعدة التجار المشتركة بين المستخدمين — OVERRIDES §25 و§25.1.
 *
 * اللي بيتشارك: الاسم وأشكاله وتصنيف من الشجرة وحالة التأكيد بس.
 * مفيش مبالغ ولا تواريخ عمليات ولا مين ضاف (`updatedAt` وقت آخر تعديل للمستند نفسه).
 */
export interface SharedMerchantEntry {
  normalizedName: string
  displayName: string
  aliases: string[]
  /** معرّف تصنيف من شجرة التطبيق بس (نفس المعرّف في كل الحسابات) — مش تصنيف عمله مستخدم. */
  categoryId: Id | null
  /** بيتكتب من لوحة فايربيز بس — التطبيق عمره ما يكتبه `true`. */
  confirmed: boolean
  /** ISO — وقت آخر تعديل على المستند (من السيرفر). */
  updatedAt?: string
}

export const MAX_SHARED_NAME = 80
export const MAX_SHARED_ALIASES = 10

/** 4 أرقام ورا بعض = شكل رقم حساب أو تليفون أو فرع ⇒ ما يتبعتش (قاعدة 11). */
const LONG_DIGITS = /\p{N}{4,}/u

/** مفتاح المستند: الاسم الموحد بشرطة مكان المسافة — `normalizeText` ما بيسيبش «/» ولا «-». */
export function sharedMerchantKey(normalizedName: string): string {
  return normalizeText(normalizedName).replace(/ /g, '-')
}

/** الاسم ينفع يتشارك؟ فاضي أو طويل أو فيه رقم طويل ⇒ لأ. */
export function shareableName(name: string | undefined): string | null {
  const normalized = normalizeText(name ?? '')
  if (!normalized || normalized.length > MAX_SHARED_NAME || LONG_DIGITS.test(normalized)) return null
  return normalized
}

/**
 * القاعدة اللي جوه التطبيق (`merchant-reference.json`) كأساس — §25.1 «المؤكدين من المرجع».
 * التاجر اللي ليه تصنيف مؤكد في المرجع بيبدأ مؤكد.
 */
export function baselineCatalog(merchants: readonly Merchant[]): Map<string, SharedMerchantEntry> {
  const out = new Map<string, SharedMerchantEntry>()
  for (const m of merchants) {
    out.set(sharedMerchantKey(m.normalizedName), {
      normalizedName: m.normalizedName,
      displayName: m.displayName,
      aliases: [...(m.aliases ?? [])],
      categoryId: m.verifiedCategoryId ?? null,
      confirmed: !!m.verifiedCategoryId,
    })
  }
  return out
}

/** المستند المشترك قصاد الأساس: تعديل مش مؤكد ما يغلبش تاجر مؤكد في المرجع. */
export function effectiveEntry(
  baseline: SharedMerchantEntry | undefined,
  remote: SharedMerchantEntry | undefined,
): SharedMerchantEntry | undefined {
  if (!remote) return baseline
  if (baseline?.confirmed && !remote.confirmed) return baseline
  return remote
}

/**
 * مساهمة المستخدم لما يأكد تصنيف عملية — §25.1 «المشتريات المؤكدة بس».
 * `null` = ما يتبعتش حاجة: مش شراء، أو الاسم مش آمن، أو التصنيف مش من الشجرة،
 * أو التاجر **مؤكد** (قاعدة التعارض: التغيير يفضل للمستخدم بس)، أو مفيش جديد.
 */
export function contributionFor(input: {
  transaction: Pick<Transaction, 'economicKind' | 'observedDirection' | 'rawMerchantName'>
  categoryId: Id
  treeCategoryIds: ReadonlySet<Id>
  current: SharedMerchantEntry | undefined
}): SharedMerchantEntry | null {
  const { transaction, categoryId, current } = input
  if (transaction.economicKind !== 'purchase' || transaction.observedDirection !== 'out') return null
  if (!input.treeCategoryIds.has(categoryId)) return null
  const normalized = shareableName(transaction.rawMerchantName)
  if (!normalized) return null
  if (current?.confirmed) return null
  if (current && current.categoryId === categoryId) return null
  const displayName = (transaction.rawMerchantName ?? '').trim().replace(/\s+/g, ' ').slice(0, MAX_SHARED_NAME)
  return {
    normalizedName: normalized,
    displayName: current?.displayName ?? displayName,
    aliases: (current?.aliases ?? []).slice(0, MAX_SHARED_ALIASES),
    categoryId,
    confirmed: false,
  }
}

export interface AccountMerchantSyncPlan {
  /** تجار مؤكدين في القاعدة المشتركة ومش موجودين في الحساب. */
  add: Merchant[]
  /** تجار موجودين في الحساب من غير تصنيف مؤكد، والقاعدة فيها تصنيف مؤكد. */
  fill: Merchant[]
}

/**
 * اللي يتكتب في حساب المستخدم من التغييرات المشتركة.
 * **المؤكد بس** بيوصل الحساب (spec/05: ممنوع تأكيد آلي للغامض)،
 * و**تصنيف المستخدم نفسه عمره ما يتكتب فوقه** (§25 قاعدة التعارض).
 * المعرّف ثابت من الاسم، فتكرار المزامنة ما بيكررش تجار.
 */
export function planAccountMerchantSync(
  entries: readonly SharedMerchantEntry[],
  account: readonly Merchant[],
  treeCategoryIds: ReadonlySet<Id>,
): AccountMerchantSyncPlan {
  const index = merchantIndex(account)
  const add: Merchant[] = []
  const fill: Merchant[] = []
  const seen = new Set<string>()
  for (const entry of entries) {
    const normalized = normalizeText(entry.normalizedName)
    if (!entry.confirmed || !entry.categoryId || !treeCategoryIds.has(entry.categoryId)) continue
    if (!normalized || seen.has(normalized)) continue
    seen.add(normalized)
    const existing = index.get(normalized)
    if (existing) {
      if (!existing.verifiedCategoryId) fill.push({ ...existing, verifiedCategoryId: entry.categoryId })
      continue
    }
    add.push({
      id: `merch-shared-${sharedMerchantKey(normalized)}`,
      displayName: entry.displayName || normalized,
      normalizedName: normalized,
      ...(entry.aliases.length ? { aliases: entry.aliases.slice(0, MAX_SHARED_ALIASES) } : {}),
      verifiedCategoryId: entry.categoryId,
    })
  }
  return { add, fill }
}

/** أحدث وقت تعديل — منه المزامنة الجاية بتبدأ. */
export function latestUpdate(entries: readonly SharedMerchantEntry[], previous: string | null): string | null {
  let latest = previous
  for (const e of entries) if (e.updatedAt && (!latest || e.updatedAt > latest)) latest = e.updatedAt
  return latest
}
