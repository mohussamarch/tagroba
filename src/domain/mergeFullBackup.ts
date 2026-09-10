import { BACKUP_GROUPS,BACKUP_RELATIONS,backupRowId,emptyBackupData,type FullBackupData,type BackupRow,type BackupGroup } from './fullBackup'
import { transactionContentKey } from './mergeBackup'
import type { Transaction } from './entities/types'
const naturalKeys:Partial<Record<BackupGroup,string[]>>={budgets:['periodKey'],categoryBudgets:['budgetId','categoryId'],
  obligations:['personId','originTransactionId','kind','currency'],allocations:['personId','transactionId','allocationKind','currency'],
  settlements:['obligationId','transactionId'],transactionTags:['transactionId','tagId']}
function semantic(group:BackupGroup,row:BackupRow):string|undefined {
  if(group==='transactions')return transactionContentKey(row as unknown as Transaction)
  const keys=naturalKeys[group]
  return keys?JSON.stringify(keys.map(key=>row[key])):undefined
}
/** Remap dependent links when the same transaction already exists under another ID. */
export function mergeFullBackup(incoming:FullBackupData,existing:FullBackupData){
  const additions=emptyBackupData(),maps=new Map<string,Map<string,string>>()
  for(const group of BACKUP_GROUPS){
    const remap=new Map<string,string>();maps.set(group,remap)
    const byId=new Map(existing[group].map(row=>[backupRowId(group,row),row]))
    const byContent=new Map<string,BackupRow[]>(),consumed=new Set<string>()
    for(const row of existing[group]){const key=semantic(group,row);if(key)byContent.set(key,[...(byContent.get(key)??[]),row])}
    for(const original of incoming[group]){
      const row={...original},originalId=backupRowId(group,original)
      for(const [field,target] of Object.entries(BACKUP_RELATIONS[group]??{}))if(row[field]!=null)row[field]=maps.get(target)?.get(String(row[field]))??row[field]
      const id=backupRowId(group,row),key=semantic(group,row)
      const found=byId.get(id)??(key?byContent.get(key)?.find(candidate=>!consumed.has(backupRowId(group,candidate))):undefined)
      if(found){const foundId=backupRowId(group,found);consumed.add(foundId);remap.set(originalId,foundId);continue}
      additions[group].push(row);remap.set(originalId,id)
    }
  }
  return additions
}
