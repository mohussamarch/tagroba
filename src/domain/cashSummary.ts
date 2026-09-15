import { addMoney, subtractMoney, type Halalas } from './money'
import { countsAsPersonalExpense } from './entities/economicKind'
import { personalShareOf } from './ledger'
import type { IsoDate, PersonAllocation, Transaction, Wallet } from './entities/types'

/**
 * الكاش لوحده — OVERRIDES §32: «محتاج أشوف بشكل منفصل أنا معايا كام كاش وتقريبًا اتبقى منه كام…
 * ويكون في طريقة يعرف هو صرف كام من الكاش بس».
 *
 * **تقريبي بطبيعته:** الرصيد = رصيد بداية محفظة الكاش + الداخل − الخارج **المتسجل** من يوم البداية.
 * أي كاش اتصرف ومااتسجلش مش هيبان — والشاشة بتقول كده.
 */
export interface CashSummary {
  wallet: Pick<Wallet, 'id' | 'name' | 'openingBalanceMinor' | 'openingAt'>
  /** الرصيد التقريبي دلوقتي. */
  balanceMinor: Halalas
  /** الداخل للكاش من يوم البداية (وارد على المحفظة + تحويل ليها زي سحب من الصراف). */
  inSinceOpeningMinor: Halalas
  /** الخارج من الكاش من يوم البداية. */
  outSinceOpeningMinor: Halalas
  /** المصروف الشخصي اللي اتدفع كاش في الفترة (من محفظة الكاش أو معلّم «اتدفعت كاش»). */
  spentInPeriodMinor: Halalas
  /** عمليات الكاش في الفترة — الأحدث أولًا. */
  periodTransactions: Transaction[]
}

/** العملية دي كاش؟ على محفظة الكاش، أو المستخدم علّم إنها اتدفعت كاش. */
export function isCashTransaction(t: Pick<Transaction, 'walletId' | 'transferToWalletId' | 'isCashTagged'>, cashWalletId: string): boolean {
  return t.walletId === cashWalletId || t.transferToWalletId === cashWalletId || t.isCashTagged
}

export function summarizeCash(input: {
  wallet: Wallet
  /** عمليات من أول يوم البداية أو أول الفترة (الأقدم فيهم) لحد النهارده أو آخر الفترة. */
  transactions: readonly Transaction[]
  allocations: readonly PersonAllocation[]
  period: { start: IsoDate; end: IsoDate }
}): CashSummary {
  const { wallet, period } = input
  let inMinor: Halalas = 0
  let outMinor: Halalas = 0
  let spent: Halalas = 0
  const inPeriod: Transaction[] = []

  for (const t of input.transactions) {
    // الرصيد: الحركات على محفظة الكاش نفسها بس، من يوم البداية (قبله داخل في رصيد البداية)
    if (t.occurredAt >= wallet.openingAt) {
      if (t.transferToWalletId === wallet.id) inMinor = addMoney(inMinor, t.amountMinor)
      else if (t.walletId === wallet.id) {
        if (t.observedDirection === 'in') inMinor = addMoney(inMinor, t.amountMinor)
        else outMinor = addMoney(outMinor, t.amountMinor)
      }
    }
    // المصروف من الكاش في الفترة: من المحفظة أو معلّم كاش، ومن غير المستبعد (زي الرقم الكبير)
    if (t.occurredAt >= period.start && t.occurredAt <= period.end && isCashTransaction(t, wallet.id)) {
      inPeriod.push(t)
      if (t.observedDirection === 'out' && t.transferToWalletId !== wallet.id && countsAsPersonalExpense(t.economicKind) && !t.excludedFromBudget) {
        spent = addMoney(spent, personalShareOf(t, input.allocations))
      }
    }
  }

  inPeriod.sort((a, b) => (a.occurredAt === b.occurredAt ? b.sourceOrder - a.sourceOrder : a.occurredAt < b.occurredAt ? 1 : -1))
  return {
    wallet: { id: wallet.id, name: wallet.name, openingBalanceMinor: wallet.openingBalanceMinor, openingAt: wallet.openingAt },
    balanceMinor: subtractMoney(addMoney(wallet.openingBalanceMinor, inMinor), outMinor),
    inSinceOpeningMinor: inMinor,
    outSinceOpeningMinor: outMinor,
    spentInPeriodMinor: spent,
    periodTransactions: inPeriod,
  }
}
