import { merchantIndex } from './merchantIndex'
import { normalizeText } from './normalize'
import type { Id, Merchant } from './entities/types'

/**
 * «افتكر المحل ده» — OVERRIDES §36: المستخدم اختار تصنيف لمحل من رسالة بنك وقال «أيوه افتكره».
 * المحل الموجود (بالاسم أو اسم بديل) بياخد التصنيف المؤكد، والجديد بيتعمل. التصنيف المؤكد أقوى من القواعد (spec/05).
 * `null` لو الاسم فاضي أو أرقام بس — مفيش محل نفتكره.
 */
export function rememberMerchant(all: readonly Merchant[], rawName: string, categoryId: Id, newId: Id): Merchant | null {
  const displayName = rawName.trim().replace(/\s+/g, ' ')
  const normalizedName = normalizeText(displayName)
  if (!normalizedName || /^[\d\s*•.:-]*$/.test(displayName)) return null
  const existing = merchantIndex(all).get(normalizedName)
  if (existing) return { ...existing, verifiedCategoryId: categoryId }
  return { id: newId, displayName: displayName.slice(0, 120), normalizedName, verifiedCategoryId: categoryId }
}
