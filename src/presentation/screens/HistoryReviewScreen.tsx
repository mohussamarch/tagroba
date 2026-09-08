import {useState} from 'react'
import type {UserContainer} from '../../app/container'
import {BULK_KINDS,type HistoryPreview} from '../../application/useCases/reviewHistory'
import {ruleFor,isConsistentWithObservedDirection,type EconomicKind} from '../../domain/entities/economicKind'
import {formatAmount} from '../../domain/formatMoney'
import {ErrorNotice} from '../components/ErrorNotice'
import type {Category,Transaction} from '../../domain/entities/types'
import './ImportSheet.css'
export function HistoryReviewScreen({user,today,categories,onClose,onChanged}:{
 user:UserContainer;today:string;categories:Category[];onClose:()=>void;onChanged:()=>void
}) {
 const [from,setFrom]=useState(today.slice(0,4)+'-01-01'),[to,setTo]=useState(today)
 const [view,setView]=useState<HistoryPreview|null>(null),[busy,setBusy]=useState(false)
 const [error,setError]=useState<unknown>(null),[message,setMessage]=useState('')
 const [selected,setSelected]=useState<string[]>([]),[kind,setKind]=useState<EconomicKind>('unclassified')
 async function run(action:()=>Promise<void>) {
  setBusy(true);setError(null);setMessage('')
  try {await action()}catch(e){setError(e)}finally{setBusy(false)}
 }
 async function refresh(){setView(await user.reviewHistory.preview(from,to));setSelected([])}
 const label=(id:string|undefined)=>categories.find(c=>c.id===id)?.name??'بلا تصنيف'
 return <div className="sheet" role="dialog" aria-modal="true" aria-label="مراجعة العمليات القديمة">
  <section className="sheet__panel"><header className="sheet__head"><h2 className="sheet__title">مراجعة العمليات القديمة</h2>
   <button className="btn btn--quiet" onClick={onClose}>إغلاق</button></header>
  <div className="sheet__body">
   <label className="sheet__field">من<input className="sheet__input" type="date" disabled={busy} value={from} onChange={e=>{setFrom(e.target.value);setView(null)}}/></label>
   <label className="sheet__field">إلى<input className="sheet__input" type="date" disabled={busy} value={to} onChange={e=>{setTo(e.target.value);setView(null)}}/></label>
   <button className="btn" disabled={busy} onClick={()=>void run(refresh)}>{busy?'بنراجع…':'اعرض المعاينة'}</button>
   {error!=null&&<ErrorNotice cause={error}/>}
   {message&&<p role="status">{message}</p>}
   {view&&<>
    <h3>تطبيق القواعد على القديم</h3>
    <p>{view.rows.length} عملية في النطاق؛ {view.categoryPlan.changed.length} تصنيف هيتغير؛ {view.categoryPlan.skippedConfirmed.length} تصنيف مؤكد يدويًا هيفضل زي ما هو.</p>
    <details><summary>راجع كل تغييرات التصنيف</summary>
     {view.categoryPlan.changed.map(c=><p key={c.transactionId}>{view.rows.find(t=>t.id===c.transactionId)?.rawMerchantName}:
      {label(c.fromCategoryId)} ← {label(c.toCategoryId)} — {c.reason}</p>)}</details>
    <button className="btn" disabled={busy||!view.categoryPlan.changed.length} onClick={()=>void run(async()=>{
     await user.reviewHistory.applyCategories(view.rows.map(t=>t.id),view.categoryPlan)
     await refresh();onChanged();setMessage('اتطبقت القواعد على العمليات المؤهلة.')
    })}>تأكيد تطبيق القواعد</button>
    <h3>تحديد النوع لمجموعة متشابهة</h3>
    <p>التشابه بالاسم والاتجاه والعملة فقط، مش دليل إن الغرض واحد. اختار بنفسك العمليات والنوع. الديون والتحويلات راجعها فرديًا لربط أطرافها.</p>
    {view.groups.length===0&&<p>مفيش مجموعات غير مؤكدة في النطاق ده.</p>}
    {view.groups.map(group=><details key={group[0].id}>
     <summary>{group[0].rawMerchantName} — {group.length} عمليات ({group[0].observedDirection==='in'?'وارد':'صادر'})</summary>
     <button className="btn btn--quiet" disabled={busy} onClick={()=>{setSelected(group.map(t=>t.id));setKind('unclassified')}}>اختار المجموعة دي للمراجعة</button>
     {group.map(t=><label className="sheet__field" key={t.id}><span><input type="checkbox"
      checked={selected.includes(t.id)} disabled={busy||(!selected.includes(t.id)&&!group.some(x=>selected.includes(x.id)))}
      onChange={e=>setSelected(s=>e.target.checked?[...s,t.id]:s.filter(id=>id!==t.id))}/><Tx t={t}/></span></label>)}
    </details>)}
    {selected.length>0&&<>
     <label className="sheet__field">النوع الذي اخترته<select className="sheet__input" value={kind} onChange={e=>setKind(e.target.value as EconomicKind)}>
      <option value="unclassified">اختار النوع بنفسك</option>{BULK_KINDS.filter(k=>isConsistentWithObservedDirection(k,view.rows.find(t=>selected.includes(t.id))!.observedDirection)).map(k=><option key={k} value={k}>{ruleFor(k).label}</option>)}
     </select></label>
     <p>هتؤكد {selected.length} عملية كـ «{ruleFor(kind).label}». ده هيغيّر حساب الدخل أو المصروف، والمبلغ البنكي ثابت.</p>
     <button className="btn" disabled={busy||kind==='unclassified'} onClick={()=>void run(async()=>{
      const result=await user.reviewHistory.setGroup(selected,kind)
      await refresh();onChanged();setMessage('اتأكدت '+result.applied+' عملية، واتخطّت '+result.skipped+' عملية مؤكدة.')
     })}>تأكيد النوع للمحدد</button>
    </>}
   </>}
  </div></section>
 </div>
}
function Tx({t}:{t:Transaction}) {return <>{t.occurredAt} · <bdi>{formatAmount(t.amountMinor,t.currency)} {t.currency}</bdi> · {t.rawDescription||t.rawMerchantName}</>}
