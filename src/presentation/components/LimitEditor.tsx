import { useState, type FormEvent } from 'react'
import { formatAmount } from '../../domain/formatMoney'
import { tryParseMoney } from '../../domain/money'
import './LimitEditor.css'

interface Props {
  /** السقف الحالي بالهللة، أو null لو لسه مفيش. */
  initialMinor: number | null
  initialThreshold: number | null
  withThreshold?: boolean
  onSave: (limitMinor: number, thresholdPercent: number | null) => Promise<void>
  /** يُمرَّر فقط حين يوجد سقف يُمسح. */
  onClear?: () => Promise<void>
  onCancel: () => void
}

/**
 * محرر سقف — spec/04: «ميزانية | تحرير القيمة والعتبة، مقارنة المتوسط، **وحفظ فعلي**».
 *
 * الإدخال نصّي ويُحوَّل بـ `tryParseMoney` — نفس المحوِّل الذي يقرأ الكشف،
 * فيقبل الأرقام العربية والفواصل ويرفض ما لا يُقرأ، بلا أي عملية عشرية.
 */
export function LimitEditor({
  initialMinor,
  initialThreshold,
  withThreshold = false,
  onSave,
  onClear,
  onCancel,
}: Props) {
  const [text, setText] = useState(
    initialMinor === null ? '' : formatAmount(initialMinor, 'SAR', { grouping: false }),
  )
  const [threshold, setThreshold] = useState(
    initialThreshold === null ? '' : String(initialThreshold),
  )
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    const limitMinor = tryParseMoney(text)
    if (limitMinor === null) {
      setError('اكتب مبلغ صحيح، زي 3000 أو 3000.50')
      return
    }
    if (limitMinor <= 0) {
      setError('السقف لازم يكون أكبر من صفر')
      return
    }

    let thresholdPercent: number | null = null
    if (withThreshold && threshold.trim() !== '') {
      const parsed = Number(threshold.trim())
      if (!Number.isInteger(parsed) || parsed < 1 || parsed > 100) {
        setError('العتبة لازم تكون رقم صحيح بين 1 و100')
        return
      }
      thresholdPercent = parsed
    }

    setBusy(true)
    try {
      await onSave(limitMinor, thresholdPercent)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="limitEditor" onSubmit={submit} noValidate>
      <label className="limitEditor__field">
        <span className="limitEditor__label">السقف بالريال</span>
        <input
          className="limitEditor__input"
          type="text"
          inputMode="decimal"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="3000"
          autoFocus
          aria-invalid={error ? 'true' : 'false'}
        />
      </label>

      {withThreshold && (
        <label className="limitEditor__field">
          <span className="limitEditor__label">نبّهني عند (٪) — اختياري</span>
          <input
            className="limitEditor__input"
            type="text"
            inputMode="numeric"
            value={threshold}
            onChange={(e) => setThreshold(e.target.value)}
            placeholder="80"
          />
          <span className="limitEditor__hint">سيبها فاضية يعني مفيش تنبيه.</span>
        </label>
      )}

      {error && (
        <p className="limitEditor__error" role="alert">
          <span aria-hidden="true">⚠</span> {error}
        </p>
      )}

      <div className="limitEditor__actions">
        <button type="submit" className="btn" disabled={busy}>
          {busy ? 'بنحفظ…' : 'حفظ'}
        </button>
        {/* الإلغاء متاح دائمًا — لا يُحبس المستخدم في المحرر */}
        <button type="button" className="btn btn--quiet" onClick={onCancel}>
          إلغاء
        </button>
        {onClear && (
          <button
            type="button"
            className="btn btn--danger"
            disabled={busy}
            onClick={async () => {
              setBusy(true)
              try {
                await onClear()
              } catch (cause) {
                setError(cause instanceof Error ? cause.message : String(cause))
              } finally {
                setBusy(false)
              }
            }}
          >
            امسح السقف
          </button>
        )}
      </div>
    </form>
  )
}
