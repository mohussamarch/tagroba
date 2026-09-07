import { formatAmount } from '../../domain/formatMoney'
import { formatQuantity } from '../../domain/quantity'
import type { AssetRow } from '../../application/useCases/manageAssets'
import '../screens/InvestmentScreen.css'

/**
 * سجل الأصل — كل شراء وكل بيع بتاريخه.
 *
 * `spec/01`: «**سجل بيع جزئي**» — البيع الجزئي لازم يفضل ظاهرًا كحدث
 * مستقل، مش يتبلع في متوسط. الترتيب زمني عشان يقرا القصة بالترتيب.
 */
export function AssetHistory({ row }: { row: AssetRow }) {
  const events = [
    ...row.lots.map((lot) => ({
      key: lot.id,
      at: lot.purchasedAt,
      label: 'شراء',
      tone: 'buy' as const,
      quantity: lot.quantity,
      amountMinor: lot.principalMinor,
      feeMinor: lot.feeMinor,
    })),
    ...row.sales.map((sale) => ({
      key: sale.id,
      at: sale.soldAt,
      label: 'بيع',
      tone: 'sell' as const,
      quantity: sale.quantity,
      amountMinor: sale.grossProceedsMinor,
      feeMinor: sale.feeMinor,
    })),
  ].sort((a, b) => (a.at === b.at ? 0 : a.at < b.at ? 1 : -1))

  if (events.length === 0) {
    return (
      <p className="notice" role="status">
        مفيش حركات لسه على الأصل ده.
      </p>
    )
  }

  return (
    <ul className="investHistory">
      {events.map((event) => (
        <li key={event.key} className={`investHistory__row investHistory__row--${event.tone}`}>
          <div className="investHistory__main">
            <span className="investHistory__label">
              {event.label} {formatQuantity(event.quantity)} {row.asset.unitLabel}
            </span>
            <span className="investHistory__date">{event.at}</span>
          </div>
          <div className="investHistory__amount num">
            {formatAmount(event.amountMinor)}
            {/* الرسوم تظهر مستقلة فلا تختفي داخل المبلغ */}
            {event.feeMinor > 0 && (
              <span className="investHistory__fee">رسوم {formatAmount(event.feeMinor)}</span>
            )}
          </div>
        </li>
      ))}
    </ul>
  )
}
