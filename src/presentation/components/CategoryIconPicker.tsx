import { CATEGORY_ICONS, CategoryIcon } from './CategoryIcon'

const KEYS = Object.keys(CATEGORY_ICONS)

/** شبكة رموز التصنيف — OVERRIDES §33.1. مقفولة لحد ما يدوس، عشان الفورم ما يطولش. */
export function CategoryIconPicker({
  value,
  disabled,
  onChange,
}: {
  value: string
  disabled: boolean
  onChange: (key: string) => void
}) {
  return (
    <details className="catForm__icons">
      <summary className="catForm__iconsSummary">
        <span className="catForm__iconNow" aria-hidden="true">
          <CategoryIcon iconKey={value} size={18} />
        </span>
        غيّر الرمز
      </summary>
      <div className="catForm__iconGrid" role="radiogroup" aria-label="رمز التصنيف">
        {KEYS.map((key, index) => (
          <button
            key={key}
            type="button"
            role="radio"
            aria-checked={key === value}
            aria-label={`رمز ${index + 1}`}
            className="catForm__icon"
            disabled={disabled}
            onClick={() => onChange(key)}
          >
            <CategoryIcon iconKey={key} size={20} />
          </button>
        ))}
      </div>
    </details>
  )
}
