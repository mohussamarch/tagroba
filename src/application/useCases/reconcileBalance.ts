import { reconcileBalance, type LedgerMovement, type ReconcileResult } from '../../domain/reconcile'
import { buildPeriod, daysBetween, periodForDate, type Period } from '../../domain/period'
import type { Halalas } from '../../domain/money'
import type { Id, Transaction, Wallet } from '../../domain/entities/types'
import type { TransactionRepository, WalletRepository } from '../ports/repositories'

/**
 * ReconcileBalance — مطابقة الرصيد الجاري بالكشف.
 *
 * spec/02: «افتتاحي + الوارد − الصادر لكل محفظة وعملة، وتُراجع حسب
 *           ترتيب المصدر؛ **لا تفترض ترتيب عمليات اليوم إذا غاب دليل**.»
 *
 * OVERRIDES §7-ب: السلسلة من 4,837.83 @ 2025-01-01 تنتهي عند
 * 2,707.25 @ 2026-09-04 وتطابق عمود الرصيد في كل سطر بدقة الهللة.
 *
 * ⚠️ **قيد القراءة:** المطابقة تحتاج كل عمليات المحفظة، وهذا يتعارض
 * ظاهريًا مع «كل استعلام محدود بفترة» (ARCHITECTURE §5.6). الحل:
 * القراءة **فترة فترة** من تاريخ الافتتاح حتى اليوم، فيبقى كل استعلام
 * محدودًا، ويُعلن للمستخدم عدد الفترات المقروءة وتكلفتها.
 */

export interface ReconcileOutcome {
  wallet: Wallet
  result: ReconcileResult
  /** عدد العمليات التي لا تحمل رصيدًا معلنًا فلا تُقارن. */
  withoutStatedBalance: number
  /** عمليات لا تنتمي لأي محفظة — تُذكر ولا تُنسب بالتخمين. */
  unassignedCount: number
  /** عدد الفترات التي قُرئت. */
  periodsRead: number
}

export interface ReconcileDeps {
  txns: TransactionRepository
  wallets: WalletRepository
}

/** سقف الفترات المقروءة في مرة واحدة — حماية من قراءة لا تنتهي. */
const MAX_PERIODS = 60

function nextPeriod(period: Period, payday: number): Period {
  const [year, month] = period.key.split('-').map(Number)
  const total = year * 12 + (month - 1) + 1
  return buildPeriod(Math.floor(total / 12), (total % 12) + 1, payday)
}

export function makeReconcileBalance(deps: ReconcileDeps) {
  return async function run(options: {
    walletId: Id
    /** آخر تاريخ يُقرأ حتى عنده. */
    until: string
    payday: number
  }): Promise<ReconcileOutcome> {
    const wallet = await deps.wallets.findById(options.walletId)
    if (!wallet) throw new Error(`محفظة غير موجودة: ${options.walletId}`)

    const collected: Transaction[] = []
    let unassignedCount = 0
    let periodsRead = 0

    // الفترة التي **تحوي** تاريخ الافتتاح، لا التي تبدأ في شهره:
    // الشهر المالي يبدأ يوم الراتب، فأول يناير يقع في فترة ديسمبر.
    let period = periodForDate(wallet.openingAt, options.payday)

    while (period.start <= options.until && periodsRead < MAX_PERIODS) {
      const rows = await deps.txns.listByDateRange(period.start, period.end)
      periodsRead++
      for (const row of rows) {
        if (row.occurredAt < wallet.openingAt || row.occurredAt > options.until) continue
        if (row.walletId === undefined) {
          unassignedCount++
          continue
        }
        if (row.walletId !== wallet.id) continue
        collected.push(row)
      }
      if (period.end >= options.until) break
      period = nextPeriod(period, options.payday)
    }

    // الترتيب بترتيب المصدر: التاريخ ثم رقم السطر — لا يُعاد ترتيب اليوم
    collected.sort((a, b) =>
      a.occurredAt === b.occurredAt
        ? a.sourceOrder - b.sourceOrder
        : a.occurredAt < b.occurredAt
          ? -1
          : 1,
    )

    const movements: LedgerMovement[] = collected.map((t) => {
      const movement: LedgerMovement = {
        date: t.occurredAt,
        sourceOrder: t.sourceOrder,
        debitMinor: t.observedDirection === 'out' ? t.amountMinor : 0,
        creditMinor: t.observedDirection === 'in' ? t.amountMinor : 0,
        label: t.rawMerchantName || t.rawDescription || t.sourceCategory || '',
      }
      if (t.statedBalanceMinor !== undefined) movement.statedBalanceMinor = t.statedBalanceMinor
      return movement
    })

    return {
      wallet,
      result: reconcileBalance(wallet.openingBalanceMinor, wallet.openingAt, movements),
      withoutStatedBalance: movements.filter((m) => m.statedBalanceMinor === undefined).length,
      unassignedCount,
      periodsRead,
    }
  }
}

/** كم فترة بين تاريخين — لتقدير تكلفة القراءة قبل تشغيلها. */
export function estimatePeriodsBetween(from: string, to: string): number {
  return Math.max(1, Math.ceil(daysBetween(from, to) / 30))
}

export type { ReconcileResult }
export type { Halalas }
