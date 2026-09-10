import {
  collection, doc, documentId, getDocsFromServer, limit, orderBy, query, startAfter, writeBatch,
  type Firestore, type QueryDocumentSnapshot,
} from 'firebase/firestore'
import { BACKUP_GROUPS } from '../../domain/fullBackup'
import { emptyStoredData, type IdPatch } from '../../domain/idRepair'
import type { IdRepairPort } from '../../application/ports/IdRepairPort'

const PAGE = 200
/** أقل من حد Firestore (500) بهامش. */
const WRITE_BATCH = 400

/**
 * قراءة بصفحات من الخادم بترتيب معرّف الوثيقة (حقل واحد، بلا فهرس مركّب —
 * ARCHITECTURE §12)، مع الاحتفاظ بـ`doc.id` لأنه الحقيقة الوحيدة الباقية.
 * الكتابة `update` فقط: لا تنشئ وثيقة ولا تمسح حقلًا غير المصحح.
 */
export function firestoreIdRepair(db: Firestore, uid: string): IdRepairPort {
  return {
    async readAll() {
      const data = emptyStoredData()
      for (const group of BACKUP_GROUPS) {
        let cursor: QueryDocumentSnapshot | undefined
        for (;;) {
          const page = await getDocsFromServer(query(
            collection(db, 'users', uid, group), orderBy(documentId()), limit(PAGE),
            ...(cursor ? [startAfter(cursor)] : []),
          ))
          data[group].push(...page.docs.map((d) => ({ docId: d.id, data: d.data() })))
          if (page.size < PAGE) break
          cursor = page.docs[page.docs.length - 1]
        }
      }
      return data
    },
    async apply(patches: readonly IdPatch[]) {
      let written = 0
      for (let i = 0; i < patches.length; i += WRITE_BATCH) {
        const chunk = patches.slice(i, i + WRITE_BATCH)
        const batch = writeBatch(db)
        for (const patch of chunk) batch.update(doc(db, 'users', uid, patch.group, patch.docId), patch.fields)
        await batch.commit()
        written += chunk.length
      }
      return written
    },
  }
}
