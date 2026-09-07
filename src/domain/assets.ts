import { addMoney, subtractMoney, type Halalas } from './money'
import { formatMoney, NOT_AVAILABLE } from './formatMoney'
import {
  addQuantity,
  formatQuantity,
  shareOfAmount,
  subtractQuantity,
  valueOfQuantity,
  type Quantity,
} from './quantity'
import { daysBetween } from './period'
import type { AssetLot, AssetPrice, AssetSale } from './entities/assets'
import type { Id, IsoDate } from './entities/types'

/**
 * حساب المراكز الاستثمارية — دوال نقية.
 *
 * طريقة التكلفة: **المتوسط المرجَّح** (weighted average cost).
 * قرار معلن ومكتوب: البديل (FIFO) يحتاج المستخدم يفتكر أنهي دفعة باع منها،
 * وده سؤال ما يعرفش إجابته غالبًا. المتوسط لا يسأله سؤالًا لا يملك جوابه.
 *
 * `spec/06`: «بيع أصل جزئي | الكمية والتكلفة المتبقية والربح المحقق صحيحة؛
 *  **لا دخل من كامل الحصيلة**».
 */

export class AssetError extends Error {}

/** بعد كام يوم يبقى السعر «قديم»؟ حد معلن، لا تخمين. */
export const PRICE_STALE_AFTER_DAYS = 7

export type PriceState =
  | { kind: 'missing' }
  | { kind: 'fresh'; price: AssetPrice; ageDays: number }
  | { kind: 'stale'; price: AssetPrice; ageDays: number }

export interface AssetPosition {
  assetId: Id
  /** الكمية المتبقية بعد كل عمليات البيع. */
  heldQuantity: Quantity
  /** تكلفة المتبقي وحده — لا تكلفة ما بيع. */
  costBasisMinor: Halalas
  /** الربح (أو الخسارة) المحقق من كل عمليات البيع، بعد الرسوم. */
  realizedGainMinor: Halalas
  /** مجموع رسوم الشراء والبيع — يُعرض مستقلًا فلا يختفي في التكلفة. */
  totalFeesMinor: Halalas
  /** إجمالي ما قُبض من البيع. **ليس دخلًا** ولا يدخل مجاميع الفترة. */
  grossProceedsMinor: Halalas
  priceState: PriceState
  /** القيمة بسعر اليوم. `null` لو مفيش سعر — **لا صفر مكان المجهول**. */
  marketValueMinor: Halalas | null
  /** الربح غير المحقق = القيمة − التكلفة. `null` لو القيمة مجهولة. */
  unrealizedGainMinor: Halalas | null
}

/** حدث واحد في تاريخ الأصل، مرتَّب زمنيًا قبل الحساب. */
type Event =
  | { at: IsoDate; order: 0; lot: AssetLot }
  | { at: IsoDate; order: 1; sale: AssetSale }

function orderEvents(lots: readonly AssetLot[], sales: readonly AssetSale[]): Event[] {
  const events: Event[] = [
    ...lots.map((lot) => ({ at: lot.purchasedAt, order: 0 as const, lot })),
    ...sales.map((sale) => ({ at: sale.soldAt, order: 1 as const, sale })),
  ]
  // نفس اليوم: الشراء قبل البيع، وإلا بعنا ما لا نملك
  return events.sort((a, b) => (a.at === b.at ? a.order - b.order : a.at < b.at ? -1 : 1))
}

/**
 * يحسب مركز أصل من دفعاته ومبيعاته.
 *
 * التكلفة المتبقية تُحسب **بالطرح** بعد اقتطاع حصة المباع، فمجموع
 * (تكلفة المباع + تكلفة المتبقي) = التكلفة الكلية بالضبط، بلا تسرب تقريب.
 */
export function computePosition(
  assetId: Id,
  lots: readonly AssetLot[],
  sales: readonly AssetSale[] = [],
  price?: AssetPrice,
  today?: IsoDate,
): AssetPosition {
  let held: Quantity = 0
  let cost: Halalas = 0
  let realized: Halalas = 0
  let fees: Halalas = 0
  let proceeds: Halalas = 0

  for (const event of orderEvents(lots, sales)) {
    if (event.order === 0) {
      const { lot } = event
      if (lot.quantity <= 0) throw new AssetError('كمية الشراء لازم تكون أكبر من صفر')
      held = addQuantity(held, lot.quantity)
      // الرسوم داخل التكلفة — قرار معلن في `entities/assets.ts`
      cost = addMoney(cost, lot.principalMinor, lot.feeMinor)
      fees = addMoney(fees, lot.feeMinor)
      continue
    }

    const { sale } = event
    if (sale.quantity <= 0) throw new AssetError('كمية البيع لازم تكون أكبر من صفر')
    if (sale.quantity > held) {
      throw new AssetError(
        `بيع ${formatQuantity(sale.quantity)} والمملوك وقتها ` +
          `${formatQuantity(held)} فقط. مفيش رصيد سالب صامت.`,
      )
    }

    const costOfSold = shareOfAmount(cost, sale.quantity, held)
    const net = subtractMoney(sale.grossProceedsMinor, sale.feeMinor)

    realized = addMoney(realized, subtractMoney(net, costOfSold))
    cost = subtractMoney(cost, costOfSold)
    held = subtractQuantity(held, sale.quantity)
    fees = addMoney(fees, sale.feeMinor)
    proceeds = addMoney(proceeds, sale.grossProceedsMinor)
  }

  const priceState = assessPrice(price, today)
  // مفيش كمية ⇒ القيمة صفر معلوم لا مجهول. فيه كمية بلا سعر ⇒ مجهول لا صفر
  const marketValue: Halalas | null =
    held <= 0
      ? 0
      : priceState.kind === 'missing'
        ? null
        : valueOfQuantity(held, priceState.price.pricePerUnitMinor)

  return {
    assetId,
    heldQuantity: held,
    costBasisMinor: cost,
    realizedGainMinor: realized,
    totalFeesMinor: fees,
    grossProceedsMinor: proceeds,
    priceState,
    marketValueMinor: marketValue,
    unrealizedGainMinor: marketValue === null ? null : subtractMoney(marketValue, cost),
  }
}

/**
 * حالة السعر. `spec/01`: «السعر المفقود/القديم واضح».
 * القديم **يُعرض بتاريخه** ولا يُخفى ولا يُستبدل بتخمين.
 */
export function assessPrice(price?: AssetPrice, today?: IsoDate): PriceState {
  if (!price) return { kind: 'missing' }
  if (!today) return { kind: 'stale', price, ageDays: 0 }

  const ageDays = daysBetween(price.asOf, today)
  // سعر بتاريخ في المستقبل لا نصدّقه كطازج
  if (ageDays < 0) return { kind: 'stale', price, ageDays }
  return ageDays > PRICE_STALE_AFTER_DAYS
    ? { kind: 'stale', price, ageDays }
    : { kind: 'fresh', price, ageDays }
}

/** جملة حالة السعر بلغة المستخدم — لا رقم بلا مصدر. */
export function describePriceState(state: PriceState): string {
  if (state.kind === 'missing') return 'مفيش سعر متسجل. القيمة الحالية غير متاحة'
  const when = `بتاريخ ${state.price.asOf}`
  if (state.kind === 'fresh') return `السعر ${formatMoney(state.price.pricePerUnitMinor)} ${when}`
  if (state.ageDays < 0) return `السعر ${when} — تاريخ في المستقبل، راجعه`
  return `السعر ${formatMoney(state.price.pricePerUnitMinor)} ${when} — بقاله ${state.ageDays} يوم`
}

export interface PortfolioTotals {
  costBasisMinor: Halalas
  realizedGainMinor: Halalas
  /** `null` لو **أي** أصل مملوك بلا سعر — المجموع الناقص لا يُعرض كأنه كامل. */
  marketValueMinor: Halalas | null
  unrealizedGainMinor: Halalas | null
  /** عدد الأصول اللي منعت حساب الإجمالي، عشان الرسالة تقول السبب. */
  assetsWithoutPrice: number
}

/**
 * مجاميع المحفظة. لو أصل واحد مملوك بلا سعر، القيمة الإجمالية `null`
 * لأنها هتبقى ناقصة، والناقص المعروض كإجمالي كذب.
 */
export function computePortfolioTotals(positions: readonly AssetPosition[]): PortfolioTotals {
  let cost = 0
  let realized = 0
  let market = 0
  let missing = 0

  for (const p of positions) {
    cost = addMoney(cost, p.costBasisMinor)
    realized = addMoney(realized, p.realizedGainMinor)
    if (p.marketValueMinor === null) missing += 1
    else market = addMoney(market, p.marketValueMinor)
  }

  const complete = missing === 0
  return {
    costBasisMinor: cost,
    realizedGainMinor: realized,
    marketValueMinor: complete ? market : null,
    unrealizedGainMinor: complete ? subtractMoney(market, cost) : null,
    assetsWithoutPrice: missing,
  }
}

/** يعرض مبلغًا قد يكون مجهولًا — «غير متاح» لا صفر. */
export function formatAssetValue(value: Halalas | null): string {
  return value === null ? NOT_AVAILABLE : formatMoney(value)
}
