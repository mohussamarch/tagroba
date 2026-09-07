import { parsePriceFeed, PriceFeedError, type PriceFeed } from '../../domain/priceFeed'

/**
 * يجيب ملف الأسعار. **مكان الشبكة الوحيد في ميزة الأسعار.**
 *
 * مصدران بالترتيب:
 * ١. النسخة اللي بيحدّثها GitHub Actions — الأحدث، بتتجدد بلا نشر جديد
 * ٢. النسخة المرفقة مع التطبيق — احتياطي لو الأول مش متاح
 *
 * الاتنين بيوصلوا بتاريخهم، فمفيش سعر بيتعرض من غير ما يقول هو من إمتى.
 * فشل الاتنين ⇒ **خطأ صريح**، مش قايمة فاضية بتبان كأن مفيش أسعار.
 */

const REMOTE_URL =
  'https://raw.githubusercontent.com/mohussamarch/tagroba/main/public/prices.json'

const LOCAL_URL = '/prices.json'

const TIMEOUT_MS = 8000

async function fetchJson(url: string): Promise<unknown> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  try {
    const response = await fetch(url, { cache: 'no-store', signal: controller.signal })
    if (!response.ok) throw new PriceFeedError(`${url} رجّع ${response.status}`)
    return await response.json()
  } finally {
    clearTimeout(timer)
  }
}

export interface LoadedFeed {
  feed: PriceFeed
  /** من فين جه — يظهر للمستخدم فيعرف هل الرقم متجدد ولا مرفق. */
  origin: 'remote' | 'bundled'
}

export async function loadPriceFeed(): Promise<LoadedFeed> {
  const reasons: string[] = []

  for (const [origin, url] of [
    ['remote', REMOTE_URL],
    ['bundled', LOCAL_URL],
  ] as const) {
    try {
      return { feed: parsePriceFeed(await fetchJson(url)), origin }
    } catch (cause) {
      reasons.push(`${origin}: ${cause instanceof Error ? cause.message : String(cause)}`)
    }
  }

  throw new PriceFeedError(
    `مقدرناش نجيب ملف الأسعار. جرّب تاني وانت متصل بالإنترنت. (${reasons.join(' — ')})`,
  )
}
