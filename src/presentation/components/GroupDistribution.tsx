import { useMemo, useState, type CSSProperties } from 'react'
import { ChartPie } from 'lucide-react'
import { DonutChart } from './DonutChart'
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
  // لون المجموعة = لون أكبر تصنيف جواها (OVERRIDES §31: ألوان التصنيفات في كل مكان)؛ «بلا تصنيف» رمادي
  const groupColor = (group: { mains: readonly { categoryId: string }[] }) =>
    group.mains[0] ? byId.get(group.mains[0].categoryId) : undefined
  const money = (minor: number) => (amountsHidden ? '••••' : formatAmount(minor))

  if (distribution.length === 0) return null

  if (!view.grouped) {
    return (
      <section className="card" aria-label="توزيع التصنيفات">
        <h2 className="card__title"><ChartPie size={16} aria-hidden="true" />التصنيفات</h2>
        <DonutChart
          label={`توزيع المصروف على أكبر ${Math.min(distribution.length, 5)} تصنيفات`}
          segments={distribution.slice(0, 5).map((slice) => ({
            key: slice.categoryId ?? '—',
            shareTenthPercent: slice.shareTenthPercent,
            style: catStyle(slice.categoryId ? byId.get(slice.categoryId) : undefined),
          }))}
          center={<>
            <span className="donut__centerValue">{percentOf(distribution[0].shareTenthPercent)}%</span>
            <span className="donut__centerLabel">{(distribution[0].categoryId ? byId.get(distribution[0].categoryId)?.name : undefined) ?? 'بلا تصنيف'}</span>
          </>}
        />
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
      <h2 className="card__title"><ChartPie size={16} aria-hidden="true" />المصروف بالمجموعات</h2>
      {view.groups.length > 0 && (
        <DonutChart
          label={`توزيع المصروف على ${view.groups.length} مجموعات`}
          segments={view.groups.map((group) => ({ key: group.key, shareTenthPercent: group.shareTenthPercent, style: catStyle(groupColor(group)) }))}
          center={<>
            <span className="donut__centerValue">{percentOf(view.groups[0].shareTenthPercent)}%</span>
            <span className="donut__centerLabel">{groupName(view.groups[0].key)}</span>
          </>}
        />
      )}
      <ul className="dist">
        {view.groups.map((group) => {
          const name = groupName(group.key)
          const percent = percentOf(group.shareTenthPercent)
          const color = catStyle(groupColor(group))
          const expandable = group.mains.length > 0
          const isOpen = open === group.key
          return (
            <li key={group.key} className="dist__row groupDist__row">
              <button type="button" className="groupDist__toggle" disabled={!expandable} aria-expanded={expandable ? isOpen : undefined}
                onClick={() => setOpen(isOpen ? null : group.key)}>
                <span className={`groupDist__icon${color ? ' groupDist__icon--cat' : ''}`} aria-hidden="true" style={color}><CategoryIcon iconKey={GROUP_ICONS.get(group.key) ?? 'tag'} size={18} /></span>
                <span className="dist__name">{name}</span>
                <span className="dist__amount num">{money(group.amountMinor)}</span>
              </button>
              <div className="dist__bar" role="img" aria-label={`${name}: ${percent} بالمئة، ${group.count} عملية`}>
                <div className={`dist__fill ${color ? 'groupDist__fill--cat' : 'groupDist__fill'}`} style={{ width: `${percent}%`, ...color }} />
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
