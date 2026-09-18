/** All persisted account collections. Device permissions, pending SMS and UI cache are excluded. */
export const BACKUP_GROUPS = ['wallets','categories','merchants','rules','people','assets','tags','budgets',
  'recurringItems','importBatches','transactions','obligations','allocations','settlements',
  'sourceRecords','transactionTags','categoryBudgets','assetLots','assetSales','assetPrices','notificationReceipts',
  'projects','projectLinks','projectRules'] as const
export type BackupGroup = typeof BACKUP_GROUPS[number]
/** مجموعات اتضافت بعد أول نسخ إصدار 2 (المشاريع §34): نسخة قديمة من غيرها بتتقري كأنها فاضية. */
export const LATER_BACKUP_GROUPS:readonly BackupGroup[]=['projects','projectLinks','projectRules']
export type BackupRow = Record<string, unknown>
export type FullBackupData = Record<BackupGroup, BackupRow[]>
export interface FullBackupFile {
  app:'masroufy'; schemaVersion:2; exportedAt:string; checksum:string
  counts:Record<string,number>; data:FullBackupData
  /** ملف الحساب — `backupProfile.ts`. مش موجود في النسخ القديمة، و`null` = الحساب مالوش ملف. */
  profile?:BackupRow|null
}
export function emptyBackupData():FullBackupData {
  return Object.fromEntries(BACKUP_GROUPS.map(group=>[group,[] as BackupRow[]])) as FullBackupData
}
export function backupRowId(group:BackupGroup,row:BackupRow):string {
  return String(row[group==='assetPrices'?'assetId':group==='notificationReceipts'?'eventKey':'id']??'')
}
/** Sort object keys only; preserve row order and numeric values for checksum verification. */
export function canonicalBackup(value:unknown):string {
  if(Array.isArray(value))return '['+value.map(canonicalBackup).join(',')+']'
  if(value&&typeof value==='object')return '{'+Object.keys(value).sort().map(key=>JSON.stringify(key)+':'+canonicalBackup((value as BackupRow)[key])).join(',')+'}'
  return JSON.stringify(value)
}
export const BACKUP_LABELS:Record<BackupGroup,string>={wallets:'المحافظ',categories:'التصنيفات',merchants:'التجار',rules:'القواعد',people:'الأشخاص',assets:'الأصول',tags:'الوسوم',budgets:'الميزانيات',recurringItems:'الاشتراكات والفواتير',importBatches:'دفعات الاستيراد',transactions:'العمليات',obligations:'الديون والأمانات',allocations:'تخصيصات الأشخاص',settlements:'التسويات',sourceRecords:'مصادر العمليات',transactionTags:'روابط الوسوم',categoryBudgets:'سقوف التصنيفات',assetLots:'مشتريات الأصول',assetSales:'مبيعات الأصول',assetPrices:'أسعار الأصول',notificationReceipts:'حالات قراءة التنبيهات',projects:'المشاريع',projectLinks:'روابط المشاريع',projectRules:'قواعد المشاريع'}
export const BACKUP_RELATIONS:Partial<Record<BackupGroup,Record<string,BackupGroup>>>={
  categories:{parentId:'categories'},merchants:{verifiedCategoryId:'categories'},rules:{categoryId:'categories'},
  transactions:{walletId:'wallets',transferToWalletId:'wallets',categoryId:'categories',merchantId:'merchants'},
  obligations:{personId:'people',originTransactionId:'transactions'},allocations:{personId:'people',transactionId:'transactions'},
  settlements:{obligationId:'obligations',transactionId:'transactions'},sourceRecords:{batchId:'importBatches',transactionId:'transactions'},
  transactionTags:{tagId:'tags',transactionId:'transactions'},categoryBudgets:{budgetId:'budgets',categoryId:'categories'},
  assetLots:{assetId:'assets',transactionId:'transactions'},assetSales:{assetId:'assets',transactionId:'transactions'},assetPrices:{assetId:'assets'},
  projectLinks:{projectId:'projects',transactionId:'transactions'},projectRules:{projectId:'projects'},
}
