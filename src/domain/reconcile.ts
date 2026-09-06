import { addMoney, subtractMoney, type Halalas } from './money'
import type { IsoDate } from './entities/types'

/**
 * مطابقة الرصيد الجاري — spec/02 و OVERRIDES §7-ب.
 *
 * القاعدة: افتتاحي + الوارد − الصادر، **بترتيب المصدر**.
 * spec/02: «لا تفترض ترتيب عمليات اليوم إذا غاب دليل» —
 * لذلك يُحفظ ترتيب السطر في الملف (sourceOrder) ويُستخدم كما هو،
 * ولا يُعاد الترتيب داخل اليوم.
 */

/** حركة واحدة على المحفظة بترتيب المصدر. */
export interface LedgerMovement {
  date: IsoDate
  /** ترتيب السطر في الملف الأصلي — يحفظ ترتيب اليوم كما ورد. */
  sourceOrder: number
  debitMinor: Halalas // خارج
  creditMinor: Halalas // داخل
  /** الرصيد المعلن في الكشف بعد هذه الحركة، إن وُجد. */
  statedBalanceMinor?: Halalas
  /** مرجع للعرض عند اكتشاف فرق. */
  reference?: string
  label?: string
}

export interface BalanceMismatch {
  index: number
  date: IsoDate
  computedMinor: Halalas
  statedMinor: Halalas
  differenceMinor: Halalas
  reference?: string
  label?: string
  /** هل يشترك هذا اليوم في أكثر من حركة؟ لو نعم فالغموض في الترتيب وارد. */
  sameDayCount: number
}

export interface ReconcileResult {
  openingMinor: Halalas
  openingAt: IsoDate
  closingMinor: Halalas
  closingAt: IsoDate | null
  totalDebitMinor: Halalas
  totalCreditMinor: Halalas
  movementCount: number
  /** الحركات التي خالف فيها المحسوب المعلن. فارغة = مطابقة كاملة. */
  mismatches: BalanceMismatch[]
  /** عدد الحركات التي فيها رصيد معلن، أي القابلة للمقارنة أصلًا. */
  checkedCount: number
  /**
   * تفسير الغموض عند وجود فروق في يوم متعدد الحركات.
   * spec/06: «توضيح الغموض؛ لا ادعاء تحديد أول فرق يقينًا».
   */
  ambiguityNote: string | null
}

/**
 * يطبّق الحركات بالترتيب على رصيد افتتاحي، ويقارن بالرصيد المعلن حيث وُجد.
 *
 * لا يُعاد ترتيب المدخل — الترتيب المُمرَّر هو ترتيب المصدر.
 */
export function reconcileBalance(
  openingMinor: Halalas,
  openingAt: IsoDate,
  movements: readonly LedgerMovement[],
): ReconcileResult {
  const perDay = new Map<IsoDate, number>()
  for (const m of movements) perDay.set(m.date, (perDay.get(m.date) ?? 0) + 1)

  let balance = openingMinor
  let totalDebit = 0
  let totalCredit = 0
  let checked = 0
  const mismatches: BalanceMismatch[] = []

  movements.forEach((m, index) => {
    totalDebit = addMoney(totalDebit, m.debitMinor)
    totalCredit = addMoney(totalCredit, m.creditMinor)
    balance = addMoney(subtractMoney(balance, m.debitMinor), m.creditMinor)

    if (m.statedBalanceMinor === undefined) return
    checked++
    if (balance !== m.statedBalanceMinor) {
      mismatches.push({
        index,
        date: m.date,
        computedMinor: balance,
        statedMinor: m.statedBalanceMinor,
        differenceMinor: subtractMoney(balance, m.statedBalanceMinor),
        reference: m.reference,
        label: m.label,
        sameDayCount: perDay.get(m.date) ?? 1,
      })
    }
  })

  let ambiguityNote: string | null = null
  if (mismatches.length > 0) {
    const first = mismatches[0]
    ambiguityNote =
      first.sameDayCount > 1
        ? `أول فرق في ${first.date}، واليوم ده فيه ${first.sameDayCount} حركة. ` +
          `ترتيب حركات اليوم الواحد مش مثبت في الكشف، فمش أكيد إن دي بالذات هي أول عملية فيها مشكلة.`
        : `أول فرق في ${first.date}، واليوم ده فيه حركة واحدة، فالفرق يخصها هي.`
  }

  return {
    openingMinor,
    openingAt,
    closingMinor: balance,
    closingAt: movements.length > 0 ? movements[movements.length - 1].date : null,
    totalDebitMinor: totalDebit,
    totalCreditMinor: totalCredit,
    movementCount: movements.length,
    mismatches,
    checkedCount: checked,
    ambiguityNote,
  }
}
