import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { DEFAULT_PAYDAY, periodForDate, type Period } from '../domain/period'
import type { HomeScreenData } from '../application/useCases/loadHomeScreen'
import type { TransactionsScreenData } from '../application/useCases/loadTransactionsScreen'
import type { BudgetScreenData } from '../application/useCases/loadBudgetScreen'
import type { PersonRow } from '../application/useCases/managePeople'
import type { PortfolioView } from '../application/useCases/manageAssets'
import type { NotificationsView } from '../application/useCases/loadNotifications'
import type { Wallet } from '../domain/entities/types'
import type { Container, UserContainer } from './container'
import { loadIndependently } from './loadIndependently'

const SECTIONS = ['home','transactions','budget','wallets','people','portfolio'] as const
type Section = typeof SECTIONS[number]
const initialLoading = Object.fromEntries(SECTIONS.map(k=>[k,true])) as Record<Section,boolean>

/** Independent publication, one shared period, and a real bootstrap barrier. */
export function useAppData(container: Container, uid: string) {
  const user: UserContainer = useMemo(() => container.forUser(uid), [container, uid])
  const today = useMemo(() => new Date().toISOString().slice(0, 10), [])
  const payday = DEFAULT_PAYDAY
  const [period, changePeriod] = useState<Period>(() => periodForDate(today, payday))
  const [home,setHome] = useState<HomeScreenData|null>(null)
  const [txnData,setTxnData] = useState<TransactionsScreenData|null>(null)
  const [budgetData,setBudgetData] = useState<BudgetScreenData|null>(null)
  const [wallets,setWallets] = useState<Wallet[]>([])
  const [people,setPeople] = useState<PersonRow[]>([])
  const [portfolio,setPortfolio] = useState<PortfolioView|null>(null)
  const [notifications,setNotifications] = useState<NotificationsView|null>(null)
  const [pending,setPending] = useState(initialLoading)
  const [errors,setErrors] = useState<Partial<Record<Section,unknown>>>({})
  const [recovery,setRecovery] = useState<string|null>(null)
  const requestId = useRef(0)
  const bootstrap = useRef<Promise<void>|null>(null)

  const load = useCallback(async () => {
    const id = ++requestId.current
    const current = () => id === requestId.current
    setPending({...initialLoading})
    setErrors({})
    try {
      if (!bootstrap.current) {
        bootstrap.current = (async () => {
          const [,walletSeed,outcomes] = await Promise.all([
            user.seedUserReferences(), user.seedWallets(false), user.resumeStagedBatch.cleanupAll(),
          ])
          setWallets(walletSeed.wallets)
          if (outcomes.length) {
            const total = outcomes.reduce((sum,o)=>sum+o.deletedTransactions,0)
            setRecovery('فيه استيراد سابق ماكملش؛ اتنضفت الدفعات المعلقة ('+total+' عملية). تقدر تستورد الملف تاني.')
          }
        })().catch(error => { bootstrap.current=null; throw error })
      }
      // All calls, including React's repeated effect, wait for recovery to finish.
      await bootstrap.current
      if (!current()) return
      await loadIndependently({
        home: async () => {
          const data = await user.loadHomeScreen({period,today,payday})
          if(current()) setHome(data)
        },
        transactions: async () => {
          const data = await user.loadTransactionsScreen({period})
          if(current()) setTxnData(data)
        },
        budget: async () => {
          const data = await user.loadBudgetScreen({period,today,payday})
          if(current()) setBudgetData(data)
          const view = await user.loadNotifications.load(data)
          if(current()) setNotifications(view)
        },
        wallets: async () => {
          const data = await user.wallets.listAll()
          if(current()) setWallets(data)
        },
        people: async () => {
          const data = await user.managePeople.listWithBalances()
          if(current()) setPeople(data)
        },
        portfolio: async () => {
          const data = await user.manageAssets.listPortfolio(today)
          if(current()) setPortfolio(data)
        },
      }, (section,error) => {
        if(current()) setErrors(old=>({...old,[section]:error}))
      }, section => {
        if(current()) setPending(old=>({...old,[section]:false}))
      })
    } catch(error) {
      if(current()) {
        setErrors(Object.fromEntries(SECTIONS.map(k=>[k,error])))
        setPending(Object.fromEntries(SECTIONS.map(k=>[k,false])) as Record<Section,boolean>)
      }
    }
  },[user,period,today,payday])

  useEffect(()=>{
    void load()
    return ()=>{requestId.current++}
  },[load])

  const setPeriod = (next:Period) => {
    if(next.start===period.start && next.end===period.end) return
    requestId.current++
    setHome(null);setTxnData(null);setBudgetData(null);setNotifications(null)
    setPending({...initialLoading})
    changePeriod(next)
  }
  return {
    user,today,payday,period,setPeriod,home,txnData,budgetData,wallets,people,portfolio,
    notifications,pending,errors,recovery,
    dismissRecovery:()=>setRecovery(null),reload:load,
  }
}
