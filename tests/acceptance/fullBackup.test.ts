import {describe,it,expect} from 'vitest'
import {makeFullBackup} from '../../src/application/useCases/fullBackup'
import {memoryFullBackup} from '../../src/infrastructure/memory/fullBackup'
import {emptyBackupData,canonicalBackup} from '../../src/domain/fullBackup'
import {backupDigest} from '../../src/infrastructure/backupDigest'
import {mergeFullBackup} from '../../src/domain/mergeFullBackup'
const now='2026-09-10T00:00:00Z'
function fixture(){
  const data=emptyBackupData()
  data.wallets=[{id:'w',name:'بنك',kind:'bank',currency:'SAR',openingBalanceMinor:0,openingAt:'2020-01-01'}]
  data.categories=[{id:'c',parentId:null,name:'مطاعم',iconKey:'food',lightColor:'#fff',darkColor:'#000',active:true,order:1}]
  data.merchants=[{id:'m',displayName:'Store',normalizedName:'store',verifiedCategoryId:'c',aliases:['shop']}]
  data.rules=[{id:'r',priority:1,matchText:'shop',matchMode:'contains',categoryId:'c',enabled:true}]
  data.people=[{id:'p',name:'شخص تجريبي',archived:false}]
  data.tags=[{id:'tag',displayName:'هدية',normalizedName:'هدية'}]
  data.transactions=[{id:'t',occurredAt:'2020-01-02',datePrecision:'day',sourceOrder:1,economicKind:'purchase',economicKindConfirmed:true,observedDirection:'out',amountMinor:5000,currency:'SAR',walletId:'w',merchantId:'m',categoryId:'c',categoryConfirmed:true,excludedFromBudget:false,reviewState:'confirmed',isCashTagged:false,createdAt:now,updatedAt:now}]
  data.obligations=[{id:'o',personId:'p',originTransactionId:'t',kind:'receivable',originalMinor:2000,currency:'SAR'}]
  data.allocations=[{id:'a',transactionId:'t',personId:'p',allocationKind:'receivable',amountMinor:2000,currency:'SAR'}]
  data.settlements=[{id:'s',transactionId:'t',obligationId:'o',amountMinor:1000}]
  data.transactionTags=[{id:'tt',transactionId:'t',tagId:'tag'}]
  data.budgets=[{id:'2030-01',periodKey:'2030-01',periodStart:'2030-01-28',periodEnd:'2030-02-27',totalLimitMinor:80000,thresholdPercent:80,createdAt:now,updatedAt:now}]
  data.categoryBudgets=[{id:'cb',budgetId:'2030-01',categoryId:'c',limitMinor:20000,notifyEnabled:true,thresholdPercent:80}]
  data.assets=[{id:'gold',name:'ذهب',kind:'gold',unitLabel:'جرام',currency:'SAR',archived:false}]
  data.assetLots=[{id:'lot',assetId:'gold',purchasedAt:'2020-01-02',quantity:100000000,principalMinor:30000,feeMinor:0}]
  data.assetSales=[{id:'sale',assetId:'gold',soldAt:'2021-01-02',quantity:50000000,grossProceedsMinor:20000,feeMinor:0}]
  data.assetPrices=[{assetId:'gold',pricePerUnitMinor:40000,asOf:'2026-09-10',source:'manual'}]
  data.recurringItems=[{id:'sub',name:'اشتراك',merchantKey:'shop',kind:'subscription',cycleMonths:1,expectedMinor:1000,currency:'SAR',nextDueAt:'2026-10-01',active:true,confirmed:true}]
  data.importBatches=[{id:'batch',sourceType:'sms',fileHash:'test',fileName:'test',importedAt:now,state:'committed',counts:{total:1,imported:1,duplicates:0,similar:0,conflicts:0,invalid:0}}]
  data.sourceRecords=[{id:'source',batchId:'batch',accountIdentity:'bank',sourceReference:null,sourceHash:'hash',originalRowIndex:1,rawLine:'redacted',transactionId:'t',matchingState:'new',reason:'new'}]
  data.notificationReceipts=[{eventKey:'budget|2030-01',threshold:80,periodStart:'2030-01-28',sentAt:now}]
  return data
}
describe('نسخة شاملة',()=>{
  it('round-trips all 21 groups, old history, empty future budgets and more than 200 batches',async()=>{
    const data=fixture()
    data.importBatches=Array.from({length:205},(_,i)=>({...data.importBatches[0],id:i===0?'batch':'batch'+i}))
    const file=await makeFullBackup(memoryFullBackup(data),backupDigest).create(now)
    const target=memoryFullBackup(),service=makeFullBackup(target,backupDigest)
    const plan=await service.plan(JSON.stringify(file))
    expect(plan.lines).toHaveLength(21);expect((await target.read()).transactions).toHaveLength(0)
    await service.apply(plan.file)
    expect(await target.read()).toEqual(data)
    expect((await service.apply(file)).totalAdded).toBe(0)
  })
  it('does not collapse two legitimate identical transactions within the backup',()=>{
    const data=fixture();data.transactions.push({...data.transactions[0],id:'t2'})
    expect(mergeFullBackup(data,emptyBackupData()).transactions).toHaveLength(2)
    const existing=fixture();existing.transactions[0].id='existing'
    expect(mergeFullBackup(data,existing).transactions).toHaveLength(1)
  })
  it('remaps all transaction links when matching existing content under another ID',async()=>{
    const incoming=fixture(),existing=fixture();existing.transactions[0].id='existing'
    for(const group of ['allocations','settlements','sourceRecords','transactionTags'] as const)existing[group]=[]
    existing.obligations=[]
    const target=memoryFullBackup(existing),service=makeFullBackup(target,backupDigest)
    const file=await makeFullBackup(memoryFullBackup(incoming),backupDigest).create(now)
    await service.apply(file)
    const result=await target.read()
    expect(result.transactions).toHaveLength(1)
    expect(result.allocations[0].transactionId).toBe('existing')
    expect(result.obligations[0].originTransactionId).toBe('existing')
    expect(result.sourceRecords[0].transactionId).toBe('existing')
  })
  it('preserves existing budget/category limits and never merges different currencies',()=>{
    const incoming=fixture(),existing=fixture()
    existing.budgets[0].totalLimitMinor=999;existing.categoryBudgets[0].limitMinor=123
    incoming.transactions[0]={...incoming.transactions[0],id:'eur',currency:'EUR'}
    const additions=mergeFullBackup(incoming,existing)
    expect(additions.budgets).toHaveLength(0);expect(additions.categoryBudgets).toHaveLength(0)
    expect(additions.transactions).toHaveLength(1)
  })
  it.each(['checksum','missing','money','relation','enum'])('rejects corrupt %s before any write',async kind=>{
    const source=makeFullBackup(memoryFullBackup(fixture()),backupDigest),file=await source.create(now)
    if(kind==='checksum')file.data.people[0].name='changed'
    if(kind==='missing')delete (file.data as Partial<typeof file.data>).settlements
    if(kind==='money')file.data.transactions[0].amountMinor=1.25
    if(kind==='relation')file.data.allocations[0].personId='missing'
    if(kind==='enum')file.data.transactions[0].economicKind='unknown'
    const target=memoryFullBackup(),service=makeFullBackup(target,backupDigest)
    await expect(service.apply(file)).rejects.toThrow()
    expect((await target.read()).transactions).toHaveLength(0)
  })
  it('rechecks current records after preview, and rejects an over-allocation merged with edits',async()=>{
    const source=fixture(),target=memoryFullBackup(),service=makeFullBackup(target,backupDigest)
    const file=await makeFullBackup(memoryFullBackup(source),backupDigest).create(now)
    await service.plan(JSON.stringify(file))
    await target.addMissing(source)
    expect((await service.apply(file)).totalAdded).toBe(0)
    const changed=fixture();changed.transactions[0].amountMinor=500
    const conflict=makeFullBackup(memoryFullBackup(changed),backupDigest)
    await expect(conflict.apply(file)).rejects.toThrow()
  })
  it('checksum survives object-key reordering',async()=>{
    expect(await backupDigest(canonicalBackup({b:2,a:1}))).toBe(await backupDigest(canonicalBackup({a:1,b:2})))
  })
  it('exports and restores the same live repositories used by demo screens',async()=>{
    const {createDemoContainer}=await import('../../src/app/demoContainer')
    const user=createDemoContainer().forUser('demo')
    await user.seedWallets(false)
    const file=await user.fullBackup.create(now)
    expect(file.data.wallets.length).toBeGreaterThan(0)
    expect(file.data.categories.length).toBeGreaterThan(0)
    const plan=await user.fullBackup.plan(JSON.stringify(file))
    expect(plan.totalToAdd).toBe(0)
  })
})
