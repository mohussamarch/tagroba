import { useEffect, useMemo, useState } from 'react'
import { TransactionRow } from '../components/TransactionRow'
import { PeriodPicker } from '../components/PeriodPicker'
import { formatAmount, NOT_AVAILABLE } from '../../domain/formatMoney'
import { parseQuery, searchTransactions, AMOUNT_TOLERANCE_PER_THOUSAND } from '../../domain/search'
import type { SearchableTransaction } from '../../domain/search'
import type { TransactionsScreenData } from '../../application/useCases/loadTransactionsScreen'
import type { Period } from '../../domain/period'
import './TransactionsScreen.css'

interface Props {
  loading: boolean
  error: string | null
  data: TransactionsScreenData | null
  amountsHidden: boolean
  period: Period
  payday: number
  onPeriodChange: (period: Period) => void
  onImport: () => void
  onFixKinds: () => void
  onRetry: () => void
}

/**
 * شاشة العمليات — spec/01 و spec/04.
 *
 * الشاشة **لا تحسب مبلغًا ولا تكلّم مستودعًا**. تستقبل بيانات محسوبة
 * من حالة الاستخدام، وتنادي formatAmount للعرض فقط.
 * البحث محلي بالكامل في domain/search — لا تُرسل بيانات البحث للخارج.
 */
export function TransactionsScreen({
  loading,
  error,
  data,
  amountsHidden,
  period,
  payday,
  onPeriodChange,
  onImport,
  onFixKinds,
  onRetry,
}: Props) {
  const [rawQuery, setRawQuery] = useState('')
  const [debounced, setDebounced] = useState('')

  // بحث مؤجل قصير — spec/04
  useEffect(() => {
    const id = setTimeout(() => setDebounced(rawQuery), 200)
    return () => clearTimeout(id)
  }, [rawQuery])

  const categoryNameById = useMemo(
    () => new Map((data?.categories ?? []).map((c) => [c.id, c.name])),
    [data?.categories],
  )

  const searchable: SearchableTransaction[] = useMemo(
    () =>
      (data?.transactions ?? []).map((transaction) => {
        const item: SearchableTransaction = { transaction }
        const name = transaction.categoryId
          ? categoryNameById.get(transaction.categoryId)
          : undefined
        if (name) item.categoryName = name
        return item
      }),
    [data?.transactions, categoryNameById],
  )

  const visible = useMemo(() => {
    if (!debounced.trim()) return searchable.map((s) => s.transaction)
    return searchTransactions(searchable, parseQuery(debounced)).map((h) => h.transaction)
  }, [searchable, debounced])

  const query = useMemo(() => parseQuery(debounced), [debounced])

  return (
    <div className="txns">
      <PeriodPicker period={period} payday={payday} onChange={onPeriodChange} />

      <div className="search">
        <input
          className="search__input"
          type="search"
          value={rawQuery}
          onChange={(e) => setRawQuery(e.target.value)}
          placeholder="ابحث باسم أو مبلغ أو تصنيف…"
          aria-label="البحث في العمليات"
          aria-describedby="search-hint"
        />
        <p className="search__hint" id="search-hint">
          {query.amount
            ? `بحث بالمبلغ ${formatAmount(query.amount.targetMinor)} ± ${AMOUNT_TOLERANCE_PER_THOUSAND / 10}٪ — ${visible.length} نتيجة`
            : 'الأرقام تبحث بالمبلغ ±٥٪. البحث محلي على جهازك ومش بيتبعت لأي مكان.'}
        </p>
      </div>

      {data && (
        <>
          {/* الخانات الثلاث بالترتيب RTL، ولا تختفي أي خانة — spec/04 */}
          <div className="metrics">
            <Metric label="الدخل" amount={data.incomeMinor} hidden={amountsHidden} tone="in" />
            <Metric
              label="المتبقي"
              amount={data.remainingMinor}
              hidden={amountsHidden}
              tone={data.remainingMinor !== null && data.remainingMinor < 0 ? 'out' : 'none'}
            />
            <div className="metric">
              <span className="metric__label">معدل الادخار</span>
              <span className="metric__value">
                {data.savingsRatePercent === null
                  ? NOT_AVAILABLE
                  : `${data.savingsRatePercent.toFixed(1)}%`}
              </span>
            </div>
          </div>

          {/* لا رقم بلا مصدر (CLAUDE.md #10) */}
          {data.unclassifiedCount > 0 && (
            <div className="notice notice--action" role="status">
              <div>
                {data.unclassifiedCount === data.totalCount ? (
                  <>
                    <strong>الأرقام لسه غير متاحة.</strong> كل الـ{data.totalCount} عملية محتاجة
                    تحدد نوعها (دخل؟ تحويل؟ قرض؟).
                  </>
                ) : (
                  <>
                    <strong>الأرقام دي ناقصة.</strong> فيه {data.unclassifiedCount} عملية من{' '}
                    {data.totalCount} لسه محتاجة تحدد نوعها.
                  </>
                )}
              </div>
              <button type="button" className="btn" onClick={onFixKinds}>
                حدّد الأنواع
              </button>
            </div>
          )}
        </>
      )}

      {loading && !data && (
        <p className="notice" role="status">
          بنحمّل العمليات…
        </p>
      )}

      {error && (
        <div className="notice" role="alert">
          <strong>مقدرناش نحمّل العمليات.</strong>
          <br />
          {error}
          <br />
          <button
            type="button"
            className="btn btn--quiet"
            onClick={onRetry}
            style={{ marginTop: 10 }}
          >
            جرّب تاني
          </button>
        </div>
      )}

      {!loading && !error && data && visible.length === 0 && (
        <div className="empty">
          {debounced.trim() ? (
            <>
              <span className="empty__title">مفيش نتايج للبحث ده</span>
              <span>جرّب كلمة تانية، أو امسح البحث عشان تشوف كل العمليات.</span>
            </>
          ) : (
            <>
              <span className="empty__title">مفيش عمليات في الفترة دي</span>
              <span>غيّر الفترة من فوق، أو استورد كشف حساب.</span>
              <button type="button" className="btn" onClick={onImport}>
                استيراد كشف
              </button>
            </>
          )}
        </div>
      )}

      {!error && visible.length > 0 && (
        <ul className="list">
          {visible.map((t) => {
            const props: Parameters<typeof TransactionRow>[0] = {
              transaction: t,
              amountsHidden,
            }
            const name = t.categoryId ? categoryNameById.get(t.categoryId) : undefined
            if (name) props.categoryName = name
            return <TransactionRow key={t.id} {...props} />
          })}
        </ul>
      )}
    </div>
  )
}

/**
 * خانة ملخص واحدة.
 * `amount === null` تعني «غير متاح» صراحةً، لا صفرًا — CLAUDE.md #10.
 */
function Metric({
  label,
  amount,
  hidden,
  tone,
}: {
  label: string
  amount: number | null
  hidden: boolean
  tone: 'in' | 'out' | 'none'
}) {
  const toneClass =
    amount === null || tone === 'none'
      ? ''
      : tone === 'in'
        ? ' metric__value--in'
        : ' metric__value--out'
  return (
    <div className="metric">
      <span className="metric__label">{label}</span>
      <span className={`metric__value${amount === null ? '' : ' num'}${toneClass}`}>
        {amount === null ? NOT_AVAILABLE : hidden ? '••••' : formatAmount(amount)}
      </span>
    </div>
  )
}
