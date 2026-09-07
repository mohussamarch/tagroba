import {
  buildBudgetNotifications,
  filterUnseen,
  receiptFor,
  staleReceipts,
  type NotificationEvent,
} from '../../domain/notifications'
import type { BudgetScreenData } from './loadBudgetScreen'
import type { Clock, NotificationReceiptRepository } from '../ports/repositories'

/**
 * LoadNotifications — يبني التنبيهات غير المقروءة من حالة الميزانية.
 *
 * ⚠️ **مفيش دفع من السيرفر.** الإشعار اللي بيرن على الموبايل والتطبيق
 * مقفول محتاج Cloud Functions، وهي مش متاحة في باقة Spark
 * (CLAUDE.md #12). فدي تنبيهات بتظهر جوه التطبيق لما يفتحه، وبس.
 * القيد ده مكتوب للمستخدم في الشاشة، مش متهرب منه.
 *
 * المدخل حالة الميزانية المحسوبة أصلًا — الملف ده **ما بيحسبش** مصروفًا
 * ولا سقفًا من جديد، فمستحيل يعرض رقمًا مخالفًا لشاشة الميزانية.
 */

export interface NotificationsView {
  /** اللي المستخدم ماشافهوش. */
  unseen: NotificationEvent[]
  /** كل اللي متولد دلوقتي، مقروء وغير مقروء — لصفحة الإشعارات. */
  all: NotificationEvent[]
}

export interface LoadNotificationsDeps {
  receipts: NotificationReceiptRepository
  clock: Clock
}

export function makeLoadNotifications(deps: LoadNotificationsDeps) {
  /** يبني التنبيهات الحالية بلا ما يعلّم حاجة كمقروءة. */
  async function load(budget: BudgetScreenData): Promise<NotificationsView> {
    const nameOf = new Map(budget.categories.map((c) => [c.id, c.name]))
    const limitOf = new Map(budget.categoryBudgets.map((c) => [c.categoryId, c]))

    const all = buildBudgetNotifications({
      periodStart: budget.period.start,
      totalStatus: budget.totalStatus,
      totalThresholdPercent: budget.budget?.thresholdPercent ?? null,
      // التصنيف بلا سقف ما بيدخلش أصلًا — spec/06
      categories: budget.lines.flatMap((line) => {
        if (!line.status) return []
        // التنبيه مقفول لو المستخدم قافله لهذا التصنيف
        const limit = limitOf.get(line.categoryId)
        if (limit && !limit.notifyEnabled) return []
        return [
          {
            categoryId: line.categoryId,
            categoryName: nameOf.get(line.categoryId) ?? line.categoryId,
            status: line.status,
            thresholdPercent: limit?.thresholdPercent ?? null,
          },
        ]
      }),
      spentKnown: budget.spentKnown,
    })

    const receipts = await deps.receipts.listAll()
    return { all, unseen: filterUnseen(all, receipts) }
  }

  /**
   * يعلّم تنبيهات كمقروءة. الإيصال هو اللي بيمنع ظهورها تاني،
   * فالتعليم لازم يحصل **بعد** ما المستخدم يشوفها فعلًا لا قبلها.
   */
  async function markSeen(events: readonly NotificationEvent[]): Promise<void> {
    if (events.length === 0) return
    const now = deps.clock.nowIso()
    await deps.receipts.saveMany(events.map((event) => receiptFor(event, now)))
  }

  /**
   * ينضّف إيصالات الفترات اللي فاتت — بتمنع تكرارًا خلاص ما بقاش وارد.
   * التنضيف اختياري: فشله ما يمنعش التنبيهات من الشغل.
   */
  async function pruneOldReceipts(keepFromPeriodStart: string): Promise<number> {
    const receipts = await deps.receipts.listAll()
    const stale = staleReceipts(receipts, keepFromPeriodStart)
    if (stale.length === 0) return 0
    await deps.receipts.deleteMany(stale.map((r) => r.eventKey))
    return stale.length
  }

  return { load, markSeen, pruneOldReceipts }
}
