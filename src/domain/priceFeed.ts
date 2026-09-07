import { isValidIsoDate } from './period'
import type { Halalas } from './money'
import type { IsoDate } from './entities/types'

/**
 * ملف الأسعار — دالة نقية تقرا وتتحقق، ولا تتصل بشبكة.
 *
 * الملف بيتولد بره التطبيق (GitHub Actions) لأن Cloud Functions مش
 * متاحة في باقة Spark — CLAUDE.md #12. التطبيق بيقراه كملف عادي.
 *
 * القاعدة الحاكمة (#10): **لا رقم بلا مصدر.** أي مدخل ناقص أو غلط
 * **يتشال** ويتسجل سببه، وما يتحطش صفر ولا قيمة مخمّنة مكانه.
 */

export class PriceFeedError extends Error {}

export interface FeedPrice {
  symbol: string
  name: string
  unit: string
  pricePerUnitMinor: Halalas
  asOf: IsoDate
  source: string
}

export interface PriceFeed {
  generatedAt: string
  baseCurrency: string
  prices: FeedPrice[]
  /** مدخلات مرفوضة عند القراءة، بسببها — الغياب مفهوم لا غامض. */
  rejected: { symbol: string; reason: string }[]
  /** مصادر فشلت وقت التوليد، كما كتبها الملف نفسه. */
  failures: { source: string; reason: string }[]
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function text(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

/**
 * يقرا الملف. يرمي لو الشكل نفسه غلط (مش ملف أسعار أصلًا)،
 * ويرفض المدخلات الفردية الغلط بلا ما يوقف الباقي.
 */
export function parsePriceFeed(raw: unknown): PriceFeed {
  if (!isRecord(raw)) throw new PriceFeedError('ملف الأسعار مش بالشكل المتوقع')

  const generatedAt = text(raw.generatedAt)
  if (!generatedAt) throw new PriceFeedError('ملف الأسعار مالوش تاريخ توليد')

  const baseCurrency = text(raw.baseCurrency)
  if (!baseCurrency) throw new PriceFeedError('ملف الأسعار مش قايل عملته')

  if (!isRecord(raw.prices)) throw new PriceFeedError('ملف الأسعار مفيهوش أسعار')

  const prices: FeedPrice[] = []
  const rejected: { symbol: string; reason: string }[] = []

  for (const [symbol, entry] of Object.entries(raw.prices)) {
    if (!isRecord(entry)) {
      rejected.push({ symbol, reason: 'المدخل مش كائن' })
      continue
    }

    const amount = entry.pricePerUnitMinor
    // عدد صحيح موجب فقط — الكسر العشري هنا معناه إن المولّد غلط
    if (typeof amount !== 'number' || !Number.isInteger(amount) || amount <= 0) {
      rejected.push({ symbol, reason: 'السعر مش عددًا صحيحًا موجبًا بالهللة' })
      continue
    }

    const asOf = text(entry.asOf)
    if (!asOf || !isValidIsoDate(asOf)) {
      rejected.push({ symbol, reason: 'مفيش تاريخ صالح للسعر' })
      continue
    }

    const source = text(entry.source)
    if (!source) {
      rejected.push({ symbol, reason: 'مفيش مصدر مكتوب للسعر' })
      continue
    }

    prices.push({
      symbol,
      name: text(entry.name) ?? symbol,
      unit: text(entry.unit) ?? '',
      pricePerUnitMinor: amount,
      asOf,
      source,
    })
  }

  const failures = Array.isArray(raw.failures)
    ? raw.failures.filter(isRecord).map((f) => ({
        source: text(f.source) ?? 'مصدر غير معروف',
        reason: text(f.reason) ?? 'سبب غير مكتوب',
      }))
    : []

  return { generatedAt, baseCurrency, prices, rejected, failures }
}

/** فهرس بالرمز للبحث السريع. */
export function indexFeed(feed: PriceFeed): Map<string, FeedPrice> {
  return new Map(feed.prices.map((p) => [p.symbol, p]))
}

/**
 * يشرح الملف بجملة واحدة للمستخدم — عدد الأسعار وتاريخها وما فشل.
 * المستخدم لازم يعرف إن الملف اتولد إمتى، مش بس الأرقام اللي فيه.
 */
export function describeFeed(feed: PriceFeed): string {
  const day = feed.generatedAt.slice(0, 10)
  const missing = feed.failures.length + feed.rejected.length
  const base = `${feed.prices.length} سعر محدَّث يوم ${day}`
  return missing === 0 ? base : `${base}، و${missing} مصدر مجابش سعر`
}
