import type { NotificationReceipt } from '../../domain/notifications'
import type { NotificationReceiptRepository } from '../../application/ports/repositories'
import type { Snapshotable } from './memoryRepositories'

const clone = <T>(value: T): T => structuredClone(value)

/** إيصالات التنبيه في الذاكرة — للاختبار بلا فايربيز (CLAUDE.md #6). */
export class MemoryNotificationReceiptRepository
  implements NotificationReceiptRepository, Snapshotable<NotificationReceipt[]>
{
  private items = new Map<string, NotificationReceipt>()

  constructor(seed: readonly NotificationReceipt[] = []) {
    for (const r of seed) this.items.set(r.eventKey, clone(r))
  }

  async listAll(): Promise<NotificationReceipt[]> {
    return [...this.items.values()].map(clone)
  }

  async saveMany(receipts: readonly NotificationReceipt[]): Promise<void> {
    for (const r of receipts) this.items.set(r.eventKey, clone(r))
  }

  async deleteMany(eventKeys: readonly string[]): Promise<void> {
    for (const key of eventKeys) this.items.delete(key)
  }

  snapshot(): NotificationReceipt[] {
    return [...this.items.values()].map(clone)
  }

  restore(state: NotificationReceipt[]): void {
    this.items = new Map(state.map((r) => [r.eventKey, clone(r)]))
  }
}
