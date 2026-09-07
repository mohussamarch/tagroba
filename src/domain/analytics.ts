import { addMoney, rateOfMoney, type Halalas } from './money'
import { countsAsPersonalExpense } from './entities/economicKind'
import { personalShareOf } from './ledger'
import { daysBetween, remainingDaysInPeriod, type Period } from './period'
import type { Id, PersonAllocation, Transaction } from './entities/types'

/**
 * التحليل — spec/01 و spec/02.
 *
 * القاعدة الحاكمة في هذا الملف كله:
 * **«المتوسط والتوقع يعتمدان على البيانات المتاحة فقط مع ذكر قصورها.»**
 *
 * لذلك كل دالة هنا ترجّع `null` أو حالة صريحة حين لا يكفي الدليل،
 * ولا ترجّع صفرًا ولا تخمينًا. القيمة `null` تُعرض «غير متاح».
 */

/* ───────────────────────── توزيع التصنيفات ───────────────────────── */

export interface CategorySlice {
  categoryId: Id | null
  amountMinor: Halalas
  count: number
  /** النسبة من الإجمالي بالعُشر من المئة (855 = 85.5٪). عدد صحيح. */
  shareTenthPercent: number
}

/**
 * توزيع المصروف الشخصي على التصنيفات.
 *
 * spec/02: «الفرع يدخل في إجمالي الأصل **مرة واحدة**» و«لا تجمع إجمالي
 * الوسوم فوق إجمالي التصنيفات». هنا كل عملية تُحسب مرة واحدة بتصنيفها
 * النهائي، فالمجموع يساوي إجمالي المصروف بالضبط.
 *
 * العمليات بلا تصنيف تُجمع تحت `categoryId: null` وتُعرض كـ«بلا تصنيف»،
 * ولا تُلحق بتصنيف مخترع.
 */
export function categoryDistribution(
  transactions: readonly Transaction[],
  allocations: readonly PersonAllocation[] = [],
): { slices: CategorySlice[]; totalMinor: Halalas } {
  const byCategory = new Map<Id | null, { amount: Halalas; count: number }>()
  let total = 0

  for (const t of transactions) {
    if (!countsAsPersonalExpense(t.economicKind)) continue
    if (t.excludedFromBudget) continue

    const share = personalShareOf(t, allocations)
    if (share === 0) continue

    const key = t.categoryId ?? null
    const current = byCategory.get(key) ?? { amount: 0, count: 0 }
    byCategory.set(key, { amount: addMoney(current.amount, share), count: current.count + 1 })
    total = addMoney(total, share)
  }

  const slices: CategorySlice[] = [...byCategory.entries()]
    .map(([categoryId, v]) => ({
      categoryId,
      amountMinor: v.amount,
      count: v.count,
      shareTenthPercent: total === 0 ? 0 : rateOfMoney(v.amount, 1000, total),
    }))
    .sort((a, b) => b.amountMinor - a.amountMinor)

  return { slices, totalMinor: total }
}

/* ───────────────────────── المتاح اليومي ───────────────────────── */

export interface DailyAllowance {
  /** null = غير متاح (لا سقف محدد). لا يُخترع سقف من المتوسط. */
  amountMinor: Halalas | null
  remainingDays: number
  reason: string
}

/**
 * المتاح اليومي = max(0، المتبقي من الميزانية) ÷ الأيام المتبقية.
 *
 * spec/02: «بحسب نطاق الفترة المعلن. **لا تستخدم 11 يومًا أو 30 يومًا ثابتة**.»
 * spec/06: «تصنيف بلا سقف/تاريخ ⇒ لا سقف أو متوسط مخترع».
 */
export function dailyAllowance(
  budgetLimitMinor: Halalas | null,
  spentMinor: Halalas,
  today: string,
  period: Period,
): DailyAllowance {
  const remainingDays = remainingDaysInPeriod(today, period)

  if (budgetLimitMinor === null) {
    return {
      amountMinor: null,
      remainingDays,
      reason: 'مفيش سقف محدد للفترة دي. المتاح اليومي محتاج سقف تحدده انت.',
    }
  }

  if (remainingDays === 0) {
    return {
      amountMinor: null,
      remainingDays: 0,
      reason: 'الفترة دي خلصت، فمفيش أيام باقية يتوزع عليها.',
    }
  }

  const left = Math.max(0, budgetLimitMinor - spentMinor)
  return {
    amountMinor: Math.floor(left / remainingDays),
    remainingDays,
    reason:
      left === 0
        ? 'خلصت السقف بتاع الفترة دي.'
        : `المتبقي من السقف موزّع على ${remainingDays} يوم باقيين.`,
  }
}

/* ───────────────────────── التوقع ───────────────────────── */

/** أقل عدد أيام مضت قبل ما يكون للتوقع معنى. */
export const MIN_DAYS_FOR_FORECAST = 5

export interface Forecast {
  /** null = البيانات لا تكفي. لا يُعرض رقم مخمَّن. */
  projectedMinor: Halalas | null
  elapsedDays: number
  totalDays: number
  /** قصور التوقع مذكور دائمًا — spec/02. */
  caveat: string
}

/**
 * توقع مصروف نهاية الفترة من المعدل اليومي حتى الآن.
 *
 * spec/01: «توقع نهاية الفترة **بعد تاريخ كافٍ**».
 * قبل ذلك يُرجع null، لأن معدل يومين لا يتنبأ بشهر.
 */
export function forecastPeriodSpend(
  spentMinor: Halalas,
  today: string,
  period: Period,
): Forecast {
  const totalDays = period.days
  // الأيام المنقضية شاملةً اليوم الحالي
  const elapsedRaw = daysBetween(period.start, today) + 1
  const elapsedDays = Math.max(0, Math.min(elapsedRaw, totalDays))

  if (elapsedDays < MIN_DAYS_FOR_FORECAST) {
    return {
      projectedMinor: null,
      elapsedDays,
      totalDays,
      caveat: `عدى ${elapsedDays} يوم بس من الفترة. التوقع محتاج ${MIN_DAYS_FOR_FORECAST} أيام على الأقل عشان يبقى ليه معنى.`,
    }
  }

  if (elapsedDays >= totalDays) {
    return {
      projectedMinor: spentMinor,
      elapsedDays,
      totalDays,
      caveat: 'الفترة خلصت، فده المصروف الفعلي مش توقع.',
    }
  }

  // معدل يومي × إجمالي الأيام، بحساب صحيح وتقريب واحد معلن
  const projected = rateOfMoney(spentMinor, totalDays, elapsedDays)

  return {
    projectedMinor: projected,
    elapsedDays,
    totalDays,
    caveat:
      `محسوب من معدل ${elapsedDays} يوم عدوا، ومفروض إن باقي الفترة هيمشي بنفس المعدل. ` +
      `أي مصروف كبير غير متوقع هيغيّر الرقم ده.`,
  }
}

/* ───────────────────────── تغطية البيانات ───────────────────────── */

export interface DataCoverage {
  total: number
  /** عمليات لم يُحدَّد نوعها الاقتصادي بعد. */
  unclassified: number
  /** هل يمكن الاعتماد على مجاميع الفترة؟ */
  totalsReliable: boolean
  note: string | null
}

/**
 * يقيس كفاية البيانات قبل عرض أي رقم.
 *
 * spec/01: «لا مؤشر تغطية مبني على **افتراض** أن BARQ صرف غير مرصود» —
 * لذلك هذا المؤشر يقيس ما هو **معروف فعلًا** (كم عملية بلا نوع)،
 * ولا يفترض شيئًا عن الفلوس التي خرجت إلى محافظ خارجية.
 */
export function assessCoverage(transactions: readonly Transaction[]): DataCoverage {
  const total = transactions.length
  const unclassified = transactions.filter((t) => t.economicKind === 'unclassified').length

  if (total === 0) {
    return { total: 0, unclassified: 0, totalsReliable: true, note: null }
  }
  if (unclassified === total) {
    return {
      total,
      unclassified,
      totalsReliable: false,
      note: `كل الـ${total} عملية لسه محتاجة تحديد نوعها، فالمجاميع غير متاحة.`,
    }
  }
  if (unclassified > 0) {
    return {
      total,
      unclassified,
      totalsReliable: false,
      note: `${unclassified} عملية من ${total} لسه محتاجة تحديد نوعها، فالأرقام دي ناقصة.`,
    }
  }
  return { total, unclassified: 0, totalsReliable: true, note: null }
}
