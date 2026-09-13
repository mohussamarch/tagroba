import { useState } from 'react'
import type { UserContainer } from '../../app/container'
import type { RepairProgress } from '../../application/useCases/repairStoredIds'

type Plan = Awaited<ReturnType<UserContainer['cleanupOrphans']['preview']>>

const kb = (bytes: number) => `${Math.max(1, Math.round(bytes / 1024))} ك.ب`
const text = (error: unknown) => (error instanceof Error ? error.message : String(error))

/**
 * تنظيف بقايا استيراد متراجَع عنه — قرار المالك 2026-09-13 (HANDOVER §36).
 * المعاينة قراءة بس، وبتعرض **أعداد** (مفيش مبالغ ولا أوصاف). الحذف بعد زر تأكيد فيه
 * العدد، وبعد نسخة مؤكدة الحجم في مجلد التطبيق. الإلغاء لا يُعطَّل أبدًا (HANDOVER §9.3).
 */
export function OrphanCleanupPanel({ user, onDone }: { user: UserContainer; onDone: () => void }) {
  const [plan, setPlan] = useState<Plan | null>(null)
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState<RepairProgress | null>(null)
  const [message, setMessage] = useState('')

  async function check() {
    setBusy(true); setMessage(''); setPlan(null)
    try {
      const result = await user.cleanupOrphans.preview()
      const nothing = result.transactions.length === 0 && result.records.length === 0
      if (nothing && result.keptLinked === 0 && result.keptNoEvidence === 0) {
        setMessage('مفيش بقايا من استيرادات متراجَع عنها.')
      } else setPlan(result)
    } catch (error) {
      setMessage('الفحص ما كملش: ' + text(error))
    } finally { setBusy(false) }
  }

  async function clean() {
    if (!plan) return
    setBusy(true); setMessage(''); setProgress(null)
    try {
      const outcome = await user.cleanupOrphans.apply(plan, setProgress)
      const saved = outcome.backup
      setMessage((saved
        ? saved.bytes === null ? 'النسخة اتبعتت لتنزيلات المتصفح (المتصفح مش بيأكد حجمها). ' : `النسخة اتحفظت (${kb(saved.bytes)}) في مجلد التطبيق. `
        : '')
        + `اتمسح ${outcome.removed} مستند.`
        + (outcome.skipped ? ` و${outcome.skipped} ظهروا بعد المعاينة فما اتلمسوش — افحص تاني.` : ''))
      setPlan(null)
      onDone()
    } catch (error) {
      setMessage('التنظيف ما كملش: ' + text(error) + '. دوس «افحص» تاني وكمّل — اللي اتمسح مش هيظهر تاني.')
    } finally { setBusy(false); setProgress(null) }
  }

  const total = plan ? plan.transactions.length + plan.records.length : 0
  const months = plan ? Object.entries(plan.byMonth).sort(([a], [b]) => b.localeCompare(a)) : []
  return (
    <section className="sheet__field" aria-label="بقايا استيراد متراجع عنه">
      <p className="settings__hint">
        التراجع عن استيراد في نسخة قديمة كان ممكن يسيب عمليات مكررة وسجلات من غير عملية. الفحص بيقرا بس.
      </p>
      <button type="button" className="btn btn--quiet" onClick={() => void check()} disabled={busy}>
        {busy && !plan ? 'بنفحص…' : 'افحص بقايا الاستيرادات المتراجَع عنها'}
      </button>
      {message && <p className="notice" role="status">{message}</p>}
      {plan && <>
        <p>المعاينة بس — لسه ما اتمسحش حاجة.</p>
        <ul>
          <li>عمليات مكررة هتتمسح: {plan.transactions.length}</li>
          <li>سجلات مصدر من غير عملية هتتمسح: {plan.records.length}</li>
        </ul>
        {months.length > 0 && <details>
          <summary>العمليات المكررة حسب الشهر</summary>
          <ul>{months.map(([month, count]) => <li key={month}>{month}: {count}</li>)}</ul>
        </details>}
        {plan.keptLinked > 0 && (
          <p className="notice">{plan.keptLinked} عملية من نفس الاستيراد متربطة بشخص أو تسوية أو وسم — مش هتتمسح.</p>
        )}
        {plan.keptNoEvidence > 0 && (
          <p className="notice">{plan.keptNoEvidence} عملية من نفس الاستيراد مالهاش توأم مسجّل يثبت إنها مكررة — مش هتتمسح.</p>
        )}
        {total > 0 && <>
          <p className="settings__hint">
            العملية بتتمسح بس لو ليها توأم بنفس اليوم والمبلغ والاتجاه من كشف مسجّل، ومفيش عليها أي ارتباط.
            قبل الحذف هيتحفظ ملف فيه المستندات دي كاملة. خليك جوه التطبيق لحد ما يخلص.
          </p>
          <button type="button" className="btn" onClick={() => void clean()} disabled={busy}>
            {busy ? (progress ? `بنمسح… ${progress.written} من ${progress.total}` : 'بنحفظ النسخة…') : `احفظ نسخة وامسح ${total}`}
          </button>
        </>}
        <button type="button" className="btn btn--quiet" onClick={() => setPlan(null)}>إلغاء</button>
      </>}
    </section>
  )
}
