import { useState, type CSSProperties } from 'react'
import { CircleDashed, Tag } from 'lucide-react'
import { formatAmount } from '../../domain/formatMoney'
import type { Transaction } from '../../domain/entities/types'
import { CategoryIcon } from './CategoryIcon'
import './TransactionRow.css'

interface Props {
  transaction: Transaction
  categoryName?: string
  /** لون التصنيف من `Category.lightColor` — الصف كله والدايرة بيتلونوا بيه بشفافية. */
  categoryColor?: string
  /** نسخة اللون المقروءة في الوضع الغامق (`Category.darkColor`). */
  categoryDarkColor?: string
  /** رمز التصنيف (OVERRIDES §28). */
  categoryIconKey?: string
  /** شعار المحل (OVERRIDES §25.1) — لو فشل تحميله بيرجع رمز التصنيف. */
  logoUrl?: string
  /** إخفاء المبالغ — إعداد الخصوصية في spec/01. */
  amountsHidden?: boolean
  /** يُمرَّر حين يكون الصف قابلًا للفتح. */
  onOpen?: () => void
  /** قايمة «النقط التلاتة» لإعدادات العملية — OVERRIDES §30. */
  onMenu?: () => void
}

/**
 * صف عملية — spec/04: أيقونة، اسم وسطر ثانوي، مبلغ.
 *
 * إعادة تصميم 2026-09-11 (معتمدة من المالك في Figma): السطر التاني نص هادي واحد،
 * والمبلغ بإشارة − أو + بدل سهم. **الإشارة واللون مش لوحدهم**: فيه تسمية كاملة لقارئ الشاشة.
 * 2026-09-15 (OVERRIDES §31): **الصف كله من عيلة لون التصنيف**، ومفيش حروف جوه الدايرة —
 * شعار المحل، أو رمز التصنيف، أو علامة وسم عامة، أو علامة «بلا تصنيف».
 *
 * المكوّن **لا يحسب أي مبلغ**؛ يعرض ما جاءه ويستدعي formatAmount فقط
 * (ARCHITECTURE.md §3، القاعدة 4).
 */
export function TransactionRow({
  transaction,
  categoryName,
  categoryColor,
  categoryDarkColor,
  categoryIconKey,
  logoUrl,
  amountsHidden = false,
  onOpen,
  onMenu,
}: Props) {
  const [failedLogo, setFailedLogo] = useState<string | null>(null)
  const showLogo = Boolean(logoUrl && failedLogo !== logoUrl)
  const isIncoming = transaction.observedDirection === 'in'
  const name = transaction.rawMerchantName?.trim() || transaction.rawDescription?.trim() || 'بلا اسم'
  const showIcon = Boolean(categoryIconKey && categoryIconKey !== 'tag')

  const amountText = amountsHidden ? '••••' : formatAmount(transaction.amountMinor)
  const directionLabel = isIncoming ? 'وارد' : 'صادر'
  const needsReview = transaction.reviewState === 'needs_review'

  // السطر التاني: التصنيف · كاش · التاريخ — الموجود بس، بلا فراغات
  const meta = [
    categoryName,
    transaction.isCashTagged ? 'كاش' : null,
    transaction.occurredAt,
  ].filter(Boolean) as string[]

  const catStyle = categoryColor
    ? ({ '--cat-light': categoryColor, '--cat-dark': categoryDarkColor ?? categoryColor } as CSSProperties)
    : undefined

  return (
    <li
      className={`row${categoryColor ? ' row--cat' : ''}${onOpen ? ' row--clickable' : ''}${onMenu ? ' row--menu' : ''}`}
      style={catStyle}
    >
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
        className={`row__icon${showLogo ? ' row__icon--logo' : categoryColor ? ' row__icon--cat' : ''}`}
        aria-hidden="true"
      >
        {showLogo ? (
          <img src={logoUrl} alt="" loading="lazy" decoding="async" referrerPolicy="no-referrer" onError={() => setFailedLogo(logoUrl!)} />
        ) : showIcon ? (
          <CategoryIcon iconKey={categoryIconKey!} size={20} />
        ) : categoryName ? (
          <Tag size={18} />
        ) : (
          <CircleDashed size={18} />
        )}
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

      {/* «النقط التلاتة» على شمال العملية — OVERRIDES §30. فوق زر فتح الصف عشان الدوسة ما تفتحش التفاصيل */}
      {onMenu && (
        <button type="button" className="row__menu" onClick={onMenu} aria-label={`خيارات ${name}`}>
          <span aria-hidden="true">⋯</span>
        </button>
      )}
    </li>
  )
}
