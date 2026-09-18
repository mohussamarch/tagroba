import { BACKUP_GROUPS,BACKUP_LABELS,LATER_BACKUP_GROUPS,type FullBackupFile } from '../../domain/fullBackup'
import { checkFullBackupData } from '../../domain/checkFullBackup'
import { mergeFullBackup } from '../../domain/mergeFullBackup'
import { checkBackupFinance } from '../../domain/checkBackupFinance'
import { backupChecksumText,checkBackupProfile } from '../../domain/backupProfile'
import { normalizeBudgetIds,pointLinesAtLiveBudgets } from '../../domain/backupBudgetIds'
import type {FullBackupData} from '../../domain/fullBackup'
import type { FullBackupPort,BackupDigest } from '../ports/FullBackupPort'
export function makeFullBackup(port:FullBackupPort,digest:BackupDigest){
  async function check(raw:string):Promise<FullBackupFile>{
    if(raw.length>40_000_000)throw Error('النسخة أكبر من الحد المدعوم (40 ميجابايت)')
    let file:FullBackupFile
    try{file=JSON.parse(raw)}catch{throw Error('الملف مش JSON صالح')}
    if(!file||file.app!=='masroufy'||file.schemaVersion!==2)throw Error('اختر نسخة شاملة بإصدار 2؛ للنسخ القديمة استخدم استعادة النسخة القديمة')
    // البصمة على اللي اتصدّر فعلًا؛ نسخة أقدم من المشاريع مالهاش مجموعاتها فبتتقري فاضية (OVERRIDES §34)
    const signed=backupChecksumText(file.data,file.profile)
    const missing=file.data&&typeof file.data==='object'?LATER_BACKUP_GROUPS.filter(key=>!(key in file.data)):[]
    for(const key of missing){file.data[key]=[];file.counts={...file.counts,[key]:0}}
    checkFullBackupData(file.data)
    checkBackupFinance(file.data)
    checkBackupProfile(file.profile)
    for(const key of BACKUP_GROUPS)if(file.counts?.[key]!==file.data[key].length)throw Error('عدد السجلات غير مطابق: '+BACKUP_LABELS[key])
    if(await digest(signed)!==file.checksum)throw Error('بصمة سلامة النسخة غير مطابقة؛ الملف اتغير أو اتلف')
    // النسخة اتأكدت؛ النسخة المكمّلة بتتبصم تاني عشان التطبيق بعد المعاينة يتأكد منها هي
    if(missing.length)file.checksum=await digest(backupChecksumText(file.data,file.profile))
    return file
  }
  async function create(exportedAt:string):Promise<FullBackupFile>{
    // ميزانيات قديمة بمعرّف عشوائي بتاخد مفتاح فترتها في النسخة بس (backupBudgetIds.ts)
    const data=normalizeBudgetIds(await port.read());checkFullBackupData(data);checkBackupFinance(data)
    const profile=await port.readProfile();checkBackupProfile(profile)
    return {app:'masroufy',schemaVersion:2,exportedAt,data,profile,checksum:await digest(backupChecksumText(data,profile)),counts:Object.fromEntries(BACKUP_GROUPS.map(key=>[key,data[key].length]))}
  }
  async function plan(raw:string){
    const file=await check(raw),existing=normalizeBudgetIds(await port.read()),additions=mergeFullBackup(file.data,existing)
    validateMerge(existing,additions)
    const lines=BACKUP_GROUPS.map(key=>({key,label:BACKUP_LABELS[key],incoming:file.data[key].length,toAdd:additions[key].length,skipped:file.data[key].length-additions[key].length,note:null}))
    const profile={incoming:!!file.profile,toAdd:!!file.profile&&!(await port.readProfile())}
    return {file,lines,profile,totalToAdd:lines.reduce((sum,line)=>sum+line.toAdd,0)+(profile.toAdd?1:0),warnings:[
      'الموجود يفضل كما هو. روابط العمليات المتكررة تُنقل لمعرّفات العمليات الموجودة.',
      'ملف الحساب (الاسم والمرتب ويوم الراتب) بيتضاف بس لو الحساب مالوش ملف — ما بيتكتبش فوق الموجود.',
      'الاستعادة على دفعات: لو الاتصال انقطع قد يُحفظ جزء؛ أعد نفس النسخة لاستكمال الناقص دون الكتابة فوق الموجود. تجنب التعديل من جهاز آخر أثناء النسخ والاستعادة.',
      'النسخة تشمل بيانات الحساب؛ أذونات الهاتف ورسائل المراجعة المحلية وإعدادات المظهر لا تُستعاد منها.',
    ]}
  }
  async function apply(input:FullBackupFile){
    const file=await check(JSON.stringify(input)),live=await port.read(),existing=normalizeBudgetIds(live)
    const additions=mergeFullBackup(file.data,existing);validateMerge(existing,additions)
    // سقوف التصنيفات المضافة لحساب ميزانيته بالمعرّف القديم بتشاور على معرّفه الحقيقي عشان تبان
    const added=await port.addMissing({...additions,categoryBudgets:pointLinesAtLiveBudgets(additions.categoryBudgets,live.budgets)})
    // ملف الحساب آخر حاجة، ولو موجود ما يتكتبش فوقه (OVERRIDES §26)
    const profileAdded=file.profile?await port.addProfileIfMissing(file.profile):false
    const addedByGroup:Record<string,number>={...added,profile:profileAdded?1:0}
    return {added:addedByGroup,totalAdded:Object.values(added).reduce((a,b)=>a+b,0)+(profileAdded?1:0)}
  }
  return {create,plan,apply}
}
function validateMerge(existing:FullBackupData,additions:FullBackupData){
  const combined=Object.fromEntries(BACKUP_GROUPS.map(key=>[key,[...existing[key],...additions[key]]])) as FullBackupData
  checkFullBackupData(combined);checkBackupFinance(combined)
}
