import { useCallback,useEffect,useMemo,useRef,useState } from 'react'
import { DEFAULT_PAYDAY,periodForDate,type Period } from '../domain/period'
import type { HomeScreenData,PeriodSummary } from '../application/useCases/loadHomeScreen'
import type { TransactionsScreenData } from '../application/useCases/loadTransactionsScreen'
import type { BudgetScreenData } from '../application/useCases/loadBudgetScreen'
import type { PersonRow } from '../application/useCases/managePeople'
import type { PortfolioView } from '../application/useCases/manageAssets'
import type { NotificationsView } from '../application/useCases/loadNotifications'
import type { Wallet } from '../domain/entities/types'
import type { Container } from './container'
import { ScreenRequests } from './ScreenRequests'
export type AppTab='home'|'transactions'|'budget'|'people'|'invest'|'settings'|'more'
const SECTIONS=['home','transactions','budget','wallets','people','portfolio','history','notifications'] as const
type Section=typeof SECTIONS[number]
const initialLoading=Object.fromEntries(SECTIONS.map(key=>[key,true])) as Record<Section,boolean>
const tabSection:Record<AppTab,Section>={home:'home',transactions:'transactions',budget:'budget',people:'people',invest:'portfolio',settings:'wallets',more:'wallets'}

export function useAppData(container:Container,uid:string,activeTab:AppTab){
 const user=useMemo(()=>container.forUser(uid),[container,uid])
 const today=useMemo(()=>new Date().toISOString().slice(0,10),[])
 const payday=DEFAULT_PAYDAY
 const [period,changePeriod]=useState(()=>periodForDate(today,payday))
 const [home,setHome]=useState<HomeScreenData|null>(null)
 const [txnData,setTxnData]=useState<TransactionsScreenData|null>(null)
 const [budgetData,setBudgetData]=useState<BudgetScreenData|null>(null)
 const [wallets,setWallets]=useState<Wallet[]>([])
 const [people,setPeople]=useState<PersonRow[]>([])
 const [portfolio,setPortfolio]=useState<PortfolioView|null>(null)
 const [notifications,setNotifications]=useState<NotificationsView|null>(null)
 const [pending,setPending]=useState(initialLoading)
 const [errors,setErrors]=useState<Partial<Record<Section,unknown>>>({})
 const [recovery,setRecovery]=useState<string|null>(null)
 const [snapshotAt,setSnapshotAt]=useState<string|null>(null)
 const requests=useRef(new ScreenRequests())
 const generation=useRef(0)
 const bootstrap=useRef<Promise<void>|null>(null)
 const keyFor=useCallback((section:Section)=>section+':'+(['wallets','people','portfolio'].includes(section)?today:period.key),[today,period.key])

 const prepare=useCallback(()=>{
  if(!bootstrap.current){
   bootstrap.current=(async()=>{
    // تنظيف الاستيراد المعلّق لا يمنع فتح الشاشات أبدًا — فشله كان يوقف التطبيق كله
    const [,walletSeed,outcomes]=await Promise.all([user.seedUserReferences(),user.seedWallets(false),user.resumeStagedBatch.cleanupAll().catch(()=>null)])
    setWallets(walletSeed.wallets)
    if(outcomes===null||outcomes.some(o=>o.error))setRecovery('فيه استيراد سابق لم يكتمل ومقدرناش ننظّفه. الشاشات شغالة، لكن إعادة استيراد نفس الكشف ممكن تعتبر صفوفه مكررة لحد ما يتعمل إصلاح البيانات.')
    else if(outcomes.length)setRecovery('اتنضّف استيراد سابق لم يكتمل. تقدر تستورد الملف تاني.')
   })().catch(error=>{bootstrap.current=null;throw error})
  }
  return bootstrap.current
 },[user])

 const ensure=useCallback(async function loadSection(section:Section):Promise<void>{
  const version=generation.current
  const current=()=>generation.current===version
  const key=keyFor(section)
  if(requests.current.peek(key)===undefined)setPending(old=>({...old,[section]:true}))
  try{
   await prepare()
   if(!current())return
   const loadBudget=()=>requests.current.load(keyFor('budget'),()=>user.loadBudgetScreen({period,today,payday}))
   const jobs:Record<Section,()=>Promise<unknown>>={
    home:()=>user.loadHomeScreen({period,today,payday,includeHistory:false}),
    transactions:()=>user.loadTransactionsScreen({period}),
    budget:()=>user.loadBudgetScreen({period,today,payday}),
    wallets:()=>user.wallets.listAll(),
    people:()=>user.managePeople.listWithBalances(),
    portfolio:()=>user.manageAssets.listPortfolio(today),
    history:async()=>{
     const data=requests.current.peek<HomeScreenData>(keyFor('home'))
     if(!data)throw Error('الرئيسية لم تجهز بعد')
     return user.loadHomeHistory({period,payday,current:data})
    },
    notifications:async()=>user.loadNotifications.load(await loadBudget()),
   }
   const reused=requests.current.peek(key)!==undefined
   const result=await requests.current.load(key,jobs[section])
   if(!current())return
   setErrors(old=>({...old,[section]:undefined}))
   if(section==='home'){
    const data=result as HomeScreenData
    const history=requests.current.peek<PeriodSummary[]>(keyFor('history'))
    setHome(history?{...data,recentPeriods:history}:data);setSnapshotAt(null)
    if(!reused)void user.homeSnapshot.save({data,savedAt:new Date().toISOString()})
    void loadSection('history')
   }else if(section==='transactions')setTxnData(result as TransactionsScreenData)
   else if(section==='budget')setBudgetData(result as BudgetScreenData)
   else if(section==='wallets')setWallets(result as Wallet[])
   else if(section==='people')setPeople(result as PersonRow[])
   else if(section==='portfolio')setPortfolio(result as PortfolioView)
   else if(section==='notifications')setNotifications(result as NotificationsView)
   else if(section==='history')setHome(old=>old?.period.key===period.key?{...old,recentPeriods:result as PeriodSummary[]}:old)
  }catch(error){if(current())setErrors(old=>({...old,[section]:error}))}
  finally{if(current())setPending(old=>({...old,[section]:false}))}
 },[user,prepare,keyFor,period,today,payday])

 useEffect(()=>{
  let alive=true
  const version=generation.current
  void user.homeSnapshot.read(period.key).then(snapshot=>{
   if(alive&&generation.current===version&&snapshot&&!requests.current.peek(keyFor('home'))){
    setHome(snapshot.data);setSnapshotAt(snapshot.savedAt)
   }
  })
  return ()=>{alive=false}
 },[user,period.key,keyFor])
 useEffect(()=>{void ensure(tabSection[activeTab]);void ensure('wallets')},[ensure,activeTab])

 const reload=useCallback(async()=>{
  generation.current++
  requests.current.invalidate()
  await user.homeSnapshot.clear()
  await Promise.all([ensure(tabSection[activeTab]),...(activeTab==='home'?[]:[ensure('home')])])
 },[user,ensure,activeTab])
 useEffect(()=>{
  let backgroundAt=Date.now()
  const onVisibility=()=>{if(document.hidden)backgroundAt=Date.now();else if(Date.now()-backgroundAt>30000)void reload()}
  document.addEventListener("visibilitychange",onVisibility)
  return ()=>document.removeEventListener("visibilitychange",onVisibility)
 },[reload])
 const setPeriod=(next:Period)=>{
  if(next.key===period.key)return
  generation.current++
  setHome(null);setTxnData(null);setBudgetData(null);setNotifications(null);setSnapshotAt(null)
  setErrors({});setPending({...initialLoading});changePeriod(next)
 }
 return {user,today,payday,period,setPeriod,home,txnData,budgetData,wallets,people,portfolio,notifications,
  pending,errors,recovery,snapshotAt,activePending:pending[tabSection[activeTab]],
  dismissRecovery:()=>setRecovery(null),reload,ensure,clearSnapshot:()=>user.homeSnapshot.clear()}
}
