import {TabButton} from './TabButton'
export type AppTab='home'|'budget'|'transactions'|'people'|'invest'|'settings'
const TABS:{key:AppTab;label:string;icon:string}[]=[
 {key:'home',label:'الرئيسية',icon:'⌂'},{key:'budget',label:'الميزانية',icon:'◱'},
 {key:'transactions',label:'العمليات',icon:'☰'},{key:'people',label:'الأشخاص',icon:'◎'},
 {key:'invest',label:'الاستثمار',icon:'◈'},{key:'settings',label:'الإعدادات',icon:'⚙'},
]
export function ShellTabs({tab,onChange}:{tab:AppTab;onChange:(tab:AppTab)=>void}) {
 return <nav className="shell__tabs" aria-label="التنقل الرئيسي">
  {TABS.map(t=><TabButton key={t.key} label={t.label} icon={t.icon} active={tab===t.key} onClick={()=>onChange(t.key)}/>)}
 </nav>
}
