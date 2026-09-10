import { useEffect, useState } from 'react'
import type { UserContainer } from '../../app/container'
import type { Wallet } from '../../domain/entities/types'
import type { SmsRow } from '../../application/ports/BankSmsPort'
import { formatAmount } from '../../domain/formatMoney'
import { ImportSheet } from './ImportSheet'

type View = Awaited<ReturnType<UserContainer['smsInbox']['refresh']>>
export function SmsInboxSheet({user,wallets,onClose,onImported}: {
  user:UserContainer;wallets:Wallet[];onClose:()=>void;onImported:()=>void
}) {
  const [view,setView]=useState<View|null>(null), [error,setError]=useState('')
  const [busy,setBusy]=useState(false), [senders,setSenders]=useState('AlRajhiBank')
  const [selected,setSelected]=useState<Set<string>>(new Set())
  const [review,setReview]=useState<{rows:SmsRow[];content:string;items:{id:string;lineNumber:number}[]}|null>(null)
  async function run(action:()=>Promise<View>) {
    setBusy(true);setError('')
    try {const next=await action();setView(next);if(next.senders.length)setSenders(next.senders.join(', '))}
    catch(cause){setError(cause instanceof Error?cause.message:String(cause))}
    finally{setBusy(false)}
  }
  useEffect(()=>{void run(()=>user.smsInbox.refresh())},[user])
  function startReview() {
    const rows:SmsRow[]=[],items:{id:string;lineNumber:number}[]=[]
    for(const item of view?.items??[])if(selected.has(item.id)&&item.parsed.ok){
      rows.push(item.parsed.row);items.push({id:item.id,lineNumber:item.parsed.row.lineNumber})
    }
    if(rows.length)setReview({rows,items,content:JSON.stringify(rows)})
  }
  if(review)return <ImportSheet user={user} wallets={wallets} initialSms={review}
    onClose={()=>setReview(null)} onImported={()=>{setReview(null);setSelected(new Set());onImported();void run(()=>user.smsInbox.refresh())}}
    onRowsImported={lines=>user.smsInbox.imported(review.items,lines)}/>
  const valid=view?.items.filter(item=>item.parsed.ok)??[]
  return <div className="sheet" role="dialog" aria-modal="true" aria-label="رسائل جديدة">
    <div className="sheet__panel"><header className="sheet__head"><h2 className="sheet__title">رسائل جديدة</h2>
      <button className="iconBtn" onClick={onClose} disabled={busy} aria-label="إغلاق">✕</button></header>
      <div className="sheet__body" style={{display:'grid',gap:16}}>
        <p>راجع الرسائل هنا، واختار اللي تحب تسجله. مفيش عملية بتتحفظ قبل تأكيدك.</p>
        {view?.enabled&&view.permission?<p role="status">القراءة التلقائية مفعّلة</p>:view?.enabled?<p role="alert">إذن أندرويد غير متاح. الرسائل المعلقة محفوظة؛ أعد تفعيل الإذن لاستكمال القراءة.</p>:<p>القراءة التلقائية متوقفة</p>}
        <details open={view?.count===0&&!view.enabled}>
        <summary>إعداد القراءة التلقائية وإيقافها</summary>
        <label>أسماء مرسلي البنك (افصل بفاصلة)<input className="sheet__input" value={senders} onChange={e=>setSenders(e.target.value)} disabled={busy} style={{width:'100%'}}/></label>
        <p>التفعيل الأول يبدأ من دلوقتي. الرسائل الأقدم متاحة من «إضافة ← رسائل البنك». الإيقاف يحافظ على الرسائل المعلقة؛ تسجيل الخروج يوقف الجمع لحماية حسابك.</p>
        <button className="btn" disabled={busy} onClick={()=>void run(()=>user.smsInbox.enable(senders.split(/[,،\n]/)))}>
          {view?.enabled&&view.permission?'حفظ المرسلين':'موافقة وتفعيل القراءة التلقائية'}</button>
        {view?.enabled&&<button className="btn btn--quiet" disabled={busy} onClick={()=>void run(()=>user.smsInbox.disable())}>إيقاف القراءة التلقائية</button>}
        <p className="notice">الرسائل اللي توصل والتطبيق مقفول تظهر للمراجعة عند فتحه. لو أندرويد أوقف التطبيق، بنحاول استكمال الرسائل الفائتة وقت الفتح.</p>
        </details>
        <button className="btn btn--quiet" disabled={busy} onClick={()=>void run(()=>user.smsInbox.refresh())}>{busy?'بنراجع الرسائل…':'تحديث الرسائل'}</button>
        {error&&<p role="alert">{error}</p>}
        {view&&<>
          <h3>{view.count} رسالة معلقة</h3>
          {(view.more||view.count>200)&&<p>نعرض حتى 200 رسالة للمراجعة. راجعها ثم حدّث لعرض الباقي؛ لا نحذف الرسائل غير المعروضة.</p>}
          <button className="btn btn--quiet" disabled={busy||!valid.length} onClick={()=>setSelected(new Set(valid.map(item=>item.id)))}>تحديد كل القابل للمراجعة</button>
          {view.items.map(item=><section className="card" key={item.id}>
            <strong>{item.sender}</strong><span> · {item.receivedAt.slice(0,10)}</span>
            {item.parsed.ok?<label style={{display:'block'}}><input type="checkbox" checked={selected.has(item.id)} disabled={busy}
              onChange={e=>setSelected(old=>{const next=new Set(old);if(e.target.checked)next.add(item.id);else next.delete(item.id);return next})}/>
              {item.parsed.row.merchantName||'عملية بنكية'} · <span className="num" style={{color:item.parsed.row.direction==='out'?'var(--c-outgoing)':'var(--c-incoming)'}}>{formatAmount(item.parsed.row.amountMinor)}</span>
            </label>:<p>{item.parsed.reason}</p>}
            <button className="btn btn--quiet" disabled={busy} onClick={()=>void run(()=>user.smsInbox.dismiss([item.id]))}>استبعاد من المراجعة بدون تسجيل</button>
          </section>)}
          <button className="btn" disabled={busy||!selected.size} onClick={startReview}>مراجعة المحدد ومنع التكرار</button>
          {view.count===0&&!busy&&<p>مفيش رسائل معلقة حاليًا.</p>}
        </>}
      </div>
    </div>
  </div>
}
