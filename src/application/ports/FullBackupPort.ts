import type { BackupRow, FullBackupData } from '../../domain/fullBackup'
export interface FullBackupPort {
  /** Complete, paginated server read; never silently falls back to a partial offline cache. */
  read():Promise<FullBackupData>
  /** Add missing IDs only. May span batches; retry safely after interruption, never replace existing. */
  addMissing(data:FullBackupData):Promise<Record<string,number>>
  /** ملف الحساب (مستند واحد) أو `null` لو لسه ما اتحفظش — OVERRIDES §26. */
  readProfile():Promise<BackupRow|null>
  /** بيكتب ملف الحساب **لو الحساب مالوش ملف بس**، وعمره ما يكتب فوق الموجود. `true` = اتكتب. */
  addProfileIfMissing(profile:BackupRow):Promise<boolean>
}
export type BackupDigest=(text:string)=>Promise<string>
