import { useEffect, useState } from 'react'
import type { UserContainer } from '../../app/container'
import type { Wallet } from '../../domain/entities/types'
import { SmsInboxSheet } from '../screens/SmsInboxSheet'

/** Mounted once in the shell: catches up after foreground without blocking home. */
export function SmsInboxAccess({user,wallets,onImported}:{user:UserContainer;wallets:Wallet[];onImported:()=>void}) {
  const [open,setOpen]=useState(false),[count,setCount]=useState(0),[error,setError]=useState('')
  useEffect(()=>{
    if(!user.smsInbox.available)return
    let alive=true,working=false
    const refresh=async()=>{
      if(document.hidden||working)return
      working=true
      try{const next=await user.smsInbox.refresh();if(alive){setCount(next.count);setError(next.enabled&&!next.permission?'إذن الرسائل غير متاح':'')}}
      catch{if(alive)setError('تعذر تحديث رسائل البنك')}
      finally{working=false}
    }
    void refresh()
    document.addEventListener('visibilitychange',refresh)
    const timer=window.setInterval(()=>void refresh(),60000)
    return()=>{alive=false;window.clearInterval(timer);document.removeEventListener('visibilitychange',refresh)}
  },[user,open])
  if(!user.smsInbox.available)return null
  return <>
    <button className="btn btn--quiet" onClick={()=>setOpen(true)}>رسائل جديدة ({count}) · إعداد القراءة التلقائية{error?' — '+error:''}</button>
    {open&&<SmsInboxSheet user={user} wallets={wallets} onClose={()=>setOpen(false)} onImported={onImported}/>}
  </>
}
