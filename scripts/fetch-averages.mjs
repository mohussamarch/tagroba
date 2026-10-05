/**
 * يبني `public/averages.json` — متوسطات «هتوصل لكام؟» (OVERRIDES §69.6). بيشتغل في نفس مهمة الأسعار اليومية
 * (`.github/workflows/prices.yml`) لكن **بيحسب مرة في الشهر بس**: يوم 1، أو لو الملف أقدم من 28 يوم، أو لو مش موجود،
 * أو بـ`--force`. غير كده بيخرج من غير ما يلمس الملف.
 *
 * قواعد ملزمة:
 * - كل معدل **عدد صحيح بنقاط الأساس** محسوب بـ`BigInt` (القاعدة والتقريب في `averages/math.mjs`).
 * - **لا رقم بلا مصدر:** كل سطر عليه المصدر ورابطه والفترة والطريقة ورسمي ولا خاص.
 * - مصدر فشل ⇒ **الرقم القديم بيفضل وعليه `stale: true`** والسبب. مفيش رقم قديم ⇒ السطر مش موجود (التطبيق بيقول «غير متاح»).
 *   **عمر ما بيتكتب صفر مكان المجهول.**
 * - الأرقام اليدوية (MSCI · البنوك المركزية · مؤشر العقار) في `averages/manual.mjs` وعليها تاريخ المراجعة.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { manualEntries } from './averages/manual.mjs'
import {
  CMO_FALLBACK_URL,
  CMO_PAGE_URL,
  cpiAverage,
  findCmoLink,
  goldEgpSet,
  goldSeriesFromWorkbook,
  goldUsd,
  wdiSeries,
  wdiUrl,
} from './averages/sources.mjs'

export const OUT = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'averages.json')
export const REFRESH_AFTER_DAYS = 28
const UA = { 'User-Agent': 'masroufy-price-bot' }

/** الجلب الحقيقي — الاختبار بيبدّله بردود ثابتة. */
export const realHttp = {
  async text(url) {
    const r = await fetch(url, { headers: UA, redirect: 'follow' })
    if (!r.ok) throw new Error(`${url} رجّع ${r.status}`)
    return r.text()
  },
  async json(url) {
    return JSON.parse(await this.text(url))
  },
  async buffer(url) {
    const r = await fetch(url, { headers: UA, redirect: 'follow' })
    if (!r.ok) throw new Error(`${url} رجّع ${r.status}`)
    return Buffer.from(await r.arrayBuffer())
  },
}

/** وقت الحساب؟ يوم 1 في الشهر (UTC) · مفيش ملف · الملف أقدم من 28 يوم · أو إجباري. */
export function isDue(previous, now, force = false) {
  if (force || !previous?.generatedAt) return true
  if (now.getUTCDate() === 1) return true
  const age = (now.getTime() - Date.parse(previous.generatedAt)) / 86_400_000
  return !(age < REFRESH_AFTER_DAYS) // تاريخ بايظ (NaN) ⇒ يتحسب
}

/** بيحسب كل السطور المحسوبة؛ كل مصدر لوحده (فشله ما يوقفش الباقي). */
async function computeAll(http, failures) {
  const out = {}
  const attempt = async (label, keys, run) => {
    try {
      Object.assign(out, await run())
    } catch (cause) {
      failures.push({ source: label, keys, reason: String(cause?.message ?? cause) })
    }
  }

  let goldUrl = CMO_FALLBACK_URL
  let gold = null
  await attempt('World Bank Pink Sheet (gold)', ['GOLD_USD'], async () => {
    try {
      goldUrl = findCmoLink(await http.text(CMO_PAGE_URL)) ?? CMO_FALLBACK_URL
    } catch {
      goldUrl = CMO_FALLBACK_URL // الصفحة وقعت ⇒ آخر رابط معروف
    }
    gold = goldSeriesFromWorkbook(await http.buffer(goldUrl))
    return { GOLD_USD: goldUsd(gold, goldUrl) }
  })

  const fxUrl = wdiUrl('EGY', 'PA.NUS.FCRF')
  await attempt('World Bank WDI PA.NUS.FCRF (EGP) + gold', ['GOLD_EGP', 'GOLD_USD_ANNUAL', 'EGP_PER_USD'], async () => {
    if (!gold) throw new Error('سلسلة الذهب فشلت')
    return goldEgpSet(gold, wdiSeries(await http.json(fxUrl)), goldUrl, fxUrl)
  })

  for (const [key, country] of [['CPI_SA', 'SAU'], ['CPI_EG', 'EGY']]) {
    const url = wdiUrl(country, 'FP.CPI.TOTL.ZG')
    await attempt(`World Bank WDI FP.CPI.TOTL.ZG (${country})`, [key], async () => ({ [key]: cpiAverage(wdiSeries(await http.json(url)), country, url) }))
  }
  return out
}

/** الملف الجديد: المحسوب الطازة + القديم لأي مصدر فشل (معلَّم «قديم») + اليدوي. */
export async function buildAverages({ http, previous, now }) {
  const failures = []
  const fresh = await computeAll(http, failures)
  const today = now.toISOString().slice(0, 10)
  const averages = {}
  for (const [key, entry] of Object.entries(fresh)) averages[key] = { ...entry, computedOn: today }
  for (const failure of failures) {
    for (const key of failure.keys) {
      const old = previous?.averages?.[key]
      if (!old || old.kind !== 'computed' || !Number.isInteger(old.valueBp)) continue // مفيش قديم ⇒ السطر غايب (مش صفر)
      averages[key] = { ...old, stale: true, staleSince: old.stale ? (old.staleSince ?? today) : today, staleReason: failure.reason }
    }
  }
  Object.assign(averages, manualEntries())
  return {
    schema: 1,
    generatedAt: now.toISOString(),
    windowYears: 10,
    note: 'Rates are integer basis points (1bp = 0.01%). Computed monthly from the World Bank; manual entries are reviewed by hand (see reviewedOn).',
    averages,
    failures: failures.map(({ source, reason }) => ({ source, reason })),
  }
}

async function main() {
  const force = process.argv.includes('--force') || process.env.AVERAGES_FORCE === '1'
  const previous = existsSync(OUT) ? JSON.parse(readFileSync(OUT, 'utf8')) : null
  const now = new Date()
  if (!isDue(previous, now, force)) {
    console.log(`المتوسطات اتحسبت ${previous.generatedAt.slice(0, 10)} — مش وقتها (مرة في الشهر)`)
    return
  }
  const feed = await buildAverages({ http: realHttp, previous, now })
  mkdirSync(dirname(OUT), { recursive: true })
  writeFileSync(OUT, `${JSON.stringify(feed, null, 2)}\n`, 'utf8')
  const stale = Object.values(feed.averages).filter((e) => e.stale).length
  console.log(`✓ ${Object.keys(feed.averages).length} متوسط (${stale} قديم)، ${feed.failures.length} فشل → ${OUT}`)
  for (const f of feed.failures) console.error(`✗ ${f.source}: ${f.reason}`)
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) await main()
