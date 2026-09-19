import type { SharedMerchantCatalogPort, SyncCursorPort } from '../../application/ports/SharedMerchantCatalogPort'
import { sharedMerchantKey, type SharedMerchantEntry } from '../../domain/sharedMerchantCatalog'

/**
 * قاعدة التجار المشتركة في الذاكرة — للاختبار والمعاينة.
 * بتقلّد قواعد الحماية: تكتب مش مؤكد بس، وترفض تعديل المؤكد.
 */
export class MemorySharedMerchantCatalog implements SharedMerchantCatalogPort {
  private items = new Map<string, SharedMerchantEntry>()
  private tick = 0

  constructor(seed: readonly SharedMerchantEntry[] = []) {
    for (const e of seed) this.items.set(sharedMerchantKey(e.normalizedName), structuredClone(e))
  }

  async listChangedSince(sinceIso: string | null): Promise<SharedMerchantEntry[]> {
    return [...this.items.values()]
      .filter((e) => !sinceIso || (e.updatedAt ?? '') > sinceIso)
      .map((e) => structuredClone(e))
  }

  async listConfirmed(): Promise<SharedMerchantEntry[]> {
    return [...this.items.values()].filter((e) => e.confirmed).map((e) => structuredClone(e))
  }

  async get(normalizedName: string): Promise<SharedMerchantEntry | undefined> {
    const found = this.items.get(sharedMerchantKey(normalizedName))
    return found && structuredClone(found)
  }

  async save(entry: SharedMerchantEntry): Promise<void> {
    const key = sharedMerchantKey(entry.normalizedName)
    if (entry.confirmed) throw new Error('permission-denied: confirmed is written from the Firebase console only')
    if (this.items.get(key)?.confirmed) throw new Error('permission-denied: confirmed merchants are read-only')
    this.tick += 1
    this.items.set(key, { ...structuredClone(entry), updatedAt: `2026-01-01T00:00:${String(this.tick).padStart(2, '0')}.000Z` })
  }

  /** زي التأكيد من لوحة فايربيز — للاختبار بس. */
  confirmFromConsole(normalizedName: string, categoryId: string, updatedAt: string): void {
    const key = sharedMerchantKey(normalizedName)
    const current = this.items.get(key)
    this.items.set(key, { normalizedName, displayName: current?.displayName ?? normalizedName, aliases: current?.aliases ?? [], categoryId, confirmed: true, updatedAt })
  }
}

export class MemorySyncCursor implements SyncCursorPort {
  constructor(private value: string | null = null) {}
  read() { return this.value }
  write(iso: string) { this.value = iso }
}
