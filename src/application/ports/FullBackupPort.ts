import type { FullBackupData } from '../../domain/fullBackup'
export interface FullBackupPort {
  /** Complete, paginated server read; never silently falls back to a partial offline cache. */
  read():Promise<FullBackupData>
  /** Add missing IDs only. May span batches; retry safely after interruption, never replace existing. */
  addMissing(data:FullBackupData):Promise<Record<string,number>>
}
export type BackupDigest=(text:string)=>Promise<string>
