import {it,expect} from 'vitest'
import {makeLoadTransactionsScreen} from '../../src/application/useCases/loadTransactionsScreen'
import {makeManageCategories} from '../../src/application/useCases/manageCategories'
import {makeManageRules} from '../../src/application/useCases/manageRules'
import {merchantIndex} from '../../src/domain/merchantIndex'
import {categorize} from '../../src/domain/categorize'
import {parseQuery,searchTransactions} from '../../src/domain/search'
import {MemoryTransactionRepository,MemoryCategoryRepository,MemoryAllocationRepository,MemoryMerchantRepository,MemoryRuleRepository,SequentialIdGenerator} from '../../src/infrastructure/memory/memoryRepositories'
import {MemoryTagRepository,MemoryTransactionTagRepository} from '../../src/infrastructure/memory/memoryTagRepositories'
import type {Category,Transaction} from '../../src/domain/entities/types'
const c:Category={id:'c',name:'تسوق',parentId:null,active:true,order:0,iconKey:'tag',lightColor:'green',darkColor:'green'}
it('الوسوم والأسماء البديلة تدخل البحث دون مضاعفة الإجماليات',async()=>{
 const txns=new MemoryTransactionRepository()
 const t:Transaction={id:'t',occurredAt:'2026-09-01',datePrecision:'day',sourceOrder:1,economicKind:'purchase',economicKindConfirmed:true,observedDirection:'out',amountMinor:72000,currency:'SAR',categoryConfirmed:false,excludedFromBudget:false,reviewState:'suggested',isCashTagged:false,createdAt:'',updatedAt:'',rawMerchantName:'STORE KSA'}
 await txns.saveMany([t])
 const tags=new MemoryTagRepository(),transactionTags=new MemoryTransactionTagRepository()
 await tags.save({id:'gift',displayName:'هدية',normalizedName:'هدية'})
 await transactionTags.saveMany([{id:'l1',transactionId:'t',tagId:'gift'},{id:'l2',transactionId:'t',tagId:'gift'}])
 const merchants=new MemoryMerchantRepository([{id:'m',displayName:'متجري',normalizedName:'STORE',aliases:['STORE KSA'],verifiedCategoryId:'c'}])
 const data=await makeLoadTransactionsScreen({txns,tags,transactionTags,merchants,categories:new MemoryCategoryRepository([c]),allocations:new MemoryAllocationRepository()})({today:'2026-09-08'})
 expect(data.expenseMinor).toBe(72000)
 expect(data.tagNamesByTransaction.t).toEqual(['هدية'])
 const searchable=[{transaction:t,tagNames:data.tagNamesByTransaction.t,merchantNames:data.merchantNamesByTransaction.t}]
 expect(searchTransactions(searchable,parseQuery('هدية'))).toHaveLength(1)
 expect(searchTransactions(searchable,parseQuery('متجري'))).toHaveLength(1)
 expect(categorize({currentConfirmed:false,merchantName:'STORE KSA'},{merchantsByNormalizedName:merchantIndex(await merchants.listAll()),rules:[],categoryIdByName:new Map()}).categoryId).toBe('c')
})
it('إخفاء وتغيير لون تصنيف يحتفظ بالمعرف والفرع',async()=>{
 const categories=new MemoryCategoryRepository([c,{...c,id:'p',name:'لون آخر',lightColor:'blue'}])
 const use=makeManageCategories({categories,ids:new SequentialIdGenerator()})
 const result=await use.save({id:'c',name:'تسوق',active:false,paletteId:'p'})
 expect(result).toMatchObject({id:'c',active:false,lightColor:'blue',parentId:null})
 await expect(use.save({name:'تسوق',active:true,paletteId:'p'})).rejects.toThrow('نفس الاسم')
})
it('الاسم البديل المؤكد يحفظ ويمنع الاستيلاء على هوية تاجر آخر',async()=>{
 const merchants=new MemoryMerchantRepository([{id:'a',displayName:'Amazon',normalizedName:'AMAZON'},{id:'b',displayName:'Other',normalizedName:'OTHER'}])
 const use=makeManageRules({merchants,categories:new MemoryCategoryRepository([c]),rules:new MemoryRuleRepository(),ids:new SequentialIdGenerator()})
 await use.addAlias('a','Amazon KSA')
 expect((await merchants.findByNormalizedName('Amazon KSA'))?.id).toBe('a')
 await expect(use.addAlias('b','Amazon KSA')).rejects.toThrow('تاجر تاني')
})
