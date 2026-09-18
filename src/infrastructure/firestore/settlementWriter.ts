import { collection, doc, getDocFromServer, getDocsFromServer, query, runTransaction, where, type Firestore } from 'firebase/firestore'
import type { SettlementWriter } from '../../application/ports/SettlementWriter'
import type { Obligation, Settlement } from '../../domain/entities/types'
import { prepareSettlement } from '../../domain/settlementCommand'

class SnapshotMoved extends Error {}
/** This revision is bumped by every cooperating settlement writer, including backup restore. */
export const settlementRevision = (db: Firestore, uid: string) => doc(db, 'users', uid, 'concurrency', 'settlements')

export function firestoreSettlementWriter(db: Firestore, uid: string): SettlementWriter {
  const revisionRef = settlementRevision(db, uid)
  return {
    async settle(input) {
      const target = doc(db, 'users', uid, 'settlements', input.id)
      for (let attempt = 0; attempt < 8; attempt++) {
        const before = await getDocFromServer(revisionRef)
        const revision = before.data()?.revision ?? 0
        if (!Number.isSafeInteger(revision) || revision < 0) throw new Error('حالة التسويات غير سليمة وتحتاج مراجعة')
        const snapshot = await getDocsFromServer(query(collection(db, 'users', uid, 'settlements'), where('obligationId', '==', input.obligationId)))
        const rows = snapshot.docs.map(row => ({ ...row.data(), id: row.id }) as Settlement)
        try {
          return await runTransaction(db, async tx => {
            const [current, obligation, saved] = await Promise.all([
              tx.get(revisionRef), tx.get(doc(db, 'users', uid, 'obligations', input.obligationId)), tx.get(target),
            ])
            if ((current.data()?.revision ?? 0) !== revision) throw new SnapshotMoved()
            const prior = saved.exists() ? { ...saved.data(), id: saved.id } as Settlement : undefined
            const result = prepareSettlement(input, obligation.exists() ? { ...obligation.data(), id: obligation.id } as Obligation : undefined, prior ? [prior, ...rows.filter(row => row.id !== prior.id)] : rows)
            if (!prior) {
              tx.set(target, result)
              tx.set(revisionRef, { revision: revision + 1 })
            }
            return result
          })
        } catch (error) {
          if (!(error instanceof SnapshotMoved)) throw error
        }
      }
      throw new Error('التسويات اتغيرت أثناء الحفظ. جرّب تاني بنفس الطلب')
    },
  }
}
