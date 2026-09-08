import type {Merchant} from './entities/types'
import {normalizeText} from './normalize'
/** Explicit aliases only; canonical names win in old conflicting backups. */
export function merchantIndex(merchants:readonly Merchant[]) {
 const out=new Map<string,Merchant>()
 for(const m of merchants) for(const a of m.aliases??[]) out.set(normalizeText(a),m)
 for(const m of merchants) out.set(normalizeText(m.normalizedName),m)
 return out
}
