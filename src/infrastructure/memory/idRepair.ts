import { emptyStoredData, type StoredData } from '../../domain/idRepair'
import type { IdRepairPort } from '../../application/ports/IdRepairPort'
import type { RemoveDocsPort } from '../../application/ports/RemoveDocsPort'

/** تنفيذ الذاكرة لاختبارات الإصلاح والتنظيف ووضع المعاينة. نفس العقد: مسار الوثيقة فقط. */
export function memoryIdRepair(initial: StoredData = emptyStoredData()): IdRepairPort & RemoveDocsPort & { snapshot(): StoredData } {
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
    remove: async (items) => {
      for (const item of items) {
        const index = data[item.group].findIndex((r) => r.docId === item.docId)
        if (index < 0) throw new Error(`وثيقة غير موجودة: ${item.group}/${item.docId}`)
        data[item.group].splice(index, 1)
      }
      return items.length
    },
  }
}
