import type { Currency } from '../money'
export interface RecurringItem {
  id: string
  name: string
  merchantKey: string
  kind: 'subscription' | 'bill'
  cycleMonths: 1 | 3 | 12
  expectedMinor: number
  currency: Currency
  nextDueAt: string
  active: boolean
  confirmed: boolean
}
export interface RecurringCandidate {
  name: string
  merchantKey: string
  currency: Currency
  expectedMinor: number
  nextDueAt: string
  cycleMonths: 1 | 3 | 12
  transactionIds: string[]
  reason: string
}
