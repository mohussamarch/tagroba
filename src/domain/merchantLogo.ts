import { normalizeText } from './normalize'

/**
 * شعارات التجار — OVERRIDES §25 و§25.1.
 * - **ملف جوه التطبيق** للمشهورين (المالك بيجيبه ومسؤول عن حقوقه) — بيغلب دايمًا.
 * - **أونلاين** من براند فيتش بدومين المحل، ومقفول لحد ما رقم التعريف يتحط (شرطهم: عرض من سيرفرهم من غير حفظ).
 * - مفيش الاتنين ⇒ `undefined` والصف بيعرض رمز التصنيف.
 */
export interface MerchantLogoEntry {
  /** أسماء المحل كما بتظهر في الكشف (بتتوحّد قبل المقارنة). */
  names: string[]
  /** اسم ملف الشعار جوه التطبيق. */
  file?: string
  /** دومين المحل للشعار الأونلاين، زي `example.com`. */
  domain?: string
}

export type LogoSource = { kind: 'bundled'; file: string } | { kind: 'online'; domain: string }

const DOMAIN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/

export function buildLogoIndex(entries: readonly MerchantLogoEntry[]): Map<string, MerchantLogoEntry> {
  const index = new Map<string, MerchantLogoEntry>()
  for (const entry of entries) {
    for (const name of entry.names) {
      const key = normalizeText(name)
      // أول تعريف للاسم هو اللي بيفضل — تكرار الاسم في الملف ما يغيّرش الشعار بالصدفة
      if (key && !index.has(key)) index.set(key, entry)
    }
  }
  return index
}

export function logoSourceFor(
  merchantName: string | undefined,
  index: ReadonlyMap<string, MerchantLogoEntry>,
  options: { onlineEnabled: boolean; bundledFiles: ReadonlySet<string> },
): LogoSource | undefined {
  const entry = index.get(normalizeText(merchantName ?? ''))
  if (!entry) return undefined
  if (entry.file && options.bundledFiles.has(entry.file)) return { kind: 'bundled', file: entry.file }
  const domain = entry.domain?.trim().toLowerCase()
  if (options.onlineEnabled && domain && DOMAIN.test(domain)) return { kind: 'online', domain }
  return undefined
}
