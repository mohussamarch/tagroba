import type { Settlement } from '../../domain/entities/types'
import type { SettlementCommand } from '../../domain/settlementCommand'

export interface SettlementWriter {
  /** Atomic read/check/write; same ID and payload is safe to retry after a lost response. */
  settle(input: SettlementCommand): Promise<Settlement>
}
