import {TabButton} from './TabButton'
export type AppTab='home'|'budget'|'transactions'|'people'|'invest'|'settings'|'more'
/** خمس خانات وزر الإضافة في النص — قرار المالك 2026-09-11 (OVERRIDES §20). */
const BEFORE:{key:AppTab;label:string;icon:string}[]=[
 {key:'home',label:'الرئيسية',icon:'⌂'},{key:'transactions',label:'العمليات',icon:'☰'},
]
const AFTER:{key:AppTab;label:string;icon:string}[]=[
 {key:'budget',label:'الميزانية',icon:'◱'},{key:'more',label:'المزيد',icon:'⋯'},
]
export function ShellTabs({tab,onChange,onAdd}:{tab:AppTab;onChange:(tab:AppTab)=>void;onAdd:()=>void}) {
 return <nav className="shell__tabs" aria-label="التنقل الرئيسي">
  {BEFORE.map(t=><TabButton key={t.key} label={t.label} icon={t.icon} active={tab===t.key} onClick={()=>onChange(t.key)}/>)}
  <button type="button" className="tab tab--add" onClick={onAdd} aria-label="إضافة عملية أو كشف">
   <span aria-hidden="true" className="tab__add">＋</span>
  </button>
  {AFTER.map(t=><TabButton key={t.key} label={t.label} icon={t.icon} active={tab===t.key} onClick={()=>onChange(t.key)}/>)}
 </nav>
}
