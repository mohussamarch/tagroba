import { useState } from 'react'
import type { UserContainer } from '../../app/container'

type Plan = Awaited<ReturnType<UserContainer['repairBudgetIds']['preview']>>

const kb = (bytes: number) => `${Math.max(1, Math.round(bytes / 1024))} ك.ب`

/**
 * تصليح معرّف الميزانيات القديمة — موافقة المالك 2026-09-15 («صلّحه بنسخة احتياطية»).
 * الفحص قراءة بس، والتصليح بيحفظ نسخة من المستندات اللي هتتعدل في مجلد التطبيق الأول.
 */
export function BudgetIdRepairPanel({ user, onDone }: { user: UserContainer; onDone: () => void }) {
  const [plan, setPlan] = useState<Plan | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  async function check() {
    setBusy(true); setMessage(''); setPlan(null)
    try {
      const result = await user.repairBudgetIds.preview()
      if (result.patches.length === 0) setMessage('معرّفات الميزانيات سليمة — مفيش حاجة تتصلح.')
      else setPlan(result)
    } catch (error) {
      setMessage('الفحص ما كملش: ' + (error instanceof Error ? error.message : String(error)))
    } finally { setBusy(false) }
  }

  async function repair() {
    if (!plan) return
    setBusy(true); setMessage('')
    try {
      const outcome = await user.repairBudgetIds.apply(plan)
      const saved = outcome.backup
      setMessage((saved && saved.bytes !== null ? `النسخة اتحفظت (${kb(saved.bytes)}). ` : '')
        + `اتصلح ${outcome.written} مستند.`
        + (outcome.skipped ? ` و${outcome.skipped} اتغيروا بعد المعاينة فما اتلمسوش — افحص تاني.` : ''))
      setPlan(null)
      onDone()
    } catch (error) {
      setMessage('التصليح ما كملش: ' + (error instanceof Error ? error.message : String(error)) + '. دوس «افحص» تاني.')
    } finally { setBusy(false) }
  }

  return (
    <section className="sheet__field" aria-label="معرّفات الميزانيات">
      <p className="settings__hint">
        ميزانيات اتعملت بنسخة قديمة من التطبيق متخزنة برقم عشوائي بدل مفتاح الفترة. الفحص بيقرا بس.
      </p>
      <button type="button" className="btn btn--quiet" onClick={() => void check()} disabled={busy}>
        {busy && !plan ? 'بنفحص…' : 'افحص معرّفات الميزانيات'}
      </button>
      {message && <p className="notice" role="status">{message}</p>}
      {plan && <>
        <p>المعاينة بس — لسه ما اتكتبش حاجة. هيتعدّل {plan.budgets} ميزانية و{plan.lines} سقف تصنيف تحتها.</p>
        <p className="settings__hint">قبل الكتابة هيتحفظ ملف فيه المستندات دي زي ما هي دلوقتي، في مجلد التطبيق، ونتأكد من حجمه.</p>
        <button type="button" className="btn" onClick={() => void repair()} disabled={busy}>
          {busy ? 'بنصلّح…' : 'احفظ نسخة وصلّح'}
        </button>
        <button type="button" className="btn btn--quiet" onClick={() => setPlan(null)}>إلغاء</button>
      </>}
    </section>
  )
}
