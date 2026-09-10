import {useState} from 'react'
import type {UserContainer} from '../../app/container'
type Plan=Awaited<ReturnType<UserContainer['fullBackup']['plan']>>
export function FullRestorePanel({user,onDone}:{user:UserContainer;onDone:()=>void}){
  const [plan,setPlan]=useState<Plan|null>(null),[busy,setBusy]=useState(false),[message,setMessage]=useState('')
  async function preview(file:File){
    setBusy(true);setPlan(null);setMessage('')
    try{setPlan(await user.fullBackup.plan(await file.text()))}
    catch(error){setMessage(error instanceof Error?error.message:String(error))}finally{setBusy(false)}
  }
  async function apply(){
    if(!plan)return
    setBusy(true);setMessage('')
    try{const outcome=await user.fullBackup.apply(plan.file);setMessage('تمت إضافة '+outcome.totalAdded+' عنصر بدون استبدال الموجود.');setPlan(null);onDone()}
    catch{setMessage('تعذر إكمال الاستعادة. قد تكون دفعات اتحفظت؛ أعد معاينة نفس الملف لاستكمال الناقص.');setPlan(null);onDone()}
    finally{setBusy(false)}
  }
  return <section className="sheet__field">
    <label>استعادة نسخة شاملة<input type="file" accept=".json,application/json" disabled={busy} onChange={e=>{const file=e.target.files?.[0];if(file)void preview(file)}}/></label>
    {busy&&<p role="status">بنراجع بيانات النسخة…</p>}
    {message&&<p role="status">{message}</p>}
    {plan&&<>
      <p>المعاينة فقط — لم يُحفظ شيء. الإضافات: {plan.totalToAdd}</p>
      <ul>{plan.lines.filter(line=>line.incoming>0).map(line=><li key={line.key}>{line.label}: {line.toAdd} إضافة · {line.skipped} موجود</li>)}</ul>
      {plan.warnings.map(warning=><p className="notice" key={warning}>{warning}</p>)}
      <button className="btn" disabled={busy||plan.totalToAdd===0} onClick={()=>void apply()}>تأكيد دمج النسخة الشاملة</button>
      <button className="btn btn--quiet" disabled={busy} onClick={()=>setPlan(null)}>إلغاء</button>
    </>}
  </section>
}
