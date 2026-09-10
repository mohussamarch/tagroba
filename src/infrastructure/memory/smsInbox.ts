import type { SmsInboxPort, SmsInboxState, QueuedSms } from '../../application/ports/SmsInboxPort'
/** Synthetic inbox adapter for tests/demo; no device permission or bank data. */
export function memorySmsInbox(messages: QueuedSms[] = [], available = false): SmsInboxPort {
  let enabled = false, senders: string[] = [], queue = [...messages]
  const state = (): SmsInboxState => ({enabled, permission: enabled, more: false, count: queue.length,
    senders: [...senders], messages: queue.map(message => ({...message}))})
  return {available,
    sync: async () => state(),
    enable: async names => {enabled = true; senders = [...names]; return state()},
    disable: async () => {enabled = false; return state()},
    acknowledge: async ids => {queue = queue.filter(message => !ids.includes(message.id)); return state()},
  }
}
