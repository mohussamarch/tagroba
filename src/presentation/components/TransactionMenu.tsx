import { X } from 'lucide-react'
import { formatAmount } from '../../domain/formatMoney'
import type { Transaction } from '../../domain/entities/types'
import './TransactionMenu.css'

export type TransactionMenuAction = 'category' | 'note' | 'flags' | 'person' | 'settle' | 'project' | 'details'

const ITEMS: { action: TransactionMenuAction; label: string; hint: string }[] = [
  { action: 'category', label: 'غيّر التصنيف', hint: 'اختار تصنيف أو فرعي — اختيارك بيتحسب تأكيد' },
  { action: 'note', label: 'ضيف ملاحظة', hint: 'مثلًا: قهوة مع أحمد' },
  { action: 'person', label: 'على شخص كدين أو هدية', hint: 'دفعت عنه أو سلّفته، أو استلمت منه' },
  { action: 'settle', label: 'اربطها بدين موجود', hint: 'الفلوس دي سداد أو تحصيل لدين متسجل قبل كده' },
  { action: 'project', label: 'ضيف لمشروع', hint: 'اجمعها مع عمليات تانية لهدف واحد — المبلغ ما يتحسبش مرتين' },
  { action: 'flags', label: 'كاش واستبعاد ووسوم', hint: 'علامات للتقارير — المبلغ ما بيتغيرش' },
  { action: 'details', label: 'كل التفاصيل', hint: 'كل إعدادات العملية في صفحة واحدة' },
]

/**
 * قايمة «النقط التلاتة» لكل عملية — OVERRIDES §30 (نص المالك: «يدوس عليها تظهرله قايمة بإعدادات خاصة لكل عملية،
 * سواء هغيّر تصنيفها أو هضيف ملاحظات ليها أو هربطها بفلوس جت قبل كده أو هضيفها على شخص معين كدين… إلخ»).
 * القايمة بتوجّه بس: كل اختيار بيفتح الورقة الموجودة المسؤولة عنه، مفيش منطق ولا كتابة هنا.
 */
export function TransactionMenu({ transaction, amountsHidden, onChoose, onClose }: {
  transaction: Transaction
  amountsHidden: boolean
  onChoose: (action: TransactionMenuAction) => void
  onClose: () => void
}) {
  const name = transaction.rawMerchantName?.trim() || transaction.rawDescription?.trim() || 'بلا اسم'
  const isIncoming = transaction.observedDirection === 'in'
  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label={`خيارات ${name}`} onClick={onClose}>
      <div className="sheet__panel txnMenu" onClick={(e) => e.stopPropagation()}>
        <header className="sheet__head">
          <div className="txnMenu__title">
            <h2 className="sheet__title">{name}</h2>
            <span className="txnMenu__meta">
              {transaction.occurredAt} · {isIncoming ? 'وارد' : 'صادر'} ·{' '}
              <span className="num">{amountsHidden ? '••••' : formatAmount(transaction.amountMinor, transaction.currency)}</span>
            </span>
          </div>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <X size={18} aria-hidden="true" />
          </button>
        </header>
        <ul className="txnMenu__list">
          {ITEMS.map((item) => (
            <li key={item.action}>
              <button type="button" className="txnMenu__item" onClick={() => onChoose(item.action)}>
                <span className="txnMenu__label">{item.label}</span>
                <span className="txnMenu__hint">{item.hint}</span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
