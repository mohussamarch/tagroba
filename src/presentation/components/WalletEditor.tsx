import { useState, type FormEvent } from 'react'
import { formatAmount } from '../../domain/formatMoney'
import { tryParseMoney } from '../../domain/money'
import { isValidIsoDate } from '../../domain/period'
import type { Wallet } from '../../domain/entities/types'
import '../components/LimitEditor.css'

interface Props {
  wallet: Wallet
  onSave: (wallet: Wallet) => Promise<void>
  onCancel: () => void
}

/**
 * محرر المحفظة: الرصيد الافتتاحي **وتاريخه**.
 *
 * ⚠️ التاريخ ليس تفصيلًا. سلسلة المطابقة تبدأ منه، وأي عملية قبله
 * **لا تدخل الحساب**. مستخدم جديد يستورد كشفًا يغطي شهورًا ماضية
 * بينما تاريخ الافتتاح اليوم ⇒ لا يُطابَق شيء، بلا سبب ظاهر.
 * اكتُشف بالتشغيل: المحفظة كانت تُزرع بتاريخ اليوم بلا وسيلة لتعديله.
 */
export function WalletEditor({ wallet, onSave, onCancel }: Props) {
  const [amountText, setAmountText] = useState(
    formatAmount(wallet.openingBalanceMinor, wallet.currency, { grouping: false }),
  )
  const [dateText, setDateText] = useState(wallet.openingAt)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    const openingBalanceMinor = tryParseMoney(amountText)
    if (openingBalanceMinor === null) {
      setError('اكتب مبلغ صحيح، زي 4837.83')
      return
    }
    if (!isValidIsoDate(dateText)) {
      setError('التاريخ لازم يكون بصيغة YYYY-MM-DD وتاريخ موجود فعلًا')
      return
    }

    setBusy(true)
    try {
      await onSave({ ...wallet, openingBalanceMinor, openingAt: dateText })
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="limitEditor" onSubmit={submit} noValidate>
      <label className="limitEditor__field">
        <span className="limitEditor__label">الرصيد الافتتاحي بالريال</span>
        <input
          className="limitEditor__input"
          type="text"
          inputMode="decimal"
          value={amountText}
          onChange={(e) => setAmountText(e.target.value)}
          placeholder="4837.83"
          autoFocus
        />
        <span className="limitEditor__hint">
          الرصيد قبل أول عملية في الكشف. ممكن يكون سالب لو الحساب كان مكشوفًا.
        </span>
      </label>

      <label className="limitEditor__field">
        <span className="limitEditor__label">تاريخ الرصيد ده</span>
        <input
          className="limitEditor__input"
          type="date"
          value={dateText}
          onChange={(e) => setDateText(e.target.value)}
        />
        <span className="limitEditor__hint">
          المطابقة بتبدأ من هنا. أي عملية قبل التاريخ ده **مش هتدخل الحساب**،
          فخليه قبل أول عملية في كشفك.
        </span>
      </label>

      {error && (
        <p className="limitEditor__error" role="alert">
          <span aria-hidden="true">⚠</span> {error}
        </p>
      )}

      <div className="limitEditor__actions">
        <button type="submit" className="btn" disabled={busy}>
          {busy ? 'بنحفظ…' : 'حفظ'}
        </button>
        <button type="button" className="btn btn--quiet" onClick={onCancel}>
          إلغاء
        </button>
      </div>
    </form>
  )
}
