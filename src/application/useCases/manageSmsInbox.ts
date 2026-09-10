import type { BankSmsParser } from '../ports/BankSmsPort'
import type { SmsInboxPort, SmsInboxState } from '../ports/SmsInboxPort'

export function makeManageSmsInbox(port: SmsInboxPort, parser: BankSmsParser) {
  function prepare(state: SmsInboxState) {
    return {...state, items: state.messages.map((message, index) => ({
      id: message.id, sender: message.sender, receivedAt: message.receivedAt,
      parsed: parser(message, index + 1),
    }))}
  }
  return {
    available: port.available,
    async refresh() { return prepare(await port.sync()) },
    async enable(senders: string[]) {
      const clean = [...new Set(senders.map(s => s.trim()).filter(Boolean))]
      if (!clean.length || clean.length > 10 || clean.some(s => s.length > 50)) throw Error('اكتب اسم مرسل البنك كما يظهر في الرسائل (حتى 10 أسماء)')
      await port.enable(clean)
      return prepare(await port.sync())
    },
    async disable() { return prepare(await port.disable()) },
    async dismiss(ids: string[]) { return prepare(await port.acknowledge(ids)) },
    /** Only confirmed lines are removed. Cancelled/unselected/unsupported items remain. */
    async imported(items: { id: string; lineNumber: number }[], confirmed: number[]) {
      const lines = new Set(confirmed)
      const ids = items.filter(item => lines.has(item.lineNumber)).map(item => item.id)
      if (ids.length) await port.acknowledge(ids)
    },
  }
}
