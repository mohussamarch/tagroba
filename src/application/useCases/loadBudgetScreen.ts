import {
  averageCompletedSpend,
  budgetStatus,
  buildCategoryLines,
  detectAnomaly,
  type AverageResult,
  type BudgetStatus,
  type CategoryBudgetLine,
  type CompletedPeriodSpend,
} from '../../domain/budget'
import { categoryDistribution, assessCoverage } from '../../domain/analytics'
import { computePeriodTotals } from '../../domain/ledger'
import { dailyAllowance, type DailyAllowance } from '../../domain/analytics'
import { buildPeriod, type Period } from '../../domain/period'
import type { Halalas } from '../../domain/money'
import { withEstimatedKinds } from '../../domain/estimatedKinds'
import type { Budget, Category, CategoryBudget, Id } from '../../domain/entities/types'
import type {
  AllocationRepository,
  BudgetRepository,
  CategoryRepository,
  TransactionRepository,
} from '../ports/repositories'

/**
 * LoadBudgetScreen — spec/01.
 *
 * «سقف إجمالي وسقوف اختيارية للتصنيفات، و**متوسط منفصل من فترات مكتملة**،
 *  وعلامة شذوذ، وحالة دون تاريخ أو سقف.
 *  **لا تُخلق ميزانيات من متوسطات دون اختيار المستخدم.**»
 *
 * «منفصل» تُنفَّذ حرفيًا: `averageMinor` و `totalStatus` حقلان مختلفان،
 * ولا يُستعمل الأول قيمةً افتراضية للثاني في أي مسار.
 */

/** كم فترة سابقة تُقرأ لحساب المتوسط والشذوذ. */
const HISTORY_PERIODS = 6

export interface BudgetScreenData {
  period: Period
  budget: Budget | null
  /** null = المستخدم لم يضع سقفًا إجماليًا. */
  totalStatus: BudgetStatus | null
  spentMinor: Halalas
  /** موثوقية مصروف الفترة الجارية. */
  spentReliable: boolean
  spentNote: string | null
  /**
   * هل المصروف **معروف أصلًا**؟
   *
   * `false` حين لا تكون ولا عملية محددة النوع: وقتها `spentMinor` صفر
   * حسابيًا لكنه **مجهول** لا صفر. عرض شريط «0 من 3000» وقتها راحة
   * كاذبة، وعرض «المصروف 0.00» كذب صريح (CLAUDE.md #10).
   */
  spentKnown: boolean

  /** **معلومة تُعرض، لا سقف يُطبَّق.** */
  average: AverageResult
  anomaly: ReturnType<typeof detectAnomaly>
  allowance: DailyAllowance

  categories: Category[]
  lines: CategoryBudgetLine[]
  categoryBudgets: CategoryBudget[]
}

export interface LoadBudgetScreenDeps {
  txns: TransactionRepository
  categories: CategoryRepository
  allocations: AllocationRepository
  budgets: BudgetRepository
}

function shiftPeriod(period: Period, delta: number, payday: number): Period {
  const [year, month] = period.key.split('-').map(Number)
  const total = year * 12 + (month - 1) + delta
  return buildPeriod(Math.floor(total / 12), (total % 12) + 1, payday)
}

export function makeLoadBudgetScreen(deps: LoadBudgetScreenDeps) {
  return async function load(options: {
    period: Period
    today: string
    payday: number
  }): Promise<BudgetScreenData> {
    const { period, today, payday } = options

    const [transactions, categories, budget] = await Promise.all([
      deps.txns.listByDateRange(period.start, period.end),
      deps.categories.listAll(),
      deps.budgets.findByPeriod(period.key),
    ])
    const allocations = await deps.allocations.listByTransactionIds(transactions.map((t) => t.id))
    // الواضح يتحسب بنوعه المقترح، بنفس قاعدة الرئيسية (OVERRIDES §18)
    const names = new Map(categories.map((c) => [c.id, c.name]))
    const estimated = withEstimatedKinds(transactions, names)
    const counted = estimated.transactions

    const totals = computePeriodTotals(counted, allocations)
    const coverage = assessCoverage(counted)
    const spentMinor = totals.personalExpenseMinor
    // مجهول لا صفر: فيه عمليات وولا واحدة محددة النوع
    const spentKnown = !(coverage.total > 0 && coverage.unclassified === coverage.total)

    const categoryBudgets = budget ? await deps.budgets.listCategoryBudgets(budget.id) : []

    // ─── تاريخ الفترات السابقة: للمتوسط والشذوذ فقط ───
    const history: CompletedPeriodSpend[] = []
    const historyByCategory = new Map<Id, Halalas[]>()

    const historyRows = await Promise.all(Array.from({ length: HISTORY_PERIODS }, async (_, index) => {
      const i = index + 1
      const p = shiftPeriod(period, -i, payday)
      const rows = await deps.txns.listByDateRange(p.start, p.end)
      const rowAllocations = await deps.allocations.listByTransactionIds(rows.map((r) => r.id))
      return { p, rows, rowAllocations }
    }))
    for (const { p, rows: rawRows, rowAllocations } of historyRows) {
      const rows = withEstimatedKinds(rawRows, names).transactions
      const t = computePeriodTotals(rows, rowAllocations)
      const c = assessCoverage(rows)

      history.push({
        periodKey: p.key,
        spentMinor: t.personalExpenseMinor,
        // فترة فيها عملية واحدة غير محددة **ليست موثوقة**
        reliable: c.unclassified === 0,
        transactionCount: rows.length,
      })

      // تاريخ كل تصنيف — من الفترات الموثوقة فقط
      if (c.unclassified === 0 && rows.length > 0) {
        const { slices } = categoryDistribution(rows, rowAllocations)
        for (const slice of slices) {
          if (slice.categoryId === null) continue
          const list = historyByCategory.get(slice.categoryId) ?? []
          list.push(slice.amountMinor)
          historyByCategory.set(slice.categoryId, list)
        }
      }
    }

    const average = averageCompletedSpend(history)

    // متوسط كل تصنيف — من تاريخه هو
    const averageByCategory = new Map<Id, Halalas | null>()
    for (const [categoryId, values] of historyByCategory) {
      averageByCategory.set(
        categoryId,
        averageCompletedSpend(
          values.map((v, i) => ({
            periodKey: `${categoryId}-${i}`,
            spentMinor: v,
            reliable: true,
            transactionCount: 1,
          })),
        ).averageMinor,
      )
    }

    const { slices } = categoryDistribution(counted, allocations)
    const spendByCategory = new Map<Id, Halalas>()
    for (const slice of slices) {
      if (slice.categoryId !== null) spendByCategory.set(slice.categoryId, slice.amountMinor)
    }

    const limitByCategory = new Map(
      categoryBudgets.map((cb) => [
        cb.categoryId,
        { limitMinor: cb.limitMinor, thresholdPercent: cb.notifyEnabled ? cb.thresholdPercent : null },
      ]),
    )

    const totalLimit = budget?.totalLimitMinor ?? null

    return {
      period,
      budget,
      // بلا مصروف معروف لا تُبنى حالة سقف: «0 من 3000» راحة كاذبة
      totalStatus:
        totalLimit === null || !spentKnown
          ? null
          : budgetStatus(totalLimit, spentMinor, budget!.thresholdPercent),
      spentMinor,
      spentReliable: coverage.totalsReliable && estimated.estimatedCount === 0,
      spentNote: estimated.needsReviewCount > 0
        ? `تقريبي: ${estimated.needsReviewCount} عملية محتاجة تأكيد نوعها، والرقم هيتظبط لما تأكدها.`
        : estimated.estimatedCount > 0
          ? `تقريبي: ${estimated.estimatedCount} عملية نوعها اتحدد تلقائي.`
          : coverage.note,
      spentKnown,
      average,
      anomaly: detectAnomaly(
        spentMinor,
        history.filter((h) => h.reliable && h.transactionCount > 0).map((h) => h.spentMinor),
      ),
      // ⚠️ المتاح اليومي يستعمل **السقف** لا المتوسط. لو غاب السقف يبقى غير متاح
      allowance: dailyAllowance(spentKnown ? totalLimit : null, spentMinor, today, period),
      categories,
      lines: buildCategoryLines(spendByCategory, limitByCategory, averageByCategory, historyByCategory),
      categoryBudgets,
    }
  }
}
