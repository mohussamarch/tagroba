import {useState} from 'react'
import type {Merchant} from '../../domain/entities/types'
export function MerchantAliasEditor({merchants,busy,onSave}:{merchants:Merchant[];busy:boolean;onSave:(id:string,alias:string)=>Promise<unknown>}) {
 const [id,setId]=useState(''),[alias,setAlias]=useState('')
 return <form className="card" onSubmit={e=>{e.preventDefault();void onSave(id,alias)}}>
  <h3>ربط اسم بديل بتاجر</h3>
  <p className="sheet__hint">مطابقة كاملة للاسم اللي في الكشف. الربط الجديد يستخدم تصنيف التاجر؛ السجل الأصلي محفوظ.</p>
  <label className="sheet__field">التاجر<select className="sheet__input" required value={id} onChange={e=>setId(e.target.value)}>
   <option value="">اختار التاجر</option>{merchants.map(m=><option key={m.id} value={m.id}>{m.displayName}</option>)}</select></label>
  <label className="sheet__field">الاسم البديل<input className="sheet__input" required maxLength={120} value={alias} onChange={e=>setAlias(e.target.value)}/></label>
  <button className="btn" disabled={busy||!id||!alias.trim()}>تأكيد ربط الاسم</button>
  <p>{merchants.find(m=>m.id===id)?.aliases?.join('، ')}</p>
 </form>
}
