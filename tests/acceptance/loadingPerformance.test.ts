import {it,expect,vi} from 'vitest'
import {coalesceReads} from '../../src/infrastructure/coalesceReads'
import {loadIndependently} from '../../src/app/loadIndependently'
function deferred<T>() {
 let resolve!:(value:T)=>void
 const promise=new Promise<T>(done=>{resolve=done})
 return {promise,resolve}
}
it('publishes the ready screen before a slow unrelated screen',async()=>{
 const slow=deferred<void>()
 const events:string[]=[]
 const work=loadIndependently({
  home:async()=>{events.push('home')},
  portfolio:async()=>{await slow.promise;events.push('portfolio')},
 },()=>{},name=>events.push(name+'-done'))
 await new Promise(r=>setTimeout(r,0))
 expect(events).toContain('home-done')
 expect(events).not.toContain('portfolio')
 slow.resolve();await work
 expect(events).toContain('portfolio-done')
})
it('one failed screen does not discard a successful screen',async()=>{
 const errors=vi.fn(),done=vi.fn()
 await loadIndependently({home:async()=>{},people:async()=>{throw Error('offline')}},errors,done)
 expect(errors).toHaveBeenCalledWith('people',expect.any(Error))
 expect(done).toHaveBeenCalledWith('home')
})
it('coalesces matching in-flight reads and returns isolated values',async()=>{
 const gate=deferred<{id:string}[]>()
 const list=vi.fn(()=>gate.promise)
 const repo=coalesceReads({listByDateRange:list})
 const a=repo.listByDateRange(),b=repo.listByDateRange()
 await Promise.resolve()
 expect(list).toHaveBeenCalledTimes(1)
 gate.resolve([{id:'one'}])
 const first=await a;first[0].id='edited'
 expect(await b).toEqual([{id:'one'}])
 await repo.listByDateRange()
 expect(list).toHaveBeenCalledTimes(2)
})
it('failed reads are retryable and writes invalidate pending sharing',async()=>{
 const list=vi.fn().mockRejectedValueOnce(Error('offline')).mockResolvedValue([])
 const repo=coalesceReads({listAll:list,save:async()=>{}})
 await expect(repo.listAll()).rejects.toThrow('offline')
 await repo.listAll()
 expect(list).toHaveBeenCalledTimes(2)
 const pending=deferred<unknown[]>()
 list.mockImplementation(()=>pending.promise)
 const a=repo.listAll()
 await repo.save()
 const b=repo.listAll()
 await Promise.resolve()
 expect(list).toHaveBeenCalledTimes(4)
 pending.resolve([]);await Promise.all([a,b])
})
it('does not share cache across user repository instances',async()=>{
 const first=coalesceReads({listAll:async()=>['user-a']})
 const second=coalesceReads({listAll:async()=>['user-b']})
 expect(await first.listAll()).toEqual(['user-a'])
 expect(await second.listAll()).toEqual(['user-b'])
})

import { makeLoadHomeScreen } from '../../src/application/useCases/loadHomeScreen'
import { MemoryTransactionRepository, MemoryCategoryRepository, MemoryAllocationRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { buildPeriod } from '../../src/domain/period'

it('starts all five historic home reads before any historic response resolves', async () => {
 const txns = new MemoryTransactionRepository()
 const gate = deferred<void>()
 const dates:string[] = []
 const period = buildPeriod(2026, 9, 28)
 const original = txns.listByDateRange.bind(txns)
 txns.listByDateRange = async (start,end) => {
  dates.push(start)
  if(start !== period.start) await gate.promise
  return original(start,end)
 }
 const load = makeLoadHomeScreen({txns,categories:new MemoryCategoryRepository(),allocations:new MemoryAllocationRepository()})
 const pending = load({period,today:'2026-10-01',payday:28})
 for(let n=0;n<20;n++) await Promise.resolve()
 const started = dates.length
 gate.resolve()
 const result = await pending
 expect(started).toBe(6)
 expect(result.recentPeriods).toHaveLength(6)
 expect(result.recentPeriods[0].period.key).toBe(period.key)
})
