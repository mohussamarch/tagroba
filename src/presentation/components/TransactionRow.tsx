import { formatAmount } from '../../domain/formatMoney'
import type { Transaction } from '../../domain/entities/types'

interface Props {
  transaction: Transaction
  categoryName?: string
  /** إخفاء المبالغ — إعداد الخصوصية في spec/01. */
  amountsHidden?: boolean
}

/**
 * صف عملية — spec/04: أيقونة، اسم وسطر ثانوي، مبلغ.
 *
 * المكوّن **لا يحسب أي مبلغ**؛ يعرض ما جاءه ويستدعي formatAmount فقط
 * (ARCHITECTURE.md §3، القاعدة 4).
 */
export function TransactionRow({ transaction, categoryName, amountsHidden = false }: Props) {
  const isIncoming = transaction.observedDirection === 'in'
  const name = transaction.rawMerchantName?.trim() || transaction.rawDescription?.trim() || 'بلا اسم'

  // الشعار غائب ⇒ الأحرف الأولى، لا مساحة مكسورة (spec/04)
  const initials = name.replace(/[^\p{L}\p{N}]/gu, '').slice(0, 2).toUpperCase() || '؟'

  const amountText = amountsHidden ? '••••' : formatAmount(transaction.amountMinor)
  const directionLabel = isIncoming ? 'وارد' : 'صادر'

  return (
    <li className="row">
      <div className="row__icon" aria-hidden="true">
        {initials}
      </div>

      <div className="row__main">
        <div className="row__name">{name}</div>
        <div className="row__meta">
          <span>{transaction.occurredAt}</span>
          {categoryName && <span className="badge">{categoryName}</span>}
          {transaction.isCashTagged && (
            // شارة كاش ليست وحدها وسيلة الفهم — لها تسمية كاملة لقارئ الشاشة
            <span className="badge">
              كاش<span className="visually-hidden"> — العملية دي من محفظة الكاش</span>
            </span>
          )}
          {transaction.reviewState === 'needs_review' && (
            <span className="badge badge--review">
              محتاجة مراجعة
              <span className="visually-hidden"> — مفيش قاعدة ولا تاجر مؤكد طابقها</span>
            </span>
          )}
          {transaction.reviewState === 'suggested' && (
            <span className="badge">
              مقترح<span className="visually-hidden"> — التصنيف مقترح آليًا ومحتاج تأكيدك</span>
            </span>
          )}
        </div>
      </div>

      <div className={`row__amount ${isIncoming ? 'row__amount--in' : 'row__amount--out'}`}>
        {/* السهم يميّز الاتجاه بلا اعتماد على اللون وحده — spec/04 */}
        <span aria-hidden="true">{isIncoming ? '↓ ' : '↑ '}</span>
        <span className="num">{amountText}</span>
        <span className="visually-hidden">
          {' '}
          ريال سعودي، {directionLabel}
        </span>
      </div>
    </li>
  )
}
