import { useState } from 'react'
import { formatAmount, NOT_AVAILABLE } from '../../domain/formatMoney'
import { formatQuantity } from '../../domain/quantity'
import { describePriceState } from '../../domain/assets'
import { ASSET_KIND_LABELS } from '../../domain/entities/assets'
import { ErrorNotice } from '../components/ErrorNotice'
import { AssetSheet } from './AssetSheet'
import { AddAssetSheet } from './AddAssetSheet'
import { PriceSync } from '../components/PriceSync'
import type { AssetRow, PortfolioView } from '../../application/useCases/manageAssets'
import type { UserContainer } from '../../app/container'
import './InvestmentScreen.css'

interface Props {
  user: UserContainer
  data: PortfolioView | null
  loading: boolean
  error: unknown
  amountsHidden: boolean
  onChanged: () => void
  onRetry: () => void
}

/**
 * شاشة الاستثمار — `spec/01`:
 * «أصل يدوي… تكلفة ورسوم وكمية وتاريخ وسجل بيع جزئي؛
 *  **السعر المفقود/القديم واضح. البيع تسجيل فقط. لا تداول أو تنفيذ أوامر.**»
 *
 * كل رقم هنا محسوب في `domain/assets.ts`. الشاشة تعرض ولا تحسب.
 */
export function InvestmentScreen({
  user,
  data,
  loading,
  error,
  amountsHidden,
  onChanged,
  onRetry,
}: Props) {
  const [addOpen, setAddOpen] = useState(false)
  const [openAsset, setOpenAsset] = useState<AssetRow | null>(null)

  const money = (value: number | null) =>
    value === null ? NOT_AVAILABLE : amountsHidden ? '••••' : formatAmount(value)

  if (loading && !data) {
    return (
      <p className="notice" role="status">
        بنحمّل الاستثمار…
      </p>
    )
  }

  if (error) return <ErrorNotice cause={error} onRetry={onRetry} />

  const rows = data?.rows ?? []
  const totals = data?.totals

  return (
    <div className="invest">
      <section className="card invest__totals">
        <h2 className="card__title">المحفظة</h2>
        <div className="invest__metrics">
          <Metric label="التكلفة" value={money(totals?.costBasisMinor ?? 0)} />
          <Metric label="القيمة الحالية" value={money(totals?.marketValueMinor ?? null)} />
          <Metric
            label="ربح غير محقق"
            value={money(totals?.unrealizedGainMinor ?? null)}
            tone={signOf(totals?.unrealizedGainMinor ?? null)}
          />
          <Metric
            label="ربح محقق"
            value={money(totals?.realizedGainMinor ?? 0)}
            tone={signOf(totals?.realizedGainMinor ?? 0)}
          />
        </div>

        {/* الناقص لا يُعرض كأنه كامل — السبب مكتوب لا مخفي */}
        {totals && totals.assetsWithoutPrice > 0 && (
          <p className="invest__warn" role="status">
            القيمة الحالية غير متاحة لأن {totals.assetsWithoutPrice} من الأصول مالهاش سعر
            متسجل. حط السعر من داخل الأصل عشان يظهر الإجمالي.
          </p>
        )}

        <PriceSync user={user} onChanged={onChanged} />

        {/* البيع تسجيل فقط — مكتوب صراحة فلا يتوقع المستخدم تداولًا */}
        <p className="invest__note">
          التطبيق بيسجّل بس. مفيش شراء ولا بيع ولا اتصال بأي سوق.
        </p>
      </section>

      <button type="button" className="btn" onClick={() => setAddOpen(true)}>
        ＋ أصل جديد
      </button>

      {rows.length === 0 ? (
        <div className="empty">
          <span className="empty__title">مفيش أصول لسه</span>
          <span>
            ضيف أصل — ذهب أو سهم أو صندوق — وبعدها سجّل الشراء بكميته وتكلفته وتاريخه.
          </span>
        </div>
      ) : (
        <ul className="invest__list">
          {rows.map((row) => (
            <li
              key={row.asset.id}
              className={`card invest__row${row.asset.archived ? ' invest__row--archived' : ''}`}
            >
              <button
                type="button"
                className="invest__open"
                onClick={() => setOpenAsset(row)}
                aria-label={`فتح ${row.asset.name}`}
              >
                <div className="invest__head">
                  <span className="invest__name">
                    {row.asset.name}
                    <span className="badge">{ASSET_KIND_LABELS[row.asset.kind]}</span>
                    {row.asset.archived && <span className="badge">مؤرشف</span>}
                  </span>
                  <span className="invest__value num">{money(row.position.marketValueMinor)}</span>
                </div>

                <div className="invest__facts">
                  <span>
                    {formatQuantity(row.position.heldQuantity)} {row.asset.unitLabel}
                  </span>
                  <span>تكلفتها {money(row.position.costBasisMinor)}</span>
                  {row.position.unrealizedGainMinor !== null && (
                    <span className={`invest__gain invest__gain--${signOf(row.position.unrealizedGainMinor)}`}>
                      {money(row.position.unrealizedGainMinor)}
                    </span>
                  )}
                </div>

                {/* حالة السعر مكتوبة دائمًا — لا رقم بلا مصدر */}
                <div
                  className={`invest__price invest__price--${row.position.priceState.kind}`}
                >
                  {describePriceState(row.position.priceState)}
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}

      {addOpen && (
        <AddAssetSheet
          user={user}
          onClose={() => setAddOpen(false)}
          onAdded={() => {
            setAddOpen(false)
            onChanged()
          }}
        />
      )}

      {openAsset && (
        <AssetSheet
          user={user}
          row={openAsset}
          onClose={() => setOpenAsset(null)}
          /* ربط السعر يحدّث البيانات بلا ما يقفل الورقة في وش المستخدم */
          onRefresh={onChanged}
          onChanged={() => {
            setOpenAsset(null)
            onChanged()
          }}
        />
      )}
    </div>
  )
}

function signOf(value: number | null): 'up' | 'down' | 'zero' {
  if (value === null || value === 0) return 'zero'
  return value > 0 ? 'up' : 'down'
}

function Metric({
  label,
  value,
  tone = 'zero',
}: {
  label: string
  value: string
  tone?: 'up' | 'down' | 'zero'
}) {
  return (
    <div className="investMetric">
      <span className="investMetric__label">{label}</span>
      <span className={`investMetric__value num investMetric__value--${tone}`}>{value}</span>
    </div>
  )
}
