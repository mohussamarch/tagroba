import { useEffect, useState } from 'react'
import { formatAmount } from '../../domain/formatMoney'
import type { FeedPrice } from '../../domain/priceFeed'
import type { Asset } from '../../domain/entities/assets'
import type { UserContainer } from '../../app/container'
import { ErrorNotice } from './ErrorNotice'
import '../screens/InvestmentScreen.css'

/**
 * ربط أصل برمز في ملف الأسعار.
 *
 * القايمة **مقفولة على اللي في الملف فعلًا** — مفيش كتابة رمز باليد،
 * فما ينفعش يربط بحاجة مش موجودة وبعدين يستنى سعرًا عمره ما هييجي.
 */
export function FeedLink({
  user,
  asset,
  onChanged,
}: {
  user: UserContainer
  asset: Asset
  onChanged: () => void
}) {
  const [options, setOptions] = useState<FeedPrice[] | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  // الاختيار يبان فورًا — الأصل الجاي كـprop بيتأخر لحد ما التحميل يخلص
  const [linked, setLinked] = useState<string | null>(asset.feedSymbol ?? null)

  useEffect(() => {
    let alive = true
    user
      .loadPriceFeed()
      .then(({ feed }) => alive && setOptions(feed.prices))
      .catch((cause) => alive && setError(cause))
    return () => {
      alive = false
    }
  }, [user])

  async function pick(symbol: string | null) {
    setBusy(true)
    try {
      await user.manageAssets.linkToFeed(asset.id, symbol)
      setLinked(symbol)
      onChanged()
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  if (error) return <ErrorNotice cause={error} />

  if (!options) {
    return (
      <p className="sheet__hint" role="status">
        بنجيب قايمة الأسعار المتاحة…
      </p>
    )
  }

  return (
    <div className="feedLink">
      <span className="sheet__label">سعر تلقائي</span>
      <p className="sheet__hint">
        اختار الرمز اللي يخص الأصل ده، وبعدها زر «تحديث الأسعار» هيجيب سعره لوحده.
        أي حاجة برّه القايمة سعرها يدوي.
      </p>

      <div className="investKinds__row">
        <button
          type="button"
          className={`chip${!linked ? ' chip--on' : ''}`}
          onClick={() => pick(null)}
          disabled={busy}
          aria-pressed={!linked}
        >
          يدوي
        </button>
        {options.map((option) => (
          <button
            key={option.symbol}
            type="button"
            className={`chip${linked === option.symbol ? ' chip--on' : ''}`}
            onClick={() => pick(option.symbol)}
            disabled={busy}
            aria-pressed={linked === option.symbol}
          >
            {option.name}
            {/* السعر جنب الاسم عشان يتأكد إنه اختار الصح قبل ما يربط */}
            <span className="feedLink__price num">
              {formatAmount(option.pricePerUnitMinor)}
              {option.unit ? ` / ${option.unit}` : ''}
            </span>
          </button>
        ))}
      </div>
    </div>
  )
}
