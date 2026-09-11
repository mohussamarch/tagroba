import { useState } from 'react'
import type { UserContainer } from '../../app/container'
import type { ImportBatch } from '../../domain/entities/types'
import type { RevertDecision } from '../../application/useCases/revertImportBatch'
import { REVERT_BLOCKED_MESSAGE } from '../../application/useCases/revertImportBatch'

type Plan = Awaited<ReturnType<UserContainer['revertImportBatch']['plan']>>

const SOURCE_LABEL: Record<ImportBatch['sourceType'], string> = {
  csv_preview: 'ملف CSV', csv_legacy: 'ملف CSV قديم', pdf_alrajhi: 'PDF الراجحي', sms: 'رسائل البنك',
}
const STATE_LABEL: Record<ImportBatch['state'], string> = {
  committed: 'مسجّل', staged: 'لم يكتمل', reverted: 'متراجَع عنه',
}
const KEEP_LABEL: Record<Exclude<RevertDecision, 'deleted'>, string> = {
  kept_other_source: 'ليها مصدر تاني', kept_has_settlement: 'عليها تسوية', kept_has_allocation: 'متربطة بشخص',
}
const when = (iso: string) => iso.slice(0, 16).replace('T', ' ')
const text = (error: unknown) => (error instanceof Error ? error.message : String(error))

/**
 * سجل الاستيرادات والتراجع — HANDOVER §30 (بند 1-ب).
 * المعاينة قراءة فقط؛ الحذف بعد زر تأكيد يذكر العدد. الإلغاء لا يُعطَّل أبدًا،
 * و`busy` يُصفَّر في finally (HANDOVER §9.3). لا مبالغ هنا — أعداد فقط.
 */
export function ImportHistoryPanel({ user, onDone }: { user: UserContainer; onDone: () => void }) {
  const [batches, setBatches] = useState<ImportBatch[] | null>(null)
  const [pending, setPending] = useState<{ batch: ImportBatch; plan: Plan } | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  async function load() {
    setBusy(true); setMessage('')
    try { setBatches(await user.revertImportBatch.history()) }
    catch (error) { setMessage('تعذر تحميل السجل: ' + text(error)) }
    finally { setBusy(false) }
  }

  async function preview(batch: ImportBatch) {
    setBusy(true); setMessage(''); setPending(null)
    try { setPending({ batch, plan: await user.revertImportBatch.plan(batch.id) }) }
    catch (error) { setMessage(text(error)) }
    finally { setBusy(false) }
  }

  async function confirm() {
    if (!pending) return
    setBusy(true); setMessage('')
    try {
      const done = await user.revertImportBatch.execute(pending.batch.id)
      setMessage(`اتشال ${done.toDelete.length} عملية` + (done.toKeep.length ? ` وفضل ${done.toKeep.length} ليها سند تاني.` : '.'))
      setPending(null)
      onDone()
      setBatches(await user.revertImportBatch.history())
    } catch (error) {
      setMessage('التراجع ما كملش: ' + text(error))
    } finally { setBusy(false) }
  }

  const plan = pending?.plan
  const kept = plan ? Object.entries(
    plan.toKeep.reduce<Record<string, number>>((acc, o) => ({ ...acc, [o.decision]: (acc[o.decision] ?? 0) + 1 }), {}),
  ) : []

  return (
    <section className="sheet__field" aria-label="سجل الاستيرادات">
      <p className="settings__hint">
        كل كشف أو رسايل استوردتها. لو كشف اتستورد مرتين تقدر تتراجع عن نسخة منهم. قبل أي حذف هتشوف هيتشال كام وإيه اللي هيفضل.
      </p>
      <button type="button" className="btn btn--quiet" onClick={() => void load()} disabled={busy}>
        {busy && !batches ? 'بنحمّل…' : 'سجل الاستيرادات'}
      </button>
      {message && <p className="notice" role="status">{message}</p>}
      {batches?.length === 0 && <p className="notice">مفيش استيرادات مسجلة.</p>}
      {batches && batches.length > 0 && (
        <ul className="settings__wallets">
          {batches.map((batch) => (
            <li key={batch.id} className="settings__wallet">
              <div className="settings__walletHead">
                <span className="settings__walletName">{batch.fileName}</span>
                <span>{STATE_LABEL[batch.state]}</span>
              </div>
              <span className="settings__hint">
                {when(batch.importedAt)} · {SOURCE_LABEL[batch.sourceType]} · {batch.counts.imported} عملية
              </span>
              {batch.state === 'committed' && (
                <button type="button" className="link" onClick={() => void preview(batch)} disabled={busy}>
                  تراجع عن الاستيراد ده…
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {pending && plan && (
        <div className="notice" role="alert">
          <strong>معاينة التراجع عن «{pending.batch.fileName}» ({when(pending.batch.importedAt)}) — لسه ما اتحذفش حاجة</strong>
          {plan.blocked ? <p>{REVERT_BLOCKED_MESSAGE}</p> : <>
            <p>هيتشال {plan.toDelete.length} عملية من {plan.expectedCount}.</p>
            {kept.map(([decision, count]) => (
              <p key={decision}>هيفضل {count}: {KEEP_LABEL[decision as keyof typeof KEEP_LABEL]}.</p>
            ))}
            <p>العمليات اللي هتتشال مش هترجع إلا لو استوردت الكشف تاني.</p>
          </>}
          <div className="settings__row">
            <button type="button" className="btn" onClick={() => void confirm()}
              disabled={busy || plan.blocked || plan.toDelete.length === 0}>
              {busy ? 'بنتراجع…' : `تراجع عن ${plan.toDelete.length} عملية`}
            </button>
            <button type="button" className="btn btn--quiet" onClick={() => setPending(null)}>إلغاء</button>
          </div>
        </div>
      )}
    </section>
  )
}
