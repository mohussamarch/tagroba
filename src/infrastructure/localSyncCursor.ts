import type { SyncCursorPort } from '../application/ports/SharedMerchantCatalogPort'

/**
 * آخر مزامنة لقاعدة التجار المشتركة على الجهاز ده — OVERRIDES §25.1.
 * مش بيانات مالية؛ لو التخزين مش متاح أو اتمسح، المزامنة الجاية بتقرا كله (المعرّفات ثابتة فمفيش تكرار).
 */
export function localSyncCursor(uid: string, name = 'shared-merchants'): SyncCursorPort {
  const key = `masroufy-sync-v1:${uid}:${name}`
  return {
    read() {
      try {
        const value = localStorage.getItem(key)
        return value && !Number.isNaN(Date.parse(value)) ? value : null
      } catch { return null }
    },
    write(iso) {
      try { localStorage.setItem(key, iso) } catch { /* التخزين اختياري */ }
    },
  }
}
