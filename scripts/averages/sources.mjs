/**
 * مصادر المتوسطات **المحسوبة** (OVERRIDES §69.6) — كل دالة بتاخد بيانات خام وبترجّع سطر جاهز أو بترمي بالسبب.
 * الجلب نفسه في `fetch-averages.mjs` (عشان الاختبار يبدّله بردود ثابتة).
 *
 * - الذهب الشهري: «Pink Sheet» البنك الدولي (متوسط الشهر بالدولار للأونصة) — شيت `Monthly Prices` عمود `Gold`.
 * - صرف الجنيه: البنك الدولي WDI `PA.NUS.FCRF` (السعر الرسمي، متوسط السنة).
 * - التضخم: البنك الدولي WDI `FP.CPI.TOTL.ZG` (التغيّر السنوي لمتوسط الأسعار — من الهيئة العامة للإحصاء والجهاز المركزي).
 */
import { readSheet } from './xlsx.mjs'
import { cagrBp, geometricMeanBp, toScaled } from './math.mjs'

export const WINDOW_YEARS = 10

export const CMO_PAGE_URL = 'https://www.worldbank.org/en/research/commodity-markets'
/** الرابط اللي اتلقى 2026-10-05 — بيتغير كل شهر غالبًا، فبيتدوّر عليه في الصفحة الأول وده احتياطي بس. */
export const CMO_FALLBACK_URL =
  'https://thedocs.worldbank.org/en/doc/74e8be41ceb20fa0da750cda2f6b9e4e-0050012026/related/CMO-Historical-Data-Monthly.xlsx'
export const wdiUrl = (country, indicator) =>
  `https://api.worldbank.org/v2/country/${country}/indicator/${indicator}?format=json&per_page=200`

/** رابط ملف الذهب الشهري من صفحة البنك الدولي، أو null لو مش لاقيه. */
export function findCmoLink(html) {
  const m = String(html).match(/https:\/\/thedocs\.worldbank\.org\/[^"'\s<>]*CMO-Historical-Data-Monthly\.xlsx/i)
  return m ? m[0] : null
}

/** سلسلة الذهب الشهرية من ملف الإكسل: "2025-12" ⇒ النص زي ما هو (مش Number). */
export function goldSeriesFromWorkbook(buffer) {
  const rows = readSheet(buffer, 'Monthly Prices')
  let column = -1
  for (const row of rows.slice(0, 20)) {
    column = row.findIndex((cell) => typeof cell === 'string' && cell.trim() === 'Gold')
    if (column >= 0) break
  }
  if (column < 0) throw new Error('عمود «Gold» مش موجود في شيت Monthly Prices')
  const series = new Map()
  for (const row of rows) {
    const m = typeof row[0] === 'string' ? row[0].trim().match(/^(\d{4})M(\d{2})$/) : null
    const value = row[column]
    if (m && typeof value === 'string' && /^\d+(\.\d+)?$/.test(value.trim())) series.set(`${m[1]}-${m[2]}`, value.trim())
  }
  if (series.size === 0) throw new Error('مفيش أسعار ذهب في الشيت')
  return series
}

/** رد WDI ([meta, rows]) ⇒ سنة ⇒ قيمة، والسنين الفاضية (null) برا. */
export function wdiSeries(json) {
  const rows = Array.isArray(json) ? json[1] : null
  if (!Array.isArray(rows)) throw new Error(`رد البنك الدولي مش بالشكل المتوقع${json?.[0]?.message ? ` (${JSON.stringify(json[0].message)})` : ''}`)
  const out = new Map()
  for (const r of rows) if (r && /^\d{4}$/.test(String(r.date)) && typeof r.value === 'number') out.set(Number(r.date), r.value)
  if (out.size === 0) throw new Error('مفيش ولا سنة فيها قيمة')
  return out
}

const latestDecember = (series) =>
  Math.max(...[...series.keys()].filter((k) => k.endsWith('-12')).map((k) => Number(k.slice(0, 4))))

/** مجموع شهور السنة (12 شهر كاملين) بمقياس 10^6، أو null لو ناقص شهر. */
function yearSum(series, year) {
  let sum = 0n
  for (let m = 1; m <= 12; m++) {
    const v = series.get(`${year}-${String(m).padStart(2, '0')}`)
    if (v === undefined) return null
    sum += toScaled(v, 6)
  }
  return sum
}

/** الذهب بالدولار: ديسمبر لديسمبر (متوسط الشهر)، آخر 10 سنين لحد آخر ديسمبر موجود. */
export function goldUsd(series, sourceUrl) {
  const end = latestDecember(series)
  const start = end - WINDOW_YEARS
  const a = series.get(`${start}-12`)
  const b = series.get(`${end}-12`)
  if (!Number.isFinite(end) || a === undefined || b === undefined) throw new Error('ديسمبر البداية أو النهاية ناقص')
  return computed({
    valueBp: cagrBp(toScaled(a, 6), toScaled(b, 6), WINDOW_YEARS),
    currency: 'USD',
    periodStart: `${start}-12`,
    periodEnd: `${end}-12`,
    official: true,
    sourceName: 'World Bank Commodity Price Data (Pink Sheet), monthly gold $/troy oz',
    sourceUrl,
    method: `المعدل المركّب من متوسط ديسمبر ${start} (${a}$) لمتوسط ديسمبر ${end} (${b}$). بالريال نفس الرقم (الربط 3.75).`,
  })
}

/** السنة الأخيرة اللي فيها 12 شهر ذهب **وسعر صرف** — والسنة اللي قبلها بـ10 سنين لازم يبقى فيها الاتنين. */
function annualWindow(series, fx) {
  const years = [...fx.keys()].filter((y) => yearSum(series, y) !== null).sort((x, y) => y - x)
  for (const end of years) if (fx.has(end - WINDOW_YEARS) && yearSum(series, end - WINDOW_YEARS) !== null) return [end - WINDOW_YEARS, end]
  throw new Error('مفيش سنتين بينهم 10 سنين فيهم الذهب والصرف كاملين')
}

/**
 * الذهب بالجنيه + نفس الذهب بالدولار **على نفس الأساس** + نزول الجنيه — كلهم متوسط سنة لمتوسط سنة
 * (الصرف الرسمي في WDI سنوي بس). الأساس الواحد بيخلّي (1 + بالجنيه) ÷ (1 + بالدولار) = (1 + نزول الجنيه) بالظبط تقريبًا.
 */
export function goldEgpSet(series, fx, goldUrl, fxUrl) {
  const [start, end] = annualWindow(series, fx)
  const gs = yearSum(series, start)
  const ge = yearSum(series, end)
  const fs = toScaled(fx.get(start), 6)
  const fe = toScaled(fx.get(end), 6)
  const period = { periodStart: String(start), periodEnd: String(end) }
  const fxNote = `سعر الصرف الرسمي متوسط ${start} (${fx.get(start)}) ومتوسط ${end} (${fx.get(end)})`
  return {
    GOLD_EGP: computed({
      ...period,
      valueBp: cagrBp(gs * fs, ge * fe, WINDOW_YEARS),
      currency: 'EGP',
      official: true,
      sourceName: 'World Bank Pink Sheet monthly gold × World Bank WDI PA.NUS.FCRF (official EGP per USD, annual average)',
      sourceUrl: goldUrl,
      method: `متوسط سعر الذهب في السنة بالدولار × ${fxNote}، والمعدل المركّب بين السنتين. أغلب الزيادة نزول الجنيه.`,
    }),
    GOLD_USD_ANNUAL: computed({
      ...period,
      valueBp: cagrBp(gs, ge, WINDOW_YEARS),
      currency: 'USD',
      official: true,
      sourceName: 'World Bank Pink Sheet monthly gold, annual average of the 12 months',
      sourceUrl: goldUrl,
      method: 'نفس الذهب بالدولار على أساس متوسط السنة — عشان يتقارن بالجنيه على نفس الأساس.',
    }),
    EGP_PER_USD: computed({
      ...period,
      valueBp: cagrBp(fs, fe, WINDOW_YEARS),
      currency: 'EGP',
      official: true,
      sourceName: 'World Bank WDI PA.NUS.FCRF — official exchange rate (EGP per USD, period average)',
      sourceUrl: fxUrl,
      method: `متوسط نزول الجنيه قدام الدولار في السنة: ${fxNote}.`,
    }),
  }
}

/** التضخم: المتوسط الهندسي لآخر 10 نسب سنوية (لازم العشرة كلهم موجودين). */
export function cpiAverage(wdi, country, sourceUrl) {
  const end = Math.max(...wdi.keys())
  const years = Array.from({ length: WINDOW_YEARS }, (_, i) => end - WINDOW_YEARS + 1 + i)
  const missing = years.filter((y) => !wdi.has(y))
  if (missing.length) throw new Error(`سنين ناقصة في التضخم: ${missing.join('، ')}`)
  const agency = country === 'SAU' ? 'GASTAT' : 'CAPMAS'
  return computed({
    valueBp: geometricMeanBp(years.map((y) => wdi.get(y))),
    currency: country === 'SAU' ? 'SAR' : 'EGP',
    periodStart: String(end - WINDOW_YEARS),
    periodEnd: String(end),
    official: true,
    sourceName: `World Bank WDI FP.CPI.TOTL.ZG — consumer price inflation, annual % (${agency} data)`,
    sourceUrl,
    method: `المتوسط الهندسي لتضخم ${years[0]}–${end} (يعني من مستوى أسعار ${end - WINDOW_YEARS} لمستوى ${end}).`,
  })
}

function computed(fields) {
  return { kind: 'computed', stale: false, ...fields }
}
