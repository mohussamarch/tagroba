import { useState, type FormEvent } from 'react'
import { ASSET_KIND_LABELS, ASSET_UNIT_DEFAULTS, type AssetKind } from '../../domain/entities/assets'
import { ErrorNotice } from '../components/ErrorNotice'
import type { UserContainer } from '../../app/container'
import './InvestmentScreen.css'

interface Props {
  user: UserContainer
  onClose: () => void
  onAdded: () => void
}

const KINDS = Object.keys(ASSET_KIND_LABELS) as AssetKind[]

/** إضافة أصل استثماري. وحدة القياس تتغيّر مع النوع ويقدر يعدّلها. */
export function AddAssetSheet({ user, onClose, onAdded }: Props) {
  const [name, setName] = useState('')
  const [kind, setKind] = useState<AssetKind>('gold')
  const [unitLabel, setUnitLabel] = useState(ASSET_UNIT_DEFAULTS.gold)
  const [unitTouched, setUnitTouched] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)

  function pickKind(next: AssetKind) {
    setKind(next)
    // الوحدة تتبع النوع لحد ما يكتبها بنفسه، وبعدها ما نلمسهاش
    if (!unitTouched) setUnitLabel(ASSET_UNIT_DEFAULTS[next])
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await user.manageAssets.addAsset({ name, kind, unitLabel })
      onAdded()
    } catch (cause) {
      setError(cause)
    } finally {
      // لا يتعلّق الزر في «بنحفظ…» لو الحفظ فشل
      setBusy(false)
    }
  }

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="أصل جديد">
      <div className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">أصل جديد</h2>
          {/* زر الإغلاق ما يتعطّلش أبدًا — المستخدم ما يتحبسش */}
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <span aria-hidden="true">✕</span>
          </button>
        </header>

        <form className="sheet__body" onSubmit={submit} noValidate>
          <label className="sheet__field">
            <span className="sheet__label">الاسم</span>
            <input
              className="sheet__input"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="ذهب عيار 21"
              maxLength={80}
              autoFocus
            />
          </label>

          <fieldset className="sheet__field investKinds">
            <legend className="sheet__label">النوع</legend>
            <div className="investKinds__row">
              {KINDS.map((k) => (
                <button
                  key={k}
                  type="button"
                  className={`chip${k === kind ? ' chip--on' : ''}`}
                  onClick={() => pickKind(k)}
                  aria-pressed={k === kind}
                >
                  {ASSET_KIND_LABELS[k]}
                </button>
              ))}
            </div>
          </fieldset>

          <label className="sheet__field">
            <span className="sheet__label">وحدة القياس</span>
            <input
              className="sheet__input"
              type="text"
              value={unitLabel}
              onChange={(e) => {
                setUnitTouched(true)
                setUnitLabel(e.target.value)
              }}
              placeholder="جرام"
              maxLength={20}
            />
            <span className="sheet__hint">
              الكمية هتظهر بالوحدة دي، فما تبقاش رقمًا بلا معنى.
            </span>
          </label>

          {error ? <ErrorNotice cause={error} /> : null}

          <button type="submit" className="btn" disabled={busy || !name.trim()}>
            {busy ? 'بنحفظ…' : 'حفظ'}
          </button>
        </form>
      </div>
    </div>
  )
}
