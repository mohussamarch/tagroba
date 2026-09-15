import { useEffect, useState } from 'react'
import type { CashSummary } from '../domain/cashSummary'
import type { useAppData } from './useAppData'

/**
 * بيانات كارت الكاش — OVERRIDES §32. بيتحمّل مع الرئيسية وبيتحدث كل ما بياناتها تتحدث
 * (تعديل مبلغ أو إضافة عملية بيعيد تحميل الرئيسية). فشل التحميل = الكارت ما يظهرش، مش رقم غلط.
 */
export function useCashSummary(app: ReturnType<typeof useAppData>, active: boolean): CashSummary | null {
  const [cash, setCash] = useState<CashSummary | null>(null)
  const { user, period, today, home } = app
  useEffect(() => {
    if (!active || !home) return
    let alive = true
    user.loadCashSummary({ period, today })
      .then((summary) => { if (alive) setCash(summary) })
      .catch(() => { if (alive) setCash(null) })
    return () => { alive = false }
  }, [user, period, today, home, active])
  return cash
}
