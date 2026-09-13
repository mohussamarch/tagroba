import type { CleanupItem } from '../../domain/orphanCleanup'

/**
 * حذف مستندات **بمسار الوثيقة الحقيقي** (docId) — مش بحقل `id` اللي ممكن يكون مقصوص.
 * الحذف بالحقل المقصوص هو اللي ساب بقايا التراجع عن دفعة الإكسل.
 * يرجع عدد اللي اتحذف. الدفعة الواحدة ذرية.
 */
export interface RemoveDocsPort {
  remove(items: readonly CleanupItem[]): Promise<number>
}
