import { useEffect, useState } from 'react'
import { formatAmount } from '../../domain/formatMoney'
import { ErrorNotice } from '../components/ErrorNotice'
import { TagEditor } from '../components/TagEditor'
import type { Category, Tag, Transaction } from '../../domain/entities/types'
import type { UserContainer } from '../../app/container'
import './TransactionSheet.css'

interface Props {
  user: UserContainer
  transaction: Transaction
  categories: Category[]
  onClose: () => void
  onChanged: () => void
  /** فتح ورقة ربط الشخص — إجراء مستقل بمنطقه الخاص. */
  onLinkPerson: () => void
}

/**
 * صفحة العملية — `spec/01`: «صفحات فرعية للتاجر و**التصنيف** والأصل والشخص».
 *
 * اللي بيتعدّل: التصنيف، الملاحظة، وسم الكاش، الاستبعاد من الميزانية، الوسوم.
 * اللي **ما بيتعدلش**: المبلغ والتاريخ والاتجاه — دول حقائق من الكشف لا آراء،
 * وتعديلهم كان هيكسر مطابقة الرصيد ومنع التكرار. مكتوب في الشاشة نفسها
 * عشان الغياب يبقى مفهومًا لا ناقصًا.
 */
export function TransactionSheet({
  user,
  transaction,
  categories,
  onClose,
  onChanged,
  onLinkPerson,
}: Props) {
  const [note, setNote] = useState(transaction.note ?? '')
  const [categoryId, setCategoryId] = useState(transaction.categoryId ?? '')
  const [isCash, setIsCash] = useState(transaction.isCashTagged)
  const [excluded, setExcluded] = useState(transaction.excludedFromBudget)
  const [tags, setTags] = useState<Tag[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [saved, setSaved] = useState<string | null>(null)

  useEffect(() => {
    let alive = true
    user.editTransaction
      .load(transaction.id)
      .then((detail) => alive && setTags(detail.tags))
      .catch((cause) => alive && setError(cause))
    return () => {
      alive = false
    }
  }, [user, transaction.id])

  /** كل تعديل بيتحفظ لوحده — مفيش زر «حفظ» بيخلي المستخدم مش عارف اتحفظ ولا لأ. */
  async function run(label: string, action: () => Promise<unknown>) {
    setError(null)
    setSaved(null)
    setBusy(true)
    try {
      await action()
      setSaved(label)
      onChanged()
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  const isIncoming = transaction.observedDirection === 'in'

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="تفاصيل العملية">
      <div className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">تفاصيل العملية</h2>
          {/* الإغلاق ما يتعطّلش أبدًا */}
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <span aria-hidden="true">✕</span>
          </button>
        </header>

        <div className="sheet__body">
          {/* الحقائق — للعرض بس */}
          <section className="txnFacts">
            <span
              className="txnFacts__amount num"
              style={{ color: isIncoming ? 'var(--c-incoming)' : 'var(--c-outgoing)' }}
            >
              {formatAmount(transaction.amountMinor, transaction.currency)}
            </span>
            <span className="txnFacts__meta">
              {transaction.occurredAt} · {isIncoming ? 'وارد' : 'صادر'}
            </span>
            {transaction.rawMerchantName || transaction.sourceOperationType ? (
              <span className="txnFacts__raw">
                {transaction.rawMerchantName ?? transaction.sourceOperationType}
              </span>
            ) : null}
            <span className="sheet__hint">
              المبلغ والتاريخ والاتجاه جايين من الكشف وما بيتعدلوش — تعديلهم بيكسر
              مطابقة الرصيد ومنع التكرار.
            </span>
          </section>

          <label className="sheet__field">
            <span className="sheet__label">التصنيف</span>
            <select
              className="sheet__input"
              value={categoryId}
              disabled={busy}
              onChange={(e) => {
                const next = e.target.value
                setCategoryId(next)
                void run('اتحفظ التصنيف', () =>
                  user.editTransaction.setCategory(transaction.id, next || null),
                )
              }}
            >
              <option value="">بلا تصنيف</option>
              {categories.filter(c=>c.active||c.id===categoryId).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>

          <label className="sheet__field">
            <span className="sheet__label">ملاحظة</span>
            <textarea
              className="sheet__input txnNote"
              value={note}
              maxLength={1000}
              disabled={busy}
              onChange={(e) => setNote(e.target.value)}
              onBlur={() => {
                if (note.trim() === (transaction.note ?? '').trim()) return
                void run('اتحفظت الملاحظة', () =>
                  user.editTransaction.setNote(transaction.id, note),
                )
              }}
              placeholder="مثلًا: قهوة مع أحمد"
            />
          </label>

          <Toggle
            label="اتدفعت كاش"
            hint="علامة للتقارير بس — الخصم من المحفظة زي ما هو."
            checked={isCash}
            disabled={busy}
            onChange={(next) => {
              setIsCash(next)
              void run('اتحفظ وسم الكاش', () =>
                user.editTransaction.setCashTag(transaction.id, next),
              )
            }}
          />

          <Toggle
            label="مستبعدة من الميزانية"
            hint="بتظهر في خانة مستقلة — الاستبعاد مش إخفاء."
            checked={excluded}
            disabled={busy}
            onChange={(next) => {
              setExcluded(next)
              void run('اتحفظ الاستبعاد', () =>
                user.editTransaction.setExcludedFromBudget(transaction.id, next),
              )
            }}
          />

          <TagEditor
            user={user}
            transactionId={transaction.id}
            tags={tags}
            onChanged={(next) => {
              setTags(next)
              onChanged()
            }}
          />

          <button type="button" className="btn btn--quiet" onClick={onLinkPerson}>
            ربط بشخص
          </button>

          {error ? <ErrorNotice cause={error} /> : null}
          {saved ? (
            <p className="notice" role="status">
              {saved}
            </p>
          ) : null}
        </div>
      </div>
    </div>
  )
}

function Toggle({
  label,
  hint,
  checked,
  disabled,
  onChange,
}: {
  label: string
  hint: string
  checked: boolean
  disabled: boolean
  onChange: (next: boolean) => void
}) {
  return (
    <label className="sheet__field txnToggle">
      <span className="txnToggle__row">
        <input
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={(e) => onChange(e.target.checked)}
        />
        <span className="sheet__label">{label}</span>
      </span>
      <span className="sheet__hint">{hint}</span>
    </label>
  )
}
