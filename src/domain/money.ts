/**
 * نموذج المال — الوحدة الصغرى (integer minor units)
 *
 * كل مبلغ عدد صحيح بالهللة. 96.47 ر.س = 9647.
 * ممنوع منعًا باتًا استخدام الأعداد العشرية (floating point) في أي حساب مالي،
 * لأن 0.1 + 0.2 !== 0.3 في IEEE 754، وده يكسر مطابقة الرصيد بدقة الهللة.
 * التحويل للريال يحدث في طبقة العرض فقط (ARCHITECTURE.md §4).
 *
 * هذا الملف لا يستورد أي شيء. domain/ نقي تمامًا.
 */

/** مبلغ بالهللة. عدد صحيح، قد يكون سالبًا. */
export type Halalas = number

/** رمز العملة بمعيار ISO 4217. */
export type Currency = 'SAR' | 'EGP' | 'USD' | 'EUR' | 'GBP' | 'AED'

/** عدد الوحدات الصغرى في وحدة واحدة، لكل عملة. */
const MINOR_UNITS: Record<Currency, number> = {
  SAR: 100, // هللة
  EGP: 100, // قرش
  USD: 100,
  EUR: 100,
  GBP: 100,
  AED: 100,
}

export function minorUnitsOf(currency: Currency): number {
  return MINOR_UNITS[currency] ?? 100
}

/** الحد الأقصى الآمن: 2^53−1 هللة ≈ 90 تريليون ريال. أي مبلغ فوقه خطأ لا تقريب. */
const MAX_SAFE = Number.MAX_SAFE_INTEGER

export class MoneyError extends Error {
  constructor(
    message: string,
    readonly input?: string,
  ) {
    super(message)
    this.name = 'MoneyError'
  }
}

/** يتحقق أن القيمة عدد صحيح صالح كمبلغ. يرمي MoneyError عند الفشل. */
export function assertHalalas(value: unknown, context = 'مبلغ'): asserts value is Halalas {
  if (typeof value !== 'number' || !Number.isInteger(value)) {
    throw new MoneyError(`${context}: القيمة ليست عددًا صحيحًا بالهللة`, String(value))
  }
  if (!Number.isFinite(value) || Math.abs(value) > MAX_SAFE) {
    throw new MoneyError(`${context}: المبلغ خارج المدى الآمن`, String(value))
  }
}

/* ────────────────────────────── التحليل (parsing) ────────────────────────────── */

/** الأرقام العربية الهندية والفارسية إلى لاتينية، والفاصلة العشرية العربية إلى نقطة. */
export function normalizeDigits(text: string): string {
  let out = ''
  for (const ch of text) {
    const code = ch.codePointAt(0)!
    if (code >= 0x0660 && code <= 0x0669) out += String.fromCharCode(code - 0x0660 + 48)
    else if (code >= 0x06f0 && code <= 0x06f9) out += String.fromCharCode(code - 0x06f0 + 48)
    else if (ch === '٫') out += '.' // الفاصلة العشرية العربية
    else if (ch === '٬') out += ',' // فاصل الآلاف العربي
    else out += ch
  }
  return out
}

/**
 * يحوّل نصًا ماليًا إلى هللات **بدون أي عملية عشرية**.
 * يقبل: "96.47" · "1,234.5" · الأرقام العربية · "-12" · "12.4-" · "(12.40)"
 * يرفض: "" · "NaN" · "Infinity" · "1.2.3" · أكثر من خانتين كسريتين غير صفرية
 *
 * السبب في تجنب parseFloat: parseFloat("96.47") * 100 = 9646.999999999998
 */
export function parseMoney(raw: string, currency: Currency = 'SAR'): Halalas {
  if (typeof raw !== 'string') throw new MoneyError('المبلغ ليس نصًا', String(raw))

  let s = normalizeDigits(raw).trim()
  if (s === '') throw new MoneyError('المبلغ فارغ', raw)

  // فواصل الآلاف والمسافات (عادية، غير قابلة للكسر، ورفيعة)
  s = s.replace(/[\s  ,]/g, '')

  // إشارة سالبة سابقة أو لاحقة، أو أقواس محاسبية
  let negative = false
  if (/^\(.*\)$/.test(s)) {
    negative = true
    s = s.slice(1, -1)
  }
  if (s.startsWith('-') || s.startsWith('−')) {
    negative = !negative
    s = s.slice(1)
  } else if (s.startsWith('+')) {
    s = s.slice(1)
  }
  if (s.endsWith('-')) {
    negative = !negative
    s = s.slice(0, -1)
  }

  const m = /^(\d*)(?:\.(\d*))?$/.exec(s)
  if (!m || (m[1] === '' && (m[2] ?? '') === '')) {
    throw new MoneyError('صيغة المبلغ غير صالحة', raw)
  }

  const intPart = m[1] === '' ? '0' : m[1]
  const fracRaw = m[2] ?? ''

  const scale = minorUnitsOf(currency)
  const decimals = String(scale).length - 1 // 100 → 2

  // خانات كسرية زائدة: تُرفض لو غير صفرية، ولا تُقرّب بصمت
  if (fracRaw.length > decimals) {
    const extra = fracRaw.slice(decimals)
    if (/[^0]/.test(extra)) {
      throw new MoneyError(
        `المبلغ فيه أكثر من ${decimals} خانة كسرية غير صفرية — لا يُقرَّب بصمت`,
        raw,
      )
    }
  }

  const frac = fracRaw.slice(0, decimals).padEnd(decimals, '0')

  // البناء من الأرقام مباشرة — لا ضرب ولا قسمة عشرية
  const digits = intPart + frac
  if (digits.length > 16) throw new MoneyError('المبلغ خارج المدى الآمن', raw)

  const value = Number(digits)
  if (!Number.isSafeInteger(value)) throw new MoneyError('المبلغ خارج المدى الآمن', raw)

  return negative ? -value : value
}

/** مثل parseMoney لكن يرجّع null بدل ما يرمي — للاستيراد الذي يجمع الأخطاء بأرقام الصفوف. */
export function tryParseMoney(raw: string, currency: Currency = 'SAR'): Halalas | null {
  try {
    return parseMoney(raw, currency)
  } catch {
    return null
  }
}

/* ────────────────────────────── الحساب ────────────────────────────── */

export function addMoney(...amounts: Halalas[]): Halalas {
  let total = 0
  for (const a of amounts) {
    assertHalalas(a)
    total += a
  }
  assertHalalas(total, 'المجموع')
  return total
}

export function subtractMoney(a: Halalas, b: Halalas): Halalas {
  assertHalalas(a)
  assertHalalas(b)
  const r = a - b
  assertHalalas(r, 'الفرق')
  return r
}

export function negateMoney(a: Halalas): Halalas {
  assertHalalas(a)
  return -a
}

export function absMoney(a: Halalas): Halalas {
  assertHalalas(a)
  return Math.abs(a)
}

/** يجمع مبالغ قائمة. مجموع القائمة الفارغة صفر — وده صفر محسوب لا صفر مفترض. */
export function sumMoney(amounts: readonly Halalas[]): Halalas {
  return addMoney(...amounts)
}

/**
 * ضرب مبلغ في عدد صحيح (عدد الأقساط مثلاً). لا يقبل كسورًا.
 * للنسب استخدم rateOfMoney أو splitMoney.
 */
export function multiplyMoneyByInt(a: Halalas, times: number): Halalas {
  assertHalalas(a)
  if (!Number.isInteger(times)) throw new MoneyError('المضروب فيه ليس عددًا صحيحًا', String(times))
  const r = a * times
  assertHalalas(r, 'الناتج')
  return r
}

/**
 * نسبة من مبلغ (numerator/denominator)، بتقريب نصف-لأعلى-بعيدًا-عن-الصفر
 * عند حد معلن واحد. العددان صحيحان — لا يدخل عشري في الحساب.
 */
export function rateOfMoney(a: Halalas, numerator: number, denominator: number): Halalas {
  assertHalalas(a)
  if (!Number.isInteger(numerator) || !Number.isInteger(denominator)) {
    throw new MoneyError('النسبة يجب أن تكون بعددين صحيحين')
  }
  if (denominator === 0) throw new MoneyError('القسمة على صفر')
  const sign = a < 0 !== numerator < 0 ? -1 : 1
  const num = Math.abs(a) * Math.abs(numerator)
  const den = Math.abs(denominator)
  const q = Math.floor(num / den)
  const rem = num - q * den
  const rounded = rem * 2 >= den ? q + 1 : q
  const r = sign * rounded
  assertHalalas(r, 'النسبة')
  return r
}

/**
 * يقسّم مبلغًا على n حصة **بدون فقد ولا خلق هللة**.
 * المجموع دائمًا يساوي الأصل بالضبط؛ الباقي يُوزَّع هللة هللة على الحصص الأولى.
 */
export function splitMoney(a: Halalas, parts: number): Halalas[] {
  assertHalalas(a)
  if (!Number.isInteger(parts) || parts <= 0) {
    throw new MoneyError('عدد الحصص يجب أن يكون عددًا صحيحًا موجبًا', String(parts))
  }
  const sign = a < 0 ? -1 : 1
  const abs = Math.abs(a)
  const base = Math.floor(abs / parts)
  let remainder = abs - base * parts
  const out: Halalas[] = []
  for (let i = 0; i < parts; i++) {
    const extra = remainder > 0 ? 1 : 0
    if (extra) remainder--
    out.push(sign * (base + extra))
  }
  return out
}

export function compareMoney(a: Halalas, b: Halalas): -1 | 0 | 1 {
  assertHalalas(a)
  assertHalalas(b)
  return a < b ? -1 : a > b ? 1 : 0
}

export const isZeroMoney = (a: Halalas): boolean => a === 0
export const isPositiveMoney = (a: Halalas): boolean => a > 0
export const isNegativeMoney = (a: Halalas): boolean => a < 0

/**
 * مقارنة بهامش نسبي — للبحث بالمبلغ ±5% (spec/06).
 * الهامش بالألف: 50 يعني ٥٪. لا يدخل عشري.
 */
export function withinRelativeTolerance(
  value: Halalas,
  target: Halalas,
  tolerancePerThousand: number,
): boolean {
  assertHalalas(value)
  assertHalalas(target)
  const margin = rateOfMoney(absMoney(target), tolerancePerThousand, 1000)
  return absMoney(subtractMoney(value, target)) <= margin
}
