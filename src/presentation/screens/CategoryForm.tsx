import { useMemo, useState, type FormEvent } from 'react'
import type { Category } from '../../domain/entities/types'
import type { CategoryGroupKey } from '../../domain/categoryTree'
import type { CategorySaveInput } from '../../domain/categoryEdit'
import { categoryPlaceChoices } from '../../domain/categoryManageView'
import {
  CATEGORY_SWATCHES,
  childColors,
  firstFreeSwatch,
  swatchColors,
  swatchKeyOf,
  type CategoryColorPair,
} from '../../domain/categoryPalette'
import { CategoryIcon } from '../components/CategoryIcon'
import { CategoryIconPicker } from '../components/CategoryIconPicker'
import { catColorStyle } from '../components/categoryStyle'

interface Props {
  categories: readonly Category[]
  /** `null` = تصنيف جديد. */
  editing: Category | null
  busy: boolean
  onSave: (input: CategorySaveInput) => void
  onCancel: () => void
}

/**
 * فورم التصنيف — OVERRIDES §33.1. بيتعمل من جديد مع كل تعديل (`key`)، فالقيم المبدئية من `editing`.
 * القواعد نفسها (مستوى واحد، لون الفرعي من أبوه...) في `planCategorySave`؛ هنا اختيار وعرض بس.
 */
export function CategoryForm({ categories, editing, busy, onSave, onCancel }: Props) {
  const choices = useMemo(() => categoryPlaceChoices(categories, editing?.id ?? null), [categories, editing])
  const byId = useMemo(() => new Map(categories.map((c) => [c.id, c])), [categories])
  const currentParent = editing?.parentId ? byId.get(editing.parentId) : undefined

  const [name, setName] = useState(editing?.name ?? '')
  const [active, setActive] = useState(editing?.active ?? true)
  const [iconKey, setIconKey] = useState(editing?.iconKey ?? 'tag')
  const [place, setPlace] = useState<'main' | 'sub'>(currentParent ? 'sub' : 'main')
  const [parentId, setParentId] = useState(currentParent?.id ?? choices.parentGroups[0]?.options[0]?.id ?? '')
  // حساب قديم فيه تصنيفات من غير مجموعة ⇒ الجديد بيبدأ زيها؛ غير كده «الحياة الشخصية»
  const [groupKey, setGroupKey] = useState<string>(
    () => editing?.groupKey ?? currentParent?.groupKey ?? (choices.groups[0]?.key === null ? '' : 'personal'),
  )
  // `null` = لونه الحالي (مش من الدرجات المعروضة)
  const [swatchKey, setSwatchKey] = useState<string | null>(() =>
    editing && !currentParent ? swatchKeyOf(editing.lightColor) : firstFreeSwatch(categories),
  )

  const parent = place === 'sub' ? byId.get(parentId) : undefined
  const offPalette = editing && !currentParent && swatchKeyOf(editing.lightColor) === null ? editing : null
  let preview: CategoryColorPair | null
  if (place === 'sub') {
    const siblings = parent ? categories.filter((c) => c.parentId === parent.id && c.id !== editing?.id).length : 0
    preview = !parent ? null : editing?.parentId === parent.id ? editing : childColors(parent, siblings)
  } else {
    preview = swatchKey ? swatchColors(swatchKey) : offPalette
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    onSave({
      ...(editing ? { id: editing.id } : {}),
      name,
      active,
      iconKey,
      parentId: place === 'sub' ? parentId : null,
      ...(place === 'main' ? { groupKey: groupKey === '' ? null : (groupKey as CategoryGroupKey) } : {}),
      ...(place === 'main' && swatchKey ? { swatchKey } : {}),
    })
  }

  return (
    <form className="card catForm" onSubmit={submit} noValidate>
      <div className="catForm__head">
        <span className="catChip" style={preview ? catColorStyle(preview) : undefined} aria-hidden="true">
          <CategoryIcon iconKey={iconKey} size={20} />
        </span>
        <h3 className="catForm__title">{editing ? `تعديل «${editing.name}»` : 'تصنيف جديد'}</h3>
      </div>

      <label className="sheet__field">
        اسم التصنيف
        <input
          className="sheet__input"
          maxLength={80}
          value={name}
          disabled={busy}
          onChange={(e) => setName(e.target.value)}
        />
      </label>

      <fieldset className="catForm__fieldset">
        <legend className="sheet__label">مكانه</legend>
        <div className="catForm__chips">
          <button type="button" className="catForm__chip" aria-pressed={place === 'main'} disabled={busy} onClick={() => setPlace('main')}>
            تصنيف أساسي
          </button>
          <button
            type="button"
            className="catForm__chip"
            aria-pressed={place === 'sub'}
            disabled={busy || choices.parentGroups.length === 0}
            onClick={() => setPlace('sub')}
          >
            فرعي تحت تصنيف
          </button>
        </div>
        {choices.hasSubs && <p className="sheet__hint">تحته فرعيات، فبيفضل تصنيف أساسي.</p>}
      </fieldset>

      {place === 'main' ? (
        <>
          <label className="sheet__field">
            المجموعة
            <select className="sheet__input" value={groupKey} disabled={busy} onChange={(e) => setGroupKey(e.target.value)}>
              {choices.groups.map((g) => (
                <option key={g.key ?? ''} value={g.key ?? ''}>
                  {g.label}
                </option>
              ))}
            </select>
          </label>
          <fieldset className="catForm__fieldset">
            <legend className="sheet__label">اللون</legend>
            <div className="catForm__swatches" role="radiogroup" aria-label="لون التصنيف">
              {offPalette && (
                <Swatch label="لونه الحالي" colors={offPalette} on={swatchKey === null} disabled={busy} onPick={() => setSwatchKey(null)} />
              )}
              {CATEGORY_SWATCHES.map((s) => (
                <Swatch key={s.key} label={s.name} colors={swatchColors(s.key)!} on={swatchKey === s.key} disabled={busy} onPick={() => setSwatchKey(s.key)} />
              ))}
            </div>
          </fieldset>
        </>
      ) : (
        <>
          <label className="sheet__field">
            تحت
            <select className="sheet__input" value={parentId} disabled={busy} onChange={(e) => setParentId(e.target.value)}>
              {choices.parentGroups.map((g) => (
                <optgroup key={g.key} label={g.label}>
                  {g.options.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.label}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
          </label>
          {parent && <p className="sheet__hint">لونه درجة من لون «{parent.name}» عشان يبان إنه تحته.</p>}
        </>
      )}

      <CategoryIconPicker value={iconKey} disabled={busy} onChange={setIconKey} />

      <label className="catForm__check">
        <input type="checkbox" checked={active} disabled={busy} onChange={(e) => setActive(e.target.checked)} />
        ظاهر في الاختيارات
      </label>

      <div className="catForm__actions">
        <button type="submit" className="btn" disabled={busy || !name.trim() || (place === 'sub' && !parent)}>
          {busy ? 'بنحفظ…' : editing ? 'احفظ التعديل' : 'ضيف التصنيف'}
        </button>
        {/* الإلغاء ما يتعطلش أبدًا (HANDOVER §9.3) */}
        <button type="button" className="btn btn--quiet" onClick={onCancel}>
          إلغاء
        </button>
      </div>
    </form>
  )
}

function Swatch({
  label,
  colors,
  on,
  disabled,
  onPick,
}: {
  label: string
  colors: CategoryColorPair
  on: boolean
  disabled: boolean
  onPick: () => void
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={on}
      aria-label={label}
      className="catForm__swatch"
      style={catColorStyle(colors)}
      disabled={disabled}
      onClick={onPick}
    />
  )
}
