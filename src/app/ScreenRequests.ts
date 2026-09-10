/** Cache only within this signed-in session; invalidation rejects late writes. */
export class ScreenRequests {
 private values=new Map<string,unknown>()
 private pending=new Map<string,Promise<unknown>>()
 private revision=0
 peek<T>(key:string):T|undefined{return this.values.get(key) as T|undefined}
 invalidate(){this.revision++;this.values.clear();this.pending.clear()}
 async load<T>(key:string,work:()=>Promise<T>):Promise<T>{
  if(this.values.has(key))return this.values.get(key) as T
  const existing=this.pending.get(key)
  if(existing)return existing as Promise<T>
  const revision=this.revision
  const promise=Promise.resolve().then(work)
  this.pending.set(key,promise)
  try{
   const value=await promise
   if(this.revision===revision){
    this.values.set(key,value)
    if(this.values.size>30)this.values.delete(this.values.keys().next().value!)
   }
   return value
  }finally{if(this.pending.get(key)===promise)this.pending.delete(key)}
 }
}
