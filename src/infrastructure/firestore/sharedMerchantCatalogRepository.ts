import {
  collection,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  where,
  type DocumentData,
  type Firestore,
} from 'firebase/firestore'
import type { SharedMerchantCatalogPort } from '../../application/ports/SharedMerchantCatalogPort'
import { sharedMerchantKey, type SharedMerchantEntry } from '../../domain/sharedMerchantCatalog'

/**
 * قاعدة التجار المشتركة على Firestore — `sharedMerchants/{key}` (OVERRIDES §25).
 * الحماية في `firestore.rules`: قراءة لأي داخل، كتابة مش مؤكد بس، ومفيش مسح.
 * القراءة بالتغييرات بس (`updatedAt > آخر مزامنة`) عشان حد الـ50,000 قراءة في اليوم.
 */
const COLLECTION = 'sharedMerchants'

function fromDoc(data: DocumentData): SharedMerchantEntry | undefined {
  if (typeof data.normalizedName !== 'string' || !data.normalizedName) return undefined
  const updatedAt = data.updatedAt instanceof Timestamp ? data.updatedAt.toDate().toISOString() : undefined
  return {
    normalizedName: data.normalizedName,
    displayName: typeof data.displayName === 'string' ? data.displayName : data.normalizedName,
    aliases: Array.isArray(data.aliases) ? data.aliases.filter((a: unknown): a is string => typeof a === 'string') : [],
    categoryId: typeof data.categoryId === 'string' ? data.categoryId : null,
    // أي قيمة غير `true` بالظبط (من اللوحة) = مش مؤكد
    confirmed: data.confirmed === true,
    ...(updatedAt ? { updatedAt } : {}),
  }
}

export class FirestoreSharedMerchantCatalog implements SharedMerchantCatalogPort {
  constructor(private readonly db: Firestore) {}

  async listChangedSince(sinceIso: string | null): Promise<SharedMerchantEntry[]> {
    const col = collection(this.db, COLLECTION)
    const q = sinceIso
      ? query(col, where('updatedAt', '>', Timestamp.fromDate(new Date(sinceIso))), orderBy('updatedAt'))
      : query(col, orderBy('updatedAt'))
    const snap = await getDocs(q)
    return snap.docs.map((d) => fromDoc(d.data())).filter((e): e is SharedMerchantEntry => !!e)
  }

  /** حقل واحد بمساواة ⇒ فهرس تلقائي. بيتنادى مرة في اليوم على الأكتر من `sync`. */
  async listConfirmed(): Promise<SharedMerchantEntry[]> {
    const snap = await getDocs(query(collection(this.db, COLLECTION), where('confirmed', '==', true)))
    return snap.docs.map((d) => fromDoc(d.data())).filter((e): e is SharedMerchantEntry => !!e)
  }

  async get(normalizedName: string): Promise<SharedMerchantEntry | undefined> {
    const snap = await getDoc(doc(this.db, COLLECTION, sharedMerchantKey(normalizedName)))
    return snap.exists() ? fromDoc(snap.data()) : undefined
  }

  async save(entry: SharedMerchantEntry): Promise<void> {
    await setDoc(doc(this.db, COLLECTION, sharedMerchantKey(entry.normalizedName)), {
      normalizedName: entry.normalizedName,
      displayName: entry.displayName,
      aliases: entry.aliases,
      categoryId: entry.categoryId,
      confirmed: false,
      updatedAt: serverTimestamp(),
    })
  }
}
