import { formatMoney } from './formatMoney'
import type { BudgetStatus } from './budget'
import type { Id, IsoDate } from './entities/types'

/**
 * الإشعارات — دوال نقية تبني التنبيهات ولا ترسل حاجة.
 *
 * ⚠️ **حد تقني حقيقي، مش اختصار:** الدفع من السيرفر (server push)
 * مستحيل هنا. الإشعار اللي بيوصل الموبايل والتطبيق مقفول محتاج
 * Cloud Functions، وهي مش متاحة في باقة Spark (CLAUDE.md #12).
 * فاللي بيتبنى هنا **تنبيهات داخل التطبيق** بتظهر لما يفتحه.
 * ده مكتوب في الشاشة نفسها كمان، عشان محدش يستنى تنبيه مش هييجي.
 *
 * القاعدة الحاكمة (`spec/06`): «تصنيف بلا سقف/تاريخ ⇒ **لا سقف أو
 * متوسط مخترع ولا تنبيه عتبة**». يعني التنبيه ما بيتولدش إلا من رقم
 * حطه المستخدم بنفسه.
 */

export type NotificationKind = 'budget_total' | 'budget_category' | 'recurring_due'

export type NotificationSeverity = 'info' | 'warn' | 'over'

export interface NotificationEvent {
  /**
   * مفتاح الحدث — بيمنع تكرار نفس التنبيه.
   *
   * بيضم الفترة والهدف والعتبة، فعبور ٨٠٪ بينبّه مرة، وعبور ١٠٠٪
   * بعدها بينبّه مرة تانية لأنه **حدث تاني** مش تكرار للأول.
   */
  eventKey: string
  kind: NotificationKind
  severity: NotificationSeverity
  title: string
  body: string
  periodStart: IsoDate
  categoryId?: Id
  recurringId?: Id
  /** العتبة اللي اتعدّت، بالمئة. */
  threshold?: number
}

/**
 * إيصال تنبيه اتبعت — `spec/03`:
 * «NotificationReceipt: eventKey، threshold، category/recurringId،
 *  periodStart، sentAt؛ **لمنع تكرار التنبيه**»
 */
export interface NotificationReceipt {
  eventKey: string
  threshold: number | null
  categoryId?: Id
  recurringId?: Id
  periodStart: IsoDate
  sentAt: string
}

/** العتبات المعلنة. عتبة المستخدم بتتضاف لهم لو مختلفة. */
const HARD_THRESHOLDS = [100] as const

function severityFor(threshold: number): NotificationSeverity {
  if (threshold >= 100) return 'over'
  return threshold >= 90 ? 'warn' : 'info'
}

/**
 * العتبات اللي عدّاها سقف واحد فعلًا، مرتبة.
 *
 * بترجّع **كل** عتبة اتعدّت لا الأعلى بس، عشان لو المستخدم مافتحش
 * التطبيق أيام ما يضيعش تنبيه ٨٠٪ لمجرد إنه بقى فوق ١٠٠٪.
 */
function crossedThresholds(status: BudgetStatus, userThreshold: number | null): number[] {
  const candidates = new Set<number>(HARD_THRESHOLDS)
  // عتبة المستخدم هي الوحيدة اللي هو طلبها — بتتحط جنب عتبة التجاوز
  if (userThreshold !== null && userThreshold > 0) candidates.add(userThreshold)

  return [...candidates]
    .filter((threshold) => status.usedTenthPercent >= threshold * 10)
    .sort((a, b) => a - b)
}

export interface BudgetNotificationInput {
  periodStart: IsoDate
  /** حالة السقف الإجمالي، أو null لو مفيش سقف إجمالي. */
  totalStatus: BudgetStatus | null
  /** العتبة اللي حددها المستخدم للسقف الإجمالي. */
  totalThresholdPercent: number | null
  /**
   * التصنيفات اللي **ليها سقف**. اللي بلا سقف ما بيتبعتش أصلًا،
   * فمفيش احتمال تنبيه عتبة على تصنيف بلا سقف.
   */
  categories: readonly {
    categoryId: Id
    categoryName: string
    status: BudgetStatus
    thresholdPercent: number | null
  }[]
  /**
   * هل المصروف معروف أصلًا؟ `false` لما تكون فيه عمليات بلا نوع
   * اقتصادي محدد — وقتها الرقم ناقص، والتنبيه عليه تخويف بلا أساس.
   */
  spentKnown: boolean
}

/** يبني تنبيهات الميزانية. بيحسب مش بيقرر إمتى تتعرض. */
export function buildBudgetNotifications(
  input: BudgetNotificationInput,
): NotificationEvent[] {
  // رقم ناقص ما ينفعش يتبنى عليه تنبيه (CLAUDE.md #10)
  if (!input.spentKnown) return []

  const events: NotificationEvent[] = []

  if (input.totalStatus) {
    for (const threshold of crossedThresholds(input.totalStatus, input.totalThresholdPercent)) {
      events.push({
        eventKey: `${input.periodStart}|total|${threshold}`,
        kind: 'budget_total',
        severity: severityFor(threshold),
        threshold,
        periodStart: input.periodStart,
        title: threshold >= 100 ? 'عدّيت الميزانية' : `وصلت ${threshold}٪ من الميزانية`,
        body: describeStatus(input.totalStatus),
      })
    }
  }

  for (const category of input.categories) {
    for (const threshold of crossedThresholds(category.status, category.thresholdPercent)) {
      events.push({
        eventKey: `${input.periodStart}|cat:${category.categoryId}|${threshold}`,
        kind: 'budget_category',
        severity: severityFor(threshold),
        threshold,
        categoryId: category.categoryId,
        periodStart: input.periodStart,
        title:
          threshold >= 100
            ? `عدّيت سقف «${category.categoryName}»`
            : `«${category.categoryName}» وصل ${threshold}٪`,
        body: describeStatus(category.status),
      })
    }
  }

  // الأخطر الأول، ثم الأعلى عتبة
  return events.sort((a, b) => (b.threshold ?? 0) - (a.threshold ?? 0))
}

/** جملة الحالة بالريال — الهللة وحدة داخلية لا تظهر للمستخدم أبدًا. */
function describeStatus(status: BudgetStatus): string {
  const spent = formatMoney(status.spentMinor)
  const limit = formatMoney(status.limitMinor)
  if (status.remainingMinor < 0) {
    return `صرفت ${spent} من ${limit} — زيادة ${formatMoney(-status.remainingMinor)}`
  }
  return `صرفت ${spent} من ${limit} — فاضل ${formatMoney(status.remainingMinor)}`
}

/**
 * يشيل اللي اتنبّه عليه قبل كده.
 *
 * ده الغرض الوحيد من `NotificationReceipt`: التنبيه يظهر **مرة واحدة**
 * لكل حدث، فما يتحوّلش لضوضاء يتجاهلها المستخدم.
 */
export function filterUnseen(
  events: readonly NotificationEvent[],
  receipts: readonly NotificationReceipt[],
): NotificationEvent[] {
  const seen = new Set(receipts.map((r) => r.eventKey))
  return events.filter((event) => !seen.has(event.eventKey))
}

/** يبني إيصالًا من حدث — نفس المفتاح، فالمنع مضمون. */
export function receiptFor(event: NotificationEvent, sentAt: string): NotificationReceipt {
  return {
    eventKey: event.eventKey,
    threshold: event.threshold ?? null,
    ...(event.categoryId ? { categoryId: event.categoryId } : {}),
    ...(event.recurringId ? { recurringId: event.recurringId } : {}),
    periodStart: event.periodStart,
    sentAt,
  }
}

/**
 * إيصالات الفترات القديمة اللي ما بقاش ليها لازمة.
 * الإيصال بيمنع التكرار داخل فترته؛ بعد ما تعدّي بقى حِمل بلا فايدة.
 */
export function staleReceipts(
  receipts: readonly NotificationReceipt[],
  keepFromPeriodStart: IsoDate,
): NotificationReceipt[] {
  return receipts.filter((r) => r.periodStart < keepFromPeriodStart)
}

