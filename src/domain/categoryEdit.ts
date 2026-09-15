import { childColors, firstFreeSwatch, swatchColors, type CategoryColorPair } from './categoryPalette'
import { isCategoryGroupKey, type CategoryGroupKey } from './categoryTree'
import { normalizeText } from './normalize'
import type { Category, Id } from './entities/types'

/**
 * حفظ تصنيف من شاشة التصنيفات — OVERRIDES §33.1.
 *
 * - **مستوى واحد بس**: الفرعي تحت أساسي، والتصنيف اللي تحته فرعيات ما يتنقلش تحت حد.
 * - الفرعي **مالوش مجموعة ولا لون بيختاره**: مجموعته من أبوه، ولونه درجة من لون أبوه بتتحسب
 *   لما يتعمل أو يتنقل بس (تعديل اسمه أو رمزه ما بيغيرش لونه).
 * - تغيير لون الأساسي بيغيّر درجات فرعياته معاه (`recolored`) عشان يفضلوا من نفس اللون.
 * - اللي الشاشة ما بتعدلهوش (شرط الظهور، اسم «مالوش سيارة»، الترتيب) بيفضل زي ما هو.
 */

export interface CategorySaveInput {
  id?: Id
  name: string
  active: boolean
  /** من قايمة الرموز. من غيره: الحالي، أو `tag` للجديد. */
  iconKey?: string
  /** `null` = أساسي. من غيره: مكانه الحالي، والجديد أساسي. */
  parentId?: Id | null
  /** مجموعة الأساسي؛ `null` = بلا مجموعة. من غيرها: الحالية. الفرعي بيتجاهلها. */
  groupKey?: CategoryGroupKey | null
  /** لون الأساسي من `CATEGORY_SWATCHES`. من غيره: الحالي، أو أول لون مش مستخدم للجديد. */
  swatchKey?: string
}

export interface CategorySavePlan {
  item: Category
  /** فرعيات اتحسبت درجاتها من جديد لأن لون أبوها اتغير. */
  recolored: Category[]
}

const ICON_KEY = /^[a-z0-9-]{1,40}$/

export function planCategorySave(
  all: readonly Category[],
  input: CategorySaveInput,
  newId: () => Id,
): CategorySavePlan {
  const old = input.id === undefined ? undefined : all.find((c) => c.id === input.id)
  if (input.id !== undefined && !old) throw new Error('التصنيف مش موجود.')
  const name = input.name.trim()
  if (!name || name.length > 80) throw new Error('اكتب اسم التصنيف بحد أقصى ٨٠ حرف.')
  if (all.some((c) => c.id !== old?.id && normalizeText(c.name) === normalizeText(name))) {
    throw new Error('فيه تصنيف بنفس الاسم.')
  }
  const iconKey = input.iconKey ?? old?.iconKey ?? 'tag'
  if (!ICON_KEY.test(iconKey)) throw new Error('اختار رمز من القايمة.')

  const byId = new Map(all.map((c) => [c.id, c]))
  const currentParentId = old?.parentId ?? null
  const parentId = input.parentId === undefined ? currentParentId : input.parentId
  const moved = parentId !== currentParentId
  // فرعي أبوه مش موجود بيتعامل كأساسي (زي `groupCategoryOptions`)
  const parent = parentId === null ? undefined : byId.get(parentId)
  if (moved && parentId !== null) {
    if (parentId === old?.id) throw new Error('التصنيف مينفعش يبقى فرعي تحت نفسه.')
    if (!parent) throw new Error('التصنيف الأساسي اللي اخترته مش موجود.')
    if (parent.parentId !== null && byId.has(parent.parentId)) throw new Error('اختار تصنيف أساسي، مش فرعي.')
    if (old && all.some((c) => c.parentId === old.id)) {
      throw new Error('التصنيف ده تحته فرعيات، فمينفعش يتنقل تحت تصنيف تاني.')
    }
  }

  let groupKey: CategoryGroupKey | undefined
  if (parent || input.groupKey === null) groupKey = undefined
  else if (input.groupKey !== undefined) {
    if (!isCategoryGroupKey(input.groupKey)) throw new Error('المجموعة اللي اخترتها مش معروفة.')
    groupKey = input.groupKey
  } else {
    // فرعي بقى أساسي من غير ما يختار مجموعة ⇒ بيفضل في مجموعة أبوه القديم
    groupKey = old?.groupKey ?? (currentParentId === null ? undefined : byId.get(currentParentId)?.groupKey)
  }

  let colors: CategoryColorPair
  if (parent) {
    colors = old && !moved
      ? { lightColor: old.lightColor, darkColor: old.darkColor }
      : childColors(parent, all.filter((c) => c.parentId === parent.id && c.id !== old?.id).length)
  } else if (input.swatchKey !== undefined) {
    const picked = swatchColors(input.swatchKey)
    if (!picked) throw new Error('اختار لون من القايمة.')
    colors = picked
  } else if (old) {
    colors = { lightColor: old.lightColor, darkColor: old.darkColor }
  } else {
    colors = swatchColors(firstFreeSwatch(all))!
  }

  const item: Category = {
    ...old,
    id: old?.id ?? newId(),
    parentId,
    name,
    iconKey,
    ...colors,
    active: input.active,
    order: old?.order ?? Math.max(0, ...all.map((c) => c.order)) + 1,
    groupKey,
  }

  const recolored: Category[] = []
  if (old && !parent && (colors.lightColor !== old.lightColor || colors.darkColor !== old.darkColor)) {
    all
      .filter((c) => c.parentId === old.id)
      .sort((a, b) => a.order - b.order)
      .forEach((kid, index) => recolored.push({ ...kid, ...childColors(colors, index) }))
  }
  return { item, recolored }
}
