import {it,expect,vi} from 'vitest'
const mocks=vi.hoisted(()=>({getDocs:vi.fn(),get:vi.fn(),set:vi.fn()}))
vi.mock('firebase/firestore',()=>({
  collection:(_db:unknown,...parts:string[])=>parts.join('/'),doc:(_db:unknown,...parts:string[])=>parts.join('/'),
  documentId:()=>'__name__',orderBy:(key:string)=>({orderBy:key}),limit:(count:number)=>({limit:count}),
  startAfter:(cursor:unknown)=>({startAfter:cursor}),query:(path:unknown,...constraints:unknown[])=>({path,constraints}),
  getDocsFromServer:mocks.getDocs,runTransaction:async(_db:unknown,fn:(tx:unknown)=>unknown)=>fn({get:mocks.get,set:mocks.set}),
}))
import {firestoreFullBackup} from '../../src/infrastructure/firestore/fullBackupRepository'
import {emptyBackupData} from '../../src/domain/fullBackup'
it('reads server pages of 200 through the last page, within the requested user only',async()=>{
  mocks.getDocs.mockReset()
  const rows=Array.from({length:200},(_,i)=>({id:'w'+i,data:()=>({id:'w'+i})}))
  mocks.getDocs.mockResolvedValue({size:0,docs:[]}).mockResolvedValueOnce({size:200,docs:rows}).mockResolvedValueOnce({size:1,docs:[{data:()=>({id:'last'})}]})
  const data=await firestoreFullBackup({} as never,'owner').read()
  expect(data.wallets).toHaveLength(201)
  const queries=mocks.getDocs.mock.calls.map(call=>call[0])
  expect(queries.every(query=>query.path.startsWith('users/owner/'))).toBe(true)
  expect(queries.every(query=>query.constraints.some((item:{limit?:number})=>item.limit===200))).toBe(true)
  expect(queries[1].constraints).toContainEqual({startAfter:rows[199]})
})
it('rechecks document existence inside the write transaction and protects bank text without changing IDs',async()=>{
  mocks.get.mockReset();mocks.set.mockReset()
  mocks.get.mockImplementation(async(path:string)=>({exists:()=>path.endsWith('/existing')}))
  const data=emptyBackupData()
  data.people=[{id:'existing',name:'old backup'},{id:'new',name:'new'}]
  data.sourceRecords=[{id:'id12345678',accountIdentity:'حساب 1234567890',rawLine:'شراء بمبلغ 15000.50 SAR حساب 1234567890'}]
  const added=await firestoreFullBackup({} as never,'owner').addMissing(data)
  expect(added.people).toBe(1)
  expect(mocks.set.mock.calls.some(call=>call[0].endsWith('/existing'))).toBe(false)
  const source=mocks.set.mock.calls.find(call=>call[0].endsWith('/id12345678'))![1]
  expect(source.id).toBe('id12345678');expect(source.rawLine).toContain('15000.50')
  expect(source.rawLine).not.toContain('1234567890')
})
