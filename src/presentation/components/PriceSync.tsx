import { useState } from 'react'
import { describeFeed, type PriceFeed } from '../../domain/priceFeed'
import { ErrorNotice } from './ErrorNotice'
import type { SyncOutcome } from '../../application/useCases/syncAssetPrices'
import type { UserContainer } from '../../app/container'
import '../screens/InvestmentScreen.css'

/**
 * زر تحديث الأسعار ونتيجته.
 *
 * النتيجة بتقول كام اتحدّث وكام لأ **وليه** — «تم» لوحدها بتخفي
 * إن أصلًا ما اتحدّشش (spec/04: لا حالة بلا تفسير).
 */
export function PriceSync({ user, onChanged }: { user: UserContainer; onChanged: () => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [outcome, setOutcome] = useState<{ sync: SyncOutcome; feed: PriceFeed } | null>(null)

  async function run() {
    setError(null)
    setOutcome(null)
    setBusy(true)
    try {
      const { feed } = await user.loadPriceFeed()
      const sync = await user.syncAssetPrices(feed)
      setOutcome({ sync, feed })
      onChanged()
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="priceSync">
      <button type="button" className="btn btn--quiet" onClick={run} disabled={busy}>
        {busy ? 'بنجيب الأسعار…' : '↻ تحديث الأسعار'}
      </button>

      {error ? <ErrorNotice cause={error} /> : null}

      {outcome && (
        <div className="priceSync__result" role="status">
          <p className="priceSync__line">{describeFeed(outcome.feed)}</p>

          {outcome.sync.updated.length > 0 && (
            <p className="priceSync__line">
              اتحدّث {outcome.sync.updated.length}:{' '}
              {outcome.sync.updated.map((u) => u.assetName).join('، ')}
            </p>
          )}

          {outcome.sync.skipped.map((s) => (
            <p key={s.assetName} className="priceSync__line priceSync__line--warn">
              {s.assetName}: {s.reason}
            </p>
          ))}

          {outcome.sync.manualCount > 0 && (
            <p className="priceSync__line">
              {outcome.sync.manualCount} أصل سعره يدوي — اربطه بسعر تلقائي من جواه لو حابب.
            </p>
          )}

          {outcome.sync.updated.length === 0 && outcome.sync.skipped.length === 0 && (
            <p className="priceSync__line priceSync__line--warn">
              مفيش أصل مربوط بسعر تلقائي، فمفيش حاجة اتحدّثت.
            </p>
          )}
        </div>
      )}
    </div>
  )
}
