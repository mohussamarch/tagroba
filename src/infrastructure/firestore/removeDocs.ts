import { doc, writeBatch, type Firestore } from 'firebase/firestore'
import type { RemoveDocsPort } from '../../application/ports/RemoveDocsPort'

/** أقل من حد Firestore (500) — والتقسيم الأصغر للتقدم والاستئناف في حالة الاستخدام. */
const LIMIT = 400

export function firestoreRemoveDocs(db: Firestore, uid: string): RemoveDocsPort {
  return {
    async remove(items) {
      let removed = 0
      for (let i = 0; i < items.length; i += LIMIT) {
        const chunk = items.slice(i, i + LIMIT)
        const batch = writeBatch(db)
        for (const item of chunk) batch.delete(doc(db, 'users', uid, item.group, item.docId))
        await batch.commit()
        removed += chunk.length
      }
      return removed
    },
  }
}
