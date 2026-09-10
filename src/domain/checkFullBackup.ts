import { BACKUP_GROUPS,BACKUP_LABELS,BACKUP_RELATIONS,backupRowId,type FullBackupData,type BackupRow } from './fullBackup'
import { isValidIsoDate } from './period'
import { ALL_ECONOMIC_KINDS } from './entities/economicKind'
const required:Record<string,string[]>={
  wallets:['name','currency','kind','openingBalanceMinor','openingAt'],categories:['name','iconKey','lightColor','darkColor','active','order'],
  merchants:['displayName','normalizedName'],rules:['priority','matchText','matchMode','categoryId','enabled'],people:['name','archived'],
  assets:['name','kind','unitLabel','currency','archived'],tags:['displayName','normalizedName'],
  budgets:['periodKey','periodStart','periodEnd','totalLimitMinor','thresholdPercent','createdAt','updatedAt'],
  recurringItems:['name','merchantKey','kind','cycleMonths','expectedMinor','currency','nextDueAt','active','confirmed'],
  importBatches:['sourceType','fileHash','fileName','importedAt','state','counts'],
  transactions:['occurredAt','datePrecision','sourceOrder','economicKind','economicKindConfirmed','observedDirection','amountMinor','currency','categoryConfirmed','excludedFromBudget','reviewState','isCashTagged','createdAt','updatedAt'],
  obligations:['personId','originTransactionId','kind','originalMinor','currency'],allocations:['transactionId','personId','allocationKind','amountMinor','currency'],
  settlements:['transactionId','obligationId','amountMinor'],sourceRecords:['batchId','accountIdentity','sourceHash','originalRowIndex','rawLine','matchingState','reason'],
  transactionTags:['transactionId','tagId'],categoryBudgets:['budgetId','categoryId','limitMinor','notifyEnabled','thresholdPercent'],
  assetLots:['assetId','purchasedAt','quantity','principalMinor','feeMinor'],assetSales:['assetId','soldAt','quantity','grossProceedsMinor','feeMinor'],
  assetPrices:['assetId','pricePerUnitMinor','asOf','source'],notificationReceipts:['eventKey','threshold','periodStart','sentAt'],
}
const booleans=new Set(['active','archived','enabled','confirmed','economicKindConfirmed','categoryConfirmed','excludedFromBudget','isCashTagged','notifyEnabled'])
const numeric=new Set(['order','priority','sourceOrder','cycleMonths','originalRowIndex','quantity','thresholdPercent'])
const dates=new Set(['occurredAt','openingAt','periodStart','periodEnd','purchasedAt','soldAt','asOf','nextDueAt'])
/** Runtime validation before financial data can reach repositories; rejects incomplete graphs. */
export function checkFullBackupData(data:unknown):asserts data is FullBackupData {
  if(!data||typeof data!=='object'||Array.isArray(data))throw Error('بيانات النسخة غير صالحة')
  const value=data as FullBackupData
  for(const group of BACKUP_GROUPS){
    if(!Array.isArray(value[group]))throw Error('مجموعة ناقصة: '+BACKUP_LABELS[group])
    const ids=new Set<string>()
    for(const row of value[group]){
      if(!row||typeof row!=='object'||Array.isArray(row))throw Error('سجل غير صالح: '+group)
      const id=backupRowId(group,row)
      if(!id||id.length>1000||(group!=='notificationReceipts'&&id.includes('/'))||ids.has(id))throw Error('معرّف غير صالح أو مكرر: '+group)
      ids.add(id)
      for(const field of required[group])if(!(field in row))throw Error('حقل ناقص: '+group+'.'+field)
      validateFields(row,group)
      if(group==='importBatches'&&row.state==='staged')throw Error('فيه استيراد غير مكتمل. افتح التطبيق واستكمل تنظيفه قبل النسخ')
      if(group==='budgets'&&row.id!==row.periodKey)throw Error('معرّف الميزانية مختلف عن الفترة')
    }
  }
  const ids=Object.fromEntries(BACKUP_GROUPS.map(group=>[group,new Set(value[group].map(row=>backupRowId(group,row)))]))
  for(const group of BACKUP_GROUPS)for(const row of value[group])for(const [field,target] of Object.entries(BACKUP_RELATIONS[group]??{})){
    if(row[field]!=null&&!ids[target].has(String(row[field])))throw Error('علاقة ناقصة: '+group+'.'+field)
  }
}
function validateFields(row:BackupRow,group:string){
  const special=new Set([...numeric,...booleans,'counts','parentId','threshold'])
  for(const key of required[group])if(!special.has(key)&&!key.endsWith('Minor')&&typeof row[key]!=='string')throw Error('نص غير صالح: '+group+'.'+key)
  for(const [key,value] of Object.entries(row)){
    if(key.endsWith('Minor')||numeric.has(key)){
      if(value===null&&['totalLimitMinor','thresholdPercent'].includes(key))continue
      if(typeof value!=='number'||!Number.isSafeInteger(value))throw Error('مبلغ أو عدد غير صحيح: '+key)
      if(key.endsWith('Minor')&&!['openingBalanceMinor','statedBalanceMinor'].includes(key)&&value<0)throw Error('مبلغ سالب: '+key)
    }else if(booleans.has(key)&&typeof value!=='boolean')throw Error('قيمة منطقية غير صالحة: '+key)
    else if(dates.has(key)&&(typeof value!=='string'||!isValidIsoDate(value)))throw Error('تاريخ غير صالح: '+key)
  }
  if('currency'in row&&(typeof row.currency!=='string'||!/^[A-Z]{3}$/.test(row.currency)))throw Error('عملة غير صالحة')
  if('accountLast4'in row&&(typeof row.accountLast4!=='string'||!/^\d{4}$/.test(row.accountLast4)))throw Error('المسموح آخر أربعة أرقام فقط')
  if(group==='transactions'&&!['in','out'].includes(String(row.observedDirection)))throw Error('اتجاه عملية غير صالح')
  if(group==='transactions'&&Number(row.amountMinor)<=0)throw Error('مبلغ العملية لازم يكون موجبًا')
  if(group==='recurringItems'&&![1,3,12].includes(Number(row.cycleMonths)))throw Error('دورة اشتراك غير صالحة')
  if(group==='transactions'&&!ALL_ECONOMIC_KINDS.includes(row.economicKind as never))throw Error('نوع اقتصادي غير صالح')
  const enums:Record<string,Record<string,string[]>>={
    wallets:{kind:['bank','cash','own_abroad','digital_wallet']},
    transactions:{reviewState:['confirmed','suggested','needs_review'],datePrecision:['day','minute']},
    obligations:{kind:['receivable','loan_payable','custody_payable']},allocations:{allocationKind:['receivable','gift']},
    assets:{kind:['gold','stock','fund','digital','other']},assetPrices:{source:['manual','feed']},
    recurringItems:{kind:['subscription','bill']},rules:{matchMode:['contains','startsWith','exact']},
    importBatches:{state:['staged','committed','reverted'],sourceType:['csv_preview','csv_legacy','pdf_alrajhi','sms']},
    sourceRecords:{matchingState:['new','duplicate','similar','conflict','invalid']},
  }
  for(const [field,allowed] of Object.entries(enums[group]??{}))if(!allowed.includes(String(row[field])))throw Error('قيمة غير مدعومة: '+group+'.'+field)
  if(group==='importBatches')for(const key of ['total','imported','duplicates','similar','conflicts','invalid']){
    const value=(row.counts as BackupRow)?.[key]
    if(typeof value!=='number'||!Number.isSafeInteger(value)||value<0)throw Error('عداد استيراد غير صالح')
  }
}
