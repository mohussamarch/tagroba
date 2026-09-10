import { useState } from 'react'
import type { UserContainer } from '../../app/container'
import type { Wallet } from '../../domain/entities/types'
import { ImportSheet } from './ImportSheet'
type Prepared = ReturnType<UserContainer['readBankSms']['paste']>
export function SmsImportFlow({user,wallets,today,onClose,onImported}:{user:UserContainer;wallets:Wallet[];today:string;onClose:()=>void;onImported:()=>void}) {
 const [from,setFrom]=useState(today.slice(0,7)+'-01'),[to,setTo]=useState(today)
 const [senders,setSenders]=useState('AlRajhiBank')
 const [consent,setConsent]=useState(false),[text,setText]=useState('')
 const [busy,setBusy]=useState(false),[error,setError]=useState<string|null>(null)
 const [result,setResult]=useState<Prepared|null>(null),[review,setReview]=useState(false)
 async function read(){
  setError(null);setResult(null);setBusy(true)
  try {setResult(await user.readBankSms.read({from,to,senders:senders.split(/[,،\n]/).map(x=>x.trim()).filter(Boolean)}))}
  catch(cause){setError(cause instanceof Error?cause.message:String(cause))}
  finally{setBusy(false)}
 }
 function paste(){setError(null);setResult(user.readBankSms.paste(text))}
 if(review&&result)return <ImportSheet user={user} wallets={wallets} onClose={onClose} onImported={onImported} initialSms={result}/>
 return <div className="sheet" role="dialog" aria-modal="true" aria-label="رسائل البنك">
  <div className="sheet__panel"><header className="sheet__head"><h2>رسائل البنك</h2><button className="iconBtn" onClick={onClose} disabled={busy} aria-label="إغلاق">✕</button></header>
   <div className="sheet__body" style={{display:'grid',gap:16}}>
    <p>هنحلّل الرسائل على جهازك، وبعد مراجعتك وتأكيدك نحفظ العمليات في حسابك. رسائل التحقق وكلمات السر يتم تجاهلها.</p>
    <p>القراءة هنا للفترة اللي تختارها. تقدر تفعّل المتابعة التلقائية من «رسائل جديدة» في التطبيق. ندعم صيغًا محددة بالريال، وأي رسالة غير واضحة يتم تخطيها مع توضيح السبب.</p>
    {user.readBankSms.available ? <>
     <label>اسم مرسل البنك كما يظهر في الرسائل<input value={senders} onChange={e=>setSenders(e.target.value)} disabled={busy} placeholder="AlRajhiBank" style={{width:'100%'}}/></label>
     <div style={{display:'flex',gap:12,flexWrap:'wrap'}}><label>من<input type="date" value={from} onChange={e=>setFrom(e.target.value)} disabled={busy}/></label><label>إلى<input type="date" value={to} onChange={e=>setTo(e.target.value)} disabled={busy}/></label></div>
     <label><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)} disabled={busy}/> موافق على قراءة رسائل المرسلين المحددين في الفترة دي. أقدر أسحب الإذن من إعدادات أندرويد.</label>
     <button type="button" className="btn btn--primary" disabled={busy||!consent} onClick={()=>void read()}>{busy?'بنقرأ رسائل البنك…':'قراءة الرسائل وعرض النتيجة'}</button>
    </>:<p role="note">قراءة الرسائل من الهاتف متاحة داخل نسخة أندرويد. هنا تقدر تلصق رسالة للمراجعة.</p>}
    <details><summary>لصق رسالة واحدة بدل إذن القراءة</summary><label>نص رسالة العملية<textarea rows={6} value={text} onChange={e=>setText(e.target.value)} disabled={busy} style={{width:'100%'}}/></label><button type="button" className="btn" onClick={paste} disabled={busy||!text.trim()}>تحليل النص</button></details>
    {error&&<p role="alert">{error}</p>}
    {result&&<section aria-label="نتيجة قراءة الرسائل">
     <p>عمليات قابلة للمراجعة: {result.rows.length} · رسائل تم تخطيها: {result.skipped.length}</p>
     {result.truncated&&<p role="alert">وصلنا لحد 500 رسالة. اختار فترة أصغر علشان تراجع الباقي.</p>}
     {result.skipped.length>0&&<details><summary>أسباب التخطي</summary><ul>{result.skipped.map(x=><li key={x.line}>رسالة {x.line}: {x.reason}</li>)}</ul></details>}
     <button className="btn btn--primary" disabled={!result.rows.length} onClick={()=>setReview(true)}>اختيار المحفظة ومراجعة التكرار</button>
    </section>}
   </div>
  </div>
 </div>
}
