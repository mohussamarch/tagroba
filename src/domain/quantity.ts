import { MoneyError, type Halalas } from './money'
import { normalizeDigits } from './money'

/**
 * الكميات والأسعار — `spec/02`:
 * «استخدم Long بوحدات العملة الصغرى لمبالغ SAR… **وتمثيلًا عشريًا دقيقًا
 *  للأسعار والكميات وأسعار الصرف**. التقريب عند حدود معلنة، لا بعد كل عملية جمع.»
 *
 * المبلغ يفضل عددًا صحيحًا بالهللة كما هو. الكمية تحتاج كسورًا (جرام ذهب،
 * جزء سهم، أجزاء عملة رقمية)، فتُخزَّن هي كمان **عددًا صحيحًا** بمقياسها الخاص:
 * كل وحدة = 100,000,000 جزء (ثمانية أرقام عشرية).
 *
 * ليه ثمانية بالذات؟ لأنها أدق تمثيل تحتاجه أصغر وحدة متداولة (الساتوشي).
 *
 * ── المشكلة اللي المقياس ده لوحده ما بيحلهاش ──
 * ضرب كمية في سعر ينتج رقمًا قد يتجاوز حد العدد الصحيح الآمن في جافاسكربت
 * (9,007,199,254,740,991). مثال حقيقي: بتكوين واحد بسعر مليون ونص ريال
 * ⇒ 100,000,000 × 150,000,000 = رقم أكبر من الحد ⇒ **حساب غلط بصمت**.
 *
 * لذلك خطوة الضرب وحدها تتم بـ`BigInt` (دقة لا نهائية)، والنتيجة تُقرَّب
 * **مرة واحدة** عند حد معلن وترجع هللات. لا `BigInt` في التخزين ولا في العرض.
 */

/** أجزاء الوحدة الواحدة. ثمانية أرقام عشرية. */
export const QUANTITY_SCALE = 100_000_000
export const QUANTITY_DECIMALS = 8

/** كمية بوحدات مقياسها — عدد صحيح دائمًا، لا كسر عشري أبدًا. */
export type Quantity = number

export class QuantityError extends Error {}

export function assertQuantity(value: unknown, context = 'كمية'): asserts value is Quantity {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new QuantityError(`${context}: القيمة مش رقم صالح`)
  }
  if (!Number.isInteger(value)) {
    throw new QuantityError(`${context}: الكمية لازم تكون عددًا صحيحًا بوحدات المقياس`)
  }
  if (!Number.isSafeInteger(value)) {
    throw new QuantityError(`${context}: الكمية أكبر من الحد الآمن للحساب`)
  }
}

/**
 * يقرأ كمية مكتوبة. يقبل الأرقام العربية والفواصل، ويرفض ما يزيد
 * عن ثمانية أرقام عشرية **بدل ما يقرّبه بصمت** — الرفض مع تفسير قاعدة المشروع.
 */
export function parseQuantity(raw: string): Quantity {
  if (typeof raw !== 'string') throw new QuantityError('الكمية لازم تكون نصًا')

  let text = normalizeDigits(raw).trim().replace(/[\s ,٬]/g, '')
  if (!text) throw new QuantityError('اكتب الكمية')

  let negative = false
  if (text.startsWith('-')) {
    negative = true
    text = text.slice(1)
  } else if (text.startsWith('+')) {
    text = text.slice(1)
  }

  const parts = text.split(/[.٫]/)
  if (parts.length > 2) throw new QuantityError('الكمية فيها أكتر من علامة عشرية')

  const intPart = parts[0] || '0'
  const fracRaw = parts[1] ?? ''

  if (!/^\d*$/.test(intPart) || !/^\d*$/.test(fracRaw)) {
    throw new QuantityError(`«${raw.trim()}» مش كمية صالحة`)
  }
  if (fracRaw.length > QUANTITY_DECIMALS) {
    throw new QuantityError(
      `الكمية بأكتر من ${QUANTITY_DECIMALS} أرقام عشرية. ` +
        `التطبيق مش هيقرّبها من ورا ظهرك — قصّرها بنفسك.`,
    )
  }

  const frac = fracRaw.padEnd(QUANTITY_DECIMALS, '0')
  const value = Number(intPart + frac)
  assertQuantity(value, `«${raw.trim()}»`)
  return negative ? -value : value
}

/** يعرض كمية بلا أصفار زائدة على اليمين. العرض فقط — لا يُعاد إدخاله في حساب. */
export function formatQuantity(quantity: Quantity): string {
  assertQuantity(quantity)
  const negative = quantity < 0
  const digits = String(Math.abs(quantity)).padStart(QUANTITY_DECIMALS + 1, '0')
  const intPart = digits.slice(0, digits.length - QUANTITY_DECIMALS)
  const frac = digits.slice(digits.length - QUANTITY_DECIMALS).replace(/0+$/, '')
  const body = frac ? `${intPart}.${frac}` : intPart
  return negative ? `-${body}` : body
}

export function addQuantity(...values: Quantity[]): Quantity {
  let total = 0
  for (const v of values) {
    assertQuantity(v)
    total += v
  }
  assertQuantity(total, 'مجموع الكميات')
  return total
}

export function subtractQuantity(a: Quantity, b: Quantity): Quantity {
  assertQuantity(a)
  assertQuantity(b)
  return a - b
}

/* ───────────────── حد التقريب المعلن ───────────────── */

/**
 * يقسم عددين صحيحين كبيرين ويقرّب **نصف لأعلى بعيدًا عن الصفر** مرة واحدة.
 * ده الحد الوحيد اللي بيحصل فيه تقريب في كل حسابات الاستثمار.
 */
function divideRounded(numerator: bigint, denominator: bigint): number {
  if (denominator === 0n) throw new QuantityError('قسمة على صفر')
  const negative = numerator < 0n !== denominator < 0n
  const n = numerator < 0n ? -numerator : numerator
  const d = denominator < 0n ? -denominator : denominator

  // نصف لأعلى: نضيف نصف المقام قبل القسمة الصحيحة
  const quotient = (n * 2n + d) / (d * 2n)
  const result = Number(negative ? -quotient : quotient)

  if (!Number.isSafeInteger(result)) {
    throw new QuantityError('النتيجة أكبر من الحد الآمن للحساب')
  }
  return result
}

/**
 * قيمة كمية بسعر الوحدة = الكمية × السعر ÷ المقياس.
 *
 * السعر بالهللة **لكل وحدة كاملة** (جرام، سهم، عملة). الضرب بـ`BigInt`
 * فلا تجاوز صامت، والتقريب مرة واحدة في الآخر لا بعد كل خطوة.
 */
export function valueOfQuantity(quantity: Quantity, pricePerUnitMinor: Halalas): Halalas {
  assertQuantity(quantity)
  if (!Number.isInteger(pricePerUnitMinor)) {
    throw new MoneyError('سعر الوحدة لازم يكون عددًا صحيحًا بالهللة')
  }
  return divideRounded(BigInt(quantity) * BigInt(pricePerUnitMinor), BigInt(QUANTITY_SCALE))
}

/**
 * حصة من مبلغ بنسبة كمية إلى كمية — أساس تكلفة الجزء المباع.
 *
 * `spec/06`: «بيع أصل جزئي ⇒ الكمية والتكلفة المتبقية والربح المحقق صحيحة».
 * المتبقي يُحسب بالطرح لا بقسمة تانية، فمجموع الأجزاء = الكل دائمًا
 * ولا يتسرب هلل واحد في التقريب.
 */
export function shareOfAmount(
  amountMinor: Halalas,
  partQuantity: Quantity,
  wholeQuantity: Quantity,
): Halalas {
  assertQuantity(partQuantity, 'الكمية الجزئية')
  assertQuantity(wholeQuantity, 'الكمية الكلية')
  if (wholeQuantity <= 0) throw new QuantityError('الكمية الكلية لازم تكون أكبر من صفر')
  if (partQuantity < 0) throw new QuantityError('الكمية الجزئية لا تكون سالبة')
  if (partQuantity > wholeQuantity) {
    throw new QuantityError(
      `الكمية الجزئية (${formatQuantity(partQuantity)}) أكبر من الكلية ` +
        `(${formatQuantity(wholeQuantity)})`,
    )
  }
  if (partQuantity === wholeQuantity) return amountMinor
  return divideRounded(BigInt(amountMinor) * BigInt(partQuantity), BigInt(wholeQuantity))
}

/**
 * سعر الوحدة المستنتج من مبلغ وكمية — للعرض والمقارنة فقط،
 * ولا يُستخدم كأساس تكلفة (الأساس هو المبلغ الأصلي لا سعر مُستنتج مقرَّب).
 */
export function unitPriceOf(amountMinor: Halalas, quantity: Quantity): Halalas | null {
  assertQuantity(quantity)
  if (quantity <= 0) return null
  return divideRounded(BigInt(amountMinor) * BigInt(QUANTITY_SCALE), BigInt(quantity))
}
