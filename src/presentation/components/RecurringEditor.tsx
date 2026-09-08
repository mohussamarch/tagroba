import { useState, type FormEvent } from 'react'
import type { RecurringItem } from '../../domain/entities/recurring'
import { parseMoney } from '../../domain/money'
import { formatAmount } from '../../domain/formatMoney'
type Input=Omit<RecurringItem,'id'|'confirmed'> & {id?:string}
export function RecurringEditor({initial,today,choices,busy,onSave,onCancel}:{
 initial:Partial<RecurringItem>;today:string;choices:{key:string;name:string}[];busy:boolean;
 onSave:(item:Input)=>Promise<void>;onCancel:()=>void
}) {
 const [name,setName]=useState(initial.name??'')
 const [key,setKey]=useState(initial.merchantKey??'')
 const [kind,setKind]=useState<RecurringItem['kind']>(initial.kind??'subscription')
 const [cycle,setCycle]=useState<1|3|12>(initial.cycleMonths??1)
 const [value,setValue]=useState(initial.expectedMinor?formatAmount(initial.expectedMinor,initial.currency??'SAR',{grouping:false}):'')
 const [currency,setCurrency]=useState<RecurringItem['currency']>(initial.currency??'SAR')
 const [due,setDue]=useState(initial.nextDueAt??today)
 const [active,setActive]=useState(initial.active??true)
 const [error,setError]=useState('')
 async function submit(e:FormEvent) {
  e.preventDefault();setError('')
  try {await onSave({...(initial.id?{id:initial.id}:{}),name,merchantKey:key||'manual:'+name.trim(),kind,
   cycleMonths:cycle,expectedMinor:parseMoney(value,currency),currency,nextDueAt:due,active})}
  catch(e) {setError(e instanceof Error?e.message:String(e))}
 }
 return <form className="card" onSubmit={submit} key={initial.id??initial.merchantKey??'new'}>
  <h3>بيانات الالتزام</h3>
  <label className="sheet__field">اسم الخدمة<input className="sheet__input" required maxLength={120} value={name} onChange={e=>setName(e.target.value)}/></label>
  <label className="sheet__field">ربط بتاجر من السجل<select className="sheet__input" value={key} onChange={e=>setKey(e.target.value)}>
   <option value="">بدون ربط — المدفوع غير متاح</option>
   {initial.merchantKey && !choices.some(c=>c.key===initial.merchantKey) && <option value={initial.merchantKey}>{initial.name}</option>}
   {choices.map(c=><option key={c.key} value={c.key}>{c.name}</option>)}
  </select></label>
  <label className="sheet__field">النوع<select className="sheet__input" value={kind} onChange={e=>setKind(e.target.value as RecurringItem['kind'])}>
   <option value="subscription">اشتراك</option><option value="bill">فاتورة</option></select></label>
  <label className="sheet__field">الدورة<select className="sheet__input" value={cycle} onChange={e=>setCycle(Number(e.target.value) as 1|3|12)}>
   <option value="1">شهري</option><option value="3">كل ٣ شهور</option><option value="12">سنوي</option></select></label>
  <label className="sheet__field">قيمة الدورة المتوقعة<input className="sheet__input" required inputMode="decimal" value={value} onChange={e=>setValue(e.target.value)}/></label>
  <label className="sheet__field">العملة<select className="sheet__input" value={currency} onChange={e=>setCurrency(e.target.value as RecurringItem['currency'])}>
   {['SAR','EGP','USD','EUR','GBP','AED'].map(c=><option key={c}>{c}</option>)}</select></label>
  <label className="sheet__field">الموعد القادم<input className="sheet__input" type="date" required value={due} onChange={e=>setDue(e.target.value)}/></label>
  <label><input type="checkbox" checked={active} onChange={e=>setActive(e.target.checked)}/> التزام نشط</label>
  <p className="sheet__hint">الفاتورة المتغيرة: اكتب قيمة متوقعة، مش مبلغًا مؤكدًا. الحفظ لا يخصم من المحفظة.</p>
  {error && <p role="alert">{error}</p>}
  <div className="settings__row"><button className="btn" disabled={busy}>{busy?'بنحفظ…':'تأكيد وحفظ'}</button>
   <button className="btn btn--quiet" type="button" onClick={onCancel}>إلغاء</button></div>
 </form>
}
