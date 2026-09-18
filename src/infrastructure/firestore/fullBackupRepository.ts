import { collection,doc,documentId,getDocFromServer,getDocsFromServer,increment,limit,orderBy,query,runTransaction,startAfter,type Firestore,type QueryDocumentSnapshot } from 'firebase/firestore'
import { settlementRevision } from './settlementWriter'
import { BACKUP_GROUPS,backupRowId,emptyBackupData,type FullBackupData } from '../../domain/fullBackup'
import type { FullBackupPort } from '../../application/ports/FullBackupPort'
import {redactSms} from '../import/bankSmsParser'
import type {BackupRow} from '../../domain/fullBackup'
function protectBankText(row:BackupRow):BackupRow {
  const safe={...row}
  for(const key of ['accountIdentity','rawLine','rawDescription','rawMerchantName'])if(typeof safe[key]==='string')safe[key]=redactSms(safe[key] as string)
  return safe
}
export function firestoreFullBackup(db:Firestore,uid:string):FullBackupPort {
  return {
    async read(){
      const data=emptyBackupData()
      for(const group of BACKUP_GROUPS){
        let cursor:QueryDocumentSnapshot|undefined
        while(true){
          const page=await getDocsFromServer(query(collection(db,'users',uid,group),orderBy(documentId()),limit(200),...(cursor?[startAfter(cursor)]:[])))
          data[group].push(...page.docs.map(row=>row.data()))
          if(page.size<200)break
          cursor=page.docs[page.docs.length-1]
        }
      }
      return data
    },
    async addMissing(data:FullBackupData){
      const added:Record<string,number>={}
      for(const group of BACKUP_GROUPS){
        added[group]=0
        for(let start=0;start<data[group].length;start+=100){
          const rows=data[group].slice(start,start+100)
          const count=await runTransaction(db,async tx=>{
            const refs=rows.map(row=>{const id=backupRowId(group,row);return doc(db,'users',uid,group,group==='notificationReceipts'?encodeURIComponent(id).replace(/\./g,'%2E'):id)})
            const current=await Promise.all(refs.map(ref=>tx.get(ref)))
            let written=0
            current.forEach((snap,index)=>{if(!snap.exists()){tx.set(refs[index],protectBankText(rows[index]));written++}})
            if(group==='settlements'&&written)tx.set(settlementRevision(db,uid),{revision:increment(1)},{merge:true})
            return written
          })
          added[group]+=count
        }
      }
      return added
    },
    // ملف الحساب: مستند واحد `users/{uid}/profile/main` (OVERRIDES §26) — من السيرفر بس زي باقي النسخة
    async readProfile(){
      const snap=await getDocFromServer(doc(db,'users',uid,'profile','main'))
      return snap.exists()?snap.data():null
    },
    async addProfileIfMissing(profile:BackupRow){
      const ref=doc(db,'users',uid,'profile','main')
      return runTransaction(db,async tx=>{
        if((await tx.get(ref)).exists())return false
        tx.set(ref,profile);return true
      })
    },
  }
}
