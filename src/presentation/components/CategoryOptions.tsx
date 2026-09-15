import type { CategoryOptionGroup } from '../../domain/categoryOptions'

/**
 * خيارات `<select>` التصنيف مقسّمة بالمجموعات (OVERRIDES §28.1). الترتيب والإخفاء
 * محسوبين في `groupCategoryOptions` (domain)؛ هنا عرض بس.
 * حساب قديم من غير مجموعات ⇒ قايمة عادية زي الأول من غير عنوان مجموعة.
 */
export function CategoryOptions({ groups }: { groups: readonly CategoryOptionGroup[] }) {
  const option = (o: CategoryOptionGroup['options'][number]) => (
    <option key={o.id} value={o.id}>
      {o.depth ? `— ${o.label}` : o.label}
    </option>
  )
  if (groups.length === 1 && groups[0]!.key === 'ungrouped') return <>{groups[0]!.options.map(option)}</>
  return (
    <>
      {groups.map((group) => (
        <optgroup key={group.key} label={group.label}>
          {group.options.map(option)}
        </optgroup>
      ))}
    </>
  )
}
