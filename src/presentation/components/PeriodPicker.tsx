import { useState } from 'react'
import { buildPeriod, type Period } from '../../domain/period'
import './PeriodPicker.css'

export const MONTH_NAMES = [
  'يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو',
  'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر',
]

export function monthLabel(period: Period): string {
  const [year, month] = period.key.split('-').map(Number)
  return `${MONTH_NAMES[month - 1]} ${year}`
}

interface Props {
  period: Period
  payday: number
  onChange: (period: Period) => void
}

/**
 * منتقي الشهر والسنة — spec/04:
 * «منتقي شهر وسنة؛ تطبيق الاختيار على الشاشة ذات الصلة **وإظهار النطاق**.»
 *
 * إظهار النطاق ليس تفصيلًا: الشهر المالي يبدأ يوم الراتب لا يوم 1،
 * فـ«سبتمبر 2026» يعني 28 أغسطس ← 27 سبتمبر. بدون النطاق يختلط
 * الشهر المالي بالتقويمي (spec/02).
 */
export function PeriodPicker({ period, payday, onChange }: Props) {
  const [open, setOpen] = useState(false)
  const [year, month] = period.key.split('-').map(Number)

  const shift = (delta: number) => {
    const total = year * 12 + (month - 1) + delta
    onChange(buildPeriod(Math.floor(total / 12), (total % 12) + 1, payday))
  }

  return (
    <div className="periodPicker">
      <div className="periodPicker__bar">
        {/* في RTL السهم لليمين يعني «السابق» بصريًا */}
        <button
          type="button"
          className="periodPicker__arrow"
          onClick={() => shift(-1)}
          aria-label="الفترة السابقة"
        >
          <span aria-hidden="true">›</span>
        </button>

        <button
          type="button"
          className="periodPicker__label"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          aria-label={`الفترة الحالية ${monthLabel(period)}. اضغط لاختيار فترة تانية`}
        >
          <span className="periodPicker__month">{monthLabel(period)}</span>
          <span className="periodPicker__range num">
            {period.start} ← {period.end}
          </span>
        </button>

        <button
          type="button"
          className="periodPicker__arrow"
          onClick={() => shift(1)}
          aria-label="الفترة التالية"
        >
          <span aria-hidden="true">‹</span>
        </button>
      </div>

      {open && (
        <div className="periodPicker__panel">
          <div className="periodPicker__years">
            <button type="button" className="periodPicker__yearBtn" onClick={() => shift(-12)}>
              {year - 1}
            </button>
            <span className="periodPicker__yearCurrent">{year}</span>
            <button type="button" className="periodPicker__yearBtn" onClick={() => shift(12)}>
              {year + 1}
            </button>
          </div>

          <div className="periodPicker__months">
            {MONTH_NAMES.map((name, index) => {
              const isCurrent = index + 1 === month
              return (
                <button
                  key={name}
                  type="button"
                  className={`periodPicker__month${isCurrent ? ' periodPicker__month--on' : ''}`}
                  aria-current={isCurrent ? 'true' : undefined}
                  onClick={() => {
                    onChange(buildPeriod(year, index + 1, payday))
                    setOpen(false)
                  }}
                >
                  {name}
                </button>
              )
            })}
          </div>

          <p className="periodPicker__note">
            الشهر المالي بيبدأ يوم {payday} (يوم الراتب)، مش يوم 1.
          </p>
        </div>
      )}
    </div>
  )
}
