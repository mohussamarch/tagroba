import type { Halalas } from './money'
import type { ObligationKind } from './entities/types'

/**
 * ربط عملية بدين موجود («اربطها بفلوس جت قبل كده» — OVERRIDES §30).
 *
 * - **فلوس داخلة** بتحصّل دين ليك عند حد (`receivable`).
 * - **فلوس خارجة** بتسدّد دين عليك (`loan_payable`) أو بترجّع أمانة معاك لحد (`custody_payable`).
 * التسوية نفسها بتتأكد في `checkSettlement` (الزيادة بتترفض بتفسير، spec/06).
 */
export function settleableKinds(direction: 'in' | 'out'): readonly ObligationKind[] {
  return direction === 'in' ? ['receivable'] : ['loan_payable', 'custody_payable']
}

/** المبلغ المقترح للتسوية: الأقل بين مبلغ العملية والمتبقي من الدين — عشان ما يتخطاش أي منهم. */
export function suggestedSettlementMinor(transactionMinor: Halalas, remainingMinor: Halalas): Halalas {
  return transactionMinor < remainingMinor ? transactionMinor : remainingMinor
}
