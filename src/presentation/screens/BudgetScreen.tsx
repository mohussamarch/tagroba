import { useMemo, useState } from 'react'
import { PeriodPicker } from '../components/PeriodPicker'
import { LimitEditor } from '../components/LimitEditor'
import { formatAmount, NOT_AVAILABLE } from '../../domain/formatMoney'
import type { BudgetScreenData } from '../../application/useCases/loadBudgetScreen'
import type { Period } from '../../domain/period'
import type { Id } from '../../domain/entities/types'
import { BudgetBar, Fact } from '../components/BudgetBar'
import './BudgetScreen.css'

interface Props {
  data: BudgetScreenData | null
  loading: boolean
  error: string | null
  period: Period
  payday: number
  amountsHidden: boolean
  onPeriodChange: (period: Period) => void
  onSetTotal: (limitMinor: number, thresholdPercent: number | null) => Promise<void>
  onClearTotal: () => Promise<void>
  onSetCategory: (categoryId: Id, limitMinor: number) => Promise<void>
  onClearCategory: (categoryId: Id) => Promise<void>
  onRetry: () => void
}

/**
 * شاشة الميزانية — spec/01.
 *
 * «سقف إجمالي وسقوف اختيارية للتصنيفات، ومتوسط **منفصل** من فترات مكتملة،
 *  وعلامة شذوذ، وحالة دون تاريخ أو سقف.»
 *
 * الفصل بين المتوسط والسقف مرئي في التصميم لا في الكود فقط:
 * المتوسط يظهر كـ«معلومة» بلون محايد، والسقف في بطاقته المستقلة.
 * ولا يوجد زر «اعمل سقف من المتوسط» — spec/01 يمنعه.
 */
export function BudgetScreen({
  data,
  loading,
  error,
  period,
  payday,
  amountsHidden,
  onPeriodChange,
  onSetTotal,
  onClearTotal,
  onSetCategory,
  onClearCategory,
  onRetry,
}: Props) {
  const [editingTotal, setEditingTotal] = useState(false)
  const [editingCategory, setEditingCategory] = useState<Id | null>(null)

  const categoryById = useMemo(
    () => new Map((data?.categories ?? []).map((c) => [c.id, c])),
    [data?.categories],
  )

  const money = (value: number | null) =>
    value === null ? NOT_AVAILABLE : amountsHidden ? '••••' : formatAmount(value)

  if (loading && !data) {
    return (
      <p className="notice" role="status">
        بنحمّل الميزانية…
      </p>
    )
  }

  if (error) {
    return (
      <div className="notice" role="alert">
        <strong>مقدرناش نحمّل الميزانية.</strong>
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

  return (
    <div className="budget">
      <PeriodPicker period={period} payday={payday} onChange={onPeriodChange} />

      {/* ─── السقف الإجمالي ─── */}
      <section className="card" aria-label="السقف الإجمالي">
        <div className="card__head">
          <h2 className="card__title">السقف الإجمالي</h2>
          {data.totalStatus && !editingTotal && (
            <button type="button" className="link" onClick={() => setEditingTotal(true)}>
              تعديل
            </button>
          )}
        </div>

        {editingTotal ? (
          <LimitEditor
            initialMinor={data.totalStatus?.limitMinor ?? null}
            initialThreshold={data.budget?.thresholdPercent ?? null}
            withThreshold
            onSave={async (limitMinor, threshold) => {
              await onSetTotal(limitMinor, threshold)
              setEditingTotal(false)
            }}
            onClear={
              data.totalStatus
                ? async () => {
                    await onClearTotal()
                    setEditingTotal(false)
                  }
                : undefined
            }
            onCancel={() => setEditingTotal(false)}
          />
        ) : data.totalStatus ? (
          <BudgetBar status={data.totalStatus} hidden={amountsHidden} />
        ) : data.budget?.totalLimitMinor ? (
          <div className="budget__empty">
            <p className="budget__emptyText">
              السقف {money(data.budget.totalLimitMinor)}، بس المصروف لسه مجهول عشان العمليات
              محتاجة تحديد نوعها. المقارنة هتظهر بعد ما تحددها.
            </p>
            <button type="button" className="btn btn--quiet" onClick={() => setEditingTotal(true)}>
              تعديل السقف
            </button>
          </div>
        ) : (
          <div className="budget__empty">
            <p className="budget__emptyText">
              مفيش سقف للفترة دي. من غيره مفيش «متاح يومي» ولا تنبيه تجاوز.
            </p>
            <button type="button" className="btn" onClick={() => setEditingTotal(true)}>
              حدّد سقف
            </button>
          </div>
        )}

        <div className="budget__facts">
          <Fact
            label="المصروف"
            value={money(data.spentKnown ? data.spentMinor : null)}
            note={data.spentNote}
          />
          <Fact
            label="المتاح اليومي"
            value={money(data.allowance.amountMinor)}
            note={data.allowance.reason}
          />
        </div>
      </section>

      {/* ─── المتوسط: معلومة منفصلة، ليست سقفًا ─── */}
      <section className="card" aria-label="المتوسط والمقارنة">
        <h2 className="card__title">المتوسط</h2>
        <div className="budget__facts">
          <Fact
            label="متوسط الفترات المكتملة"
            value={money(data.average.averageMinor)}
            note={data.average.reason}
          />
          <Fact
            label="مقارنة بالمعتاد"
            value={
              data.anomaly.isAnomaly === null
                ? NOT_AVAILABLE
                : data.anomaly.isAnomaly
                  ? 'خارج المعتاد'
                  : 'ضمن المعتاد'
            }
            note={data.anomaly.reason}
            tone={data.anomaly.isAnomaly === true ? 'warn' : 'plain'}
          />
        </div>
        {/* لا زر «اعمل سقف من المتوسط» — spec/01 يمنع خلق ميزانية من متوسط */}
        <p className="budget__hint">
          المتوسط معلومة للمقارنة بس. السقف بتحدده انت بنفسك.
        </p>
        {data.average.excluded.length > 0 && (
          <details className="budget__excluded">
            <summary>{data.average.excluded.length} فترة مستبعدة من المتوسط</summary>
            <ul>
              {data.average.excluded.map((e) => (
                <li key={e.periodKey}>
                  <strong>{e.periodKey}</strong>: {e.reason}
                </li>
              ))}
            </ul>
          </details>
        )}
      </section>

      {/* ─── سقوف التصنيفات ─── */}
      <section className="card" aria-label="سقوف التصنيفات">
        <h2 className="card__title">التصنيفات</h2>
        {data.lines.length === 0 ? (
          <p className="budget__hint">مفيش مصروف ولا سقوف في الفترة دي.</p>
        ) : (
          <ul className="budget__lines">
            {data.lines.map((line) => {
              const category = categoryById.get(line.categoryId)
              const isEditing = editingCategory === line.categoryId
              return (
                <li key={line.categoryId} className="budget__line">
                  <div className="budget__lineHead">
                    <span className="budget__lineName">{category?.name ?? line.categoryId}</span>
                    <span className="budget__lineSpent num">{money(line.spentMinor)}</span>
                  </div>

                  {isEditing ? (
                    <LimitEditor
                      initialMinor={line.status?.limitMinor ?? null}
                      initialThreshold={null}
                      onSave={async (limitMinor) => {
                        await onSetCategory(line.categoryId, limitMinor)
                        setEditingCategory(null)
                      }}
                      onClear={
                        line.status
                          ? async () => {
                              await onClearCategory(line.categoryId)
                              setEditingCategory(null)
                            }
                          : undefined
                      }
                      onCancel={() => setEditingCategory(null)}
                    />
                  ) : (
                    <>
                      {line.status ? (
                        <BudgetBar status={line.status} hidden={amountsHidden} compact />
                      ) : (
                        <span className="budget__noLimit">{line.noLimitReason}</span>
                      )}

                      <div className="budget__lineFoot">
                        <span className="budget__lineNote">
                          {line.averageMinor === null
                            ? 'مفيش متوسط لسه'
                            : `المعتاد ${formatAmount(line.averageMinor)}`}
                          {line.anomaly.isAnomaly === true && ' · خارج المعتاد'}
                        </span>
                        <button
                          type="button"
                          className="link"
                          onClick={() => setEditingCategory(line.categoryId)}
                        >
                          {line.status ? 'تعديل السقف' : 'حدّد سقف'}
                        </button>
                      </div>
                    </>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </div>
  )
}
