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
import type {
  Id,
  Obligation,
  Person,
  PersonAllocation,
  Settlement,
} from '../../domain/entities/types'
import type {
  AllocationRepository,
  ObligationRepository,
  PersonRepository,
  SettlementRepository,
} from '../../application/ports/repositories'

/**
 * الأشخاص والالتزامات والتسويات على Firestore.
 *
 * ⚠️ `spec/03`: «Person: id، name، archived؛ **لا حذف للحساب ذي سجل**».
 * فمفيش دالة حذف للشخص — الأرشفة بدلها، والسجل يبقى.
 *
 * كل استعلام هنا **بحقل واحد** — لا فهرس مركّب (ARCHITECTURE §12).
 */

const BATCH_LIMIT = 500

function userPath(uid: string, ...segments: string[]): string {
  return ['users', uid, ...segments].join('/')
}

function clean<T extends object>(value: T): T {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(value)) if (v !== undefined) out[k] = v
  return out as T
}

async function saveAll<T extends { id: Id }>(
  db: Firestore,
  uid: string,
  name: string,
  items: readonly T[],
): Promise<void> {
  for (let i = 0; i < items.length; i += BATCH_LIMIT) {
    const batch = writeBatch(db)
    for (const item of items.slice(i, i + BATCH_LIMIT)) {
      batch.set(doc(db, userPath(uid, name, item.id)), clean(item))
    }
    await batch.commit()
  }
}

async function deleteAll(
  db: Firestore,
  uid: string,
  name: string,
  ids: readonly Id[],
): Promise<void> {
  for (let i = 0; i < ids.length; i += BATCH_LIMIT) {
    const batch = writeBatch(db)
    for (const id of ids.slice(i, i + BATCH_LIMIT)) batch.delete(doc(db, userPath(uid, name, id)))
    await batch.commit()
  }
}

/** Firestore يحد `in` بثلاثين قيمة، فالطلب يُقطَّع. */
async function queryIn<T>(
  db: Firestore,
  uid: string,
  name: string,
  field: string,
  values: readonly Id[],
): Promise<T[]> {
  if (values.length === 0) return []
  const out: T[] = []
  for (let i = 0; i < values.length; i += 30) {
    const snap = await getDocs(
      query(collection(db, userPath(uid, name)), where(field, 'in', values.slice(i, i + 30))),
    )
    out.push(...snap.docs.map((d) => d.data() as T))
  }
  return out
}

export class FirestorePersonRepository implements PersonRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  async listAll(): Promise<Person[]> {
    const snap = await getDocs(collection(this.db, userPath(this.uid, 'people')))
    return snap.docs.map((d) => d.data() as Person)
  }

  async save(person: Person): Promise<void> {
    await setDoc(doc(this.db, userPath(this.uid, 'people', person.id)), clean(person))
  }
}

export class FirestoreObligationRepository implements ObligationRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  async listByPerson(personId: Id): Promise<Obligation[]> {
    const snap = await getDocs(
      query(collection(this.db, userPath(this.uid, 'obligations')), where('personId', '==', personId)),
    )
    return snap.docs.map((d) => d.data() as Obligation)
  }

  async listByTransactionIds(ids: readonly Id[]): Promise<Obligation[]> {
    return queryIn<Obligation>(this.db, this.uid, 'obligations', 'originTransactionId', ids)
  }

  async saveMany(obligations: readonly Obligation[]): Promise<void> {
    await saveAll(this.db, this.uid, 'obligations', obligations)
  }

  async deleteMany(ids: readonly Id[]): Promise<void> {
    await deleteAll(this.db, this.uid, 'obligations', ids)
  }
}

export class FirestoreSettlementRepository implements SettlementRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  async listByObligations(obligationIds: readonly Id[]): Promise<Settlement[]> {
    return queryIn<Settlement>(this.db, this.uid, 'settlements', 'obligationId', obligationIds)
  }

  async listByTransactionIds(ids: readonly Id[]): Promise<Settlement[]> {
    return queryIn<Settlement>(this.db, this.uid, 'settlements', 'transactionId', ids)
  }

  async saveMany(settlements: readonly Settlement[]): Promise<void> {
    await saveAll(this.db, this.uid, 'settlements', settlements)
  }

  async deleteMany(ids: readonly Id[]): Promise<void> {
    await deleteAll(this.db, this.uid, 'settlements', ids)
  }
}

export class FirestoreAllocationRepository implements AllocationRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  async listByTransactionIds(ids: readonly Id[]): Promise<PersonAllocation[]> {
    return queryIn<PersonAllocation>(this.db, this.uid, 'allocations', 'transactionId', ids)
  }

  async saveMany(allocations: readonly PersonAllocation[]): Promise<void> {
    await saveAll(this.db, this.uid, 'allocations', allocations)
  }

  async deleteMany(ids: readonly Id[]): Promise<void> {
    await deleteAll(this.db, this.uid, 'allocations', ids)
  }
}

export { deleteDoc }
