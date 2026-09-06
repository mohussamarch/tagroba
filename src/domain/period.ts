import type { IsoDate } from './entities/types'

/**
 * الشهر المالي — spec/02-accounting.md.
 *
 * «بداية الشهر المالي هي يوم الراتب داخل الشهر الذي اختاره المستخدم،
 *  ونهايته بداية الفترة التالية باستثناء تلك اللحظة.
 *  اليوم 29–31 يُقيد بآخر يوم من الشهر.»
 *
 * كل الحساب هنا بأعداد صحيحة على التقويم الميلادي، بلا Date ولا منطقة زمنية،
 * فلا تتأثر النتيجة بجهاز المستخدم ولا بالتوقيت الصيفي.
 * يوم الراتب المعتمد: 28 (CLAUDE.md).
 */

export const DEFAULT_PAYDAY = 28

export interface Period {
  /** مفتاح ثابت للفترة: "2026-09" يعني الفترة التي تبدأ في سبتمبر 2026. */
  key: string
  /** أول يوم داخل الفترة (شامل). */
  start: IsoDate
  /** آخر يوم داخل الفترة (شامل) — لا لحظة البداية التالية. */
  end: IsoDate
  /** عدد الأيام في الفترة. يُحسب ولا يُفترض 30. */
  days: number
}

export class PeriodError extends Error {}

export function isLeapYear(year: number): boolean {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0
}

const MONTH_LENGTHS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

/** عدد أيام الشهر. month من 1 إلى 12. */
export function daysInMonth(year: number, month: number): number {
  if (!Number.isInteger(month) || month < 1 || month > 12) {
    throw new PeriodError(`شهر غير صالح: ${month}`)
  }
  if (month === 2 && isLeapYear(year)) return 29
  return MONTH_LENGTHS[month - 1]
}

const DATE_RE = /^(\d{4})-(\d{2})-(\d{2})$/

export interface DateParts {
  year: number
  month: number
  day: number
}

/** يحلّل YYYY-MM-DD ويرفض التواريخ المستحيلة صراحة — «لا تاريخ استحالته مكتومة» (spec/05). */
export function parseIsoDate(value: string): DateParts {
  const m = DATE_RE.exec(value)
  if (!m) throw new PeriodError(`صيغة التاريخ غير صالحة: ${value}`)
  const year = Number(m[1])
  const month = Number(m[2])
  const day = Number(m[3])
  if (month < 1 || month > 12) throw new PeriodError(`شهر غير صالح في: ${value}`)
  const max = daysInMonth(year, month)
  if (day < 1 || day > max) {
    throw new PeriodError(`يوم غير موجود: ${value} (${month} فيه ${max} يوم)`)
  }
  return { year, month, day }
}

export function isValidIsoDate(value: string): boolean {
  try {
    parseIsoDate(value)
    return true
  } catch {
    return false
  }
}

export function formatIsoDate(parts: DateParts): IsoDate {
  const mm = String(parts.month).padStart(2, '0')
  const dd = String(parts.day).padStart(2, '0')
  return `${parts.year}-${mm}-${dd}`
}

/** رقم اليوم المتسلسل منذ 1970-01-01 — لحساب فروق الأيام بأعداد صحيحة. */
export function toDayNumber(parts: DateParts): number {
  const { year, month, day } = parts
  // خوارزمية Howard Hinnant لتحويل التاريخ الميلادي إلى رقم يوم
  const y = month <= 2 ? year - 1 : year
  const era = Math.floor(y / 400)
  const yoe = y - era * 400
  const mp = (month + 9) % 12
  const doy = Math.floor((153 * mp + 2) / 5) + day - 1
  const doe = yoe * 365 + Math.floor(yoe / 4) - Math.floor(yoe / 100) + doy
  return era * 146097 + doe - 719468
}

export function daysBetween(from: IsoDate, to: IsoDate): number {
  return toDayNumber(parseIsoDate(to)) - toDayNumber(parseIsoDate(from))
}

/**
 * يوم بداية الفترة داخل شهر معيّن، بعد تقييد 29–31 بآخر يوم.
 * فبراير 2028 ويوم راتب 31 ⇒ 29 فبراير، لا «31 فبراير».
 */
export function clampPaydayToMonth(year: number, month: number, payday: number): number {
  if (!Number.isInteger(payday) || payday < 1 || payday > 31) {
    throw new PeriodError(`يوم الراتب لازم يكون بين 1 و31، مش ${payday}`)
  }
  return Math.min(payday, daysInMonth(year, month))
}

function addMonth(year: number, month: number, delta: number): { year: number; month: number } {
  const total = year * 12 + (month - 1) + delta
  return { year: Math.floor(total / 12), month: (total % 12) + 1 }
}

/**
 * يبني الفترة المالية التي مفتاحها YYYY-MM (شهر البداية).
 * البداية = يوم الراتب في ذلك الشهر (مقيَّدًا)،
 * والنهاية = اليوم السابق ليوم الراتب في الشهر التالي (مقيَّدًا).
 */
export function buildPeriod(year: number, month: number, payday = DEFAULT_PAYDAY): Period {
  const startDay = clampPaydayToMonth(year, month, payday)
  const next = addMonth(year, month, 1)
  const nextStartDay = clampPaydayToMonth(next.year, next.month, payday)

  const start = formatIsoDate({ year, month, day: startDay })
  const nextStart = formatIsoDate({ year: next.year, month: next.month, day: nextStartDay })

  const endDayNumber = toDayNumber(parseIsoDate(nextStart)) - 1
  const end = dayNumberToIso(endDayNumber)

  return {
    key: `${year}-${String(month).padStart(2, '0')}`,
    start,
    end,
    days: endDayNumber - toDayNumber(parseIsoDate(start)) + 1,
  }
}

/** عكس toDayNumber. */
export function dayNumberToIso(dayNumber: number): IsoDate {
  const z = dayNumber + 719468
  const era = Math.floor(z / 146097)
  const doe = z - era * 146097
  const yoe = Math.floor((doe - Math.floor(doe / 1460) + Math.floor(doe / 36524) - Math.floor(doe / 146096)) / 365)
  const y = yoe + era * 400
  const doy = doe - (365 * yoe + Math.floor(yoe / 4) - Math.floor(yoe / 100))
  const mp = Math.floor((5 * doy + 2) / 153)
  const day = doy - Math.floor((153 * mp + 2) / 5) + 1
  const month = mp < 10 ? mp + 3 : mp - 9
  const year = month <= 2 ? y + 1 : y
  return formatIsoDate({ year, month, day })
}

/** الفترة المالية التي يقع فيها تاريخ معيّن. */
export function periodForDate(date: IsoDate, payday = DEFAULT_PAYDAY): Period {
  const { year, month, day } = parseIsoDate(date)
  const startThisMonth = clampPaydayToMonth(year, month, payday)
  // قبل يوم الراتب ⇒ ما زلنا في فترة الشهر السابق
  if (day < startThisMonth) {
    const prev = addMonth(year, month, -1)
    return buildPeriod(prev.year, prev.month, payday)
  }
  return buildPeriod(year, month, payday)
}

export function isDateInPeriod(date: IsoDate, period: Period): boolean {
  const d = toDayNumber(parseIsoDate(date))
  return d >= toDayNumber(parseIsoDate(period.start)) && d <= toDayNumber(parseIsoDate(period.end))
}

/**
 * الأيام المتبقية في الفترة ابتداءً من `today` (شاملة اليوم نفسه).
 * تُستخدم في «المتاح اليومي» — spec/02 يمنع استخدام 30 يومًا ثابتة.
 * ترجع 0 لو التاريخ بعد نهاية الفترة.
 */
export function remainingDaysInPeriod(today: IsoDate, period: Period): number {
  const t = toDayNumber(parseIsoDate(today))
  const end = toDayNumber(parseIsoDate(period.end))
  const start = toDayNumber(parseIsoDate(period.start))
  if (t > end) return 0
  if (t < start) return period.days
  return end - t + 1
}

/** نطاق الفترة كنص للعرض — spec/04: «اعرض نطاق التاريخ كاملًا». */
export function formatPeriodRange(period: Period): string {
  return `${period.start} ← ${period.end}`
}
