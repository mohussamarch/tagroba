import { useMemo } from 'react'
import { PeriodPicker, monthLabel } from '../components/PeriodPicker'
import { TransactionRow } from '../components/TransactionRow'
import { GroupDistribution } from '../components/GroupDistribution'
import { ErrorNotice } from '../components/ErrorNotice'
import { LoadingSkeleton } from '../components/LoadingSkeleton'
import { formatAmount, NOT_AVAILABLE } from '../../domain/formatMoney'
import type { HomeScreenData } from '../../application/useCases/loadHomeScreen'
import type { Period } from '../../domain/period'
import './HomeScreen.css'

interface Props {
  data: HomeScreenData | null
  loading: boolean
  error: unknown
  payday: number
  amountsHidden: boolean
  onPeriodChange: (period: Period) => void
  onRetry: () => void
  onOpenTransactions: () => void
  onFixKinds: () => void
  /** قايمة «النقط التلاتة» لعملية — OVERRIDES §30. */
  onTransactionMenu?: (transaction: HomeScreenData['latest'][number]) => void
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
  onTransactionMenu,
}: Props) {
  const categoryById = useMemo(
    () => new Map((data?.categories ?? []).map((c) => [c.id, c])),
    [data?.categories],
  )

  if (loading && !data) {
    return <LoadingSkeleton label="بنحمّل الرئيسية…"/>
  }

  if (error && !data) return <ErrorNotice cause={error} onRetry={onRetry} />

  if (!data) return null

  const hide = (value: number | null) =>
    value === null ? NOT_AVAILABLE : amountsHidden ? '••••' : formatAmount(value)

  return (
    <div className="home">
      {Boolean(error) && <ErrorNotice cause={error} onRetry={onRetry} />}
      <PeriodPicker period={data.period} payday={payday} onChange={onPeriodChange} />

      {/* بطاقة المصروف: الرقم الأبرز، وتحته الخانات الثلاث في نفس البطاقة.
          إعادة تصميم 2026-09-11: صندوق التنبيه بفقراته الثلاث اتشال، وبقى
          سطر واحد يتضغط يودّي لمراجعة الأنواع. */}
      <section className="card home__hero" aria-label="المصروف الشخصي">
        <span className="home__heroLabel">المصروف الشخصي</span>
        <span className={`home__heroValue${data.expenseMinor === null ? ' home__heroValue--na' : ' num'}`}>
          {data.expenseMinor === null ? 'بانتظار المراجعة' : hide(data.expenseMinor)}
        </span>
        {/* الرقم الجزئي يُقال إنه جزئي، ولا يُعرض كأنه نهائي */}
        {data.partial && data.expenseMinor !== null && (
          <span className="badge badge--partial">لحد دلوقتي — الرقم لسه ناقص</span>
        )}
        {data.estimatedCount > 0 && data.expenseMinor !== null && (
          <span className="badge badge--approx">
            {data.needsReviewCount > 0 ? 'تقريبي' : 'تقريبي — الأنواع اتحددت تلقائي'}
          </span>
        )}
        <span className="home__heroSub">
          {data.transactionCount} عملية في {monthLabel(data.period)}
        </span>

        {data.needsReviewCount > 0 && (
          <button type="button" className="home__review" onClick={onFixKinds}>
            {data.needsReviewCount} عملية محتاجة تأكيد ‹
          </button>
        )}

        {/* الخانات الثلاث بالترتيب RTL، ولا تختفي أي خانة — spec/04 */}
        <div className="metrics home__metrics">
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
              {data.remainingMinor === null ? 'يحتاج مراجعة' : hide(data.remainingMinor)}
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
      </section>

      {/* سبب نقص التغطية سطر واحد هادي، مش صندوق — لا رقم بلا بيان نقصه */}
      {data.coverage.note && <p className="home__hint">{data.coverage.note}</p>}

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
          {/* الرقم المبني على المتبقي بدل السقف يُقال إنه تقريبي — OVERRIDES §19 */}
          {data.allowance.approximate && <span className="badge badge--approx">تقريبي</span>}
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

      {/* المصروف بالمجموعات «من برا» ودوسة بتفتح التصنيفات — OVERRIDES §28.1 (مع بديل قائمة للرسم، spec/04) */}
      <GroupDistribution distribution={data.distribution} categories={data.categories} amountsHidden={amountsHidden} />

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
              if (onTransactionMenu) props.onMenu = () => onTransactionMenu(t)
              const category = t.categoryId ? categoryById.get(t.categoryId) : undefined
              if (category?.name) props.categoryName = category.name
              if (category?.lightColor) props.categoryColor = category.lightColor
              if (category?.darkColor) props.categoryDarkColor = category.darkColor
              if (category?.iconKey) props.categoryIconKey = category.iconKey
              return <TransactionRow key={t.id} {...props} />
            })}
          </ul>
        )}
      </section>

      {/* «آخر ست فترات» خرجت من الرئيسية لشاشة «المزيد» — قرار المالك 2026-09-11 */}
    </div>
  )
}
