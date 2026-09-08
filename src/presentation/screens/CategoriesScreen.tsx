import {useEffect,useState} from 'react'
import type {UserContainer} from '../../app/container'
import type {Category} from '../../domain/entities/types'
import {ErrorNotice} from '../components/ErrorNotice'
import './ImportSheet.css'
export function CategoriesScreen({user,onClose,onChanged}:{user:UserContainer;onClose:()=>void;onChanged:()=>void}) {
 const [items,setItems]=useState<Category[]>([]),[id,setId]=useState('')
 const [name,setName]=useState(''),[palette,setPalette]=useState(''),[active,setActive]=useState(true)
 const [busy,setBusy]=useState(false),[error,setError]=useState<unknown>(null)
 async function load(){const rows=await user.manageCategories.list();setItems(rows);setPalette(p=>p||rows[0]?.id||'')}
 useEffect(()=>{void load().catch(setError)},[user])
 return <div className="sheet" role="dialog" aria-modal="true" aria-label="إدارة التصنيفات">
  <section className="sheet__panel"><header className="sheet__head"><h2 className="sheet__title">التصنيفات وألوانها</h2>
   <button className="btn btn--quiet" onClick={onClose}>إغلاق</button></header>
   <div className="sheet__body">
    <p>إخفاء التصنيف يشيله من اختيارات العمليات الجديدة، ويحافظ على سجله ومبالغه وقواعده القديمة.</p>
    {error!=null&&<ErrorNotice cause={error}/>}
    <form className="card" onSubmit={e=>{e.preventDefault();setBusy(true);setError(null);
     void user.manageCategories.save({...(id?{id}:{}),name,paletteId:palette,active})
      .then(async()=>{setId('');setName('');setActive(true);await load();onChanged()}).catch(setError).finally(()=>setBusy(false))}}>
     <h3>{id?'تعديل التصنيف':'تصنيف جديد'}</h3>
     <label className="sheet__field">اسم التصنيف<input className="sheet__input" required maxLength={80} value={name} onChange={e=>setName(e.target.value)}/></label>
     <label className="sheet__field">لوحة اللون<select className="sheet__input" value={palette} onChange={e=>setPalette(e.target.value)}>
      {items.map(c=><option key={c.id} value={c.id}>لون {c.name}</option>)}</select></label>
     <label><input type="checkbox" checked={active} onChange={e=>setActive(e.target.checked)}/> ظاهر في الاختيارات</label>
     <button className="btn" disabled={busy}>{busy?'بنحفظ…':'حفظ التصنيف'}</button>
     {id&&<button className="btn btn--quiet" type="button" onClick={()=>{setId('');setName('');setActive(true)}}>إلغاء التعديل</button>}
    </form>
    {items.map(c=><button className="btn btn--quiet" key={c.id} disabled={busy} onClick={()=>{setId(c.id);setName(c.name);setPalette(c.id);setActive(c.active)}}>
     {c.name}{!c.active?' — مخفي':''}</button>)}
   </div>
  </section>
 </div>
}
