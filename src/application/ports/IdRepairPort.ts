import type { IdPatch, StoredData } from '../../domain/idRepair'

/** وصول الصيانة لمستندات الحساب مع معرّف كل وثيقة — HANDOVER §23. */
export interface IdRepairPort {
  /** قراءة كاملة من الخادم مع معرّف الوثيقة الحقيقي؛ لا تعتمد على كاش ناقص. */
  readAll(): Promise<StoredData>
  /** يكتب الحقول المصححة فقط على مسارات الوثائق الموجودة. يرجع عدد الوثائق. */
  apply(patches: readonly IdPatch[]): Promise<number>
}
