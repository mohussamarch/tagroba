import type { RecurringItem } from '../../domain/entities/recurring'
import type { RecurringRepository } from '../../application/ports/RecurringRepository'
export class MemoryRecurringRepository implements RecurringRepository {
  private items = new Map<string, RecurringItem>()
  async listAll() { return [...this.items.values()].map(i=>({...i})) }
  async save(item: RecurringItem) { this.items.set(item.id,{...item}) }
}
