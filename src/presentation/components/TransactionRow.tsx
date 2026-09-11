import { formatAmount } from '../../domain/formatMoney'
import type { Transaction } from '../../domain/entities/types'
import './TransactionRow.css'

interface Props {
  transaction: Transaction
  categoryName?: string
  /** لون التصنيف من `Category.lightColor` — الدايرة بتتلون بيه بشفافية. */
  categoryColor?: string
  /** إخفاء المبالغ — إعداد الخصوصية في spec/01. */
  amountsHidden?: boolean
  /** يُمرَّر حين يكون الصف قابلًا للفتح. */
  onOpen?: () => void
}

/**
 * صف عملية — spec/04: أيقونة، اسم وسطر ثانوي، مبلغ.
 *
 * إعادة تصميم 2026-09-11 (معتمدة من المالك في Figma):
 * الدايرة بلون التصنيف وأول حرف من اسمه بالعربي بدل حروف لاتينية،
 * السطر التاني نص هادي واحد بدل شارات متناثرة، والمبلغ بإشارة − أو +
 * بدل سهم. **الإشارة واللون مش لوحدهم**: فيه تسمية كاملة لقارئ الشاشة.
 *
 * المكوّن **لا يحسب أي مبلغ**؛ يعرض ما جاءه ويستدعي formatAmount فقط
 * (ARCHITECTURE.md §3، القاعدة 4).
 */
export function TransactionRow({
  transaction,
  categoryName,
  categoryColor,
  amountsHidden = false,
  onOpen,
}: Props) {
  const isIncoming = transaction.observedDirection === 'in'
  const name = transaction.rawMerchantName?.trim() || transaction.rawDescription?.trim() || 'بلا اسم'

  // حرف التصنيف أولًا، وإلا أول حرف من الاسم — لا مساحة مكسورة (spec/04)
  const glyph =
    categoryName?.trim().charAt(0) ||
    name.replace(/[^\p{L}\p{N}]/gu, '').charAt(0).toUpperCase() ||
    '؟'

  const amountText = amountsHidden ? '••••' : formatAmount(transaction.amountMinor)
  const directionLabel = isIncoming ? 'وارد' : 'صادر'
  const needsReview = transaction.reviewState === 'needs_review'

  // السطر التاني: التصنيف · كاش · التاريخ — الموجود بس، بلا فراغات
  const meta = [
    categoryName,
    transaction.isCashTagged ? 'كاش' : null,
    transaction.occurredAt,
  ].filter(Boolean) as string[]

  return (
    <li className={`row${onOpen ? ' row--clickable' : ''}`}>
      {/* زر يغطي الصف: الفتح بالنقر وبلوحة المفاتيح معًا */}
      {onOpen && (
        <button
          type="button"
          className="row__hit"
          onClick={onOpen}
          aria-label={`افتح تفاصيل ${name}`}
        />
      )}
      <div
        className="row__icon"
        aria-hidden="true"
        style={
          categoryColor
            ? { background: `color-mix(in srgb, ${categoryColor} 16%, transparent)`, color: categoryColor }
            : undefined
        }
      >
        {glyph}
      </div>

      <div className="row__main">
        <div className="row__name">
          {needsReview && (
            <span className="row__dot" aria-hidden="true" />
          )}
          {name}
          {needsReview && (
            <span className="visually-hidden"> — نوعها لسه محتاج تأكيد منك</span>
          )}
        </div>
        <div className="row__meta">
          <span>{meta.join(' · ')}</span>
          {transaction.reviewState === 'suggested' && (
            <span className="visually-hidden">التصنيف مقترح آليًا ومحتاج تأكيدك</span>
          )}
        </div>
      </div>

      <div className={`row__amount ${isIncoming ? 'row__amount--in' : 'row__amount--out'}`}>
        {/* الإشارة **جوه** خانة الرقم المعزولة عشان تفضل على شمال الرقم،
            لأنها لما كانت بره كانت بتتقلب لآخر السطر في RTL (اتشاف على
            المحاكي 2026-09-11). الإشارة تميّز الاتجاه بلا لون — spec/04 */}
        <span className="num">
          {amountsHidden ? amountText : `${isIncoming ? '+' : '−'}${amountText}`}
        </span>
        <span className="visually-hidden">
          {' '}
          ريال سعودي، {directionLabel}
        </span>
      </div>
    </li>
  )
}
