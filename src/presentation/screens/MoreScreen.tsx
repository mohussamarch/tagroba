import type { ReactNode } from 'react'
import { ChartColumn, ChevronLeft, Repeat, Settings, Shapes, TrendingUp, Users } from 'lucide-react'
import { MONTH_NAMES, monthLabel } from '../components/PeriodPicker'
import { relativeTenths } from '../../domain/periodBars'
import { formatMoneyOrNA, NOT_AVAILABLE } from '../../domain/formatMoney'
import type { PeriodSummary } from '../../application/useCases/loadHomeScreen'
import './MoreScreen.css'

interface Item {
  label: string
  note: string
  /** علامة مرسومة جنب الكلام — OVERRIDES §31. */
  icon: ReactNode
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
    { label: 'الأشخاص والديون', note: 'مين ليه عندك ومين عليه', icon: <Users size={20} />, onClick: onOpenPeople },
    { label: 'الاستثمار', note: 'الأصول وقيمتها', icon: <TrendingUp size={20} />, onClick: onOpenInvest },
    { label: 'الاشتراكات والفواتير', note: 'اللي بيتخصم كل شهر', icon: <Repeat size={20} />, onClick: onOpenRecurring },
    { label: 'التصنيفات وألوانها', note: 'إدارة التصنيفات', icon: <Shapes size={20} />, onClick: onOpenCategories },
    { label: 'الإعدادات', note: 'المحافظ والنسخ الاحتياطي والمظهر', icon: <Settings size={20} />, onClick: onOpenSettings },
  ]
  return (
    <div className="more">
      <ul className="more__list">
        {items.map((item) => (
          <li key={item.label}>
            {/* النص الأول في الترتيب عشان يقع على اليمين في العربي،
                والسهم يقع على الشمال — اتصلح بعد ما ظهر بالعكس على المحاكي */}
            <button type="button" className="more__item" onClick={item.onClick}>
              <span className="more__icon" aria-hidden="true">{item.icon}</span>
              <span className="more__text">
                <span className="more__label">{item.label}</span>
                <span className="more__note">{item.note}</span>
              </span>
              {/* سهم مرسوم بدل «‹» — الحروف بتتقلب في النص العربي (OVERRIDES §31) */}
              <ChevronLeft className="more__chevron" size={18} aria-hidden="true" />
            </button>
          </li>
        ))}
      </ul>

      <section className="card" aria-label="آخر ست فترات">
        <h2 className="card__title"><ChartColumn size={16} aria-hidden="true" />آخر ست فترات</h2>
        {/* أعمدة المصروف (OVERRIDES §31): الأقدم يمين والحالية شمال زي منتقي الشهر، والأرقام نفسها في القايمة تحت */}
        {periods.length > 0 && (() => {
          const ordered = [...periods].reverse()
          const heights = relativeTenths(ordered.map((p) => p.expenseMinor))
          return (
            <div className="periodBars" role="img" aria-label={`رسم المصروف في آخر ${ordered.length} فترات، والأرقام في القايمة تحته`}>
              {ordered.map((p, index) => {
                const height = heights[index]
                return (
                  <div key={p.period.key} className={`periodBars__col${p.period.key === periods[0].period.key ? ' periodBars__col--current' : ''}`}>
                    <div className="periodBars__track">
                      {height === null
                        ? <span className="periodBars__bar periodBars__bar--na" />
                        : <span className="periodBars__bar" style={{ height: `${Math.max(height / 10, 2)}%`, animationDelay: `${index * 60}ms` }} />}
                    </div>
                    <span className="periodBars__label">{MONTH_NAMES[Number(p.period.key.split('-')[1]) - 1]}</span>
                  </div>
                )
              })}
            </div>
          )
        })()}
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
