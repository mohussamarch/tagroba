import { canonicalBackup, type BackupRow, type FullBackupData } from './fullBackup'
import { DEPENDENT_KINDS, MAX_NAME_LENGTH } from './userProfile'

/**
 * ملف الحساب في النسخة الشاملة — مستند واحد `users/{uid}/profile/main` (OVERRIDES §26:
 * «ومعاها ضم ملف المستخدم للنسخة الشاملة»).
 *
 * **مش جوه `BACKUP_GROUPS` عن قصد:** أدوات إصلاح المعرّفات وتنظيف البقايا بتلف على المجموعات دي،
 * ومستند الملف مالوش حقل `id` فكانت ممكن تعتبره معرّف مكسور. فالملف جزء منفصل في الملف،
 * والنسخ القديمة اللي مالهاش الجزء ده بتفضل مقبولة.
 */
export function checkBackupProfile(profile: unknown): asserts profile is BackupRow | null | undefined {
  if (profile === undefined || profile === null) return
  if (typeof profile !== 'object' || Array.isArray(profile)) throw Error('ملف الحساب في النسخة غير صالح')
  const row = profile as BackupRow
  const bad = (field: string) => { throw Error('ملف الحساب في النسخة غير صالح: ' + field) }
  const has = (field: string) => field in row && row[field] !== null

  if (has('displayName') && (typeof row.displayName !== 'string' || row.displayName.trim().length > MAX_NAME_LENGTH)) bad('displayName')
  if (has('salaryMinor') && (!Number.isSafeInteger(row.salaryMinor) || (row.salaryMinor as number) < 0)) bad('salaryMinor')
  if ('payday' in row && (!Number.isInteger(row.payday) || (row.payday as number) < 1 || (row.payday as number) > 31)) bad('payday')
  if (has('gender') && row.gender !== 'male' && row.gender !== 'female') bad('gender')
  for (const field of ['supportsDependents', 'hasCar', 'renter', 'domesticWorker', 'business']) {
    if (has(field) && typeof row[field] !== 'boolean') bad(field)
  }
  if (has('dependentKinds') && (!Array.isArray(row.dependentKinds)
    || !row.dependentKinds.every((kind) => (DEPENDENT_KINDS as readonly unknown[]).includes(kind)))) bad('dependentKinds')
  if (has('onboardedAt') && typeof row.onboardedAt !== 'string') bad('onboardedAt')
}

/**
 * النص اللي بتتحسب منه بصمة السلامة. النسخة القديمة (من غير `profile` خالص) بصمتها على البيانات بس
 * زي ما كانت، والجديدة البصمة بتغطي الملف كمان — فأي تعديل فيه بيتكشف.
 */
export function backupChecksumText(data: FullBackupData, profile: BackupRow | null | undefined): string {
  return profile === undefined ? canonicalBackup(data) : canonicalBackup({ data, profile })
}
