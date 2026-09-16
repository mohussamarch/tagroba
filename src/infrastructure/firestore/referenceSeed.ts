import { doc, getDoc, runTransaction, type Firestore } from 'firebase/firestore'
import type { ReferenceSeedPort } from '../../application/ports/ReferenceSeedPort'

/** Version 1 is a first-run marker, not a mechanism for restoring deleted defaults. */
export function firestoreReferenceSeed(db: Firestore, uid: string): ReferenceSeedPort {
  const marker = doc(db, 'users', uid, 'initialization', 'references-v1')
  const stateOf = (data: unknown): 'pending' | 'complete' => {
    const state = (data as { state?: unknown } | undefined)?.state
    if (state !== 'pending' && state !== 'complete') throw new Error('حالة تجهيز المراجع غير سليمة')
    return state
  }
  return {
    async begin(hasExistingCategories) {
      // A completed local marker also allows subsequent offline opens.
      const cached = await getDoc(marker)
      if (cached.exists() && stateOf(cached.data()) === 'complete') return 'complete'
      return runTransaction(db, async tx => {
        const current = await tx.get(marker)
        if (current.exists()) return stateOf(current.data())
        const state = hasExistingCategories ? 'complete' : 'pending'
        tx.set(marker, { state, version: 1 })
        return state
      })
    },
    async insertMissing(source) {
      for (const group of ['categories', 'rules', 'merchants'] as const) {
        const rows = source[group]
        for (let start = 0; start < rows.length; start += 100) {
          const page = rows.slice(start, start + 100)
          await runTransaction(db, async tx => {
            const current = await tx.get(marker)
            if (!current.exists()) throw new Error('تجهيز المراجع لم يبدأ')
            if (stateOf(current.data()) === 'complete') return
            const refs = page.map(row => doc(db, 'users', uid, group, row.id))
            const snapshots = await Promise.all(refs.map(ref => tx.get(ref)))
            snapshots.forEach((snapshot, index) => {
              if (!snapshot.exists()) {
                const clean = Object.fromEntries(Object.entries(page[index]).filter(([, value]) => value !== undefined))
                tx.set(refs[index], clean)
              }
            })
          })
        }
      }
    },
    async complete() {
      await runTransaction(db, async tx => {
        const current = await tx.get(marker)
        if (!current.exists()) throw new Error('تجهيز المراجع لم يبدأ')
        if (stateOf(current.data()) === 'pending') tx.set(marker, { state: 'complete', version: 1 })
      })
    },
  }
}
