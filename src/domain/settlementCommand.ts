import type { Obligation, Settlement } from './entities/types'
import { checkSettlement } from './ledger'

export interface SettlementCommand extends Settlement { personId: string }

/** Called against a consistent storage snapshot, never against a screen's cached balance. */
export function prepareSettlement(input: SettlementCommand, obligation: Obligation | undefined, rows: readonly Settlement[]): Settlement {
  if (!obligation || obligation.personId !== input.personId) throw new Error('الالتزام ده مش موجود للشخص ده')
  if (!Number.isSafeInteger(input.amountMinor) || input.amountMinor <= 0) throw new Error('مبلغ التسوية لازم يكون عدد صحيح بالهللة وأكبر من صفر')
  const existing = rows.find(row => row.id === input.id)
  if (existing) {
    if (existing.obligationId !== input.obligationId || existing.amountMinor !== input.amountMinor || existing.transactionId !== input.transactionId) {
      throw new Error('الطلب ده اتحفظ قبل كده ببيانات مختلفة. راجع التسوية المسجلة')
    }
    return existing
  }
  const check = checkSettlement(obligation, rows, input.amountMinor)
  if (!check.allowed) throw new Error(check.reason ?? 'التسوية مرفوضة')
  return { id: input.id, obligationId: input.obligationId, transactionId: input.transactionId, amountMinor: input.amountMinor }
}
