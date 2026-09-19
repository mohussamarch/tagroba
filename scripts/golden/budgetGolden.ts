import { averageCompletedSpend, budgetStatus, buildCategoryLines, detectAnomaly, type CompletedPeriodSpend } from '../../src/domain/budget'
import { assessCoverage, categoryDistribution, dailyAllowance, forecastPeriodSpend } from '../../src/domain/analytics'
import { relativeTenths } from '../../src/domain/periodBars'
import { buildPeriod } from '../../src/domain/period'
import { ALL_ECONOMIC_KINDS } from '../../src/domain/entities/economicKind'
import type { PersonAllocation, Transaction } from '../../src/domain/entities/types'
import { record, seeded } from './goldenKit'

/** الميزانية والمتوسط والشذوذ والتحليل — أرقام وهمية. */
export function budgetGolden() {
  const rnd = seeded(31)
  const periods = [buildPeriod(2026, 8), buildPeriod(2026, 1, 31), buildPeriod(2028, 1, 30)]
  const days = ['2026-08-27', '2026-08-28', '2026-08-30', '2026-09-01', '2026-09-02', '2026-09-20', '2026-09-27', '2026-09-28', '2026-01-31', '2026-02-04', '2028-02-15']
  const history = () => Array.from({ length: rnd.int(0, 6) }, () => rnd.pick([0, rnd.int(1, 100_000), 50_000, 50_000]))

  const txn = (i: number): Transaction => {
    const t: Transaction = {
      id: `t-${i}`, occurredAt: '2026-09-10', datePrecision: 'day', sourceOrder: i, economicKind: rnd.pick(ALL_ECONOMIC_KINDS),
      economicKindConfirmed: true, observedDirection: 'out', amountMinor: rnd.int(1, 200_000), currency: 'SAR', categoryConfirmed: false,
      excludedFromBudget: rnd.next() < 0.15, reviewState: 'suggested', isCashTagged: false, createdAt: 'x', updatedAt: 'x',
    }
    if (rnd.next() < 0.8) t.categoryId = rnd.pick(['food', 'fuel', 'rent', 'fun'])
    return t
  }
  const sets = Array.from({ length: 30 }, (_, s) => {
    const transactions = Array.from({ length: rnd.int(0, 20) }, (_, i) => txn(s * 100 + i))
    const allocations: PersonAllocation[] = transactions.filter(() => rnd.next() < 0.2).map((t, i) => ({
      id: `a-${i}`, transactionId: t.id, personId: 'p', allocationKind: rnd.next() < 0.5 ? 'receivable' : 'gift', amountMinor: rnd.int(0, t.amountMinor), currency: 'SAR',
    }))
    return { transactions, allocations }
  })

  return {
    budgetStatus: [[100_000, 0, null], [100_000, 90_000, 80], [100_000, 89_999, 90], [100_000, 100_000, null], [100_000, 150_000, 100], [3, 1, 33], [100_000, -500, 50], [0, 5, null], [-5, 5, null]]
      .concat(Array.from({ length: 40 }, () => [rnd.int(1, 500_000), rnd.int(0, 600_000), rnd.pick([null, 50, 80, 90, 100])]))
      .map(([limit, spent, threshold]) => record({ limit, spent, threshold }, () => budgetStatus(limit as number, spent as number, threshold as number | null))),
    averageCompletedSpend: Array.from({ length: 30 }, (_, s) => Array.from({ length: rnd.int(0, 6) }, (_, i): CompletedPeriodSpend => ({
      periodKey: `2026-0${i + 1}`, spentMinor: rnd.int(0, 900_000), reliable: rnd.next() < 0.8, transactionCount: s % 7 === 0 && i === 0 ? 0 : rnd.int(0, 40),
    }))).map((list) => record(list, () => averageCompletedSpend(list))),
    detectAnomaly: Array.from({ length: 80 }, () => [rnd.pick([0, rnd.int(0, 200_000), 50_000, 70_000, 10_000]), history()] as const)
      .concat([[60_000, [50_000, 50_000, 50_000]], [50_000, [0, 0, 0]], [1, [2, 3, 4, 5]], [100, [100, 101, 99, 100]], [205, [100, 100, 100]], [195, [100, 100, 100, 100]]])
      .map(([value, hist]) => record({ value, history: hist }, () => detectAnomaly(value, hist))),
    buildCategoryLines: Array.from({ length: 20 }, () => {
      const cats = ['food', 'fuel', 'rent', 'fun', 'gifts']
      const spend = Object.fromEntries(cats.filter(() => rnd.next() < 0.7).map((c) => [c, rnd.pick([0, rnd.int(1, 300_000)])]))
      const limits = Object.fromEntries(cats.filter(() => rnd.next() < 0.4).map((c) => [c, { limitMinor: rnd.int(1, 300_000), thresholdPercent: rnd.pick([null, 80]) }]))
      const averages = Object.fromEntries(cats.filter(() => rnd.next() < 0.5).map((c) => [c, rnd.pick([null, rnd.int(1, 300_000)])]))
      const histories = Object.fromEntries(cats.map((c) => [c, history()]))
      return { spend, limits, averages, histories }
    }).map((c) => record(c, () => buildCategoryLines(new Map(Object.entries(c.spend)), new Map(Object.entries(c.limits)), new Map(Object.entries(c.averages)), new Map(Object.entries(c.histories))))),
    relativeTenths: [[], [null], [0, 0], [100, 50, null, 25], [-5, 10, 3], [1, 3]].concat(Array.from({ length: 10 }, () => Array.from({ length: 6 }, () => rnd.pick([null, 0, rnd.int(1, 900_000)]))))
      .map((values) => record(values, () => relativeTenths(values))),
    categoryDistribution: sets.map((s) => record(s, () => categoryDistribution(s.transactions, s.allocations))),
    dailyAllowance: periods.flatMap((period) => days.flatMap((today) => [[null, 0, null], [null, 0, 5_000_00], [null, 0, -100], [100_000, 30_000, null], [100_000, 150_000, null]]
      .map(([limit, spent, remaining]) => record({ limit, spent, today, period, remaining }, () => dailyAllowance(limit as number | null, spent as number, today, period, remaining as number | null))))),
    forecastPeriodSpend: periods.flatMap((period) => days.map((today) => { const spent = rnd.int(0, 900_000); return record({ spent, today, period }, () => forecastPeriodSpend(spent, today, period)) })),
    assessCoverage: sets.slice(0, 15).map((s) => record(s.transactions, () => assessCoverage(s.transactions))).concat([record([], () => assessCoverage([]))]),
  }
}
