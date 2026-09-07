import { useState, type FormEvent } from 'react'
import { formatAmount } from '../../domain/formatMoney'
import { tryParseMoney } from '../../domain/money'
import { formatQuantity, parseQuantity } from '../../domain/quantity'
import { describePriceState } from '../../domain/assets'
import { ErrorNotice } from '../components/ErrorNotice'
import { AssetHistory } from '../components/AssetHistory'
import { FeedLink } from '../components/FeedLink'
import type { AssetRow } from '../../application/useCases/manageAssets'
import type { UserContainer } from '../../app/container'
import './InvestmentScreen.css'

interface Props {
  user: UserContainer
  row: AssetRow
  onClose: () => void
  /** بعد تسجيل حركة — بيقفل الورقة ويعيد التحميل. */
  onChanged: () => void
  /** إعادة تحميل بلا قفل — لتغييرات ما تستاهلش تخرج المستخدم. */
  onRefresh: () => void
}

type Mode = 'buy' | 'sell' | 'price' | 'history'

const MODE_LABEL: Record<Mode, string> = {
  buy: 'شراء',
  sell: 'بيع',
  price: 'السعر',
  history: 'السجل',
}

const today = () => new Date().toISOString().slice(0, 10)

/**
 * ورقة أصل واحد: تسجيل شراء أو بيع، تحديث السعر، وعرض السجل.
 *
 * `spec/01`: «**البيع تسجيل فقط**» — الكلمة مكتوبة في الشاشة نفسها
 * فما حدش يفتكر إن فيه أمر بيع بيتنفذ.
 */
export function AssetSheet({ user, row, onClose, onChanged, onRefresh }: Props) {
  const [mode, setMode] = useState<Mode>('buy')
  const [date, setDate] = useState(today())
  const [quantity, setQuantity] = useState('')
  const [amount, setAmount] = useState('')
  const [fee, setFee] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [done, setDone] = useState<string | null>(null)

  const { asset, position } = row

  function reset() {
    setQuantity('')
    setAmount('')
    setFee('')
    setError(null)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setDone(null)
    setBusy(true)
    try {
      // التحويل من نص لأرقام صحيحة هنا فقط، والرفض بتفسير لا تقريب صامت
      const feeMinor = fee.trim() ? tryParseMoney(fee) : 0
      if (feeMinor === null) throw new Error('الرسوم مش رقم صالح')

      if (mode === 'price') {
        const priceMinor = tryParseMoney(amount)
        if (priceMinor === null) throw new Error('السعر مش رقم صالح')
        await user.manageAssets.setPrice({
          assetId: asset.id,
          pricePerUnitMinor: priceMinor,
          asOf: date,
        })
        setDone('اتسجل السعر')
      } else {
        const qty = parseQuantity(quantity)
        const amountMinor = tryParseMoney(amount)
        if (amountMinor === null) throw new Error('المبلغ مش رقم صالح')

        if (mode === 'buy') {
          await user.manageAssets.recordPurchase({
            assetId: asset.id,
            purchasedAt: date,
            quantity: qty,
            principalMinor: amountMinor,
            feeMinor,
          })
          setDone('اتسجل الشراء')
        } else {
          await user.manageAssets.recordSale({
            assetId: asset.id,
            soldAt: date,
            quantity: qty,
            grossProceedsMinor: amountMinor,
            feeMinor,
          })
          setDone('اتسجل البيع')
        }
      }
      reset()
      onChanged()
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  const amountLabel =
    mode === 'buy' ? 'المدفوع للبائع' : mode === 'sell' ? 'المقبوض من المشتري' : `سعر الـ${asset.unitLabel}`

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label={asset.name}>
      <div className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">{asset.name}</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <span aria-hidden="true">✕</span>
          </button>
        </header>

        <div className="sheet__body">
          <div className="investSummary">
            <span>
              المملوك {formatQuantity(position.heldQuantity)} {asset.unitLabel}
            </span>
            <span>تكلفته {formatAmount(position.costBasisMinor)}</span>
            <span>ربح محقق {formatAmount(position.realizedGainMinor)}</span>
          </div>
          <p className={`invest__price invest__price--${position.priceState.kind}`}>
            {describePriceState(position.priceState)}
          </p>

          <div className="investKinds__row" role="tablist" aria-label="الإجراء">
            {(Object.keys(MODE_LABEL) as Mode[]).map((m) => (
              <button
                key={m}
                type="button"
                role="tab"
                aria-selected={m === mode}
                className={`chip${m === mode ? ' chip--on' : ''}`}
                onClick={() => {
                  setMode(m)
                  setDone(null)
                  setError(null)
                }}
              >
                {MODE_LABEL[m]}
              </button>
            ))}
          </div>

          {mode === 'history' ? (
            <AssetHistory row={row} />
          ) : (
            <form className="investForm" onSubmit={submit} noValidate>
              {mode === 'sell' && (
                <p className="sheet__hint">
                  تسجيل بس. التطبيق ما بيبيعش حاجة، وحصيلة البيع مش هتتحسب دخلًا في
                  الميزانية.
                </p>
              )}

              <label className="sheet__field">
                <span className="sheet__label">التاريخ</span>
                <input
                  className="sheet__input"
                  type="date"
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                />
              </label>

              {mode !== 'price' && (
                <label className="sheet__field">
                  <span className="sheet__label">الكمية ({asset.unitLabel})</span>
                  <input
                    className="sheet__input num"
                    type="text"
                    inputMode="decimal"
                    value={quantity}
                    onChange={(e) => setQuantity(e.target.value)}
                    placeholder="12.5"
                  />
                </label>
              )}

              <label className="sheet__field">
                <span className="sheet__label">{amountLabel}</span>
                <input
                  className="sheet__input num"
                  type="text"
                  inputMode="decimal"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="0.00"
                />
              </label>

              {mode !== 'price' && (
                <label className="sheet__field">
                  <span className="sheet__label">الرسوم (اختياري)</span>
                  <input
                    className="sheet__input num"
                    type="text"
                    inputMode="decimal"
                    value={fee}
                    onChange={(e) => setFee(e.target.value)}
                    placeholder="0.00"
                  />
                  <span className="sheet__hint">
                    {mode === 'buy'
                      ? 'رسوم الشراء بتنضم للتكلفة، فالربح ما يبانش أكبر من الحقيقة.'
                      : 'رسوم البيع بتتخصم من الحصيلة قبل حساب الربح.'}
                  </span>
                </label>
              )}

              {mode === 'price' && (
                <FeedLink user={user} asset={asset} onChanged={onRefresh} />
              )}

              {error ? <ErrorNotice cause={error} /> : null}
              {done ? (
                <p className="notice" role="status">
                  {done}
                </p>
              ) : null}

              <button type="submit" className="btn" disabled={busy}>
                {busy ? 'بنحفظ…' : `سجّل ${MODE_LABEL[mode]}`}
              </button>
            </form>
          )}

          <button
            type="button"
            className="btn btn--quiet"
            onClick={async () => {
              await user.manageAssets.archiveAsset(asset.id, !asset.archived)
              onChanged()
            }}
          >
            {asset.archived ? 'إلغاء الأرشفة' : 'أرشفة الأصل'}
          </button>
        </div>
      </div>
    </div>
  )
}
