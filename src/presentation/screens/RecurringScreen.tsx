import { useEffect, useState } from 'react'
import type { UserContainer } from '../../app/container'
import type { RecurringView } from '../../application/useCases/manageRecurring'
import type { RecurringItem } from '../../domain/entities/recurring'
import { formatAmount } from '../../domain/formatMoney'
import { ErrorNotice } from '../components/ErrorNotice'
import { RecurringEditor } from '../components/RecurringEditor'
import './ImportSheet.css'

export function RecurringScreen({user,today,hidden,onClose}: {
  user:UserContainer; today:string; hidden:boolean; onClose:()=>void
}) {
  const [view,setView]=useState<RecurringView|null>(null)
  const [error,setError]=useState<unknown>(null)
  const [busy,setBusy]=useState(false)
  const [editing,setEditing]=useState<Partial<RecurringItem>|null>(null)
  const amount=(v:number|null,c:RecurringItem['currency'])=>hidden?'••••':v===null?'غير متاح':formatAmount(v,c)+' '+c
  async function load() { setView(await user.manageRecurring.load(today)) }
  useEffect(()=>{void load().catch(setError)},[user,today])
  async function save(item: Omit<RecurringItem,'id'|'confirmed'> & {id?:string}) {
    setBusy(true);setError(null)
    try { await user.manageRecurring.save(item);setEditing(null);await load() }
    catch(e) {setError(e)}
    finally {setBusy(false)}
  }
  return <div className="sheet" role="dialog" aria-modal="true" aria-label="الاشتراكات والفواتير">
    <section className="sheet__panel">
      <header className="sheet__head"><h2 className="sheet__title">الاشتراكات والفواتير</h2>
        <button className="btn btn--quiet" onClick={onClose}>إغلاق</button></header>
      <div className="sheet__body">
        <p>تأكيد الاشتراك مش بيسجّل مصروف جديد. المدفوع بيتحسب من العمليات الموجودة فقط.</p>
        {error!=null && <ErrorNotice cause={error}/>}
        {!view && !error && <p role="status">بنراجع آخر ١٢ شهر…</p>}
        {!view && error!=null && <button className="btn" onClick={()=>{setError(null);void load().catch(setError)}}>حاول تاني</button>}
        {view && <>
          <p className="sheet__hint">الفترة المقروءة: {view.from} إلى {view.to}. المدفوع هو الخصومات المسجلة للخدمة؛ اكتماله يعتمد على الكشوف المستوردة.</p>
          <button className="btn" onClick={()=>setEditing({})} disabled={busy}>إضافة اشتراك أو فاتورة</button>
          {editing && <RecurringEditor key={editing.id??editing.merchantKey??'new'} initial={editing} today={today} choices={view.choices}
            busy={busy} onSave={save} onCancel={()=>setEditing(null)}/>}
          <h3>المؤكدة ({view.items.length})</h3>
          {!view.items.length && <p>لسه مفيش التزامات مؤكدة. أضف خدمة أو راجع الاقتراحات تحت.</p>}
          {view.items.map(row=><section className="card" key={row.item.id}>
            <h3>{row.item.name} {!row.item.active && '— متوقف'}</h3>
            <p>{row.item.kind==='bill'?'فاتورة':'اشتراك'} · {row.item.cycleMonths===1?'شهري':row.item.cycleMonths===3?'كل ٣ شهور':'سنوي'}</p>
            <p>قيمة الدورة: <bdi>{amount(row.item.expectedMinor,row.item.currency)}</bdi></p>
            <p>المتوقع سنويًا: <bdi>{amount(row.annualMinor,row.item.currency)}</bdi></p>
            <p>المدفوع المسجّل آخر ١٢ شهر: <bdi>{amount(row.paidMinor,row.item.currency)}</bdi></p>
            <p>{row.overdue?'الموعد المسجّل فات — راجع السداد وحدّث الموعد:':'الموعد القادم:'} <bdi>{row.item.nextDueAt}</bdi></p>
            <button className="btn btn--quiet" disabled={busy} onClick={()=>setEditing(row.item)}>تعديل الموعد والقيمة والحالة</button>
          </section>)}
          <h3>اقتراحات محتاجة تأكيدك ({view.candidates.length})</h3>
          {!view.candidates.length && <p>مفيش نمط كافي لاقتراح اشتراك جديد. ده مش معناه إن مفيش اشتراكات؛ تقدر تضيفها يدويًا.</p>}
          {view.candidates.map(c=><section className="card" key={c.merchantKey+'|'+c.currency}>
            <h3>{c.name}</h3><p>{c.reason}</p>
            <p>قيمة الدورة المقترحة: <bdi>{amount(c.expectedMinor,c.currency)}</bdi> · {c.transactionIds.length} عمليات</p>
            <button className="btn" disabled={busy} onClick={()=>setEditing({...c,kind:'subscription',active:true})}>راجع وأكّد</button>
          </section>)}
        </>}
      </div>
    </section>
  </div>
}
