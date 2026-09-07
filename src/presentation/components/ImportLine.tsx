import { formatAmount } from '../../domain/formatMoney'
import type { ImportPreviewLine } from '../../application/useCases/importStatement'
import type { MatchingState } from '../../domain/entities/types'
import '../screens/ImportSheet.css'

const STATE_LABEL: Record<MatchingState, string> = {
  new: 'جديد',
  duplicate: 'مكرر',
  similar: 'متشابه',
  conflict: 'تعارض',
  invalid: 'غير صالح',
}

interface Props {
  line: ImportPreviewLine
  checked: boolean
  disabled: boolean
  onToggle: () => void
}

/**
 * سطر واحد في معاينة الاستيراد — مستخرج من `ImportSheet` لحد الـ300 سطر.
 *
 * سطر التعارض **معطَّل عن الاختيار**: `spec/05` يمنع الاستبدال الصامت،
 * فلا يُتاح تضمينه قبل حسمه.
 */
export function ImportLine({ line, checked, disabled, onToggle }: Props) {
  const isIncoming = line.row.direction === 'in'

  return (
    <li className={`line line--${line.state}`}>
      <label className="line__check">
        <input
          type="checkbox"
          checked={checked}
          onChange={onToggle}
          disabled={disabled}
          aria-label={`تضمين سطر ${line.row.lineNumber}`}
        />
      </label>

      <div className="line__main">
        <div className="line__name">
          {line.row.merchantName || line.row.description || 'بلا اسم'}
        </div>
        <div className="line__meta">
          <span>{line.row.date}</span>
          <span className="badge">{STATE_LABEL[line.state]}</span>
        </div>
        {/* سبب الحالة مكتوب دائمًا — لا حالة بلا تفسير (spec/04) */}
        <div className="line__reason">{line.reason}</div>
      </div>

      <div
        className="line__amount num"
        style={{ color: isIncoming ? 'var(--c-incoming)' : 'var(--c-outgoing)' }}
      >
        {formatAmount(line.row.amountMinor)}
      </div>
    </li>
  )
}
