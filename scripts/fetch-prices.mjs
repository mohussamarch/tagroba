/**
 * يبني `public/prices.json` — يشتغل في GitHub Actions لا في المتصفح.
 *
 * ⚠️ Cloud Functions مش متاحة (باقة Spark) — CLAUDE.md #12.
 * فالتحديث بيحصل بره التطبيق تمامًا، والتطبيق بيقرا الملف الناتج كملف عادي.
 *
 * قواعد ملزمة محفوظة هنا كمان:
 * - كل سعر **عدد صحيح بالهللة**. التحويل بـ`BigInt` والتقريب مرة واحدة
 *   في الآخر، لا بعد كل خطوة (CLAUDE.md #1).
 * - **لا رقم بلا مصدر** (#10): مصدر كل سعر وتاريخه مكتوبان في الملف.
 * - مصدر فشل ⇒ سعره **يختفي** من الملف ويتسجل في `failures`.
 *   ما نكتبش صفرًا ولا نكرّر سعر إمبارح كأنه سعر النهارده.
 */

import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const OUT = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'prices.json')

/** جرامات في الأونصة التروي — ثابت معياري، مضروب في 10^6 ليبقى صحيحًا. */
const GRAMS_PER_OUNCE_SCALED = 31_103_477n
const GRAM_SCALE = 1_000_000n

const UA = { 'User-Agent': 'masroufy-price-bot' }

async function getJson(url) {
  const response = await fetch(url, { headers: UA, redirect: 'follow' })
  if (!response.ok) throw new Error(`${url} رجّع ${response.status}`)
  return response.json()
}

/**
 * يحوّل رقمًا عشريًا لعدد صحيح بمقياس معلن، **من نصّه لا من قيمته**،
 * فما يحصلش خطأ العشري المعروف (0.29 × 100 = 28.999…).
 */
function toScaled(value, decimals) {
  const text = typeof value === 'number' ? value.toFixed(decimals + 2) : String(value).trim()
  const negative = text.startsWith('-')
  const [intPart = '0', fracRaw = ''] = text.replace(/^[-+]/, '').split('.')
  if (!/^\d+$/.test(intPart) || !/^\d*$/.test(fracRaw)) {
    throw new Error(`«${text}» مش رقم صالح`)
  }
  // نقصّ الزيادة بالتقريب نصف لأعلى مرة واحدة
  const frac = fracRaw.padEnd(decimals + 1, '0')
  const kept = BigInt(intPart + frac.slice(0, decimals))
  const nextDigit = Number(frac[decimals] ?? '0')
  const rounded = nextDigit >= 5 ? kept + 1n : kept
  return negative ? -rounded : rounded
}

/** قسمة صحيحة بتقريب نصف لأعلى — نقطة التقريب الوحيدة. */
function divideRounded(numerator, denominator) {
  return (numerator * 2n + denominator) / (denominator * 2n)
}

const results = {}
const failures = []

/** يشغّل مصدرًا؛ فشله لا يوقف البقية ولا يلوّث الملف بقيمة ملفّقة. */
async function collect(label, run) {
  try {
    await run()
  } catch (cause) {
    failures.push({ source: label, reason: String(cause?.message ?? cause) })
    console.error(`✗ ${label}: ${cause?.message ?? cause}`)
  }
}

const asOf = new Date().toISOString().slice(0, 10)

/** سعر صرف الدولار بالريال، عدد صحيح بمقياس 10^6. */
let usdToSarScaled = null

await collect('USD/SAR', async () => {
  const data = await getJson(
    'https://query1.finance.yahoo.com/v8/finance/chart/SAR=X?interval=1d&range=1d',
  )
  const rate = data?.chart?.result?.[0]?.meta?.regularMarketPrice
  if (typeof rate !== 'number' || !Number.isFinite(rate) || rate <= 0) {
    throw new Error('مفيش سعر صرف في الرد')
  }
  usdToSarScaled = toScaled(rate, 6)
  results.USD_SAR = {
    name: 'الدولار الأمريكي',
    unit: 'دولار',
    pricePerUnitMinor: Number(divideRounded(usdToSarScaled * 100n, GRAM_SCALE)),
    asOf,
    source: 'Yahoo Finance — SAR=X',
  }
})

/**
 * الذهب: المصدر بيدي دولارًا للأونصة، والمستخدم بيشتري بالجرام بالريال.
 * التحويل كله بأعداد صحيحة: (دولار/أونصة × صرف × 100) ÷ (جرام/أونصة).
 */
const KARATS = [
  { key: 'GOLD_24K_GRAM', name: 'ذهب عيار 24', numerator: 24n },
  { key: 'GOLD_22K_GRAM', name: 'ذهب عيار 22', numerator: 22n },
  { key: 'GOLD_21K_GRAM', name: 'ذهب عيار 21', numerator: 21n },
  { key: 'GOLD_18K_GRAM', name: 'ذهب عيار 18', numerator: 18n },
]

await collect('الذهب', async () => {
  if (usdToSarScaled === null) throw new Error('سعر الصرف فشل، فسعر الذهب بالريال مجهول')
  const data = await getJson('https://api.gold-api.com/price/XAU')
  if (typeof data?.price !== 'number' || data.price <= 0) throw new Error('مفيش سعر في الرد')

  const usdPerOunceScaled = toScaled(data.price, 6) // 10^6
  for (const karat of KARATS) {
    // الضرب كله قبل أي قسمة، والتقريب مرة واحدة في الآخر
    const numerator =
      usdPerOunceScaled * usdToSarScaled * 100n * karat.numerator * GRAM_SCALE
    const denominator = GRAM_SCALE * GRAM_SCALE * GRAMS_PER_OUNCE_SCALED * 24n
    results[karat.key] = {
      name: karat.name,
      unit: 'جرام',
      pricePerUnitMinor: Number(divideRounded(numerator, denominator)),
      asOf,
      source: 'gold-api.com (XAU) + Yahoo (SAR=X)',
    }
  }
})

await collect('الفضة', async () => {
  if (usdToSarScaled === null) throw new Error('سعر الصرف فشل، فسعر الفضة بالريال مجهول')
  const data = await getJson('https://api.gold-api.com/price/XAG')
  if (typeof data?.price !== 'number' || data.price <= 0) throw new Error('مفيش سعر في الرد')

  const usdPerOunceScaled = toScaled(data.price, 6)
  results.SILVER_GRAM = {
    name: 'فضة',
    unit: 'جرام',
    pricePerUnitMinor: Number(
      divideRounded(usdPerOunceScaled * usdToSarScaled * 100n, GRAM_SCALE * GRAMS_PER_OUNCE_SCALED),
    ),
    asOf,
    source: 'gold-api.com (XAG) + Yahoo (SAR=X)',
  }
})

/**
 * أسهم تداول. القائمة **ثابتة هنا** لأن الـworkflow ما بيشوفش بيانات
 * أي مستخدم (كل حساب مقفول عليه في Firestore). أي سهم بره القائمة
 * سعره يدوي، وده مكتوب في الشاشة مش مخفي.
 */
const TADAWUL = [
  { symbol: '2222.SR', name: 'أرامكو السعودية' },
  { symbol: '1120.SR', name: 'الراجحي' },
  { symbol: '2010.SR', name: 'سابك' },
  { symbol: '7010.SR', name: 'الاتصالات السعودية' },
  { symbol: '1180.SR', name: 'الأهلي السعودي' },
]

for (const stock of TADAWUL) {
  await collect(stock.name, async () => {
    const data = await getJson(
      `https://query1.finance.yahoo.com/v8/finance/chart/${stock.symbol}?interval=1d&range=1d`,
    )
    const meta = data?.chart?.result?.[0]?.meta
    const price = meta?.regularMarketPrice
    if (typeof price !== 'number' || !Number.isFinite(price) || price <= 0) {
      throw new Error('مفيش سعر في الرد')
    }
    // السوق سعودي فالعملة ريال أصلًا — لو اتغيرت نرفض بدل ما نحوّل بالغلط
    if (meta.currency !== 'SAR') throw new Error(`العملة ${meta.currency} مش ريال`)

    results[stock.symbol] = {
      name: stock.name,
      unit: 'سهم',
      pricePerUnitMinor: Number(toScaled(price, 2)),
      asOf: new Date((meta.regularMarketTime ?? Date.now() / 1000) * 1000)
        .toISOString()
        .slice(0, 10),
      source: `Yahoo Finance — ${stock.symbol}`,
    }
  })
}

const feed = {
  generatedAt: new Date().toISOString(),
  baseCurrency: 'SAR',
  prices: results,
  /** المصادر اللي فشلت — مكتوبة عشان الغياب يبقى مفهومًا لا غامضًا. */
  failures,
}

mkdirSync(dirname(OUT), { recursive: true })
writeFileSync(OUT, `${JSON.stringify(feed, null, 2)}\n`, 'utf8')

console.log(`✓ ${Object.keys(results).length} سعر، ${failures.length} فشل → ${OUT}`)

// فشل الكل معناه إن الملف بلا فايدة — نوقف الـworkflow بدل ما نكتب فراغًا
if (Object.keys(results).length === 0) process.exit(1)
