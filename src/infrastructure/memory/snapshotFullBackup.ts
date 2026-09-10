import {BACKUP_GROUPS,backupRowId,emptyBackupData,type BackupGroup,type BackupRow} from '../../domain/fullBackup'
import type {FullBackupPort} from '../../application/ports/FullBackupPort'
interface SnapshotStore {snapshot():unknown;restore(state:never):void}
interface Binding {read():Promise<BackupRow[]>;write(rows:BackupRow[]):Promise<void>}
/** Connect the same repositories used by demo screens, rather than an unrelated empty backup store. */
export function snapshotFullBackup(stores:Partial<Record<BackupGroup,SnapshotStore>>,extras:Partial<Record<BackupGroup,Binding>>):FullBackupPort {
  const bindings={...extras}
  for(const group of BACKUP_GROUPS){
    const store=stores[group];if(!store)continue
    bindings[group]={
      read:async()=>{const state=store.snapshot();return structuredClone(state instanceof Map?[...state.values()]:state) as BackupRow[]},
      write:async rows=>{const state=store.snapshot();store.restore((state instanceof Map?new Map(rows.map(row=>[backupRowId(group,row),row])):rows) as never)},
    }
  }
  return {read:async()=>{
    const data=emptyBackupData()
    for(const group of BACKUP_GROUPS){if(!bindings[group])throw Error('Missing demo backup binding: '+group);data[group]=await bindings[group]!.read()}
    return data
  },addMissing:async data=>{
    const added:Record<string,number>={}
    for(const group of BACKUP_GROUPS){
      const binding=bindings[group];if(!binding)throw Error('Missing demo backup binding: '+group)
      const old=await binding.read(),ids=new Set(old.map(row=>backupRowId(group,row)))
      const fresh=data[group].filter(row=>!ids.has(backupRowId(group,row)))
      await binding.write([...old,...fresh]);added[group]=fresh.length
    }
    return added
  }}
}
