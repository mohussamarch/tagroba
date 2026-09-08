import { describe,it,expect } from 'vitest'
import { detectRecurring, recurringSummary, shiftMonths } from '../../src/domain/recurring'
import { makeManageRecurring } from '../../src/application/useCases/manageRecurring'
import { MemoryRecurringRepository } from '../../src/infrastructure/memory/memoryRecurringRepository'
import { MemoryCategoryRepository,MemoryTransactionRepository,SequentialIdGenerator } from '../../src/infrastructure/memory/memoryRepositories'
import type { Transaction } from '../../src/domain/entities/types'
const txn=(id:string,date:string,over:Partial<Transaction>={}):Transaction=>({
 id,occurredAt:date,datePrecision:'day',sourceOrder:1,economicKind:'purchase',
 economicKindConfirmed:true,observedDirection:'out',amountMinor:2999,currency:'SAR',
 categoryConfirmed:false,excludedFromBudget:false,reviewState:'suggested',isCashTagged:false,
 createdAt:'',updatedAt:'',rawMerchantName:'Netflix',...over})
const rows=[txn('a','2026-06-30'),txn('b','2026-07-31'),txn('c','2026-08-31')]
describe('الاشتراكات: اقتراح بلا تضاعف مصروف',()=>{
 it('ثلاثة شهور وتقارب المبلغ والموعد ينتج اقتراحا',()=>{
  expect(detectRecurring(rows,[])[0]).toMatchObject({expectedMinor:2999,nextDueAt:'2026-09-30',cycleMonths:1})
 })
 it('شهران غير كافيين',()=>expect(detectRecurring(rows.slice(0,2),[])).toEqual([]))
 it('١٢٦ زيارة مطعم ليست اشتراكا',()=>{
  const visits=Array.from({length:126},(_,i)=>txn(String(i),'2026-06-15',{rawMerchantName:'ALBAIK'}))
  expect(detectRecurring(visits,[])).toEqual([])
 })
 it('تواتر عال لخدمة رقمية يحتاج مراجعة وليس اشتراكا مفترضا',()=>{
  expect(detectRecurring([...rows,txn('d','2026-07-12')],[])).toEqual([])
 })
 it('لا يخلط العملات',()=>{
  expect(detectRecurring(rows.map((t,i)=>({...t,currency:i===1?'USD':'SAR'})),[])).toEqual([])
 })
 it('لا يقترح تحويلات أو وارد',()=>{
  expect(detectRecurring(rows.map(t=>({...t,economicKind:'internal_transfer'})),[])).toEqual([])
  expect(detectRecurring(rows.map(t=>({...t,observedDirection:'in'})),[])).toEqual([])
 })
 it('لا يقبل مبالغ متباعدة',()=>expect(detectRecurring([...rows.slice(0,2),{...rows[2],amountMinor:9900}],[])).toEqual([]))
 it('فبراير الكبيس ونهاية الشهر',()=>{
  expect(shiftMonths('2028-01-31',1)).toBe('2028-02-29')
  expect(shiftMonths('2028-02-29',12)).toBe('2029-02-28')
 })
 it('التأكيد يحفظ بدون تغيير عمليات المال ويمنع إعادة الاقتراح',async()=>{
  const txns=new MemoryTransactionRepository()
  await txns.saveMany(rows)
  const items=new MemoryRecurringRepository()
  const use=makeManageRecurring({items,txns,categories:new MemoryCategoryRepository(),ids:new SequentialIdGenerator()})
  const before=await txns.listByDateRange('2026-01-01','2026-12-31')
  const c=(await use.load('2026-09-08')).candidates[0]
  await use.save({...c,active:true,kind:'subscription'})
  const view=await use.load('2026-09-08')
  expect(view.candidates).toHaveLength(0)
  expect(view.items[0]).toMatchObject({paidMinor:8997,annualMinor:35988})
  expect(await txns.listByDateRange('2026-01-01','2026-12-31')).toEqual(before)
  await expect(use.save({...c,active:true,kind:'subscription'})).rejects.toThrow('مسجلة')
 })
 it('المدفوع داخل النطاق فقط وبلا تكرار معرف',()=>{
  const candidate=detectRecurring(rows,[])[0]
  const item={...candidate,id:'r',active:true,confirmed:true,kind:'subscription' as const}
  const data=[...rows,rows[0],txn('old','2025-09-08'),txn('future','2026-09-09')]
  expect(recurringSummary(item,data,'2026-09-08').paidMinor).toBe(8997)
 })
})
