import type { Currency, Halalas } from '../money'
import type { Quantity } from '../quantity'
import type { Id, IsoDate } from './types'

/**
 * كيانات الاستثمار — `spec/01`:
 * «الاستثمار: أصل يدوي، ذهب، سهم، صندوق، أصل رقمي، تكلفة ورسوم وكمية وتاريخ
 *  وسجل بيع جزئي؛ السعر المفقود/القديم واضح. **البيع تسجيل فقط.
 *  لا تداول أو تنفيذ أوامر.**»
 *
 * التطبيق يسجّل ما حصل بره. مفيش اتصال بسوق ولا أمر شراء ولا بيع.
 */

export type AssetKind = 'gold' | 'stock' | 'fund' | 'digital' | 'other'

export const ASSET_KIND_LABELS: Record<AssetKind, string> = {
  gold: 'ذهب',
  stock: 'سهم',
  fund: 'صندوق',
  digital: 'أصل رقمي',
  other: 'أصل آخر',
}

/** وحدة القياس الافتراضية لكل نوع — يقدر المستخدم يغيّرها. */
export const ASSET_UNIT_DEFAULTS: Record<AssetKind, string> = {
  gold: 'جرام',
  stock: 'سهم',
  fund: 'وحدة',
  digital: 'وحدة',
  other: 'وحدة',
}

export interface Asset {
  id: Id
  name: string
  kind: AssetKind
  /** «جرام»، «سهم»… يظهر جنب الكمية فلا تبقى رقمًا بلا وحدة. */
  unitLabel: string
  currency: Currency
  /** لا حذف لأصل له سجل — أرشفة فقط، مثل الأشخاص. */
  archived: boolean
  note?: string
}

/**
 * دفعة شراء. **الرسوم جزء من أساس التكلفة** — قرار معلن:
 * اللي دفعته فعلًا عشان تمتلك الأصل هو تكلفتك، فالربح المحقق
 * ما يظهرش أكبر من الحقيقة.
 */
export interface AssetLot {
  id: Id
  assetId: Id
  purchasedAt: IsoDate
  quantity: Quantity
  /** المدفوع للبائع قبل الرسوم. */
  principalMinor: Halalas
  feeMinor: Halalas
  /** العملية البنكية المقابلة، إن وُجدت. */
  transactionId?: Id
}

/**
 * بيع، كليًا أو جزئيًا. `spec/02`:
 * «شراء أو بيع أصل استثماري | حسب الحركة | **لا دخل معيشة من كامل الحصيلة**
 *  | التكلفة والربح في الاستثمار»
 *
 * يعني: حصيلة البيع **لا** تدخل دخل الفترة. الربح المحقق يظهر في
 * شاشة الاستثمار وحدها.
 */
export interface AssetSale {
  id: Id
  assetId: Id
  soldAt: IsoDate
  quantity: Quantity
  /** المقبوض من المشتري قبل خصم الرسوم. */
  grossProceedsMinor: Halalas
  feeMinor: Halalas
  transactionId?: Id
}

/**
 * سعر وحدة معروف بتاريخه. `spec/01`: «السعر المفقود/القديم واضح».
 * التاريخ جزء من الرقم لا زينة — بدونه ما ينفعش نقول إن السعر قديم.
 */
export interface AssetPrice {
  assetId: Id
  pricePerUnitMinor: Halalas
  asOf: IsoDate
  /** يدوي من المستخدم، أو من ملف الأسعار المحدَّث خارجيًا. */
  source: 'manual' | 'feed'
}
