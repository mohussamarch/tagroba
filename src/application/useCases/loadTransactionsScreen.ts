import { computePeriodTotals } from '../../domain/ledger'
import { savingsRatePercent } from '../../domain/formatMoney'
import { buildPeriod, formatPeriodRange, periodForDate, type Period } from '../../domain/period'
import type { Category, Transaction } from '../../domain/entities/types'
import type {
  AllocationRepository,
  CategoryRepository,
  TransactionRepository,
} from '../ports/repositories'

/**
 * LoadTransactionsScreen — تجهيز كل ما تحتاجه شاشة العمليات.
 *
 * وجودها هنا هو ما يحقق قاعدة ARCHITECTURE.md §3:
 * الشاشة لا تكلّم مستودعًا ولا تحسب مبلغًا. كل الحساب هنا وفي domain/.
 *
 * القراءة **محدودة بفترة** — ARCHITECTURE.md §5.6.
 */

export interface TransactionsScreenData {
  period: Period
  periodRange: string
  transactions: Transaction[]
  categories: Category[]

  /**
   * المبالغ **قابلة لأن تكون null**، وهذا مقصود.
   *
   * CLAUDE.md #10: «لا رقم بلا مصدر. لا صفر مؤكد مكان المجهول.»
   *
   * العملية المستوردة تبدأ بنوع اقتصادي غير محدد لأن spec/02 يمنع استنتاجه
   * من اتجاه السيولة. فلو كانت كل عمليات الفترة غير محددة النوع، فالدخل
   * والمصروف **مجهولان لا صفران** — عرض 0.00 وقتها كذب على المستخدم.
   */
  incomeMinor: number | null
  expenseMinor: number | null
  remainingMinor: number | null
  savingsRatePercent: number | null

  /** عدد العمليات التي لم يُحدَّد نوعها الاقتصادي بعد. */
  unclassifiedCount: number
  /** عدد عمليات الفترة كلها. */
  totalCount: number
}

export interface LoadTransactionsScreenDeps {
  txns: TransactionRepository
  categories: CategoryRepository
  allocations: AllocationRepository
}

export function makeLoadTransactionsScreen(deps: LoadTransactionsScreenDeps) {
  return async function load(options: {
    period?: Period
    today?: string
    payday?: number
  }): Promise<TransactionsScreenData> {
    const period =
      options.period ??
      (options.today
        ? periodForDate(options.today, options.payday)
        : buildPeriod(new Date().getUTCFullYear(), new Date().getUTCMonth() + 1, options.payday))

    const [transactions, categories] = await Promise.all([
      deps.txns.listByDateRange(period.start, period.end),
      deps.categories.listAll(),
    ])

    const allocations = await deps.allocations.listByTransactionIds(transactions.map((t) => t.id))
    const totals = computePeriodTotals(transactions, allocations)

    const unclassifiedCount = transactions.filter((t) => t.economicKind === 'unclassified').length
    const totalCount = transactions.length

    // فيه عمليات، لكن ولا واحدة محددة النوع ⇒ المجاميع مجهولة لا صفر
    const allUnknown = totalCount > 0 && unclassifiedCount === totalCount

    return {
      period,
      periodRange: formatPeriodRange(period),
      transactions,
      categories,
      incomeMinor: allUnknown ? null : totals.incomeMinor,
      expenseMinor: allUnknown ? null : totals.personalExpenseMinor,
      remainingMinor: allUnknown ? null : totals.remainingMinor,
      savingsRatePercent: allUnknown
        ? null
        : savingsRatePercent(totals.incomeMinor, totals.remainingMinor),
      unclassifiedCount,
      totalCount,
    }
  }
}
