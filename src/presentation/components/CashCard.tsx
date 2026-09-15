import { Banknote, ChevronLeft, X } from 'lucide-react'
import { formatAmount } from '../../domain/formatMoney'
import type { CashSummary } from '../../domain/cashSummary'
import { TransactionRow } from './TransactionRow'
import './CashCard.css'

/**
 * كارت الكاش في الرئيسية + تفاصيله — OVERRIDES §32 (رد المالك «الاتنين»).
 * المكوّن ما بيحسبش فلوس: الأرقام جاية جاهزة من `summarizeCash` (domain).
 */
export function CashCard({ cash, amountsHidden, onOpen }: {
  cash: CashSummary | null
  amountsHidden: boolean
  onOpen: () => void
}) {
  if (!cash) return null
  const money = (minor: number) => (amountsHidden ? '••••' : formatAmount(minor))
  return (
    <button type="button" className="card cashCard" onClick={onOpen} aria-label="تفاصيل الكاش">
      <span className="cashCard__icon" aria-hidden="true"><Banknote size={22} /></span>
      <span className="cashCard__text">
        <span className="cashCard__label">الكاش اللي معاك تقريبًا</span>
        <span className={`cashCard__value num${cash.balanceMinor < 0 ? ' cashCard__value--out' : ''}`}>{money(cash.balanceMinor)}</span>
        <span className="cashCard__sub">صرفت منه {money(cash.spentInPeriodMinor)} في الفترة دي</span>
      </span>
      <ChevronLeft className="cashCard__chevron" size={18} aria-hidden="true" />
    </button>
  )
}

export function CashSheet({ cash, amountsHidden, onClose }: {
  cash: CashSummary
  amountsHidden: boolean
  onClose: () => void
}) {
  const money = (minor: number) => (amountsHidden ? '••••' : formatAmount(minor))
  const facts: [string, number][] = [
    [`رصيد البداية (${cash.wallet.openingAt})`, cash.wallet.openingBalanceMinor],
    ['دخل الكاش من يومها', cash.inSinceOpeningMinor],
    ['خرج من الكاش من يومها', cash.outSinceOpeningMinor],
  ]
  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="الكاش">
      <div className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">الكاش</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق"><X size={18} aria-hidden="true" /></button>
        </header>
        <div className="sheet__body">
          <section className="cashSheet__hero">
            <span className="cashCard__label">معاك تقريبًا</span>
            <span className={`cashSheet__value num${cash.balanceMinor < 0 ? ' cashCard__value--out' : ''}`}>{money(cash.balanceMinor)}</span>
            <span className="sheet__hint">تقريبي: من رصيد البداية والعمليات المتسجلة على الكاش. أي كاش اتصرف ومااتسجلش مش ظاهر. رصيد البداية بيتعدل من الإعدادات.</span>
          </section>
          <dl className="cashSheet__facts">
            {facts.map(([label, value]) => (
              <div key={label} className="cashSheet__fact"><dt>{label}</dt><dd className="num">{money(value)}</dd></div>
            ))}
            <div className="cashSheet__fact cashSheet__fact--spent"><dt>صرفت كاش في الفترة دي</dt><dd className="num">{money(cash.spentInPeriodMinor)}</dd></div>
          </dl>
          <h3 className="cashSheet__listTitle">عمليات الكاش في الفترة ({cash.periodTransactions.length})</h3>
          {cash.periodTransactions.length === 0
            ? <p className="sheet__hint">مفيش عمليات كاش في الفترة دي.</p>
            : <ul className="list">{cash.periodTransactions.map((t) => <TransactionRow key={t.id} transaction={t} amountsHidden={amountsHidden} />)}</ul>}
        </div>
      </div>
    </div>
  )
}
