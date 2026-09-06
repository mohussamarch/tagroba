import { assertHalalas, minorUnitsOf, type Currency, type Halalas } from './money'

/**
 * تنسيق المبالغ للعرض — **المكان الوحيد** الذي تتحول فيه الهللة إلى ريال.
 *
 * التحويل هنا نصّي بحت (قسمة صحيحة + باقي)، لا قسمة عشرية،
 * فلا يظهر 96.46999999 أبدًا.
 *
 * ملاحظة معمارية: الملف في domain/ لأنه دالة نقية بلا اعتماد خارجي،
 * لكن استعماله في presentation/ فقط.
 */

export interface FormatOptions {
  /** إظهار رمز العملة. الافتراضي: نعم */
  showCurrency?: boolean
  /** إظهار إشارة + للمبالغ الموجبة (حركة الأموال). الافتراضي: لا */
  alwaysSign?: boolean
  /** فاصل الآلاف. الافتراضي: نعم */
  grouping?: boolean
  /** إخفاء المبالغ (إعداد الخصوصية في spec/01). */
  hidden?: boolean
}

const CURRENCY_LABEL: Record<Currency, string> = {
  SAR: 'ر.س',
  EGP: 'ج.م',
  USD: '$',
  EUR: '€',
  GBP: '£',
  AED: 'د.إ',
}

export function currencyLabel(currency: Currency): string {
  return CURRENCY_LABEL[currency] ?? currency
}

function groupThousands(digits: string): string {
  let out = ''
  for (let i = 0; i < digits.length; i++) {
    const fromEnd = digits.length - i
    out += digits[i]
    if (fromEnd > 1 && (fromEnd - 1) % 3 === 0) out += ','
  }
  return out
}

/**
 * يحوّل الهللة إلى نص بالريال. بدون رمز العملة.
 * 9647 → "96.47" · -9647 → "-96.47" · 0 → "0.00" · 100000 → "1,000.00"
 */
export function formatAmount(
  amount: Halalas,
  currency: Currency = 'SAR',
  options: FormatOptions = {},
): string {
  assertHalalas(amount, 'مبلغ للعرض')
  const { alwaysSign = false, grouping = true } = options

  const scale = minorUnitsOf(currency)
  const decimals = String(scale).length - 1

  const negative = amount < 0
  const abs = Math.abs(amount)

  const whole = Math.floor(abs / scale)
  const frac = abs - whole * scale

  const wholeStr = grouping ? groupThousands(String(whole)) : String(whole)
  const fracStr = decimals > 0 ? '.' + String(frac).padStart(decimals, '0') : ''

  const sign = negative ? '-' : alwaysSign ? '+' : ''
  return sign + wholeStr + fracStr
}

/** نص كامل للعرض مع رمز العملة: "96.47 ر.س". */
export function formatMoney(
  amount: Halalas,
  currency: Currency = 'SAR',
  options: FormatOptions = {},
): string {
  const { showCurrency = true, hidden = false } = options
  if (hidden) return showCurrency ? `•••• ${currencyLabel(currency)}` : '••••'
  const num = formatAmount(amount, currency, options)
  return showCurrency ? `${num} ${currencyLabel(currency)}` : num
}

/**
 * القيمة غير المتاحة تُعرض كنص صريح، لا صفر.
 * قاعدة CLAUDE.md #10: لا رقم بلا مصدر، ولا صفر مؤكد مكان المجهول.
 */
export const NOT_AVAILABLE = 'غير متاح'

export function formatMoneyOrNA(
  amount: Halalas | null | undefined,
  currency: Currency = 'SAR',
  options: FormatOptions = {},
): string {
  if (amount === null || amount === undefined) return NOT_AVAILABLE
  return formatMoney(amount, currency, options)
}

/**
 * معدل الادخار كنسبة مئوية بخانة عشرية واحدة، أو null لو الدخل صفر.
 * spec/06: «دخل0 → الادخار غير متاح؛ لا Infinity/NaN». والسالب لا يُخفى.
 */
export function savingsRatePercent(incomeMinor: Halalas, remainingMinor: Halalas): number | null {
  assertHalalas(incomeMinor, 'الدخل')
  assertHalalas(remainingMinor, 'المتبقي')
  if (incomeMinor === 0) return null
  // بالعُشر من المئة ثم قسمة على 10 عند العرض فقط
  const tenths = Math.round((remainingMinor * 1000) / incomeMinor)
  return tenths / 10
}

export function formatPercentOrNA(percent: number | null): string {
  if (percent === null || !Number.isFinite(percent)) return NOT_AVAILABLE
  const sign = percent < 0 ? '-' : ''
  const abs = Math.abs(percent)
  return `${sign}${abs.toFixed(1)}%`
}
