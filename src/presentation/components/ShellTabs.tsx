import type { ReactNode } from 'react'
import { Ellipsis, House, List, PiggyBank, Plus } from 'lucide-react'
import {TabButton} from './TabButton'
export type AppTab='home'|'budget'|'transactions'|'people'|'invest'|'settings'|'more'
/** خمس خانات وزر الإضافة في النص — قرار المالك 2026-09-11 (OVERRIDES §20). العلامات مرسومة بلون واحد (OVERRIDES §31). */
const BEFORE:{key:AppTab;label:string;icon:ReactNode}[]=[
 {key:'home',label:'الرئيسية',icon:<House size={22}/>},{key:'transactions',label:'العمليات',icon:<List size={22}/>},
]
const AFTER:{key:AppTab;label:string;icon:ReactNode}[]=[
 {key:'budget',label:'الميزانية',icon:<PiggyBank size={22}/>},{key:'more',label:'المزيد',icon:<Ellipsis size={22}/>},
]
export function ShellTabs({tab,onChange,onAdd}:{tab:AppTab;onChange:(tab:AppTab)=>void;onAdd:()=>void}) {
 return <nav className="shell__tabs" aria-label="التنقل الرئيسي">
  {BEFORE.map(t=><TabButton key={t.key} label={t.label} icon={t.icon} active={tab===t.key} onClick={()=>onChange(t.key)}/>)}
  <button type="button" className="tab tab--add" onClick={onAdd} aria-label="إضافة عملية أو كشف">
   <span aria-hidden="true" className="tab__add"><Plus size={26}/></span>
  </button>
  {AFTER.map(t=><TabButton key={t.key} label={t.label} icon={t.icon} active={tab===t.key} onClick={()=>onChange(t.key)}/>)}
 </nav>
}
