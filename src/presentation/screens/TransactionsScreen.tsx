import { useEffect, useMemo, useState } from 'react'
import { TransactionRow } from '../components/TransactionRow'
import { PeriodPicker } from '../components/PeriodPicker'
import { ErrorNotice } from '../components/ErrorNotice'
import { formatAmount, NOT_AVAILABLE } from '../../domain/formatMoney'
import { parseQuery, searchTransactions, AMOUNT_TOLERANCE_PER_THOUSAND } from '../../domain/search'
import type { SearchableTransaction } from '../../domain/search'
import type { TransactionsScreenData } from '../../application/useCases/loadTransactionsScreen'
import type { Period } from '../../domain/period'
import './TransactionsScreen.css'

interface Props {
  loading: boolean
  error: unknown
  data: TransactionsScreenData | null
  amountsHidden: boolean
  period: Period
  payday: number
  onPeriodChange: (period: Period) => void
  onImport: () => void
  onFixKinds: () => void
  onOpenHistory: () => void
  onOpenTransaction: (transaction: TransactionsScreenData["transactions"][number]) => void
  onRetry: () => void
}

/**
 * شاشة العمليات — spec/01 و spec/04.
 *
 * الشاشة **لا تحسب مبلغًا ولا تكلّم مستودعًا**. تستقبل بيانات محسوبة
 * من حالة الاستخدام، وتنادي formatAmount للعرض فقط.
 * البحث محلي بالكامل في domain/search — لا تُرسل بيانات البحث للخارج.
 *
 * ترتيب مضغوط (HANDOVER §27): القائمة كانت تحت 9 عناصر فلا تظهر عملية بلا تمرير.
 * الفلترة مطوية، والخانات الثلاث شريط واحد (لا تختفي — spec/04)، والتنبيه سطر واحد.
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
  onOpenHistory,
  onOpenTransaction,
  onRetry,
}: Props) {
  const [rawQuery, setRawQuery] = useState('')
  const [tagFilter,setTagFilter] = useState('')
  const [filtersOpen, setFiltersOpen] = useState(false)
  const availableTags=[...new Set(Object.values(data?.tagNamesByTransaction??{}).flat())].sort()
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
        const item: SearchableTransaction = { transaction, tagNames:data?.tagNamesByTransaction?.[transaction.id]??[],merchantNames:data?.merchantNamesByTransaction?.[transaction.id]??[] }
        const name = transaction.categoryId
          ? categoryNameById.get(transaction.categoryId)
          : undefined
        if (name) item.categoryName = name
        return item
      }),
    [data?.transactions, data?.tagNamesByTransaction, data?.merchantNamesByTransaction, categoryNameById],
  )

  const visible = useMemo(() => {
    const filtered = searchable.filter(s=>!tagFilter||s.tagNames?.includes(tagFilter))
    if (!debounced.trim()) return filtered.map((s) => s.transaction)
    return searchTransactions(filtered, parseQuery(debounced)).map((h) => h.transaction)
  }, [searchable, debounced, tagFilter])

  const query = useMemo(() => parseQuery(debounced), [debounced])

  return (
    <div className="txns">
      <PeriodPicker period={period} payday={payday} onChange={onPeriodChange} />

      <div className="search">
        <div className="search__row">
          <input
            className="search__input"
            type="search"
            value={rawQuery}
            onChange={(e) => setRawQuery(e.target.value)}
            placeholder="ابحث باسم أو مبلغ أو تصنيف…"
            aria-label="البحث في العمليات (محلي على جهازك)"
          />
          <button
            type="button"
            className={`btn btn--quiet search__filter${tagFilter ? ' search__filter--on' : ''}`}
            aria-expanded={filtersOpen}
            onClick={() => setFiltersOpen((open) => !open)}
          >
            {tagFilter ? `وسم: ${tagFilter}` : 'فلترة'}
          </button>
        </div>
        {filtersOpen && (
          <label className="sheet__field">تصفية بالوسم<select className="sheet__input" value={tagFilter} onChange={e=>setTagFilter(e.target.value)}><option value="">كل الوسوم</option>{availableTags.map(t=><option key={t}>{t}</option>)}{tagFilter&&!availableTags.includes(tagFilter)&&<option>{tagFilter}</option>}</select></label>
        )}
        {query.amount && (
          <p className="search__hint" role="status">
            {`بحث بالمبلغ ${formatAmount(query.amount.targetMinor)} ± ${AMOUNT_TOLERANCE_PER_THOUSAND / 10}٪ — ${visible.length} نتيجة`}
          </p>
        )}
      </div>

      {data && (
        <>
          {/* الخانات الثلاث بالترتيب RTL، ولا تختفي أي خانة — spec/04 */}
          <div className="metrics metrics--compact">
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

          {/* لا رقم بلا مصدر (CLAUDE.md #10): السطر يقول ليه الخانات غير متاحة */}
          <div className="txnsSummary" role="status">
            <span>
              {data.totalCount} عملية
              {data.unclassifiedCount > 0 && ` · ${data.unclassifiedCount} محتاجة تحديد نوع`}
            </span>
            <span className="txnsSummary__actions">
              {data.unclassifiedCount > 0 && (
                <button type="button" className="btn txnsSummary__btn" onClick={onFixKinds}>
                  حدّد الأنواع
                </button>
              )}
              <button type="button" className="btn btn--quiet txnsSummary__btn" onClick={onOpenHistory}>
                مراجعة القديم
              </button>
            </span>
          </div>
        </>
      )}

      {loading && !data && (
        <p className="notice" role="status">
          بنحمّل العمليات…
        </p>
      )}

      {error ? <ErrorNotice cause={error} onRetry={onRetry} /> : null}

      {!loading && !error && data && visible.length === 0 && (
        <div className="empty">
          {debounced.trim() || tagFilter ? (
            <>
              <span className="empty__title">مفيش نتايج للبحث ده</span>
              <span>جرّب كلمة تانية، أو امسح البحث والفلترة عشان تشوف كل العمليات.</span>
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
              onOpen: () => onOpenTransaction(t),
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
