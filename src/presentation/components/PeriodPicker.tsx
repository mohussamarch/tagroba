import { useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
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

/** «28 أغسطس – 27 سبتمبر» بدل تاريخين ISO — أبسط للقراية، والنطاق لسه ظاهر (spec/04). */
export function shortRange(period: Period): string {
  const day = (iso: string) => {
    const [, month, dayOfMonth] = iso.split('-').map(Number)
    return `${dayOfMonth} ${MONTH_NAMES[month - 1]}`
  }
  return `${day(period.start)} – ${day(period.end)}`
}

interface Props {
  period: Period
  payday: number
  onChange: (period: Period) => void
}

/**
 * منتقي الشهر والسنة — spec/04: «منتقي شهر وسنة؛ تطبيق الاختيار على الشاشة ذات الصلة **وإظهار النطاق**».
 * الشهر المالي بيبدأ يوم الراتب لا يوم 1، فالنطاق لازم يفضل ظاهر (spec/02).
 *
 * إعادة تصميم 2026-09-15 (طلب المالك «بشكل أبسط» و«اتجاه الأسهم معكوس»):
 * سطر واحد مسطح — سهم، الشهر والسنة وتحتهم النطاق بالأيام والشهور، سهم.
 * الأسهم **رسمة SVG** مش حروف «‹ ›» (الحروف دي بتنعكس لوحدها في النص العربي فكان اتجاهها بيبان غلط).
 * الاتجاه على طريقة التطبيقات العربية: **يمين = الشهر اللي فات (السهم لليمين)، شمال = الشهر الجاي (السهم للشمال)**.
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
        <button type="button" className="periodPicker__arrow" onClick={() => shift(-1)} aria-label="الشهر اللي فات">
          <ChevronRight size={22} aria-hidden="true" />
        </button>

        <button
          type="button"
          className="periodPicker__label"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          aria-label={`${monthLabel(period)}، من ${shortRange(period)}. اضغط لاختيار شهر تاني`}
        >
          <span className="periodPicker__month">{monthLabel(period)}</span>
          <span className="periodPicker__range">{shortRange(period)}</span>
        </button>

        <button type="button" className="periodPicker__arrow" onClick={() => shift(1)} aria-label="الشهر الجاي">
          <ChevronLeft size={22} aria-hidden="true" />
        </button>
      </div>

      {open && (
        <div className="periodPicker__panel">
          <div className="periodPicker__years">
            <button type="button" className="periodPicker__yearBtn" onClick={() => shift(12)}>
              {year + 1}
            </button>
            <span className="periodPicker__yearCurrent">{year}</span>
            <button type="button" className="periodPicker__yearBtn" onClick={() => shift(-12)}>
              {year - 1}
            </button>
          </div>

          <div className="periodPicker__months">
            {MONTH_NAMES.map((name, index) => {
              const isCurrent = index + 1 === month
              return (
                <button
                  key={name}
                  type="button"
                  className={`periodPicker__monthBtn${isCurrent ? ' periodPicker__monthBtn--on' : ''}`}
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
