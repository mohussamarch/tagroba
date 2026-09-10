import type { HomeSnapshot,HomeSnapshotPort } from '../application/ports/HomeSnapshotPort'
const VERSION=1
/** UI snapshot only. Financial mutations always use the normal repositories. */
export function createHomeSnapshot(uid:string):HomeSnapshotPort {
 const prefix='masroufy-home-v'+VERSION+':'+uid+':'
 return {
  async read(periodKey){
   try{
    const raw=localStorage.getItem(prefix+periodKey)
    if(!raw)return null
    const snapshot=JSON.parse(raw) as HomeSnapshot
    if(snapshot.data?.period?.key!==periodKey||!snapshot.savedAt||!Array.isArray(snapshot.data.latest)||!Array.isArray(snapshot.data.categories)||!Array.isArray(snapshot.data.recentPeriods)||!snapshot.data.coverage)return null
    // Daily allowance/forecast use "today": never reuse yesterday's computed values.
    if(snapshot.savedAt.slice(0,10)!==new Date().toISOString().slice(0,10))return null
    return snapshot
   }catch{return null}
  },
  async save(snapshot){
   try{localStorage.setItem(prefix+snapshot.data.period.key,JSON.stringify(snapshot))}catch{/* optional cache may be unavailable */}
  },
  async clear(){
   try{for(const key of Object.keys(localStorage))if(key.startsWith(prefix))localStorage.removeItem(key)}catch{/* cache is optional */}
  },
 }
}
