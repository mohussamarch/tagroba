import { useMemo } from 'react'
import { PeriodPicker, monthLabel } from '../components/PeriodPicker'
import { TransactionRow } from '../components/TransactionRow'
import { formatAmount, formatMoneyOrNA, NOT_AVAILABLE } from '../../domain/formatMoney'
import type { HomeScreenData } from '../../application/useCases/loadHomeScreen'
import type { Period } from '../../domain/period'
import './HomeScreen.css'

interface Props {
  data: HomeScreenData | null
  loading: boolean
  error: string | null
  payday: number
  amountsHidden: boolean
  onPeriodChange: (period: Period) => void
  onRetry: () => void
  onOpenTransactions: () => void
  onFixKinds: () => void
}

/**
 * الشاشة الرئيسية — spec/01.
 *
 * لا تحسب مبلغًا ولا تكلّم مستودعًا: تستقبل `HomeScreenData` جاهزًا
 * وتعرضه. كل `null` تُعرض «غير متاح» بسببها المكتوب — CLAUDE.md #10.
 */
export function HomeScreen({
  data,
  loading,
  error,
  payday,
  amountsHidden,
  onPeriodChange,
  onRetry,
  onOpenTransactions,
  onFixKinds,
}: Props) {
  const categoryById = useMemo(
    () => new Map((data?.categories ?? []).map((c) => [c.id, c])),
    [data?.categories],
  )

  if (loading && !data) {
    return (
      <p className="notice" role="status">
        بنحمّل الرئيسية…
      </p>
    )
  }

  if (error) {
    return (
      <div className="notice" role="alert">
        <strong>مقدرناش نحمّل الرئيسية.</strong>
        <br />
        {error}
        <br />
        <button type="button" className="btn btn--quiet" onClick={onRetry} style={{ marginTop: 10 }}>
          جرّب تاني
        </button>
      </div>
    )
  }

  if (!data) return null

  const hide = (value: number | null) =>
    value === null ? NOT_AVAILABLE : amountsHidden ? '••••' : formatAmount(value)

  return (
    <div className="home">
      <PeriodPicker period={data.period} payday={payday} onChange={onPeriodChange} />

      {/* المصروف الشخصي — الرقم الأبرز (spec/01) */}
      <section className="card home__hero" aria-label="المصروف الشخصي">
        <span className="home__heroLabel">المصروف الشخصي</span>
        <span className={`home__heroValue${data.expenseMinor === null ? ' home__heroValue--na' : ' num'}`}>
          {hide(data.expenseMinor)}
        </span>
        {/* الرقم الجزئي يُقال إنه جزئي، ولا يُعرض كأنه نهائي */}
        {data.partial && data.expenseMinor !== null && (
          <span className="badge badge--partial">لحد دلوقتي — الرقم لسه ناقص</span>
        )}
        <span className="home__heroSub">
          {data.transactionCount} عملية في {monthLabel(data.period)}
        </span>
      </section>

      {/* تنبيه التغطية: لا رقم بلا بيان نقصه */}
      {data.coverage.note && (
        <div className="notice notice--action" role="status">
          <div>{data.coverage.note}</div>
          <button type="button" className="btn" onClick={onFixKinds}>
            حدّد الأنواع
          </button>
        </div>
      )}

      {/* الخانات الثلاث بالترتيب RTL، ولا تختفي أي خانة — spec/04 */}
      <div className="metrics">
        <div className="metric">
          <span className="metric__label">
            الدخل{data.partial && data.incomeMinor !== null ? ' (ناقص)' : ''}
          </span>
          <span
            className={`metric__value${data.incomeMinor === null ? '' : ' num metric__value--in'}`}
          >
            {hide(data.incomeMinor)}
          </span>
        </div>
        <div className="metric">
          <span className="metric__label">المتبقي</span>
          <span
            className={`metric__value${
              data.remainingMinor === null
                ? ''
                : ` num${data.remainingMinor < 0 ? ' metric__value--out' : ''}`
            }`}
          >
            {hide(data.remainingMinor)}
          </span>
        </div>
        <div className="metric">
          <span className="metric__label">معدل الادخار</span>
          <span className="metric__value">
            {data.savingsRatePercent === null
              ? NOT_AVAILABLE
              : `${data.savingsRatePercent.toFixed(1)}%`}
          </span>
        </div>
      </div>

      {data.excludedExpenseMinor !== null && data.excludedExpenseMinor > 0 && (
        <p className="home__hint">
          ومعاها {formatAmount(data.excludedExpenseMinor)} مستبعدة من الميزانية — بتتخصم من
          محفظتك بس مش داخلة في المصروف.
        </p>
      )}

      {/* المتاح اليومي والتوقع — كل واحد بسببه لو غير متاح */}
      <div className="home__pair">
        <section className="card home__box" aria-label="المتاح اليومي">
          <span className="home__boxLabel">المتاح اليومي</span>
          <span className={`home__boxValue${data.allowance.amountMinor === null ? '' : ' num'}`}>
            {data.allowance.amountMinor === null
              ? NOT_AVAILABLE
              : formatAmount(data.allowance.amountMinor)}
          </span>
          <span className="home__boxNote">{data.allowance.reason}</span>
        </section>

        <section className="card home__box" aria-label="توقع نهاية الفترة">
          <span className="home__boxLabel">توقع نهاية الفترة</span>
          <span className={`home__boxValue${data.forecast.projectedMinor === null ? '' : ' num'}`}>
            {data.forecast.projectedMinor === null
              ? NOT_AVAILABLE
              : formatAmount(data.forecast.projectedMinor)}
          </span>
          <span className="home__boxNote">{data.forecast.caveat}</span>
        </section>
      </div>

      {/* توزيع التصنيفات — مع بديل قائمة للرسم (spec/04، الوصول) */}
      {data.distribution.length > 0 && (
        <section className="card" aria-label="توزيع التصنيفات">
          <h2 className="card__title">التصنيفات</h2>
          <ul className="dist">
            {data.distribution.slice(0, 8).map((slice) => {
              const category = slice.categoryId ? categoryById.get(slice.categoryId) : undefined
              const name = category?.name ?? 'بلا تصنيف'
              const percent = (slice.shareTenthPercent / 10).toFixed(1)
              return (
                <li key={slice.categoryId ?? '—'} className="dist__row">
                  <div className="dist__head">
                    <span className="dist__name">{name}</span>
                    <span className="dist__amount num">
                      {amountsHidden ? '••••' : formatAmount(slice.amountMinor)}
                    </span>
                  </div>
                  <div
                    className="dist__bar"
                    role="img"
                    aria-label={`${name}: ${percent} بالمئة، ${slice.count} عملية`}
                  >
                    <div
                      className="dist__fill"
                      style={{
                        width: `${percent}%`,
                        background: category?.lightColor ?? 'var(--c-muted)',
                      }}
                    />
                  </div>
                  <span className="dist__meta">
                    {percent}% · {slice.count} عملية
                  </span>
                </li>
              )
            })}
          </ul>
        </section>
      )}

      {/* أحدث العمليات */}
      <section className="card" aria-label="أحدث العمليات">
        <div className="card__head">
          <h2 className="card__title">أحدث العمليات</h2>
          <button type="button" className="link" onClick={onOpenTransactions}>
            كلها
          </button>
        </div>
        {data.latest.length === 0 ? (
          <p className="home__hint">مفيش عمليات في الفترة دي.</p>
        ) : (
          <ul className="list">
            {data.latest.map((t) => {
              const props: Parameters<typeof TransactionRow>[0] = { transaction: t, amountsHidden }
              const name = t.categoryId ? categoryById.get(t.categoryId)?.name : undefined
              if (name) props.categoryName = name
              return <TransactionRow key={t.id} {...props} />
            })}
          </ul>
        )}
      </section>

      {/* آخر ست فترات */}
      <section className="card" aria-label="آخر ست فترات">
        <h2 className="card__title">آخر ست فترات</h2>
        <ul className="periods">
          {data.recentPeriods.map((p) => (
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
