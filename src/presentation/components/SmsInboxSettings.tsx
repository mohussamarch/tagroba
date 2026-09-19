import { useState } from 'react'
import type { Wallet } from '../../domain/entities/types'

/**
 * إعداد القراءة التلقائية — ورا زرار الترس في شاشة رسايل البنك (OVERRIDES §36).
 * كان فوق القايمة ومفتوح، فكان بيزحم الشاشة ويخبي الرسايل.
 */
export function SmsInboxSettings({
  enabled, permission, senders, wallets, walletId, busy, onWallet, onEnable, onDisable,
}: {
  enabled: boolean
  permission: boolean
  senders: string[]
  wallets: readonly Wallet[]
  walletId: string
  busy: boolean
  onWallet: (id: string) => void
  onEnable: (senders: string[]) => void
  onDisable: () => void
}) {
  const [text, setText] = useState(senders.length ? senders.join(', ') : 'AlRajhiBank')
  return (
    <section className="smsSettings" aria-label="إعداد رسايل البنك">
      <p className="smsSettings__state">
        {enabled && permission ? 'القراءة التلقائية شغالة' : enabled ? 'إذن الرسايل مقفول من أندرويد — فعّله تاني' : 'القراءة التلقائية مقفولة'}
      </p>
      <label>
        العمليات بتتسجل على
        <select className="sheet__input" value={walletId} disabled={busy} onChange={(e) => onWallet(e.target.value)}>
          {wallets.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
        </select>
      </label>
      <label>
        اسم مرسل البنك (لو أكتر من واحد افصل بفاصلة)
        <input className="sheet__input" value={text} disabled={busy} onChange={(e) => setText(e.target.value)} />
      </label>
      <button type="button" className="btn" disabled={busy} onClick={() => onEnable(text.split(/[,،\n]/))}>
        {enabled && permission ? 'احفظ' : 'شغّل القراءة التلقائية'}
      </button>
      {enabled && <button type="button" className="btn btn--quiet" disabled={busy} onClick={onDisable}>اقفل القراءة التلقائية</button>}
      <p className="sheet__hint">الرسايل الأقدم تقدر تقراها من «إضافة ← رسائل البنك». القفل ما بيمسحش الرسايل المستنية.</p>
    </section>
  )
}
