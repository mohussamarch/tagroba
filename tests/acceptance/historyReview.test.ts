import {it,expect} from 'vitest'
import {makeReviewHistory} from '../../src/application/useCases/reviewHistory'
import {MemoryTransactionRepository,MemoryCategoryRepository,MemoryRuleRepository,MemoryMerchantRepository,PassthroughUnitOfWork,FixedClock} from '../../src/infrastructure/memory/memoryRepositories'
import type {Transaction,Category} from '../../src/domain/entities/types'
const c:Category={id:'c',parentId:null,name:'تسوق',iconKey:'tag',lightColor:'',darkColor:'',active:true,order:0}
const row=(id:string):Transaction=>({id,occurredAt:'2026-09-01',datePrecision:'day',sourceOrder:1,economicKind:'unclassified',economicKindConfirmed:false,observedDirection:'out',amountMinor:10000,currency:'SAR',categoryConfirmed:false,excludedFromBudget:false,reviewState:'needs_review',isCashTagged:false,rawMerchantName:'Store',createdAt:'',updatedAt:''})
async function setup(){
 const txns=new MemoryTransactionRepository();await txns.saveMany([row('a'),row('b')])
 const rules=new MemoryRuleRepository([{id:'r',categoryId:'c',matchText:'Store',matchMode:'exact',priority:1,enabled:true}])
 const use=makeReviewHistory({txns,rules,categories:new MemoryCategoryRepository([c]),merchants:new MemoryMerchantRepository(),uow:new PassthroughUnitOfWork(),clock:new FixedClock('2026-09-08')})
 return {use,txns,rules}
}
it('المعاينة لا تكتب والتطبيق يحافظ على الحقائق البنكية',async()=>{
 const {use,txns}=await setup()
 const p=await use.preview('2026-01-01','2026-09-08')
 expect(p.categoryPlan.changed).toHaveLength(2)
 expect((await txns.findByIds(['a']))[0].categoryId).toBeUndefined()
 await use.applyCategories(['a','b'],p.categoryPlan)
 expect((await txns.findByIds(['a']))[0]).toMatchObject({categoryId:'c',amountMinor:10000,observedDirection:'out'})
})
it('تأكيد يدوي بعد المعاينة يمنع الكتابة فوقه',async()=>{
 const {use,txns}=await setup()
 const p=await use.preview('2026-01-01','2026-09-08')
 await txns.update('a',{categoryConfirmed:true})
 await expect(use.applyCategories(['a','b'],p.categoryPlan)).rejects.toThrow('اتغيرت')
})
it('التصنيف الجماعي يحمي المؤكد ويتحقق من الاتجاه',async()=>{
 const {use,txns}=await setup()
 await txns.update('a',{economicKind:'fee',economicKindConfirmed:true})
 await expect(use.setGroup(['a','b'],'salary')).rejects.toThrow('اتجاه')
 expect(await use.setGroup(['a','b'],'purchase')).toEqual({applied:1,skipped:1})
 expect((await txns.findByIds(['a']))[0].economicKind).toBe('fee')
})
it('الديون والتحويلات ليست تصنيفا جماعيا بلا أطراف',async()=>{
 const {use}=await setup()
 await expect(use.setGroup(['a','b'],'internal_transfer')).rejects.toThrow('ربط')
})
