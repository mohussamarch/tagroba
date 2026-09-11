import { monthLabel } from '../components/PeriodPicker'
import { formatMoneyOrNA, NOT_AVAILABLE } from '../../domain/formatMoney'
import type { PeriodSummary } from '../../application/useCases/loadHomeScreen'
import './MoreScreen.css'

interface Item {
  label: string
  note: string
  onClick: () => void
}

/**
 * «المزيد» — الأقسام اللي خرجت من الشريط السفلي ومن الرئيسية.
 *
 * قرار المالك 2026-09-11 (OVERRIDES §20): الشريط بخمس خانات، والأشخاص
 * والاستثمار والإعدادات والاشتراكات والتصنيفات و«آخر ست فترات» هنا.
 * الشاشة **لا تحسب مبلغًا** — بتعرض أرقامًا جاهزة وبتفتح شاشات.
 */
export function MoreScreen({
  periods,
  periodsLoading,
  periodsError,
  amountsHidden,
  onOpenPeople,
  onOpenInvest,
  onOpenSettings,
  onOpenRecurring,
  onOpenCategories,
  onRetry,
}: {
  periods: PeriodSummary[]
  periodsLoading: boolean
  periodsError: unknown
  amountsHidden: boolean
  onOpenPeople: () => void
  onOpenInvest: () => void
  onOpenSettings: () => void
  onOpenRecurring: () => void
  onOpenCategories: () => void
  onRetry: () => void
}) {
  const items: Item[] = [
    { label: 'الأشخاص والديون', note: 'مين ليه عندك ومين عليه', onClick: onOpenPeople },
    { label: 'الاستثمار', note: 'الأصول وقيمتها', onClick: onOpenInvest },
    { label: 'الاشتراكات والفواتير', note: 'اللي بيتخصم كل شهر', onClick: onOpenRecurring },
    { label: 'التصنيفات وألوانها', note: 'إدارة التصنيفات', onClick: onOpenCategories },
    { label: 'الإعدادات', note: 'المحافظ والنسخ الاحتياطي والمظهر', onClick: onOpenSettings },
  ]
  return (
    <div className="more">
      <ul className="more__list">
        {items.map((item) => (
          <li key={item.label}>
            <button type="button" className="more__item" onClick={item.onClick}>
              <span className="more__chevron" aria-hidden="true">
                ‹
              </span>
              <span className="more__text">
                <span className="more__label">{item.label}</span>
                <span className="more__note">{item.note}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>

      <section className="card" aria-label="آخر ست فترات">
        <h2 className="card__title">آخر ست فترات</h2>
        {periodsLoading && periods.length === 0 && <p role="status">بنحمّل تاريخ الفترات…</p>}
        {Boolean(periodsError) && (
          <p role="alert">
            تعذر تحميل الفترات السابقة. بيانات الفترة الحالية متاحة.
            <button className="btn" onClick={onRetry}>
              إعادة المحاولة
            </button>
          </p>
        )}
        <ul className="periods">
          {periods.map((p) => (
            <li key={p.period.key} className="periods__row">
              <span className="periods__name">{monthLabel(p.period)}</span>
              <span className="periods__count">{p.transactionCount} عملية</span>
              <span className={`periods__value${p.expenseMinor === null ? '' : ' num'}`}>
                {p.expenseMinor === null
                  ? NOT_AVAILABLE
                  : amountsHidden
                    ? '••••'
                    : formatMoneyOrNA(p.expenseMinor, 'SAR', { showCurrency: false })}
              </span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
