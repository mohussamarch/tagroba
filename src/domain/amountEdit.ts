import { addMoney, type Halalas } from './money'
import { formatMoney } from './formatMoney'
import type { PersonAllocation, Settlement, Transaction } from './entities/types'

/**
 * تعديل مبلغ العملية — OVERRIDES §32 (قرار المالك 2026-09-15): «لو في عملية ثمنها 200 ودخل عدّلها
 * خلاها 180 مش مشكلة، والرقم يتحدث والمصروف الشامل يتحدث». يعلو على «المبلغ ما بيتعدلش» (spec/04).
 *
 * - المبلغ الجديد (`amountMinor`) هو اللي بيتحسب في **كل** حاجة.
 * - المبلغ الأصلي من المصدر بيتحفظ **مرة واحدة** في `originalAmountMinor` («أحتفظ بيه مستخبي»)،
 *   ومطابقة التكرار عند إعادة الاستيراد والدمج بتستعمله — فنفس سطر الكشف ما يتضافش تاني.
 * - المبلغ ما ينزلش تحت اللي متوزع على أشخاص أو متسوّى بيه دين (spec/06: لا رصيد سالب صامت).
 */
export class AmountEditError extends Error {}

/** المبلغ اللي مطابقة التكرار بتستعمله: الأصلي من المصدر لو اتعدل. */
export function sourceAmountMinor(txn: Pick<Transaction, 'amountMinor' | 'originalAmountMinor'>): Halalas {
  return txn.originalAmountMinor ?? txn.amountMinor
}

export function planAmountEdit(
  txn: Pick<Transaction, 'amountMinor' | 'originalAmountMinor'>,
  newAmountMinor: Halalas,
  allocations: readonly Pick<PersonAllocation, 'amountMinor'>[],
  settlements: readonly Pick<Settlement, 'amountMinor'>[],
): Pick<Transaction, 'amountMinor' | 'originalAmountMinor'> {
  if (!Number.isSafeInteger(newAmountMinor) || newAmountMinor <= 0) {
    throw new AmountEditError('اكتب مبلغ صحيح أكبر من صفر، زي 180 أو 180.50')
  }
  const allocated = allocations.reduce<Halalas>((sum, row) => addMoney(sum, row.amountMinor), 0)
  if (newAmountMinor < allocated) {
    throw new AmountEditError(`المبلغ أقل من اللي متوزع على أشخاص من العملية دي (${formatMoney(allocated)}). عدّل التوزيع الأول.`)
  }
  const settled = settlements.reduce<Halalas>((sum, row) => addMoney(sum, row.amountMinor), 0)
  if (newAmountMinor < settled) {
    throw new AmountEditError(`المبلغ أقل من اللي اتسدد بيه دين من العملية دي (${formatMoney(settled)}). عدّل التسوية الأول.`)
  }
  return { amountMinor: newAmountMinor, originalAmountMinor: sourceAmountMinor(txn) }
}
