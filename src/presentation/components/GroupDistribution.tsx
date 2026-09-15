import { useMemo, useState, type CSSProperties } from 'react'
import { formatAmount } from '../../domain/formatMoney'
import { CATEGORY_GROUPS } from '../../domain/categoryTree'
import { groupDistribution, type GroupSliceKey } from '../../domain/groupDistribution'
import type { CategorySlice } from '../../domain/analytics'
import type { Category } from '../../domain/entities/types'
import { CategoryIcon } from './CategoryIcon'
import './GroupDistribution.css'

const GROUP_NAMES = new Map<string, string>(CATEGORY_GROUPS.map((g) => [g.key, g.name]))
const GROUP_ICONS = new Map<string, string>(CATEGORY_GROUPS.map((g) => [g.key, g.iconKey]))
const groupName = (key: GroupSliceKey) =>
  GROUP_NAMES.get(key) ?? (key === 'ungrouped' ? 'تصنيفات من غير مجموعة' : 'بلا تصنيف')
const percentOf = (tenths: number) => (tenths / 10).toFixed(1)
const catStyle = (c?: Category) =>
  c ? ({ '--cat-light': c.lightColor, '--cat-dark': c.darkColor } as CSSProperties) : undefined

/**
 * «التصنيفات» في الرئيسية — OVERRIDES §28.1: من برا المجموعات بالكتير خمسة، ودوسة على المجموعة
 * بتفتح تصنيفاتها الأساسية. الحساب القديم من غير مجموعات بيفضل يشوف أكبر 5 تصنيفات زي الأول.
 * المكوّن ما بيحسبش فلوس: التجميع في `groupDistribution` (domain) والعرض بـ`formatAmount`.
 */
export function GroupDistribution({ distribution, categories, amountsHidden }: {
  distribution: readonly CategorySlice[]
  categories: readonly Category[]
  amountsHidden: boolean
}) {
  const view = useMemo(() => groupDistribution(distribution, categories), [distribution, categories])
  const byId = useMemo(() => new Map(categories.map((c) => [c.id, c])), [categories])
  const [open, setOpen] = useState<GroupSliceKey | null>(null)
  const money = (minor: number) => (amountsHidden ? '••••' : formatAmount(minor))

  if (distribution.length === 0) return null

  if (!view.grouped) {
    return (
      <section className="card" aria-label="توزيع التصنيفات">
        <h2 className="card__title">التصنيفات</h2>
        <ul className="dist">
          {distribution.slice(0, 5).map((slice) => {
            const category = slice.categoryId ? byId.get(slice.categoryId) : undefined
            const name = category?.name ?? 'بلا تصنيف'
            const percent = percentOf(slice.shareTenthPercent)
            return (
              <li key={slice.categoryId ?? '—'} className="dist__row">
                <div className="dist__head">
                  <span className="dist__name">{name}</span>
                  <span className="dist__amount num">{money(slice.amountMinor)}</span>
                </div>
                <div className="dist__bar" role="img" aria-label={`${name}: ${percent} بالمئة، ${slice.count} عملية`}>
                  <div className="dist__fill groupDist__fill--cat" style={{ width: `${percent}%`, ...catStyle(category) }} />
                </div>
                <span className="dist__meta">{percent}% · {slice.count} عملية</span>
              </li>
            )
          })}
        </ul>
      </section>
    )
  }

  return (
    <section className="card" aria-label="توزيع المصروف على المجموعات">
      <h2 className="card__title">المصروف بالمجموعات</h2>
      <ul className="dist">
        {view.groups.map((group) => {
          const name = groupName(group.key)
          const percent = percentOf(group.shareTenthPercent)
          const expandable = group.mains.length > 0
          const isOpen = open === group.key
          return (
            <li key={group.key} className="dist__row groupDist__row">
              <button type="button" className="groupDist__toggle" disabled={!expandable} aria-expanded={expandable ? isOpen : undefined}
                onClick={() => setOpen(isOpen ? null : group.key)}>
                <span className="groupDist__icon" aria-hidden="true"><CategoryIcon iconKey={GROUP_ICONS.get(group.key) ?? 'tag'} size={18} /></span>
                <span className="dist__name">{name}</span>
                <span className="dist__amount num">{money(group.amountMinor)}</span>
              </button>
              <div className="dist__bar" role="img" aria-label={`${name}: ${percent} بالمئة، ${group.count} عملية`}>
                <div className="dist__fill groupDist__fill" style={{ width: `${percent}%` }} />
              </div>
              <span className="dist__meta">{percent}% · {group.count} عملية{expandable ? (isOpen ? ' · دوس للقفل' : ' · دوس للتفاصيل') : ''}</span>
              {isOpen && (
                <ul className="groupDist__mains">
                  {group.mains.map((main) => {
                    const category = byId.get(main.categoryId)
                    const mainName = category?.name ?? 'تصنيف مش موجود'
                    const mainPercent = percentOf(main.shareTenthPercent)
                    return (
                      <li key={main.categoryId} className="dist__row">
                        <div className="dist__head">
                          <span className="groupDist__catIcon" aria-hidden="true" style={catStyle(category)}>
                            <CategoryIcon iconKey={category?.iconKey ?? 'tag'} size={16} />
                          </span>
                          <span className="dist__name">{mainName}</span>
                          <span className="dist__amount num">{money(main.amountMinor)}</span>
                        </div>
                        <div className="dist__bar" role="img" aria-label={`${mainName}: ${mainPercent} بالمئة، ${main.count} عملية`}>
                          <div className="dist__fill groupDist__fill--cat" style={{ width: `${mainPercent}%`, ...catStyle(category) }} />
                        </div>
                        <span className="dist__meta">{mainPercent}% · {main.count} عملية</span>
                      </li>
                    )
                  })}
                </ul>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
