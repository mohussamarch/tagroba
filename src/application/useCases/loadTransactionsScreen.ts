import { merchantIndex } from '../../domain/merchantIndex'
import { normalizeText } from '../../domain/normalize'
import { computePeriodTotals } from '../../domain/ledger'
import { withEstimatedKinds } from '../../domain/estimatedKinds'
import { savingsRatePercent } from '../../domain/formatMoney'
import { buildPeriod, formatPeriodRange, periodForDate, type Period } from '../../domain/period'
import type { Category, Transaction } from '../../domain/entities/types'
import type {
  AllocationRepository,
  CategoryRepository,
  MerchantRepository,
  TagRepository,
  TransactionTagRepository,
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
  tagNamesByTransaction: Record<string,string[]>
  merchantNamesByTransaction: Record<string,string[]>

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
  /** كام عملية اتحسبت بنوع تقديري (OVERRIDES §18). */
  estimatedCount: number
  /** منها: كام عملية التطبيق مش متأكد منها — محتاجة تأكيد. */
  needsReviewCount: number
  /** عدد عمليات الفترة كلها. */
  totalCount: number
}

export interface LoadTransactionsScreenDeps {
  txns: TransactionRepository
  categories: CategoryRepository
  allocations: AllocationRepository
  merchants?: MerchantRepository
  tags?: TagRepository
  transactionTags?: TransactionTagRepository
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

    const ids = transactions.map(t=>t.id)
    const [allocations,tags,links] = await Promise.all([
      deps.allocations.listByTransactionIds(ids),deps.tags?.listAll()??[],deps.transactionTags?.listByTransactionIds(ids)??[]])
    const tagNames = new Map(tags.map(t=>[t.id,t.displayName]))
    const tagNamesByTransaction: Record<string,string[]> = {}
    for (const link of links) {
      const name = tagNames.get(link.tagId)
      if (name) tagNamesByTransaction[link.transactionId] = [...new Set([...(tagNamesByTransaction[link.transactionId]??[]),name])]
    }
    // المجاميع بالأنواع التقديرية للواضح (OVERRIDES §18)؛ القائمة المعروضة تفضل بالعمليات الأصلية
    const estimated = withEstimatedKinds(transactions, new Map(categories.map((c) => [c.id, c.name])))
    const totals = computePeriodTotals(estimated.transactions, allocations)

    const merchantMap=merchantIndex(await deps.merchants?.listAll()??[])
    const merchantNamesByTransaction:Record<string,string[]>={}
    for(const t of transactions) {
      const m=merchantMap.get(normalizeText(t.rawMerchantName??''))
      if(m) merchantNamesByTransaction[t.id]=[m.displayName,...(m.aliases??[])]
    }
    // «محتاجة تحديد نوع» = اللي التطبيق مش متأكد منها؛ المجاميع نفسها متاحة دايمًا (OVERRIDES §18)
    const unclassifiedCount = estimated.needsReviewCount
    const totalCount = transactions.length

    // فيه عمليات، لكن ولا واحدة محددة النوع ⇒ المجاميع مجهولة لا صفر
    const allUnknown = totalCount > 0 && unclassifiedCount === totalCount

    return {
      period,
      periodRange: formatPeriodRange(period),
      tagNamesByTransaction,
      merchantNamesByTransaction,
      transactions,
      categories,
      incomeMinor: allUnknown ? null : totals.incomeMinor,
      expenseMinor: allUnknown ? null : totals.personalExpenseMinor,
      remainingMinor: allUnknown ? null : totals.remainingMinor,
      savingsRatePercent: allUnknown
        ? null
        : savingsRatePercent(totals.incomeMinor, totals.remainingMinor),
      unclassifiedCount,
      estimatedCount: estimated.estimatedCount,
      needsReviewCount: estimated.needsReviewCount,
      totalCount,
    }
  }
}
