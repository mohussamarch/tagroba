import type { RecurringItem } from '../../domain/entities/recurring'
export interface RecurringRepository {
  listAll(): Promise<RecurringItem[]>
  save(item: RecurringItem): Promise<void>
}
