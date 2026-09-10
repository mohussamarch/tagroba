import { computePeriodTotals } from '../../domain/ledger'
import { savingsRatePercent } from '../../domain/formatMoney'
import {
  assessCoverage,
  categoryDistribution,
  dailyAllowance,
  forecastPeriodSpend,
  type CategorySlice,
  type DailyAllowance,
  type DataCoverage,
  type Forecast,
} from '../../domain/analytics'
import { buildPeriod, formatPeriodRange, parseIsoDate, type Period } from '../../domain/period'
import type { Halalas } from '../../domain/money'
import type { Category, Transaction } from '../../domain/entities/types'
import type {
  AllocationRepository,
  BudgetRepository,
  CategoryRepository,
  TransactionRepository,
} from '../ports/repositories'

/**
 * LoadHomeScreen — الشاشة الرئيسية (spec/01).
 *
 * «الفترة المختارة؛ المصروف الشخصي؛ خانات الدخل والمتبقي والادخار؛
 *  المتاح من الميزانية يوميًا؛ توقع نهاية الفترة بعد تاريخ كافٍ؛
 *  توزيع التصنيفات؛ أحدث العمليات؛ آخر ست فترات.»
 *
 * كل رقم هنا إما محسوب أو `null` تُعرض «غير متاح» — CLAUDE.md #10.
 */

/** ملخص فترة سابقة في شريط «آخر ست فترات». */
export interface PeriodSummary {
  period: Period
  expenseMinor: Halalas | null
  incomeMinor: Halalas | null
  transactionCount: number
}

export interface HomeScreenData {
  period: Period
  periodRange: string

  /** المصروف الشخصي — الرقم الأبرز في الشاشة. */
  expenseMinor: Halalas | null
  incomeMinor: Halalas | null
  remainingMinor: Halalas | null
  savingsRatePercent: number | null
  /** المستبعد من الميزانية — يُعرض منفصلًا ولا يُخفى (spec/02). */
  excludedExpenseMinor: Halalas | null
  /**
   * الدخل والمصروف محسوبان من العمليات المحددة فقط، وفيه عمليات
   * لم تُحدَّد بعد. الرقم صحيح «لحد دلوقتي» لا نهائي، ولازم يُقال ذلك.
   */
  partial: boolean

  distribution: CategorySlice[]
  categories: Category[]
  /** أحدث العمليات — عدد محدود للعرض. */
  latest: Transaction[]
  transactionCount: number

  allowance: DailyAllowance
  forecast: Forecast
  coverage: DataCoverage

  /** آخر ست فترات، الأحدث أولًا. */
  recentPeriods: PeriodSummary[]
}

export interface LoadHomeScreenDeps {
  budgets?: BudgetRepository
  txns: TransactionRepository
  categories: CategoryRepository
  allocations: AllocationRepository
}

/** عدد الفترات المعروضة في الشريط السفلي — spec/01. */
const RECENT_PERIOD_COUNT = 6
const LATEST_COUNT = 5

function shiftPeriod(period: Period, delta: number, payday: number): Period {
  const [year, month] = period.key.split('-').map(Number)
  const total = year * 12 + (month - 1) + delta
  return buildPeriod(Math.floor(total / 12), (total % 12) + 1, payday)
}

export function makeLoadHomeScreen(deps: LoadHomeScreenDeps) {
  return async function load(options: {
    period: Period
    today: string
    payday: number
    /** سقف الميزانية للفترة، أو null لو لم يحدده المستخدم. */
    budgetLimitMinor?: Halalas | null
    includeHistory?: boolean
  }): Promise<HomeScreenData> {
    const { period, today, payday } = options


    const [transactions, categories, savedBudget] = await Promise.all([
      deps.txns.listByDateRange(period.start, period.end),
      deps.categories.listAll(),
      deps.budgets?.findByPeriod(period.key) ?? Promise.resolve(null),
    ])
    const budgetLimitMinor = deps.budgets ? savedBudget?.totalLimitMinor ?? null : options.budgetLimitMinor ?? null
    const allocations = await deps.allocations.listByTransactionIds(transactions.map((t) => t.id))

    const totals = computePeriodTotals(transactions, allocations)
    const coverage = assessCoverage(transactions)
    const { slices } = categoryDistribution(transactions, allocations)

    /*
     * ثلاث حالات لا اثنتان — CLAUDE.md #10:
     *
     * ١. ولا عملية محددة  ⇒ كل شيء «غير متاح»
     * ٢. بعضها محدد        ⇒ الدخل والمصروف **جزئيان صحيحان** يُعرضان
     *                        موسومين بـ `partial`، أما **المتبقي ومعدل
     *                        الادخار فـ null**: كل منهما يخلط مدخلين
     *                        ناقصين فينتج رقمًا مضلِّلًا لا ناقصًا.
     * ٣. كلها محددة        ⇒ كل شيء متاح
     *
     * الحالة ٢ اكتُشفت بالتشغيل: الشاشة عرضت «الدخل 0.00» و«المتبقي
     * −985.50» وتحتها راتب 7,000 لم يُحدَّد نوعه بعد — رقم يوحي بعجز
     * غير موجود.
     */
    const allUnknown = coverage.total > 0 && coverage.unclassified === coverage.total
    const partial = !allUnknown && coverage.unclassified > 0

    const expenseMinor = allUnknown ? null : totals.personalExpenseMinor
    const incomeMinor = allUnknown ? null : totals.incomeMinor
    const remainingMinor = allUnknown || partial ? null : totals.remainingMinor

    const latest = [...transactions]
      .sort((a, b) =>
        a.occurredAt === b.occurredAt
          ? b.sourceOrder - a.sourceOrder
          : a.occurredAt < b.occurredAt
            ? 1
            : -1,
      )
      .slice(0, LATEST_COUNT)

    // آخر ست فترات — كل واحدة استعلام محدود بمداها (ARCHITECTURE.md §5.6)
    const recentPeriods: PeriodSummary[] = options.includeHistory === false ? [] : await Promise.all(Array.from({ length: RECENT_PERIOD_COUNT }, async (_, i) => {
      const p = i === 0 ? period : shiftPeriod(period, -i, payday)
      const rows =
        i === 0 ? transactions : await deps.txns.listByDateRange(p.start, p.end)
      const rowAllocations =
        i === 0 ? allocations : await deps.allocations.listByTransactionIds(rows.map((r) => r.id))
      const t = computePeriodTotals(rows, rowAllocations)
      const c = assessCoverage(rows)
      const unknown = c.total > 0 && c.unclassified === c.total
      return {
        period: p,
        expenseMinor: unknown ? null : t.personalExpenseMinor,
        incomeMinor: unknown ? null : t.incomeMinor,
        transactionCount: rows.length,
      }
    }))

    // التأكد أن التاريخ صالح قبل استعماله في التوقع والمتاح اليومي
    parseIsoDate(today)

    return {
      period,
      periodRange: formatPeriodRange(period),
      expenseMinor,
      incomeMinor,
      remainingMinor,
      savingsRatePercent:
        allUnknown || partial
          ? null
          : savingsRatePercent(totals.incomeMinor, totals.remainingMinor),
      excludedExpenseMinor: allUnknown ? null : totals.excludedExpenseMinor,
      partial,
      distribution: slices,
      categories,
      latest,
      transactionCount: transactions.length,
      allowance: { ...dailyAllowance(budgetLimitMinor, totals.personalExpenseMinor, today, period),
        ...(allUnknown || partial ? { amountMinor:null, reason:'المصروف لسه ناقص تصنيف؛ حدّد أنواع العمليات قبل حساب المتاح اليومي.' } : {}) },
      forecast: { ...forecastPeriodSpend(totals.personalExpenseMinor, today, period),
        ...(allUnknown || partial ? { projectedMinor:null, caveat:'التوقع غير متاح لحد ما تحدّد أنواع العمليات؛ المصروف الحالي ناقص.' } : {}) },
      coverage,
      recentPeriods,
    }
  }
}
