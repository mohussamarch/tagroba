import { BACKUP_GROUPS,BACKUP_LABELS,canonicalBackup,type FullBackupFile } from '../../domain/fullBackup'
import { checkFullBackupData } from '../../domain/checkFullBackup'
import { mergeFullBackup } from '../../domain/mergeFullBackup'
import { checkBackupFinance } from '../../domain/checkBackupFinance'
import type {FullBackupData} from '../../domain/fullBackup'
import type { FullBackupPort,BackupDigest } from '../ports/FullBackupPort'
export function makeFullBackup(port:FullBackupPort,digest:BackupDigest){
  async function check(raw:string):Promise<FullBackupFile>{
    if(raw.length>40_000_000)throw Error('النسخة أكبر من الحد المدعوم (40 ميجابايت)')
    let file:FullBackupFile
    try{file=JSON.parse(raw)}catch{throw Error('الملف مش JSON صالح')}
    if(!file||file.app!=='masroufy'||file.schemaVersion!==2)throw Error('اختر نسخة شاملة بإصدار 2؛ للنسخ القديمة استخدم استعادة النسخة القديمة')
    checkFullBackupData(file.data)
    checkBackupFinance(file.data)
    for(const key of BACKUP_GROUPS)if(file.counts?.[key]!==file.data[key].length)throw Error('عدد السجلات غير مطابق: '+BACKUP_LABELS[key])
    if(await digest(canonicalBackup(file.data))!==file.checksum)throw Error('بصمة سلامة النسخة غير مطابقة؛ الملف اتغير أو اتلف')
    return file
  }
  async function create(exportedAt:string):Promise<FullBackupFile>{
    const data=await port.read();checkFullBackupData(data);checkBackupFinance(data)
    return {app:'masroufy',schemaVersion:2,exportedAt,data,checksum:await digest(canonicalBackup(data)),counts:Object.fromEntries(BACKUP_GROUPS.map(key=>[key,data[key].length]))}
  }
  async function plan(raw:string){
    const file=await check(raw),existing=await port.read(),additions=mergeFullBackup(file.data,existing)
    validateMerge(existing,additions)
    const lines=BACKUP_GROUPS.map(key=>({key,label:BACKUP_LABELS[key],incoming:file.data[key].length,toAdd:additions[key].length,skipped:file.data[key].length-additions[key].length,note:null}))
    return {file,lines,totalToAdd:lines.reduce((sum,line)=>sum+line.toAdd,0),warnings:[
      'الموجود يفضل كما هو. روابط العمليات المتكررة تُنقل لمعرّفات العمليات الموجودة.',
      'الاستعادة على دفعات: لو الاتصال انقطع قد يُحفظ جزء؛ أعد نفس النسخة لاستكمال الناقص دون الكتابة فوق الموجود. تجنب التعديل من جهاز آخر أثناء النسخ والاستعادة.',
      'النسخة تشمل بيانات الحساب؛ أذونات الهاتف ورسائل المراجعة المحلية وإعدادات المظهر لا تُستعاد منها.',
    ]}
  }
  async function apply(input:FullBackupFile){
    const file=await check(JSON.stringify(input)),existing=await port.read()
    const additions=mergeFullBackup(file.data,existing);validateMerge(existing,additions)
    const added=await port.addMissing(additions)
    return {added,totalAdded:Object.values(added).reduce((a,b)=>a+b,0)}
  }
  return {create,plan,apply}
}
function validateMerge(existing:FullBackupData,additions:FullBackupData){
  const combined=Object.fromEntries(BACKUP_GROUPS.map(key=>[key,[...existing[key],...additions[key]]])) as FullBackupData
  checkFullBackupData(combined);checkBackupFinance(combined)
}
