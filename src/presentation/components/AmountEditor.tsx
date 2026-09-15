import { useState, type FormEvent } from 'react'
import { Pencil } from 'lucide-react'
import { formatAmount } from '../../domain/formatMoney'
import { tryParseMoney, type Currency } from '../../domain/money'

/**
 * تعديل مبلغ العملية من صفحتها — OVERRIDES §32. الشاشة بتحوّل النص لهللة بس،
 * والتحقق (أكبر من صفر، مش أقل من المتوزع) في `domain/amountEdit.ts` عن طريق حالة الاستخدام.
 */
export function AmountEditor({ amountMinor, originalAmountMinor, currency, disabled, onSave }: {
  amountMinor: number
  originalAmountMinor?: number
  currency: Currency
  disabled: boolean
  onSave: (amountMinor: number) => Promise<void>
}) {
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const minor = tryParseMoney(text.trim())
    if (minor === null || minor <= 0) { setError('اكتب مبلغ صحيح أكبر من صفر، زي 180 أو 180.50'); return }
    setError(null)
    try { await onSave(minor); setEditing(false) } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)) }
  }

  return (
    <div className="amountEditor">
      {originalAmountMinor !== undefined && originalAmountMinor !== amountMinor && (
        <span className="sheet__hint">المبلغ الأصلي في الكشف {formatAmount(originalAmountMinor, currency)} — محفوظ عشان إعادة الاستيراد ما تكررش العملية.</span>
      )}
      {editing ? (
        <form className="amountEditor__form" onSubmit={(e) => void submit(e)} noValidate>
          <input className="sheet__input" inputMode="decimal" value={text} disabled={disabled} autoFocus aria-label="المبلغ الجديد" onChange={(e) => setText(e.target.value)} />
          <button type="submit" className="btn" disabled={disabled}>احفظ المبلغ</button>
          <button type="button" className="btn btn--quiet" disabled={disabled} onClick={() => { setEditing(false); setError(null) }}>إلغاء</button>
        </form>
      ) : (
        <button type="button" className="btn btn--quiet amountEditor__open" disabled={disabled}
          onClick={() => { setText(formatAmount(amountMinor, currency, { grouping: false })); setEditing(true) }}>
          <Pencil size={16} aria-hidden="true" /> عدّل المبلغ
        </button>
      )}
      {error && <p className="settings__error" role="alert">{error}</p>}
    </div>
  )
}
