import {
  buildPeriod, clampPaydayToMonth, dayNumberToIso, daysBetween, daysInMonth, formatPeriodRange, isDateInPeriod,
  isLeapYear, isValidIsoDate, parseIsoDate, periodForDate, periodIndex, remainingDaysInPeriod, shiftPeriodWithin, toDayNumber,
} from '../../src/domain/period'
import { record, seeded } from './goldenKit'

/** حالات `period.ts` — الشهر المالي من يوم الراتب (spec/02). */
export function periodGolden() {
  const rnd = seeded(28)
  const paydays = [1, 15, 28, 29, 30, 31]
  const dates: string[] = []
  for (let d = toDayNumber({ year: 2027, month: 1, day: 1 }); d <= toDayNumber({ year: 2028, month: 12, day: 31 }); d++) dates.push(dayNumberToIso(d))
  const dayNumbers = [0, -1, 1, 59, 60, 365, -719468, 2932896]
  for (let i = 0; i < 150; i++) dayNumbers.push(rnd.int(-800_000, 800_000))
  const isoInputs = [
    '2026-09-28', '2024-02-29', '2025-02-29', '2100-02-29', '2000-02-29', '2026-13-01', '2026-00-10', '2026-04-31', '2026-04-00',
    '2026-9-28', '26-09-28', '2026/09/28', ' 2026-09-28', '2026-09-28T00:00', '', '٢٠٢٦-٠٩-٢٨', '0000-01-01', '9999-12-31',
  ]
  const periods = [buildPeriod(2026, 8), buildPeriod(2026, 1, 31), buildPeriod(2028, 1, 30), buildPeriod(2025, 12, 1)]
  const pairs: [string, string][] = []
  for (let i = 0; i < 80; i++) pairs.push([rnd.pick(dates), rnd.pick(dates)])

  return {
    isLeapYear: [1900, 2000, 2024, 2025, 2028, 2100, 2400, 0].map((y) => record(y, () => isLeapYear(y))),
    daysInMonth: [[2024, 2], [2025, 2], [2026, 4], [2026, 12], [2026, 0], [2026, 13]].map(([y, m]) => record({ year: y, month: m }, () => daysInMonth(y!, m!))),
    parseIsoDate: isoInputs.map((s) => record(s, () => parseIsoDate(s))),
    isValidIsoDate: isoInputs.map((s) => record(s, () => isValidIsoDate(s))),
    toDayNumber: dates.filter((_, i) => i % 7 === 0).map((s) => record(parseIsoDate(s), () => toDayNumber(parseIsoDate(s)))),
    dayNumberToIso: dayNumbers.map((n) => record(n, () => dayNumberToIso(n))),
    daysBetween: pairs.map(([a, b]) => record({ from: a, to: b }, () => daysBetween(a, b))),
    clampPaydayToMonth: [[2028, 2, 31], [2027, 2, 29], [2026, 4, 31], [2026, 9, 28], [2026, 9, 0], [2026, 9, 32], [2026, 9, -1]].map(([y, m, p]) => record({ year: y, month: m, payday: p }, () => clampPaydayToMonth(y!, m!, p!))),
    buildPeriod: [2024, 2025, 2026, 2027, 2028].flatMap((y) => Array.from({ length: 12 }, (_, i) => i + 1).flatMap((m) => paydays.map((p) => record({ year: y, month: m, payday: p }, () => buildPeriod(y, m, p))))),
    periodForDate: [28, 31, 1].flatMap((p) => dates.map((d) => record({ date: d, payday: p }, () => periodForDate(d, p)))),
    isDateInPeriod: periods.flatMap((period) => ['2026-08-27', '2026-08-28', '2026-09-27', '2026-09-28', '2026-01-31', '2026-02-27', '2028-02-29', '2025-12-01', '2025-12-31', '2026-01-01'].map((d) => record({ date: d, period }, () => isDateInPeriod(d, period)))),
    remainingDaysInPeriod: periods.flatMap((period) => ['2026-08-20', '2026-08-28', '2026-09-10', '2026-09-27', '2026-10-01', '2028-02-15', '2025-12-31'].map((d) => record({ today: d, period }, () => remainingDaysInPeriod(d, period)))),
    formatPeriodRange: periods.map((period) => record(period, () => formatPeriodRange(period))),
    periodIndex: periods.map((period) => record(period, () => periodIndex(period))),
    shiftPeriodWithin: [-13, -1, 0, 1, 5].flatMap((delta) => [28, 31].map((p) => record({ period: buildPeriod(2026, 3, p), delta, latest: buildPeriod(2026, 8, p), payday: p }, () => shiftPeriodWithin(buildPeriod(2026, 3, p), delta, buildPeriod(2026, 8, p), p)))),
  }
}
