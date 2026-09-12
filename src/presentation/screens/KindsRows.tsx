import { formatAmount } from '../../domain/formatMoney'
import { ruleFor, type EconomicKind } from '../../domain/entities/economicKind'
import type { SuggestionLine } from '../../application/useCases/setEconomicKind'

/**
 * صفوف ورقة تحديد الأنواع — اتنقلت من `KindsSheet` لحد الملف 300 سطر
 * (CLAUDE.md #7). عرض فقط: **لا تحسب مبلغًا ولا تكلّم مستودعًا**.
 */

/** صف العملية الواضحة: خانة اختيار + اقتراح قاطع، بتتأكد بالجملة. */
export function KindRow({
  line,
  checked,
  onToggle,
}: {
  line: SuggestionLine
  checked: boolean
  onToggle: () => void
}) {
  const t = line.transaction
  const kind = line.suggestion.kind!
  return (
    <li className="kinds__row">
      <input
        type="checkbox"
        checked={checked}
        onChange={onToggle}
        aria-label={`تأكيد ${t.rawMerchantName ?? 'العملية'} كـ${ruleFor(kind).label}`}
      />
      <div className="kinds__main">
        <div className="kinds__name">{t.rawMerchantName || t.rawDescription || 'بلا اسم'}</div>
        <div className="kinds__meta">
          <span>{t.occurredAt}</span>
          <span className="badge">{ruleFor(kind).label}</span>
        </div>
        <div className="kinds__reason">{line.suggestion.reason}</div>
      </div>
      <span
        className="kinds__amount num"
        style={{ color: t.observedDirection === 'in' ? 'var(--c-incoming)' : 'var(--c-outgoing)' }}
      >
        {/* الإشارة جوه الخانة المعزولة زي صف العملية — مش لون وحده (spec/04) */}
        {t.observedDirection === 'in' ? '+' : '−'}
        {formatAmount(t.amountMinor)}
      </span>
    </li>
  )
}

/** صف العملية الغامضة: بيتعرض **واحدة في المرة** بزراير كبيرة (قرار المالك 2026-09-12). */
export function AmbiguousRow({
  line,
  onPick,
}: {
  line: SuggestionLine
  onPick: (id: string, kind: EconomicKind) => void
}) {
  const t = line.transaction
  const options = line.suggestion.kind
    ? [line.suggestion.kind, ...line.suggestion.alternatives]
    : line.suggestion.alternatives

  return (
    <li className="kinds__row kinds__row--pick">
      <div className="kinds__main">
        <div className="kinds__name">{t.rawMerchantName || t.rawDescription || 'بلا اسم'}</div>
        <div className="kinds__meta">
          <span>{t.occurredAt}</span>
          <span
            className="num"
            style={{
              color: t.observedDirection === 'in' ? 'var(--c-incoming)' : 'var(--c-outgoing)',
              fontWeight: 700,
            }}
          >
            {t.observedDirection === 'in' ? '+' : '−'}
            {formatAmount(t.amountMinor)}
          </span>
        </div>
        <div className="kinds__reason">{line.suggestion.reason}</div>
        <div className="kinds__options">
          {options.map((kind, index) => {
            // أول اختيار هو اقتراح التطبيق — لما يكون فيه اقتراح أصلًا
            const isSuggested = index === 0 && Boolean(line.suggestion.kind)
            return (
              <button
                key={kind}
                type="button"
                className={`kinds__option${isSuggested ? ' kinds__option--suggested' : ''}`}
                onClick={() => onPick(t.id, kind)}
              >
                {ruleFor(kind).label}
                {isSuggested && <span className="kinds__optionTag">الاقتراح</span>}
              </button>
            )
          })}
        </div>
      </div>
    </li>
  )
}
