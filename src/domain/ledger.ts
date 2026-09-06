import { addMoney, subtractMoney, type Halalas } from './money'
import { countsAsIncome, countsAsPersonalExpense } from './entities/economicKind'
import type {
  Obligation,
  ObligationKind,
  PersonAllocation,
  Settlement,
  Transaction,
} from './entities/types'

/**
 * الدفتر — قواعد spec/02-accounting.md كدوال نقية.
 *
 * القاعدة المركزية: **عملية واحدة، مبلغ واحد، طرق عرض متعددة.**
 * الوسوم لا تضاعف المجموع، والفرع يدخل في الأصل مرة واحدة.
 */

export class LedgerError extends Error {}

/* ───────────────────────── المصروف الشخصي ───────────────────────── */

/**
 * نصيب المستخدم من عملية = المبلغ − ما خُصِّص للآخرين كـ **دين** (receivable).
 *
 * التخصيص كـ «هدية» (gift) لا يخصم من المصروف الشخصي:
 * spec/06 «اختيار هدية لشخص ⇒ المصروف كامل؛ لا التزام عليه».
 *
 * مجموع التخصيصات لا يتجاوز قيمة الشراء (spec/03).
 */
export function personalShareOf(
  transaction: Pick<Transaction, 'id' | 'amountMinor' | 'economicKind'>,
  allocations: readonly PersonAllocation[],
): Halalas {
  if (!countsAsPersonalExpense(transaction.economicKind)) return 0

  const mine = allocations.filter((a) => a.transactionId === transaction.id)
  const allocatedTotal = addMoney(...mine.map((a) => a.amountMinor))

  if (allocatedTotal > transaction.amountMinor) {
    throw new LedgerError(
      `مجموع التخصيصات (${allocatedTotal}) أكبر من قيمة العملية (${transaction.amountMinor})`,
    )
  }

  // الدين فقط يخصم؛ الهدية تبقى مصروفك
  const receivable = addMoney(
    ...mine.filter((a) => a.allocationKind === 'receivable').map((a) => a.amountMinor),
  )
  return subtractMoney(transaction.amountMinor, receivable)
}

export interface PeriodTotals {
  incomeMinor: Halalas
  /** المصروف الشخصي: الاستهلاك غير المستبعد، بعد فصل المستحق على الآخرين. */
  personalExpenseMinor: Halalas
  /** المتبقي = الدخل − المصروف. ليس السيولة ولا رصيد البنك (spec/02). */
  remainingMinor: Halalas
  /** المصروف المستبعد من الميزانية — يُعرض منفصلًا ولا يُخفى. */
  excludedExpenseMinor: Halalas
}

/**
 * مجاميع الفترة. المدخل عمليات الفترة فقط — الدالة لا تفلتر بالتاريخ.
 *
 * الاستبعاد من الميزانية لا يلغي خصم المحفظة (spec/02)،
 * لذلك يُطرح من المصروف ويُعرض في خانته المستقلة، ولا يختفي.
 */
export function computePeriodTotals(
  transactions: readonly Transaction[],
  allocations: readonly PersonAllocation[],
): PeriodTotals {
  let income = 0
  let expense = 0
  let excluded = 0

  for (const t of transactions) {
    if (countsAsIncome(t.economicKind)) {
      income = addMoney(income, t.amountMinor)
      continue
    }
    if (!countsAsPersonalExpense(t.economicKind)) continue

    const share = personalShareOf(t, allocations)
    if (t.excludedFromBudget) excluded = addMoney(excluded, share)
    else expense = addMoney(expense, share)
  }

  return {
    incomeMinor: income,
    personalExpenseMinor: expense,
    remainingMinor: subtractMoney(income, expense),
    excludedExpenseMinor: excluded,
  }
}

/* ───────────────────────── الوسوم بلا مضاعفة ───────────────────────── */

/**
 * مجموع عمليات موسومة بوسم معيّن.
 *
 * spec/02: «انضمام جدول الوسوم لا يجوز أن يضاعف SUM».
 * التنفيذ هنا يجمع **العمليات المميزة** بمعرّفها، فوسمان على نفس العملية
 * ينتجان 720 لا 1440، وثلاثة لا ينتجون 2160 (حالة spec/06).
 */
export function sumByTag(
  transactions: readonly Transaction[],
  taggedTransactionIds: readonly string[],
  allocations: readonly PersonAllocation[] = [],
): Halalas {
  const wanted = new Set(taggedTransactionIds)
  const seen = new Set<string>()
  let total = 0
  for (const t of transactions) {
    if (!wanted.has(t.id) || seen.has(t.id)) continue
    seen.add(t.id)
    total = addMoney(total, personalShareOf(t, allocations))
  }
  return total
}

/* ───────────────────────── حسابات الأشخاص ───────────────────────── */

export interface PersonBalance {
  personId: string
  /** «لك عنده» — المتبقي من القروض والمشتريات التي دفعها المستخدم عنه. */
  receivableMinor: Halalas
  /** «له عندك» — قروض على المستخدم. منفصل عن الأمانة داخليًا (spec/02). */
  payableLoanMinor: Halalas
  /** «له عندك» — أمانات. لا تُقاصّ بالقرض ولا تُسدَّد منه ضمنيًا. */
  payableCustodyMinor: Halalas
}

/** المتبقي من التزام = الأصل − مجموع تسوياته. */
export function remainingOfObligation(
  obligation: Obligation,
  settlements: readonly Settlement[],
): Halalas {
  const paid = addMoney(
    ...settlements.filter((s) => s.obligationId === obligation.id).map((s) => s.amountMinor),
  )
  const remaining = subtractMoney(obligation.originalMinor, paid)
  if (remaining < 0) {
    throw new LedgerError(
      `تسويات الالتزام ${obligation.id} تجاوزت أصله — رصيد سالب صامت ممنوع`,
    )
  }
  return remaining
}

/**
 * أرصدة شخص. **لا تقاص تلقائي** بين «لك» و«عليك» (spec/02)،
 * والقرض والأمانة يبقيان منفصلين داخليًا حتى لو عُرض إجماليهما في خانة واحدة.
 */
export function computePersonBalance(
  personId: string,
  obligations: readonly Obligation[],
  settlements: readonly Settlement[],
): PersonBalance {
  const mine = obligations.filter((o) => o.personId === personId)
  const totalOf = (kind: ObligationKind): Halalas =>
    addMoney(...mine.filter((o) => o.kind === kind).map((o) => remainingOfObligation(o, settlements)))

  return {
    personId,
    receivableMinor: totalOf('receivable'),
    payableLoanMinor: totalOf('loan_payable'),
    payableCustodyMinor: totalOf('custody_payable'),
  }
}

export interface SettlementCheck {
  allowed: boolean
  /** سبب الرفض بلغة المستخدم — «رفض مع تفسير» (spec/06). */
  reason?: string
  /** الجزء الذي يُسوّى فعلًا لو قُبل جزئيًا. */
  settledMinor: Halalas
  /** الفائض الذي يحتاج إجراءً صريحًا كأمانة مستقلة. */
  surplusMinor: Halalas
}

/**
 * يتحقق من تسوية قبل تطبيقها.
 *
 * spec/06:
 * - «محاولة رد أمانة 300 والمتاح 200 ⇒ رفض مع تفسير؛
 *    لا السحب من رصيد قرض ضمنيًا»
 * - «سداد أكبر من الدين ⇒ رفض أو تقسيم صريح إلى تسوية وأمانة؛
 *    لا رصيد سالب صامت»
 */
export function checkSettlement(
  obligation: Obligation,
  settlements: readonly Settlement[],
  amountMinor: Halalas,
): SettlementCheck {
  if (amountMinor <= 0) {
    return { allowed: false, reason: 'مبلغ التسوية لازم يكون أكبر من صفر', settledMinor: 0, surplusMinor: 0 }
  }

  const remaining = remainingOfObligation(obligation, settlements)

  if (remaining === 0) {
    return {
      allowed: false,
      reason: 'الالتزام ده متسدد بالكامل. مفيش متبقي يتسوّى',
      settledMinor: 0,
      surplusMinor: amountMinor,
    }
  }

  if (amountMinor <= remaining) {
    return { allowed: true, settledMinor: amountMinor, surplusMinor: 0 }
  }

  // الزيادة لا تُبتلع ولا تُسحب من التزام آخر — تحتاج قرارًا صريحًا
  const kindLabel =
    obligation.kind === 'custody_payable'
      ? 'الأمانة'
      : obligation.kind === 'loan_payable'
        ? 'الدين'
        : 'المستحق'
  return {
    allowed: false,
    reason:
      `المبلغ أكبر من المتبقي. ${kindLabel} المتبقي ${remaining} هللة والمبلغ ${amountMinor}. ` +
      `تقدر تسوّي ${remaining} وتسجّل الباقي ${amountMinor - remaining} كأمانة مستقلة بإجراء صريح.`,
    settledMinor: remaining,
    surplusMinor: subtractMoney(amountMinor, remaining),
  }
}

/* ───────────────────────── التحويل الداخلي والرسوم ───────────────────────── */

export interface GrossSplit {
  principalMinor: Halalas
  feeMinor: Halalas
}

/**
 * يفصل الإجمالي المخصوم إلى أصل ورسوم.
 *
 * spec/06: «رسوم 20 ضمن خصم 1020 ⇒ أصل 1000 + رسوم 20؛ لا إجمالي 1040».
 * أي: الرسوم **لا تُضاف مرة ثانية** إذا كان المخصوم شاملًا لها.
 */
export function splitGrossIntoPrincipalAndFee(
  grossMinor: Halalas,
  feeMinor: Halalas,
): GrossSplit {
  if (feeMinor < 0) throw new LedgerError('الرسوم لا تكون سالبة')
  if (feeMinor > grossMinor) {
    throw new LedgerError(`الرسوم (${feeMinor}) أكبر من الإجمالي المخصوم (${grossMinor})`)
  }
  return { principalMinor: subtractMoney(grossMinor, feeMinor), feeMinor }
}
