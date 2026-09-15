import { summarizeCash, type CashSummary } from '../../domain/cashSummary'
import { withEstimatedKinds } from '../../domain/estimatedKinds'
import type { Period } from '../../domain/period'
import type { AllocationRepository, CategoryRepository, TransactionRepository, WalletRepository } from '../ports/repositories'

/**
 * LoadCashSummary — كارت الكاش في الرئيسية وتفاصيله (OVERRIDES §32، رد المالك «الاتنين»).
 * `null` = الحساب مالوش محفظة كاش (مش «صفر»، قاعدة 10).
 *
 * العمليات بتتحسب **بنفس الأنواع التقديرية** اللي بيستعملها رقم «ما تم صرفه» (OVERRIDES §18) —
 * من غيرها عملية كاش نوعها لسه ما اتحددش كانت بتطلع «صرفت كاش 0» وهي داخلة في الرقم الكبير
 * (اتشاف على المحاكي 1.34).
 *
 * ⚠️ تكلفة القراءة: استعلام واحد من أقدم تاريخ (بداية الكاش أو بداية الفترة) لحد آخر الفترة أو النهارده.
 */
export function makeLoadCashSummary(deps: {
  wallets: WalletRepository
  txns: TransactionRepository
  allocations: AllocationRepository
  categories: CategoryRepository
}) {
  return async function load({ period, today }: { period: Period; today: string }): Promise<CashSummary | null> {
    const wallet = (await deps.wallets.listAll()).find((w) => w.kind === 'cash') ?? null
    if (!wallet) return null
    const from = wallet.openingAt < period.start ? wallet.openingAt : period.start
    const to = today > period.end ? today : period.end
    const [rows, categories] = await Promise.all([deps.txns.listByDateRange(from, to), deps.categories.listAll()])
    const transactions = withEstimatedKinds(rows, new Map(categories.map((c) => [c.id, c.name]))).transactions
    const inPeriod = transactions.filter((t) => t.occurredAt >= period.start && t.occurredAt <= period.end)
    const allocations = await deps.allocations.listByTransactionIds(inPeriod.map((t) => t.id))
    return summarizeCash({ wallet, transactions, allocations, period })
  }
}
