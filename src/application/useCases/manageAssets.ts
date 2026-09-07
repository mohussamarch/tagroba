import { computePortfolioTotals, computePosition, type AssetPosition, type PortfolioTotals } from '../../domain/assets'
import { ASSET_UNIT_DEFAULTS, type Asset, type AssetKind, type AssetLot, type AssetPrice, type AssetSale } from '../../domain/entities/assets'
import { assertQuantity, type Quantity } from '../../domain/quantity'
import { isValidIsoDate } from '../../domain/period'
import { formatMoney } from '../../domain/formatMoney'
import type { Currency, Halalas } from '../../domain/money'
import type { Id, IsoDate } from '../../domain/entities/types'
import type {
  AssetLotRepository,
  AssetPriceRepository,
  AssetRepository,
  AssetSaleRepository,
  Clock,
  IdGenerator,
} from '../ports/repositories'

/**
 * ManageAssets — الاستثمار.
 *
 * `spec/01`: «**البيع تسجيل فقط. لا تداول أو تنفيذ أوامر.**»
 * الحساب كله في `domain/assets.ts` ومختبَر؛ الملف ده يربطه بالتخزين
 * ويتحقق من المدخلات، ولا يعيد حسابًا.
 */

export class AssetsError extends Error {}

export interface AssetRow {
  asset: Asset
  position: AssetPosition
  lots: AssetLot[]
  sales: AssetSale[]
}

export interface PortfolioView {
  rows: AssetRow[]
  totals: PortfolioTotals
}

export interface ManageAssetsDeps {
  assets: AssetRepository
  lots: AssetLotRepository
  sales: AssetSaleRepository
  prices: AssetPriceRepository
  ids: IdGenerator
  clock: Clock
}

const MAX_NAME = 80

function requireDate(value: string, label: string): IsoDate {
  if (!isValidIsoDate(value)) throw new AssetsError(`${label} مش تاريخ صالح`)
  return value
}

function requireAmount(value: Halalas, label: string, allowZero = false): Halalas {
  if (!Number.isInteger(value)) throw new AssetsError(`${label} لازم يكون رقمًا صحيحًا`)
  if (value < 0) throw new AssetsError(`${label} لا يكون سالبًا`)
  if (!allowZero && value === 0) throw new AssetsError(`${label} لازم يكون أكبر من صفر`)
  return value
}

function requireQuantity(value: Quantity): Quantity {
  assertQuantity(value)
  if (value <= 0) throw new AssetsError('الكمية لازم تكون أكبر من صفر')
  return value
}

export function makeManageAssets(deps: ManageAssetsDeps) {
  /**
   * كل الأصول بمراكزها. القراءة ثلاث مجموعات كاملة والتجميع في الذاكرة —
   * لا فهرس مركّب ولا استعلام لكل أصل على حدة.
   */
  async function listPortfolio(today?: IsoDate): Promise<PortfolioView> {
    const [assets, allLots, allSales, allPrices] = await Promise.all([
      deps.assets.listAll(),
      deps.lots.listAll(),
      deps.sales.listAll(),
      deps.prices.listAll(),
    ])

    const priceOf = new Map(allPrices.map((p) => [p.assetId, p]))
    const asOf = today ?? deps.clock.nowIso().slice(0, 10)

    const rows: AssetRow[] = assets.map((asset) => {
      const lots = allLots.filter((l) => l.assetId === asset.id)
      const sales = allSales.filter((s) => s.assetId === asset.id)
      return {
        asset,
        lots,
        sales,
        position: computePosition(asset.id, lots, sales, priceOf.get(asset.id), asOf),
      }
    })

    // المملوك أولًا، والمؤرشف أخيرًا
    rows.sort((a, b) => {
      if (a.asset.archived !== b.asset.archived) return a.asset.archived ? 1 : -1
      return b.position.costBasisMinor - a.position.costBasisMinor
    })

    return { rows, totals: computePortfolioTotals(rows.map((r) => r.position)) }
  }

  async function addAsset(input: {
    name: string
    kind: AssetKind
    unitLabel?: string
    feedSymbol?: string
    currency?: Currency
    note?: string
  }): Promise<Asset> {
    const name = input.name.trim()
    if (!name) throw new AssetsError('اكتب اسم الأصل')
    if (name.length > MAX_NAME) throw new AssetsError(`الاسم أطول من ${MAX_NAME} حرف`)

    const existing = await deps.assets.listAll()
    if (existing.some((a) => a.name.trim() === name)) {
      throw new AssetsError(`فيه أصل اسمه «${name}» موجود قبل كده`)
    }

    const asset: Asset = {
      id: deps.ids.next('asset'),
      name,
      kind: input.kind,
      unitLabel: input.unitLabel?.trim() || ASSET_UNIT_DEFAULTS[input.kind],
      currency: input.currency ?? 'SAR',
      archived: false,
      ...(input.feedSymbol ? { feedSymbol: input.feedSymbol } : {}),
      ...(input.note?.trim() ? { note: input.note.trim() } : {}),
    }
    await deps.assets.save(asset)
    return asset
  }

  /**
   * يربط أصلًا برمز في ملف الأسعار، أو يفكّ الربط.
   * فكّ الربط **ما بيمسحش** آخر سعر — الرقم القديم بتاريخه أنفع من فراغ.
   */
  async function linkToFeed(assetId: Id, feedSymbol: string | null): Promise<Asset> {
    const asset = (await deps.assets.listAll()).find((a) => a.id === assetId)
    if (!asset) throw new AssetsError('الأصل ده مش موجود')
    const next: Asset = { ...asset }
    if (feedSymbol) next.feedSymbol = feedSymbol
    else delete next.feedSymbol
    await deps.assets.save(next)
    return next
  }

  /** أرشفة لا حذف — الأصل ذو السجل يفضل ظاهرًا (نفس قاعدة الأشخاص). */
  async function archiveAsset(assetId: Id, archived: boolean): Promise<void> {
    const asset = (await deps.assets.listAll()).find((a) => a.id === assetId)
    if (!asset) throw new AssetsError('الأصل ده مش موجود')
    await deps.assets.save({ ...asset, archived })
  }

  /** يسجّل شراء. الرسوم تنضم للتكلفة — قرار معلن في `entities/assets.ts`. */
  async function recordPurchase(input: {
    assetId: Id
    purchasedAt: IsoDate
    quantity: Quantity
    principalMinor: Halalas
    feeMinor?: Halalas
    transactionId?: Id
  }): Promise<AssetLot> {
    const asset = (await deps.assets.listAll()).find((a) => a.id === input.assetId)
    if (!asset) throw new AssetsError('الأصل ده مش موجود')

    const lot: AssetLot = {
      id: deps.ids.next('lot'),
      assetId: asset.id,
      purchasedAt: requireDate(input.purchasedAt, 'تاريخ الشراء'),
      quantity: requireQuantity(input.quantity),
      principalMinor: requireAmount(input.principalMinor, 'قيمة الشراء'),
      feeMinor: requireAmount(input.feeMinor ?? 0, 'الرسوم', true),
      ...(input.transactionId ? { transactionId: input.transactionId } : {}),
    }
    await deps.lots.saveMany([lot])
    return lot
  }

  /**
   * يسجّل بيعًا، كليًا أو جزئيًا.
   *
   * `spec/02`: «**لا دخل معيشة من كامل الحصيلة**» — الحصيلة تتسجل هنا
   * ولا تُكتب كعملية دخل. لو دخلت الحساب فعلًا، تُسجَّل عملية منفصلة
   * نوعها الاقتصادي «تحويل داخلي» لا «دخل».
   *
   * التحقق من الكمية يمر على `computePosition` نفسه، فقاعدة «لا بيع
   * لأكتر من المملوك» متكتبة مرة واحدة ومختبَرة مرة واحدة.
   */
  async function recordSale(input: {
    assetId: Id
    soldAt: IsoDate
    quantity: Quantity
    grossProceedsMinor: Halalas
    feeMinor?: Halalas
    transactionId?: Id
  }): Promise<{ sale: AssetSale; position: AssetPosition }> {
    const asset = (await deps.assets.listAll()).find((a) => a.id === input.assetId)
    if (!asset) throw new AssetsError('الأصل ده مش موجود')

    const sale: AssetSale = {
      id: deps.ids.next('sale'),
      assetId: asset.id,
      soldAt: requireDate(input.soldAt, 'تاريخ البيع'),
      quantity: requireQuantity(input.quantity),
      grossProceedsMinor: requireAmount(input.grossProceedsMinor, 'حصيلة البيع'),
      feeMinor: requireAmount(input.feeMinor ?? 0, 'الرسوم', true),
      ...(input.transactionId ? { transactionId: input.transactionId } : {}),
    }
    if (sale.feeMinor > sale.grossProceedsMinor) {
      throw new AssetsError(
        `الرسوم (${formatMoney(sale.feeMinor)}) أكبر من الحصيلة ` +
          `(${formatMoney(sale.grossProceedsMinor)})`,
      )
    }

    const [lots, sales] = await Promise.all([
      deps.lots.listByAsset(asset.id),
      deps.sales.listByAsset(asset.id),
    ])

    // يرمي لو الكمية أكبر من المملوك أو البيع قبل الشراء — قبل أي كتابة
    const position = computePosition(asset.id, lots, [...sales, sale])

    await deps.sales.saveMany([sale])
    return { sale, position }
  }

  /** سعر يدوي بتاريخه. `spec/01`: «السعر المفقود/القديم واضح». */
  async function setPrice(input: {
    assetId: Id
    pricePerUnitMinor: Halalas
    asOf?: IsoDate
  }): Promise<AssetPrice> {
    const asset = (await deps.assets.listAll()).find((a) => a.id === input.assetId)
    if (!asset) throw new AssetsError('الأصل ده مش موجود')

    const price: AssetPrice = {
      assetId: asset.id,
      pricePerUnitMinor: requireAmount(input.pricePerUnitMinor, 'السعر'),
      asOf: requireDate(input.asOf ?? deps.clock.nowIso().slice(0, 10), 'تاريخ السعر'),
      source: 'manual',
    }
    await deps.prices.save(price)
    return price
  }

  return {
    listPortfolio,
    addAsset,
    archiveAsset,
    linkToFeed,
    recordPurchase,
    recordSale,
    setPrice,
  }
}
