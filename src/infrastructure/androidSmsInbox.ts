import { Capacitor, registerPlugin } from '@capacitor/core'
import type { SmsInboxPort, SmsInboxState } from '../application/ports/SmsInboxPort'
type Native = {
  sync(input: {uid: string}): Promise<SmsInboxState>
  enable(input: {uid: string; senders: string[]}): Promise<SmsInboxState>
  disable(input: {uid: string}): Promise<SmsInboxState>
  acknowledge(input: {uid: string; ids: string[]}): Promise<SmsInboxState>
}
const native = registerPlugin<Native>('SmsInbox')
export function androidSmsInbox(uid: string): SmsInboxPort {
  return {
    available: Capacitor.getPlatform() === 'android',
    sync: () => native.sync({uid}),
    enable: senders => native.enable({uid, senders}),
    disable: () => native.disable({uid}),
    acknowledge: ids => native.acknowledge({uid, ids}),
  }
}
