import type { CategorizeTransactionsDeps } from './categorizeTransactions'
import { makeCategorizeTransactions } from './categorizeTransactions'
import { makeSetEconomicKind } from './setEconomicKind'
import { normalizeText } from '../../domain/normalize'
import { parseIsoDate, daysBetween, dayNumberToIso, toDayNumber } from '../../domain/period'
import type { Transaction } from '../../domain/entities/types'
import type { EconomicKind } from '../../domain/entities/economicKind'
import { isConsistentWithObservedDirection } from '../../domain/entities/economicKind'

export const BULK_KINDS: EconomicKind[]=['purchase','support_gift','fee','salary','bonus','commission','overtime','freelance','personal_sale']
export function makeReviewHistory(deps:CategorizeTransactionsDeps) {
 const cat=makeCategorizeTransactions(deps)
 const kinds=makeSetEconomicKind(deps)
 async function read(from:string,to:string) {
  parseIsoDate(from);parseIsoDate(to)
  const length=daysBetween(from,to)
  if(length<0||length>1830) throw new Error('اختار نطاق صحيح لا يزيد عن خمس سنين.')
  const rows:Transaction[]=[]
  let start=from
  for(let i=0;i<61 && start<=to;i++) {
   const end=dayNumberToIso(Math.min(toDayNumber(parseIsoDate(start))+30,toDayNumber(parseIsoDate(to))))
   rows.push(...await deps.txns.listByDateRange(start,end))
   start=dayNumberToIso(toDayNumber(parseIsoDate(end))+1)
  }
  return [...new Map(rows.map(t=>[t.id,t])).values()]
 }
 async function preview(from:string,to:string) {
  const rows=await read(from,to)
  const categoryPlan=await cat.plan(rows)
  const groups=new Map<string,Transaction[]>()
  for(const t of rows) {
   if(t.economicKindConfirmed) continue
   const name=normalizeText(t.rawMerchantName??'')
   if(!name) continue
   const key=name+'|'+t.observedDirection+'|'+t.currency
   groups.set(key,[...(groups.get(key)??[]),t])
  }
  return {rows,categoryPlan,groups:[...groups.values()].filter(g=>g.length>1)}
 }
 async function applyCategories(ids:string[],expected:Awaited<ReturnType<typeof cat.plan>>) {
  const rows=await deps.txns.findByIds([...new Set(ids)])
  const fresh=await cat.plan(rows)
  const signature=(r:typeof fresh)=>JSON.stringify({...r,changed:[...r.changed].sort((a,b)=>a.transactionId.localeCompare(b.transactionId)),skippedConfirmed:[...r.skippedConfirmed].sort(),stillNeedsReview:[...r.stillNeedsReview].sort()})
  if(signature(fresh)!==signature(expected)) throw new Error('العمليات أو القواعد اتغيرت بعد المعاينة. اعرض المعاينة من جديد.')
  return cat.apply(rows)
 }
 async function setGroup(ids:string[],kind:EconomicKind) {
  if(!BULK_KINDS.includes(kind)) throw new Error('الديون والتحويلات تحتاج ربط الشخص أو المحفظة لكل عملية.')
  const rows=await deps.txns.findByIds([...new Set(ids)])
  if(!rows.length) throw new Error('اختار عمليات للمراجعة.')
  const first=rows[0]
  if(rows.some(t=>normalizeText(t.rawMerchantName??'')!==normalizeText(first.rawMerchantName??'') ||
   t.currency!==first.currency || t.observedDirection!==first.observedDirection))
   throw new Error('المجموعة لازم تكون لنفس الاسم والعملة والاتجاه.')
  if(!isConsistentWithObservedDirection(kind,first.observedDirection)) throw new Error('النوع لا يطابق اتجاه الحركة.')
  let applied=0
  for(const row of rows) {
   const [current]=await deps.txns.findByIds([row.id])
   if(!current || current.economicKindConfirmed) continue
   await kinds.setOne(row.id,kind);applied++
  }
  return {applied,skipped:rows.length-applied}
 }
 return {preview,applyCategories,setGroup}
}
export type HistoryPreview=Awaited<ReturnType<ReturnType<typeof makeReviewHistory>['preview']>>
