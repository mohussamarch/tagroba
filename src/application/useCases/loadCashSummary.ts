import { summarizeCash, type CashSummary } from '../../domain/cashSummary'
import type { Period } from '../../domain/period'
import type { AllocationRepository, TransactionRepository, WalletRepository } from '../ports/repositories'

/**
 * LoadCashSummary — كارت الكاش في الرئيسية وتفاصيله (OVERRIDES §32، رد المالك «الاتنين»).
 * `null` = الحساب مالوش محفظة كاش (مش «صفر»، قاعدة 10).
 *
 * ⚠️ تكلفة القراءة: استعلام واحد من أقدم تاريخ (بداية الكاش أو بداية الفترة) لحد آخر الفترة أو النهارده.
 * محفظة كاش بدايتها قديمة جدًا معناها قراءة أكتر — مقبول لأن رصيد البداية بيتحدث من أسئلة البداية والإعدادات.
 */
export function makeLoadCashSummary(deps: {
  wallets: WalletRepository
  txns: TransactionRepository
  allocations: AllocationRepository
}) {
  return async function load({ period, today }: { period: Period; today: string }): Promise<CashSummary | null> {
    const wallet = (await deps.wallets.listAll()).find((w) => w.kind === 'cash') ?? null
    if (!wallet) return null
    const from = wallet.openingAt < period.start ? wallet.openingAt : period.start
    const to = today > period.end ? today : period.end
    const transactions = await deps.txns.listByDateRange(from, to)
    const inPeriod = transactions.filter((t) => t.occurredAt >= period.start && t.occurredAt <= period.end)
    const allocations = await deps.allocations.listByTransactionIds(inPeriod.map((t) => t.id))
    return summarizeCash({ wallet, transactions, allocations, period })
  }
}
