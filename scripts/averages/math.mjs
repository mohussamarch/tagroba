/**
 * حساب المتوسطات بأعداد صحيحة بس (`BigInt`) — OVERRIDES §69.6.
 *
 * **قاعدة التقريب (مكتوبة عشان أي حد يراجعها على الورق):**
 * 1. كل رقم من المصدر بيتحوّل لعدد صحيح **من نصّه** بـ6 خانات عشرية ([toScaled]) — مفيش `Number` في أي ضرب أو قسمة.
 * 2. المعدل السنوي المركّب (CAGR) = أكبر معدل r بالجزء من المليون (ppm) بحيث (1 + r)^n × البداية ≤ النهاية — بحث ثنائي
 *    على أعداد صحيحة ([rootRatePpm])، يعني **لتحت** لأقرب جزء من المليون.
 * 3. بعدين بيتقرّب **مرة واحدة** لنقاط الأساس (basis points — 1bp = 0.01%) **النص لفوق** ([ppmToBp]).
 *    ⇒ أقصى فرق عن الرقم الحقيقي نص نقطة أساس + جزء من المليون.
 */

/** يحوّل رقمًا عشريًا لعدد صحيح × 10^decimals، **من نصّه لا من قيمته** (نفس قاعدة `fetch-prices.mjs`). التقريب النص لفوق مرة واحدة. */
export function toScaled(value, decimals) {
  const text = typeof value === 'number' ? numberText(value) : String(value).trim()
  const negative = text.startsWith('-')
  const [intPart = '0', fracRaw = ''] = text.replace(/^[-+]/, '').split('.')
  if (!/^\d+$/.test(intPart) || !/^\d*$/.test(fracRaw)) throw new Error(`«${text}» مش رقم صالح`)
  const frac = fracRaw.padEnd(decimals + 1, '0')
  const kept = BigInt(intPart + frac.slice(0, decimals))
  const rounded = Number(frac[decimals] ?? '0') >= 5 ? kept + 1n : kept
  return negative ? -rounded : rounded
}

/** نص الرقم من غير صيغة 1e-7 (JSON بيرجّع أرقام عادية، بس نتأكد). */
function numberText(value) {
  if (!Number.isFinite(value)) throw new Error(`«${value}» مش رقم`)
  const text = String(value)
  return /e/i.test(text) ? value.toFixed(12) : text
}

const PPM = 1_000_000n

/** a^n لعدد صحيح. */
function pow(a, n) {
  let out = 1n
  for (let i = 0; i < n; i++) out *= a
  return out
}

/**
 * أكبر r (ppm، ممكن يبقى سالب) بحيث (10^6 + r)^n × den ≤ num × 10^(6n). يعني «المعدل المركّب اللي بيوصّل من den لـnum في n فترة»
 * لتحت لأقرب جزء من المليون. num و den موجبين.
 */
export function rootRatePpm(num, den, n) {
  if (num <= 0n || den <= 0n || !Number.isInteger(n) || n < 1) throw new Error('مدخلات المعدل المركّب غلط')
  const target = num * pow(PPM, n)
  const fits = (r) => pow(PPM + r, n) * den <= target
  let lo = -PPM + 1n // −99.9999%
  if (!fits(lo)) throw new Error('النزول أكبر من 99.9999%')
  let hi = PPM
  while (fits(hi)) hi *= 2n
  // lo بيعدّي، hi لأ
  while (hi - lo > 1n) {
    const mid = (lo + hi) / 2n
    if (fits(mid)) lo = mid
    else hi = mid
  }
  return lo
}

/** ppm ⇒ نقاط أساس، النص لفوق (floor((r + 50) / 100) — صح للسالب كمان). */
export function ppmToBp(ppm) {
  const shifted = ppm + 50n
  const q = shifted / 100n
  return Number(shifted < 0n && shifted % 100n !== 0n ? q - 1n : q)
}

/** المعدل السنوي المركّب بين قيمتين (نفس المقياس) على [years] سنة — بنقاط الأساس. */
export function cagrBp(startScaled, endScaled, years) {
  return ppmToBp(rootRatePpm(endScaled, startScaled, years))
}

/**
 * المتوسط الهندسي لنسب سنوية بالمية (زي التضخم من البنك الدولي: 2.08 يعني 2.08%) — بنقاط الأساس.
 * كل نسبة بـ6 خانات ⇒ (1 + p/100) = (10^8 + p×10^6) / 10^8. الضرب كله صحيح، والجذر بنفس [rootRatePpm].
 */
export function geometricMeanBp(percents) {
  if (percents.length === 0) throw new Error('مفيش سنين')
  const scale = 100_000_000n
  let num = 1n
  for (const p of percents) {
    const factor = scale + toScaled(p, 6)
    if (factor <= 0n) throw new Error(`نسبة ${p}% مش منطقية`)
    num *= factor
  }
  return ppmToBp(rootRatePpm(num, pow(scale, percents.length), percents.length))
}

/**
 * الجزء اللي من نزول العملة: (1 + المعدل بالعملة المحلية) ÷ (1 + المعدل بالدولار) − 1، بنقاط الأساس، النص لفوق.
 * لو الاتنين على نفس الأساس والفترة، الناتج = متوسط نزول العملة قدام الدولار.
 */
export function devaluationPartBp(localBp, usdBp) {
  const num = BigInt(10_000 + localBp) * 10_000n
  const den = BigInt(10_000 + usdBp)
  if (den <= 0n) throw new Error('معدل الدولار غلط')
  const q = (num * 2n + den) / (den * 2n)
  return Number(q) - 10_000
}
