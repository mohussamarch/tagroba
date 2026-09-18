import { X } from 'lucide-react'
import { useMemo, useState, type FormEvent } from 'react'
import type { UserContainer } from '../../app/container'
import type { PersonRow } from '../../application/useCases/managePeople'
import { formatAmount } from '../../domain/formatMoney'
import { tryParseMoney } from '../../domain/money'
import { settleableKinds, suggestedSettlementMinor } from '../../domain/settlementSuggestion'
import type { Transaction } from '../../domain/entities/types'
import { ErrorNotice } from '../components/ErrorNotice'

const KIND_LABEL = { receivable: 'ليك عنده', loan_payable: 'عليك ليه', custody_payable: 'أمانة معاك' } as const

/**
 * «اربطها بدين موجود» من قايمة النقط التلاتة — OVERRIDES §30 («هربطها بفلوس جت قبل كده»).
 * بيعرض الديون المفتوحة اللي تناسب اتجاه العملية، والمبلغ المقترح الأقل بين العملية والمتبقي،
 * والتسوية بتتسجل بـ`managePeople.settle` مربوطة بالعملية (الزيادة بترفض بتفسير — spec/06).
 */
export function SettleWithTransactionSheet({ user, transaction, rows, loading, onClose, onSettled }: {
  user: UserContainer
  transaction: Transaction
  rows: readonly PersonRow[]
  loading: boolean
  onClose: () => void
  onSettled: () => void
}) {
  const options = useMemo(() => {
    const kinds = settleableKinds(transaction.observedDirection)
    return rows.flatMap((row) => row.obligations
      .filter(({ obligation, remainingMinor }) => remainingMinor > 0 && kinds.includes(obligation.kind))
      .map(({ obligation, remainingMinor }) => ({ row, obligation, remainingMinor })))
  }, [rows, transaction.observedDirection])

  const [choice, setChoice] = useState('')
  const [requestId] = useState(() => crypto.randomUUID())
  const [amountText, setAmountText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const chosen = options.find((o) => o.obligation.id === choice)

  function pick(id: string) {
    setChoice(id)
    const option = options.find((o) => o.obligation.id === id)
    setAmountText(option ? formatAmount(suggestedSettlementMinor(transaction.amountMinor, option.remainingMinor), 'SAR', { grouping: false }) : '')
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!chosen) { setError(new Error('اختار الدين الأول.')); return }
    const amountMinor = tryParseMoney(amountText.trim())
    if (amountMinor === null || amountMinor <= 0) { setError(new Error('اكتب مبلغ صحيح أكبر من صفر، زي 150 أو 150.50')); return }
    setBusy(true); setError(null)
    try {
      await user.managePeople.settle({ obligationId: chosen.obligation.id, personId: chosen.row.person.id, amountMinor, transactionId: transaction.id, requestId })
      onSettled()
    } catch (cause) {
      setError(cause)
    } finally { setBusy(false) }
  }

  const isIncoming = transaction.observedDirection === 'in'
  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="اربطها بدين موجود">
      <form className="sheet__panel" onSubmit={(e) => void submit(e)}>
        <header className="sheet__head">
          <h2 className="sheet__title">اربطها بدين موجود</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق"><X size={18} aria-hidden="true" /></button>
        </header>
        <div className="sheet__body">
          <p className="sheet__hint">
            {isIncoming ? 'الفلوس دي جتلك — اختار دين ليك عند حد اتسدد بيها.' : 'الفلوس دي خرجت منك — اختار دين عليك أو أمانة معاك رجّعتها بيها.'}
            {' '}المبلغ الأصلي للعملية ما بيتغيرش.
          </p>
          {loading && options.length === 0 && <p className="notice" role="status">بنحمّل الديون…</p>}
          {!loading && options.length === 0 && <p className="notice" role="status">مفيش ديون مفتوحة تناسب العملية دي. ضيف الدين من شاشة الأشخاص الأول.</p>}
          {options.length > 0 && (
            <label className="sheet__field">
              <span className="sheet__label">الدين</span>
              <select className="sheet__input" value={choice} disabled={busy} onChange={(e) => pick(e.target.value)}>
                <option value="">اختار…</option>
                {options.map(({ row, obligation, remainingMinor }) => (
                  <option key={obligation.id} value={obligation.id}>
                    {row.person.name} — {KIND_LABEL[obligation.kind]} — باقي {formatAmount(remainingMinor)}
                  </option>
                ))}
              </select>
            </label>
          )}
          {chosen && (
            <label className="sheet__field">
              <span className="sheet__label">المبلغ اللي اتسدد</span>
              <input className="sheet__input" inputMode="decimal" value={amountText} disabled={busy} onChange={(e) => setAmountText(e.target.value)} />
            </label>
          )}
          {error ? <ErrorNotice cause={error} /> : null}
          <button type="submit" className="btn" disabled={busy || !chosen}>{busy ? 'بنحفظ…' : 'اربطها'}</button>
        </div>
      </form>
    </div>
  )
}
