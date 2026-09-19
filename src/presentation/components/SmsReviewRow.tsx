import { useId } from 'react'
import type { SmsReviewLine } from '../../application/useCases/reviewSmsInbox'
import type { CategoryOptionGroup } from '../../domain/categoryOptions'
import type { Category, Id } from '../../domain/entities/types'
import { formatAmount } from '../../domain/formatMoney'
import { CategoryIcon } from './CategoryIcon'
import { CategoryOptions } from './CategoryOptions'
import { catColorStyle } from './categoryStyle'

/**
 * صف عملية من رسالة بنك — OVERRIDES §36. الضغط بيفتح اختيار التصنيف تحت الصف نفسه.
 * «غير مصنف» بيتسجل عادي (قرار المالك) — بس بلون تنبيه عشان يبان إنه محتاج تصنيف.
 */
export function SmsReviewRow({
  line, category, groups, open, busy, similar, included, onOpen, onPick, onDismiss, onInclude,
}: {
  line: SmsReviewLine
  category: Category | undefined
  groups: readonly CategoryOptionGroup[]
  open: boolean
  busy: boolean
  /** شبه عملية موجودة: ما بتتسجلش إلا لو اختارها. */
  similar?: boolean
  included?: boolean
  onOpen: () => void
  onPick: (categoryId: Id) => void
  onDismiss: () => void
  onInclude?: (on: boolean) => void
}) {
  const selectId = useId()
  const name = line.merchant || (line.direction === 'in' ? 'دخل من البنك' : 'عملية بنكية')
  const blocked = line.state === 'conflict'
  return (
    <li className={`smsRow${open ? ' smsRow--open' : ''}${similar && !included ? ' smsRow--muted' : ''}`}>
      <button type="button" className="smsRow__main" onClick={onOpen} aria-expanded={open} disabled={busy}>
        <span className={`smsRow__icon${category ? ' smsRow__icon--cat' : ''}`} style={category ? catColorStyle(category) : undefined}>
          <CategoryIcon iconKey={category?.iconKey ?? 'help-circle'} size={18} />
        </span>
        <span className="smsRow__text">
          <span className="smsRow__name">{name}</span>
          <span className={`smsRow__cat${category ? '' : ' smsRow__cat--none'}`}>
            {category ? category.name : 'غير مصنف · اضغط تختار'}
            {similar && <span className="smsRow__flag">{blocked ? 'تعارض' : 'شبه عملية موجودة'}</span>}
          </span>
        </span>
        <span className={`smsRow__amount num smsRow__amount--${line.direction}`}>
          {line.direction === 'in' ? '+' : ''}{formatAmount(line.amountMinor)}
        </span>
      </button>
      {open && (
        <div className="smsRow__edit">
          <label htmlFor={selectId}>التصنيف</label>
          <select id={selectId} className="sheet__input" value={category?.id ?? ''} disabled={busy} onChange={(e) => e.target.value && onPick(e.target.value)}>
            <option value="">اختار تصنيف…</option>
            <CategoryOptions groups={groups} />
          </select>
          {similar && (
            <p className="sheet__hint">
              {line.reason}
              {!blocked && onInclude && (
                <label className="smsRow__include">
                  <input type="checkbox" checked={!!included} disabled={busy} onChange={(e) => onInclude(e.target.checked)} /> سجّلها برضه
                </label>
              )}
            </p>
          )}
          <button type="button" className="btn btn--quiet" disabled={busy} onClick={onDismiss}>شيلها من غير ما تتسجل</button>
        </div>
      )}
    </li>
  )
}
