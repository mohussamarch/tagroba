import { categoryColors, subCategoryColors } from '../../domain/categoryColors'
import { isCategoryGroupKey, isCategoryRequirement } from '../../domain/categoryTree'
import { normalizeText } from '../../domain/normalize'
import type { Category, Id } from '../../domain/entities/types'

/**
 * بيبني التصنيفات من `categoryTree.json` (OVERRIDES §28–28.1) للزرع في الحسابات الجديدة.
 *
 * - **المعرّف من الاسم بنفس طريقة `buildCategories` القديمة** (`cat-<الاسم مطبعن>`)، فالتصنيف
 *   اللي اسمه ما اتغيرش بيفضل بنفس معرّفه — ده اللي بيخلّي نقل عمليات الحسابات القديمة ممكن.
 *   الفرعي: `<معرّف أبوه>--<اسمه مطبعن>`.
 * - `aliases`: كل اسم قديم (من `from`) أو اسم موجود ⇒ معرّف، عشان ملفات القواعد والتجار
 *   المكتوبة بالأسماء القديمة («ملابس وعطور»، «تقسيط»...) توصل للتصنيف الجديد.
 *   **اسم قديم ليه أكتر من وجهة («تأمين») متعمد إنه مش موجود** ⇒ بيتبلّغ كمجهول ولا يُخمَّن.
 * - `wordOverrides`: كلمة قاعدة ⇒ فرعي بعينه (SHORY ← تأمين السيارة).
 * - بيانات غلط (مجموعة أو شرط مش معروف، اسم مكرر، كلمة بتشاور على فرعي مش موجود) ⇒ خطأ فورًا.
 */

type RawSub = readonly string[]

interface RawMain {
  name: string
  icon: string
  h: number
  s: number
  l: number
  from?: readonly string[]
  requires?: string
  noCar?: { name: string; icon: string }
  subs: readonly RawSub[]
}

export interface RawCategoryTree {
  groups: readonly { key: string; mains: readonly RawMain[] }[]
  ruleWordOverrides?: Readonly<Record<string, readonly string[]>>
}

export interface BuiltCategoryTree {
  categories: Category[]
  aliases: Map<string, Id>
  wordOverrides: Map<string, Id>
}

const slug = (text: string) => normalizeText(text).replace(/\s+/g, '-').toLowerCase()

function requirementOf(value: string | undefined, where: string) {
  if (value === undefined) return {}
  if (!isCategoryRequirement(value)) throw new Error(`شرط ظهور مش معروف «${value}» في ${where}`)
  return { requires: value }
}

export function buildCategoryTree(raw: RawCategoryTree): BuiltCategoryTree {
  const categories: Category[] = []
  const idByPath = new Map<string, Id>()
  let order = 0

  for (const group of raw.groups) {
    if (!isCategoryGroupKey(group.key)) throw new Error(`مجموعة مش معروفة «${group.key}»`)
    for (const main of group.mains) {
      const id = `cat-${slug(main.name)}`
      categories.push({
        id, parentId: null, name: main.name, iconKey: main.icon, ...categoryColors(main.h, main.s, main.l),
        active: true, order: order++, groupKey: group.key, ...requirementOf(main.requires, main.name),
        ...(main.noCar ? { noCarName: main.noCar.name, noCarIconKey: main.noCar.icon } : {}),
      })
      idByPath.set(main.name, id)
      main.subs.forEach(([name, icon, requires], index) => {
        if (!name || !icon) throw new Error(`فرعي ناقص تحت «${main.name}»`)
        const subId = `${id}--${slug(name)}`
        categories.push({
          id: subId, parentId: id, name, iconKey: icon, ...subCategoryColors(main.h, main.s, main.l, index),
          active: true, order: order++, ...requirementOf(requires, name),
        })
        idByPath.set(`${main.name}›${name}`, subId)
      })
    }
  }

  const seen = new Set<Id>()
  for (const category of categories) {
    if (seen.has(category.id)) throw new Error(`اسم تصنيف مكرر بنفس المعرّف: ${category.id}`)
    seen.add(category.id)
  }

  // الأسماء الموجودة الأول (لو الاسم متكرر في فرعيين، ما يتربطش بحد)، وبعدين الأسماء القديمة
  const aliases = new Map<string, Id>()
  const ambiguous = new Set<string>()
  for (const category of categories) {
    const key = normalizeText(category.name)
    if (aliases.has(key)) ambiguous.add(key)
    else aliases.set(key, category.id)
  }
  for (const key of ambiguous) aliases.delete(key)
  for (const group of raw.groups) {
    for (const main of group.mains) {
      const mainId = idByPath.get(main.name)!
      for (const old of main.from ?? []) {
        const key = normalizeText(old)
        if (!aliases.has(key) && !ambiguous.has(key)) aliases.set(key, mainId)
      }
    }
  }

  const wordOverrides = new Map<string, Id>()
  for (const [word, path] of Object.entries(raw.ruleWordOverrides ?? {})) {
    const target = idByPath.get(`${path[0]}›${path[1]}`)
    if (!target) throw new Error(`كلمة «${word}» بتشاور على فرعي مش موجود: ${path.join(' › ')}`)
    wordOverrides.set(normalizeText(word), target)
  }

  return { categories, aliases, wordOverrides }
}
