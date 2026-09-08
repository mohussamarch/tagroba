import { useState, type FormEvent } from 'react'
import { tryParseMoney } from '../../domain/money'
import { ruleFor, type EconomicKind } from '../../domain/entities/economicKind'
import { ErrorNotice } from '../components/ErrorNotice'
import type { Category, Wallet } from '../../domain/entities/types'
import type { UserContainer } from '../../app/container'
import './AddTransactionSheet.css'

interface Props {
  user: UserContainer
  wallets: Wallet[]
  categories: Category[]
  today: string
  onClose: () => void
  onAdded: () => void
}

/**
 * إضافة عملية يدويًا — spec/01: «الإضافة: فاتورة/شراء، حركة أموال…».
 *
 * الأنواع المعروضة مختارة عمدًا: دي اللي المستخدم بيعملها بنفسه.
 * الباقي (رسوم بنكية، بيع أصل) بييجي من الكشف أو من شاشة الاستثمار.
 */

/** الأنواع اللي تتسجّل يدويًا، مرتّبة بالأكثر استعمالًا. */
const KINDS: { kind: EconomicKind; hint: string }[] = [
  { kind: 'purchase', hint: 'قهوة، أكل، بنزين، أي حاجة اشتريتها' },
  { kind: 'salary', hint: 'راتب دخل حسابك' },
  { kind: 'internal_transfer', hint: 'نقلت فلوس بين محافظك — مش مصروف' },
  { kind: 'support_gift', hint: 'دعم أو هدية لحد — مصروف' },
  { kind: 'loan_granted', hint: 'سلّفت حد — مش مصروف، بقى ليك عنده' },
  { kind: 'loan_received', hint: 'حد سلّفك — مش دخل، بقى عليك' },
  { kind: 'debt_repaid', hint: 'سدّدت دين عليك — مش مصروف' },
  { kind: 'debt_collected', hint: 'حصّلت دين ليك — مش دخل' },
  { kind: 'freelance', hint: 'شغل حر أو عمولة' },
]

export function AddTransactionSheet({
  user,
  wallets,
  categories,
  today,
  onClose,
  onAdded,
}: Props) {
  const [amount, setAmount] = useState('')
  const [date, setDate] = useState(today)
  const [walletId, setWalletId] = useState(wallets.find((w) => w.kind === 'cash')?.id ?? wallets[0]?.id ?? '')
  const [kind, setKind] = useState<EconomicKind>('purchase')
  const [merchantName, setMerchantName] = useState('')
  const [transferToWalletId, setTransferToWalletId] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [fieldError, setFieldError] = useState<string | null>(null)

  const wallet = wallets.find((w) => w.id === walletId)
  const isExpense = ruleFor(kind).countsAsPersonalExpense
  const isTransfer = kind === 'internal_transfer'
  const targets = wallets.filter((w) => w.id !== walletId)
  const selectedHint = KINDS.find((k) => k.kind === kind)?.hint ?? ''

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldError(null)

    const amountMinor = tryParseMoney(amount)
    if (amountMinor === null || amountMinor <= 0) {
      setFieldError('اكتب مبلغ صحيح أكبر من صفر، زي 18 أو 18.50')
      return
    }

    if (isTransfer && !transferToWalletId) {
      setFieldError('اختار راح لأنهي محفظة')
      return
    }

    setBusy(true)
    try {
      await user.addTransaction({
        amountMinor,
        occurredAt: date,
        walletId,
        ...(isTransfer ? { transferToWalletId } : {}),
        economicKind: kind,
        merchantName,
        ...(categoryId ? { categoryId } : {}),
        ...(note.trim() ? { note } : {}),
      })
      onAdded()
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="إضافة عملية">
      <div className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">عملية جديدة</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <span aria-hidden="true">✕</span>
          </button>
        </header>

        <form className="sheet__body addTx" onSubmit={submit} noValidate>
          <label className="sheet__field">
            <span className="sheet__label">المبلغ بالريال</span>
            <input
              className="sheet__input addTx__amount"
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="18.00"
              autoFocus
              aria-invalid={fieldError ? 'true' : 'false'}
            />
            {fieldError && (
              <span className="sheet__error" role="alert">
                <span aria-hidden="true">⚠</span> {fieldError}
              </span>
            )}
          </label>

          <label className="sheet__field">
            <span className="sheet__label">نوع العملية</span>
            <select
              className="sheet__input"
              value={kind}
              onChange={(e) => setKind(e.target.value as EconomicKind)}
            >
              {KINDS.map((k) => (
                <option key={k.kind} value={k.kind}>
                  {ruleFor(k.kind).label}
                </option>
              ))}
            </select>
            <span className="sheet__hint">{selectedHint}</span>
            {/* الأثر مقروء قبل الحفظ، لا يُكتشف بعده */}
            <span className={`addTx__effect addTx__effect--${isExpense ? 'exp' : 'neutral'}`}>
              {isExpense
                ? 'هتتحسب في المصروف الشخصي'
                : ruleFor(kind).countsAsIncome
                  ? 'هتتحسب في الدخل'
                  : 'مش هتتحسب لا مصروف ولا دخل — نقل فلوس بس'}
            </span>
          </label>

          <label className="sheet__field">
            <span className="sheet__label">من أنهي محفظة</span>
            <select
              className="sheet__input"
              value={walletId}
              onChange={(e) => setWalletId(e.target.value)}
            >
              {wallets.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name}
                </option>
              ))}
            </select>
            {wallet?.kind === 'cash' && (
              <span className="sheet__hint">هتتوسم «كاش» تلقائيًا وتبان في كل العروض.</span>
            )}
          </label>

          {/*
            التحويل الداخلي **لازم له طرفان** (spec/02). الحقل ده بيظهر
            للتحويل بس، وبدونه الفلوس بتتخصم من محفظة ومتظهرش في التانية.
          */}
          {isTransfer && (
            <label className="sheet__field">
              <span className="sheet__label">راح لأنهي محفظة</span>
              <select
                className="sheet__input"
                value={transferToWalletId}
                onChange={(e) => setTransferToWalletId(e.target.value)}
              >
                <option value="">اختار المحفظة</option>
                {targets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </select>
              <span className="sheet__hint">
                المبلغ هيتخصم من فوق ويتضاف هنا. مجموع محافظك مش هيتغير.
              </span>
            </label>
          )}

          <label className="sheet__field">
            <span className="sheet__label">التاريخ</span>
            <input
              className="sheet__input"
              type="date"
              value={date}
              max={today}
              onChange={(e) => setDate(e.target.value)}
            />
          </label>

          <label className="sheet__field">
            <span className="sheet__label">الاسم — اختياري</span>
            <input
              className="sheet__input"
              type="text"
              value={merchantName}
              onChange={(e) => setMerchantName(e.target.value)}
              placeholder="مثلًا: قهوة الصباح"
              maxLength={120}
            />
          </label>

          <label className="sheet__field">
            <span className="sheet__label">التصنيف — اختياري</span>
            <select
              className="sheet__input"
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
            >
              <option value="">بلا تصنيف</option>
              {categories.filter(c=>c.active).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>

          <label className="sheet__field">
            <span className="sheet__label">ملاحظة — اختياري</span>
            <textarea
              className="sheet__input addTx__note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              rows={2}
              maxLength={1000}
            />
          </label>

          {error ? <ErrorNotice cause={error} /> : null}

          <div className="sheet__foot">
            <button type="button" className="btn btn--quiet" onClick={onClose}>
              إلغاء
            </button>
            <button type="submit" className="btn" disabled={busy}>
              {busy ? 'بنحفظ…' : 'حفظ العملية'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
