import { BACKUP_GROUPS,backupRowId,emptyBackupData,type FullBackupData } from '../../domain/fullBackup'
import type { FullBackupPort } from '../../application/ports/FullBackupPort'
export function memoryFullBackup(initial:FullBackupData=emptyBackupData()):FullBackupPort {
  const data=structuredClone(initial)
  return {read:async()=>structuredClone(data),addMissing:async incoming=>{
    const added:Record<string,number>={}
    for(const group of BACKUP_GROUPS){
      const ids=new Set(data[group].map(row=>backupRowId(group,row)))
      const rows=incoming[group].filter(row=>!ids.has(backupRowId(group,row)))
      data[group].push(...structuredClone(rows));added[group]=rows.length
    }
    return added
  }}
}
