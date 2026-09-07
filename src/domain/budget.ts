import { addMoney, rateOfMoney, subtractMoney, type Halalas } from './money'
import type { Id } from './entities/types'

/**
 * الميزانية والمتوسطات والشذوذ — spec/01 و spec/06.
 *
 * القاعدتان الحاكمتان في هذا الملف:
 *
 * ١. «**لا تُخلق ميزانيات من متوسطات دون اختيار المستخدم**» (spec/01).
 *    المتوسط معلومة تُعرض، لا سقف يُطبَّق. الاثنان لا يختلطان أبدًا.
 *
 * ٢. «تصنيف بلا سقف/تاريخ ⇒ **لا سقف أو متوسط مخترع ولا تنبيه عتبة**» (spec/06).
 *    فكل دالة هنا ترجّع `null` وسببًا حين لا يكفي الدليل.
 */

/* ───────────────────────── حالة السقف ───────────────────────── */

export type BudgetLevel = 'under' | 'near' | 'over'

export interface BudgetStatus {
  limitMinor: Halalas
  spentMinor: Halalas
  /** المتبقي؛ سالب حين يُتجاوز السقف — ولا يُخفى (spec/02). */
  remainingMinor: Halalas
  /** النسبة المستهلكة بالعُشر من المئة (855 = 85.5٪). عدد صحيح. */
  usedTenthPercent: number
  level: BudgetLevel
  /** هل تجاوزت عتبة التنبيه التي حددها المستخدم؟ */
  thresholdCrossed: boolean
}

/**
 * حالة سقف واحد.
 *
 * `thresholdPercent` عتبة **يحددها المستخدم**؛ بدونها لا تنبيه (spec/06).
 */
export function budgetStatus(
  limitMinor: Halalas,
  spentMinor: Halalas,
  thresholdPercent: number | null,
): BudgetStatus {
  if (limitMinor <= 0) throw new Error('السقف لازم يكون أكبر من صفر')

  const usedTenthPercent = rateOfMoney(spentMinor, 1000, limitMinor)
  const remainingMinor = subtractMoney(limitMinor, spentMinor)

  const level: BudgetLevel =
    usedTenthPercent >= 1000 ? 'over' : usedTenthPercent >= 900 ? 'near' : 'under'

  return {
    limitMinor,
    spentMinor,
    remainingMinor,
    usedTenthPercent,
    level,
    thresholdCrossed:
      thresholdPercent === null ? false : usedTenthPercent >= thresholdPercent * 10,
  }
}

/* ───────────────────────── المتوسط ───────────────────────── */

/** أقل عدد فترات مكتملة قبل أن يكون للمتوسط معنى. */
export const MIN_PERIODS_FOR_AVERAGE = 3

/** فترة مضت، بمصروفها ومدى موثوقيته. */
export interface CompletedPeriodSpend {
  periodKey: string
  spentMinor: Halalas
  /**
   * هل كل عمليات الفترة محددة النوع الاقتصادي؟
   * فترة نصف محددة **لا تدخل المتوسط**: مصروفها معروف جزئيًا،
   * وإدخاله يجعل المتوسط أقل من الحقيقة بلا أن يظهر ذلك.
   */
  reliable: boolean
  transactionCount: number
}

export interface AverageResult {
  /** null = البيانات لا تكفي. لا يُخترع متوسط. */
  averageMinor: Halalas | null
  /** الفترات التي دخلت الحساب فعلًا. */
  usedPeriods: string[]
  /** الفترات المستبعدة وسبب استبعادها — يُعرض للمستخدم لا يُخفى. */
  excluded: { periodKey: string; reason: string }[]
  reason: string
}

/**
 * متوسط المصروف من **الفترات المكتملة الموثوقة فقط**.
 *
 * spec/01: «متوسط منفصل من فترات مكتملة».
 * الفترة الجارية مستبعدة دائمًا: مصروفها ناقص بطبيعته، وإدخاله
 * يسحب المتوسط لأسفل كل يوم.
 */
export function averageCompletedSpend(
  periods: readonly CompletedPeriodSpend[],
): AverageResult {
  const excluded: { periodKey: string; reason: string }[] = []
  const used: CompletedPeriodSpend[] = []

  for (const p of periods) {
    if (!p.reliable) {
      excluded.push({
        periodKey: p.periodKey,
        reason: 'فيها عمليات لسه محتاجة تحديد نوعها، فمصروفها ناقص',
      })
      continue
    }
    if (p.transactionCount === 0) {
      excluded.push({
        periodKey: p.periodKey,
        reason: 'مفيش فيها عمليات — مش واضح ده صفر حقيقي ولا بيانات ناقصة',
      })
      continue
    }
    used.push(p)
  }

  if (used.length < MIN_PERIODS_FOR_AVERAGE) {
    return {
      averageMinor: null,
      usedPeriods: used.map((p) => p.periodKey),
      excluded,
      reason:
        `المتوسط محتاج ${MIN_PERIODS_FOR_AVERAGE} فترات مكتملة على الأقل، ` +
        `والمتاح ${used.length}.`,
    }
  }

  const total = addMoney(...used.map((p) => p.spentMinor))
  return {
    averageMinor: Math.round(total / used.length),
    usedPeriods: used.map((p) => p.periodKey),
    excluded,
    reason: `متوسط ${used.length} فترة مكتملة.`,
  }
}

/* ───────────────────────── الشذوذ ───────────────────────── */

/** أقل عدد قيم تاريخية قبل ادعاء شذوذ. */
export const MIN_HISTORY_FOR_ANOMALY = 3

/**
 * الحد الأدنى للانحراف النسبي قبل الوصف بالشذوذ: ٢٠٪.
 * يمنع وسم فرق تافه بأنه شذوذ لمجرد أن التاريخ متطابق.
 */
const MIN_RELATIVE_DEVIATION_PER_THOUSAND = 200

/** مضاعف الانحراف المطلق المتوسط الذي يُعد بعده الرقم شاذًا. */
const ROBUST_MULTIPLIER = 3

export interface AnomalyResult {
  /** null = لا يمكن الحكم. **ليست «لا يوجد شذوذ»**. */
  isAnomaly: boolean | null
  /** الوسيط التاريخي، للعرض والمقارنة. */
  medianMinor: Halalas | null
  /** الفرق عن الوسيط؛ موجب = أعلى. */
  deviationMinor: Halalas | null
  reason: string
}

function median(values: readonly number[]): number {
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1
    ? sorted[mid]
    : Math.round((sorted[mid - 1] + sorted[mid]) / 2)
}

/**
 * يكشف الشذوذ بمقارنة قيمة بالوسيط والانحراف المطلق الوسيط (MAD).
 *
 * لماذا الوسيط لا المتوسط: شهر واحد استثنائي (سفر مثلًا) يرفع المتوسط
 * فيخفي شذوذ الشهر التالي. الوسيط لا يتأثر بقيمة شاذة واحدة.
 *
 * **لا يُدّعى شذوذ بلا تاريخ كافٍ**، والنتيجة `null` لا `false`:
 * «ما نعرفش» غير «مفيش شذوذ» (spec/06: لا سقف ولا متوسط مخترع).
 */
export function detectAnomaly(
  valueMinor: Halalas,
  historyMinor: readonly Halalas[],
): AnomalyResult {
  if (historyMinor.length < MIN_HISTORY_FOR_ANOMALY) {
    return {
      isAnomaly: null,
      medianMinor: null,
      deviationMinor: null,
      reason:
        `الحكم على الشذوذ محتاج ${MIN_HISTORY_FOR_ANOMALY} فترات سابقة على الأقل، ` +
        `والمتاح ${historyMinor.length}.`,
    }
  }

  const med = median(historyMinor)
  const deviation = valueMinor - med
  const absDeviation = Math.abs(deviation)

  if (med === 0) {
    return {
      isAnomaly: null,
      medianMinor: 0,
      deviationMinor: deviation,
      reason: 'الوسيط التاريخي صفر، فالمقارنة النسبية مالهاش معنى.',
    }
  }

  // الانحراف لازم يكون كبيرًا نسبيًا **وأيضًا** شاذًا مقارنة بتقلب التاريخ
  const relativePerThousand = rateOfMoney(absDeviation, 1000, Math.abs(med))
  if (relativePerThousand < MIN_RELATIVE_DEVIATION_PER_THOUSAND) {
    return {
      isAnomaly: false,
      medianMinor: med,
      deviationMinor: deviation,
      reason: `الفرق عن المعتاد أقل من ٢٠٪، فمش شذوذ.`,
    }
  }

  const mad = median(historyMinor.map((v) => Math.abs(v - med)))

  // تاريخ ثابت تمامًا: التقلب صفر، فأي انحراف نسبي كبير يُعد شذوذًا
  const isAnomaly = mad === 0 ? true : absDeviation >= mad * ROBUST_MULTIPLIER

  const direction = deviation > 0 ? 'أعلى' : 'أقل'
  const percent = (relativePerThousand / 10).toFixed(0)

  return {
    isAnomaly,
    medianMinor: med,
    deviationMinor: deviation,
    reason: isAnomaly
      ? `${direction} من المعتاد بـ${percent}٪ — خارج تقلب الفترات السابقة.`
      : `${direction} من المعتاد بـ${percent}٪، لكن ده داخل تقلب الفترات السابقة العادي.`,
  }
}

/* ───────────────────────── ربط السقوف بالتصنيفات ───────────────────────── */

export interface CategoryBudgetLine {
  categoryId: Id
  /** null = المستخدم لم يضع سقفًا لهذا التصنيف. */
  status: BudgetStatus | null
  spentMinor: Halalas
  averageMinor: Halalas | null
  anomaly: AnomalyResult
  /** سبب غياب السقف — يُعرض ولا يُترك فراغًا. */
  noLimitReason: string | null
}

/**
 * يبني سطور شاشة الميزانية.
 *
 * التصنيف بلا سقف **يُعرض بمصروفه ومتوسطه**، لكن بلا حالة سقف
 * وبلا أي تنبيه عتبة — spec/06.
 */
export function buildCategoryLines(
  spendByCategory: ReadonlyMap<Id, Halalas>,
  limitByCategory: ReadonlyMap<Id, { limitMinor: Halalas; thresholdPercent: number | null }>,
  averageByCategory: ReadonlyMap<Id, Halalas | null>,
  historyByCategory: ReadonlyMap<Id, readonly Halalas[]>,
): CategoryBudgetLine[] {
  const ids = new Set<Id>([...spendByCategory.keys(), ...limitByCategory.keys()])

  return [...ids]
    .map((categoryId) => {
      const spentMinor = spendByCategory.get(categoryId) ?? 0
      const limit = limitByCategory.get(categoryId)
      return {
        categoryId,
        spentMinor,
        status: limit ? budgetStatus(limit.limitMinor, spentMinor, limit.thresholdPercent) : null,
        averageMinor: averageByCategory.get(categoryId) ?? null,
        anomaly: detectAnomaly(spentMinor, historyByCategory.get(categoryId) ?? []),
        noLimitReason: limit ? null : 'مفيش سقف للتصنيف ده. تقدر تحدده من هنا.',
      }
    })
    .sort((a, b) => b.spentMinor - a.spentMinor)
}
