import { collection, doc, getDocs, setDoc, type Firestore } from 'firebase/firestore'
import type { RecurringItem } from '../../domain/entities/recurring'
import type { RecurringRepository } from '../../application/ports/RecurringRepository'
export class FirestoreRecurringRepository implements RecurringRepository {
  constructor(private db: Firestore, private uid: string) {}
  async listAll(): Promise<RecurringItem[]> {
    const snap = await getDocs(collection(this.db,'users',this.uid,'recurringItems'))
    return snap.docs.map(d=>d.data() as RecurringItem)
  }
  async save(item: RecurringItem) {
    await setDoc(doc(this.db,'users',this.uid,'recurringItems',encodeURIComponent(item.id)),item)
  }
}
