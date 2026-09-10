
import {it,expect,vi,afterEach} from 'vitest'
import {createHomeSnapshot} from '../../src/infrastructure/homeSnapshot'
import {makeLoadHomeScreen} from '../../src/application/useCases/loadHomeScreen'
import {MemoryTransactionRepository,MemoryCategoryRepository,MemoryAllocationRepository} from '../../src/infrastructure/memory/memoryRepositories'
import {buildPeriod} from '../../src/domain/period'
afterEach(()=>vi.unstubAllGlobals())
function storage(){
 const values:Record<string,string>={}
 vi.stubGlobal('localStorage',new Proxy(values,{get(target,key){
  if(key==='getItem')return (name:string)=>target[name]??null
  if(key==='setItem')return (name:string,value:string)=>{target[name]=value}
  if(key==='removeItem')return (name:string)=>{delete target[name]}
  return target[key as string]
 }}))
 return values
}
async function fixture(){
 const period=buildPeriod(2026,9,28)
 const data=await makeLoadHomeScreen({txns:new MemoryTransactionRepository(),categories:new MemoryCategoryRepository(),allocations:new MemoryAllocationRepository()})({period,payday:28,today:'2026-10-01'})
 return {data,savedAt:new Date().toISOString()}
}
it('keeps saved home views isolated per account and period, and clears only the signed-out account',async()=>{
 storage()
 const a=createHomeSnapshot('a'),b=createHomeSnapshot('b'),snapshot=await fixture()
 await a.save(snapshot);await b.save(snapshot)
 expect((await a.read(snapshot.data.period.key))?.data).toEqual(snapshot.data)
 expect(await a.read('other-month')).toBeNull()
 await a.clear()
 expect(await a.read(snapshot.data.period.key)).toBeNull()
 expect(await b.read(snapshot.data.period.key)).not.toBeNull()
})
it('corrupt or unavailable local storage never prevents opening the app',async()=>{
 const values=storage(),a=createHomeSnapshot('a'),snapshot=await fixture()
 await a.save(snapshot)
 for(const key of Object.keys(values))values[key]='invalid JSON'
 expect(await a.read(snapshot.data.period.key)).toBeNull()
 vi.stubGlobal('localStorage',{getItem(){throw Error('disabled')},setItem(){throw Error('disabled')}})
 expect(await a.read(snapshot.data.period.key)).toBeNull()
 await expect(a.save(snapshot)).resolves.toBeUndefined()
})
