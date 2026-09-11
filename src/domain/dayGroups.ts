import { addMoney, type Halalas } from './money'
import type { IsoDate, Transaction } from './entities/types'

/**
 * تجميع العمليات بالأيام — إعادة تصميم شاشة العمليات 2026-09-11.
 *
 * الشاشة **لا تحسب مبلغًا** (CLAUDE.md #4)، فمجموع اليوم بيتحسب هنا
 * في `domain/` بدالة نقية بلا أي اعتماد خارجي، زي `formatMoney` بالظبط.
 *
 * المبلغ في العملية **موجب دايمًا** والاتجاه في `observedDirection`
 * (spec/03)، فمجموع الصادر والوارد بيتفصلوا ولا بيتجمعوا مع بعض.
 * مفيش «صافي اليوم» عشان ما يبقاش رقم بيخلط اتنين مختلفين.
 */
export interface DayGroup {
  date: IsoDate
  /** مجموع اللي خرج في اليوم ده — موجب. */
  outgoingMinor: Halalas
  /** مجموع اللي دخل في اليوم ده — موجب. */
  incomingMinor: Halalas
  transactions: Transaction[]
}

export function groupByDay(transactions: readonly Transaction[]): DayGroup[] {
  const byDate = new Map<IsoDate, DayGroup>()

  for (const t of transactions) {
    const group = byDate.get(t.occurredAt) ?? {
      date: t.occurredAt,
      outgoingMinor: 0,
      incomingMinor: 0,
      transactions: [],
    }
    if (t.observedDirection === 'out') {
      group.outgoingMinor = addMoney(group.outgoingMinor, t.amountMinor)
    } else {
      group.incomingMinor = addMoney(group.incomingMinor, t.amountMinor)
    }
    group.transactions.push(t)
    byDate.set(t.occurredAt, group)
  }

  // الأحدث أولًا، وجوه اليوم الواحد ترتيب الكشف معكوس (آخر سطر فوق)
  return [...byDate.values()]
    .sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0))
    .map((group) => ({
      ...group,
      transactions: [...group.transactions].sort((a, b) => b.sourceOrder - a.sourceOrder),
    }))
}
