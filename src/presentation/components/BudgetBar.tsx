import { formatAmount } from '../../domain/formatMoney'
import type { BudgetStatus } from '../../domain/budget'
import '../screens/BudgetScreen.css'

/** شريط السقف وخانة الحقيقة — مستخرجان من BudgetScreen لحد الـ300 سطر. */

export function BudgetBar({
  status,
  hidden,
  compact = false,
}: {
  status: BudgetStatus
  hidden: boolean
  compact?: boolean
}) {
  const percent = status.usedTenthPercent / 10
  const width = Math.min(100, percent)
  const label =
    status.level === 'over' ? 'تجاوزت' : status.level === 'near' ? 'قربت تخلص' : 'في حدود السقف'

  return (
    <div className={`bar${compact ? ' bar--compact' : ''}`}>
      <div className="bar__row">
        {/* الحالة بالنص أيضًا لا باللون وحده — spec/04 */}
        <span className={`bar__label bar__label--${status.level}`}>{label}</span>
        <span className="bar__numbers num">
          {hidden ? '••••' : formatAmount(status.spentMinor)} /{' '}
          {hidden ? '••••' : formatAmount(status.limitMinor)}
        </span>
      </div>
      <div
        className="bar__track"
        role="img"
        aria-label={`${percent.toFixed(0)} بالمئة من السقف، ${label}`}
      >
        <div className={`bar__fill bar__fill--${status.level}`} style={{ width: `${width}%` }} />
      </div>
      <span className="bar__foot">
        {status.remainingMinor >= 0
          ? `باقي ${hidden ? '••••' : formatAmount(status.remainingMinor)}`
          : `تجاوزت بـ${hidden ? '••••' : formatAmount(-status.remainingMinor)}`}
        {status.thresholdCrossed && ' · عدّيت عتبة التنبيه'}
      </span>
    </div>
  )
}

export function Fact({
  label,
  value,
  note,
  tone = 'plain',
}: {
  label: string
  value: string
  note?: string | null
  tone?: 'plain' | 'warn'
}) {
  return (
    <div className="fact">
      <span className="fact__label">{label}</span>
      <span className={`fact__value${tone === 'warn' ? ' fact__value--warn' : ''}`}>{value}</span>
      {note && <span className="fact__note">{note}</span>}
    </div>
  )
}
