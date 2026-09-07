import {
  collection,
  deleteDoc,
  doc,
  getDocs,
  query,
  setDoc,
  where,
  writeBatch,
  type Firestore,
} from 'firebase/firestore'
import type { Id, Tag, TransactionTag } from '../../domain/entities/types'
import type {
  TagRepository,
  TransactionTagRepository,
} from '../../application/ports/repositories'

/**
 * الوسوم وروابطها على Firestore.
 *
 * كل استعلام **بحقل واحد** — لا فهرس مركّب (ARCHITECTURE §12).
 * و`in` محدود بثلاثين قيمة عند فايرستور فالطلب بيتقطّع.
 */

const BATCH_LIMIT = 500
const IN_LIMIT = 30

function userPath(uid: string, ...segments: string[]): string {
  return ['users', uid, ...segments].join('/')
}

export class FirestoreTagRepository implements TagRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  async listAll(): Promise<Tag[]> {
    const snap = await getDocs(collection(this.db, userPath(this.uid, 'tags')))
    return snap.docs.map((d) => d.data() as Tag)
  }

  async save(tag: Tag): Promise<void> {
    await setDoc(doc(this.db, userPath(this.uid, 'tags', tag.id)), tag)
  }

  async remove(id: Id): Promise<void> {
    await deleteDoc(doc(this.db, userPath(this.uid, 'tags', id)))
  }
}

export class FirestoreTransactionTagRepository implements TransactionTagRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  private async queryIn(field: string, values: readonly Id[]): Promise<TransactionTag[]> {
    if (values.length === 0) return []
    const out: TransactionTag[] = []
    for (let i = 0; i < values.length; i += IN_LIMIT) {
      const snap = await getDocs(
        query(
          collection(this.db, userPath(this.uid, 'transactionTags')),
          where(field, 'in', values.slice(i, i + IN_LIMIT)),
        ),
      )
      out.push(...snap.docs.map((d) => d.data() as TransactionTag))
    }
    return out
  }

  async listByTransactionIds(ids: readonly Id[]): Promise<TransactionTag[]> {
    return this.queryIn('transactionId', ids)
  }

  async listByTag(tagId: Id): Promise<TransactionTag[]> {
    return this.queryIn('tagId', [tagId])
  }

  async saveMany(links: readonly TransactionTag[]): Promise<void> {
    for (let i = 0; i < links.length; i += BATCH_LIMIT) {
      const batch = writeBatch(this.db)
      for (const link of links.slice(i, i + BATCH_LIMIT)) {
        batch.set(doc(this.db, userPath(this.uid, 'transactionTags', link.id)), link)
      }
      await batch.commit()
    }
  }

  async deleteMany(ids: readonly Id[]): Promise<void> {
    for (let i = 0; i < ids.length; i += BATCH_LIMIT) {
      const batch = writeBatch(this.db)
      for (const id of ids.slice(i, i + BATCH_LIMIT)) {
        batch.delete(doc(this.db, userPath(this.uid, 'transactionTags', id)))
      }
      await batch.commit()
    }
  }
}
