import { emptyStoredData, type StoredData } from '../../domain/idRepair'
import type { IdRepairPort } from '../../application/ports/IdRepairPort'

/** تنفيذ الذاكرة لاختبارات الإصلاح ووضع المعاينة. نفس العقد: تحديث وثيقة موجودة فقط. */
export function memoryIdRepair(initial: StoredData = emptyStoredData()): IdRepairPort & { snapshot(): StoredData } {
  const data = structuredClone(initial)
  return {
    snapshot: () => structuredClone(data),
    readAll: async () => structuredClone(data),
    apply: async (patches) => {
      for (const patch of patches) {
        const row = data[patch.group].find((r) => r.docId === patch.docId)
        if (!row) throw new Error(`وثيقة غير موجودة: ${patch.group}/${patch.docId}`)
        Object.assign(row.data, patch.fields)
      }
      return patches.length
    },
  }
}
