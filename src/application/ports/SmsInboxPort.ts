import type { BankSmsMessage } from './BankSmsPort'
export interface QueuedSms extends BankSmsMessage { id: string }
export interface SmsInboxState {
  enabled: boolean; permission: boolean; more: boolean; count: number
  senders: string[]; messages: QueuedSms[]
}
export interface SmsInboxPort {
  readonly available: boolean
  sync(): Promise<SmsInboxState>
  enable(senders: string[]): Promise<SmsInboxState>
  disable(): Promise<SmsInboxState>
  acknowledge(ids: string[]): Promise<SmsInboxState>
}
