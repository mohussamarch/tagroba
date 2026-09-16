import type { Category, ClassificationRule, Merchant } from '../../domain/entities/types'

export interface SeedSource {
  categories: readonly Category[]
  rules: readonly ClassificationRule[]
  merchants: readonly Merchant[]
}

/** Durable initialization state, separate from user-editable references. */
export interface ReferenceSeedPort {
  /** Existing accounts without a marker are adopted as complete, never repaired implicitly. */
  begin(hasExistingCategories: boolean): Promise<'pending' | 'complete'>
  /** Insert only absent IDs; implementations must recheck existence atomically. */
  insertMissing(source: SeedSource): Promise<void>
  complete(): Promise<void>
}
