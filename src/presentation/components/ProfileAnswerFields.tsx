import { DEPENDENT_KINDS, type DependentKind, type Gender } from '../../domain/userProfile'
import './ProfileAnswerFields.css'

/**
 * خانات المعلومات اللي بتظهر تصنيفات (OVERRIDES §28.1) — مشتركة بين أسئلة البداية وقسم الحساب.
 * «مش عايز أحدد» = `null` (ما اتجاوبش)، مش «لأ».
 */

interface FieldClasses {
  fieldClass: string
  labelClass?: string
  inputClass: string
}

export function YesNoField({ label, hint, value, onChange, fieldClass, labelClass, inputClass }: FieldClasses & {
  label: string
  hint?: string
  value: boolean | null
  onChange: (value: boolean | null) => void
}) {
  return (
    <label className={fieldClass}>
      <span className={labelClass}>{label}</span>
      <select className={inputClass} value={value === null ? '' : value ? 'yes' : 'no'}
        onChange={(e) => onChange(e.target.value === '' ? null : e.target.value === 'yes')}>
        <option value="">مش عايز أحدد</option>
        <option value="yes">أيوه</option>
        <option value="no">لأ</option>
      </select>
      {hint && <small className="settings__hint">{hint}</small>}
    </label>
  )
}

const kindLabel = (kind: DependentKind, gender: Gender | null) =>
  kind === 'spouse' ? (gender === 'female' ? 'الزوج' : 'الزوجة') : kind === 'children' ? 'الأولاد' : 'الأهل'

/** «بتعول مين؟» — اختيار أكتر من واحد. ولا واحد متعلّم = ما اتجاوبش. */
export function DependentKindsField({ value, gender, onChange, fieldClass, labelClass }: Omit<FieldClasses, 'inputClass'> & {
  value: DependentKind[] | null
  gender: Gender | null
  onChange: (value: DependentKind[] | null) => void
}) {
  const chosen = new Set(value ?? [])
  const toggle = (kind: DependentKind) => {
    const next = new Set(chosen)
    if (next.has(kind)) next.delete(kind)
    else next.add(kind)
    const list = DEPENDENT_KINDS.filter((k) => next.has(k))
    onChange(list.length ? list : null)
  }
  return (
    <fieldset className={fieldClass}>
      <legend className={labelClass}>بتعول مين؟</legend>
      {DEPENDENT_KINDS.map((kind) => (
        <label key={kind} className="profileAnswer__check">
          <input type="checkbox" checked={chosen.has(kind)} onChange={() => toggle(kind)} /> {kindLabel(kind, gender)}
        </label>
      ))}
    </fieldset>
  )
}
