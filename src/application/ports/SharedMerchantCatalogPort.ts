import type { SharedMerchantEntry } from '../../domain/sharedMerchantCatalog'

/** قاعدة التجار المشتركة — OVERRIDES §25. التأكيد مش من هنا (لوحة فايربيز بس). */
export interface SharedMerchantCatalogPort {
  /** المستندات اللي اتعدلت **بعد** الوقت ده (`null` = كله). */
  listChangedSince(sinceIso: string | null): Promise<SharedMerchantEntry[]>
  /** كل المؤكدين — مراجعة يومية، لأن التأكيد من اللوحة ممكن ما يغيّرش `updatedAt`. */
  listConfirmed(): Promise<SharedMerchantEntry[]>
  get(normalizedName: string): Promise<SharedMerchantEntry | undefined>
  /** بيكتب مستند مش مؤكد — القواعد بترفض أي `confirmed: true` أو تعديل على مؤكد. */
  save(entry: SharedMerchantEntry): Promise<void>
}

/** آخر مزامنة على الجهاز ده — مش بيانات مالية، وضياعها بيعمل مزامنة كاملة بس. */
export interface SyncCursorPort {
  read(): string | null
  write(iso: string): void
}
