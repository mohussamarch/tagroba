import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { DEFAULT_PAYDAY, periodForDate, type Period } from '../domain/period'
import type { HomeScreenData } from '../application/useCases/loadHomeScreen'
import type { TransactionsScreenData } from '../application/useCases/loadTransactionsScreen'
import type { BudgetScreenData } from '../application/useCases/loadBudgetScreen'
import type { PersonRow } from '../application/useCases/managePeople'
import type { Wallet } from '../domain/entities/types'
import type { Container, UserContainer } from './container'

/**
 * تحميل بيانات التطبيق كله — مفصول عن `AppShell` لحد الملف 300 سطر.
 *
 * الفترة **حالة واحدة مشتركة** بين كل الشاشات: لو كانت لكل شاشة فترتها
 * لعرضت شاشتان أرقامًا مختلفة لنفس البيانات (ARCHITECTURE §13.4).
 */
export function useAppData(container: Container, uid: string) {
  const user: UserContainer = useMemo(() => container.forUser(uid), [container, uid])

  const today = useMemo(() => new Date().toISOString().slice(0, 10), [])
  const payday = DEFAULT_PAYDAY

  const [period, setPeriod] = useState<Period>(() => periodForDate(today, payday))
  const [home, setHome] = useState<HomeScreenData | null>(null)
  const [txnData, setTxnData] = useState<TransactionsScreenData | null>(null)
  const [budgetData, setBudgetData] = useState<BudgetScreenData | null>(null)
  const [wallets, setWallets] = useState<Wallet[]>([])
  const [people, setPeople] = useState<PersonRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [recovery, setRecovery] = useState<string | null>(null)

  const requestId = useRef(0)
  const bootstrapped = useRef(false)

  const load = useCallback(async () => {
    const id = ++requestId.current
    setLoading(true)
    setError(null)
    try {
      /*
       * تجهيز لمرة واحدة قبل أي قراءة:
       * الزرع أولًا (القواعد لازمة للتصنيف)، ثم تنظيف الدفعات المعلّقة
       * (ما تحتها غير معتمد فلا يجوز أن يظهر كبيانات حقيقية).
       */
      if (!bootstrapped.current) {
        bootstrapped.current = true
        await user.seedUserReferences()
        /*
         * ⚠️ **كل مستخدم يبدأ بصفر.** الأرصدة في OVERRIDES §6 أرصدة
         * المالك، والتطبيق سيُهدى لأصدقاء — فحقنها يعني أن كل واحد
         * منهم يفتح التطبيق فيجد رصيد شخص آخر.
         */
        await user.seedWallets(false)

        const outcomes = await user.resumeStagedBatch.cleanupAll()
        if (outcomes.length > 0) {
          const total = outcomes.reduce((sum, o) => sum + o.deletedTransactions, 0)
          setRecovery(
            `فيه استيراد سابق ماكملش (${outcomes.map((o) => o.fileName).join('، ')}) ` +
              `فشلناه بالكامل عشان مايسيبش بيانات ناقصة. ` +
              `${total > 0 ? `اتشال ${total} سطر. ` : ''}تقدر تستورد الملف تاني.`,
          )
        }
      }

      const [homeData, transactionsData, budget] = await Promise.all([
        user.loadHomeScreen({ period, today, payday }),
        user.loadTransactionsScreen({ period }),
        user.loadBudgetScreen({ period, today, payday }),
      ])
      const walletList = await user.wallets.listAll()
      const peopleRows = await user.managePeople.listWithBalances()

      if (id !== requestId.current) return
      setHome(homeData)
      setTxnData(transactionsData)
      setBudgetData(budget)
      setWallets(walletList)
      setPeople(peopleRows)
    } catch (cause) {
      if (id !== requestId.current) return
      setError(cause)
    } finally {
      if (id === requestId.current) setLoading(false)
    }
  }, [user, period, today, payday])

  useEffect(() => {
    void load()
  }, [load])

  return {
    user,
    today,
    payday,
    period,
    setPeriod,
    home,
    txnData,
    budgetData,
    wallets,
    people,
    loading,
    error,
    recovery,
    dismissRecovery: () => setRecovery(null),
    reload: load,
  }
}
