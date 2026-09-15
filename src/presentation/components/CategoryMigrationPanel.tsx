import { useState } from 'react'
import type { UserContainer } from '../../app/container'
import type { RepairProgress } from '../../application/useCases/repairStoredIds'

type Plan = Awaited<ReturnType<UserContainer['migrateCategories']['preview']>>

const kb = (bytes: number) => `${Math.max(1, Math.round(bytes / 1024))} ك.ب`
/** المسار الكامل كلمة واحدة طويلة كانت بتوسّع الصفحة لبره الشاشة — من «Android/data» بس. */
const shortPath = (location: string) => location.replace(/^.*?(?=Android\/data\/)/, '')
const counts = (c: { transactions: number; rules: number; merchants: number; budgets: number }) =>
  [c.transactions && `${c.transactions} عملية`, c.rules && `${c.rules} قاعدة`, c.merchants && `${c.merchants} تاجر`, c.budgets && `${c.budgets} سقف`]
    .filter(Boolean).join('، ') || 'مفيش حاجة عليه'

/**
 * نقل التصنيفات القديمة للشجرة الجديدة — OVERRIDES §28 و§28.1
 * («يضيف الناقص وبس» + «انقلها للتصنيف الجديد» بمعاينة بالعدد ونسخة).
 * الفحص قراءة بس. التطبيق بيحفظ نسخة متأكدة الأول، وبينقل المعروض بس. «تأمين» والتعارضات ما بيتلمسوش.
 * زر الإلغاء لا يُعطَّل، و`busy` يُصفَّر في finally (HANDOVER §9.3).
 */
export function CategoryMigrationPanel({ user, onDone }: { user: UserContainer; onDone: () => void }) {
  const [plan, setPlan] = useState<Plan | null>(null)
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState<RepairProgress | null>(null)
  const [message, setMessage] = useState('')

  async function check() {
    setBusy(true); setMessage(''); setPlan(null)
    try {
      const result = await user.migrateCategories.preview()
      const nothing = result.create.length === 0 && result.patches.length === 0 && result.moves.length === 0
      if (nothing && result.ambiguous.length === 0) setMessage('حسابك على شجرة التصنيفات الجديدة — مفيش حاجة تتنقل.')
      else setPlan(result)
    } catch (error) {
      setMessage('الفحص ما كملش: ' + (error instanceof Error ? error.message : String(error)))
    } finally { setBusy(false) }
  }

  async function migrate() {
    if (!plan) return
    setBusy(true); setMessage(''); setProgress(null)
    try {
      const outcome = await user.migrateCategories.apply(plan, setProgress)
      const saved = outcome.backup
      setMessage((saved
        ? saved.bytes === null
          ? 'النسخة اتبعتت لتنزيلات المتصفح (المتصفح مش بيأكد حجمها). '
          : `النسخة اتحفظت (${kb(saved.bytes)}) في ${shortPath(saved.location)}. `
        : '')
        + `اتضاف ${outcome.created} تصنيف، واتنقل ${outcome.written} مستند، واتخفى ${outcome.hidden} تصنيف قديم.`
        + (outcome.skipped ? ` و${outcome.skipped} اتغيروا بعد المعاينة فما اتلمسوش — افحص تاني.` : ''))
      setPlan(null)
      onDone()
    } catch (error) {
      setMessage('النقل ما كملش: ' + (error instanceof Error ? error.message : String(error))
        + '. دوس «افحص» تاني وكمّل — اللي اتنقل مش هيتنقل تاني.')
    } finally { setBusy(false); setProgress(null) }
  }

  return (
    <section className="sheet__field" aria-label="نقل التصنيفات للشجرة الجديدة">
      <p className="settings__hint">
        شجرة التصنيفات الجديدة (المجموعات والفروع والرموز). الفحص بيقرا بس ويقولك هيتضاف إيه وهيتنقل إيه، ومش بيغيّر حاجة.
      </p>
      <button type="button" className="btn btn--quiet" onClick={() => void check()} disabled={busy}>
        {busy && !plan ? 'بنفحص…' : 'افحص نقل التصنيفات'}
      </button>
      {message && <p className="notice" role="status">{message}</p>}
      {plan && <>
        <p>المعاينة بس — لسه ما اتكتبش حاجة.</p>
        <p>هيتضاف {plan.create.length} تصنيف (أساسي وفرعي) ناقصين. تصنيفاتك اللي اسمها ما اتغيرش بتفضل زي ما هي.</p>
        {plan.moves.length > 0 && <>
          <p>التصنيفات القديمة اللي اتدمجت، وهتتخفي بعد النقل (مش هتتمسح):</p>
          <ul>
            {plan.moves.map((move) => (
              <li key={move.fromId}>«{move.fromName}» ← «{move.toName}»: {counts(move)}</li>
            ))}
          </ul>
        </>}
        {plan.ambiguous.length > 0 && (
          <p className="notice">
            {plan.ambiguous.map((a) => `«${a.name}» (${counts(a)})`).join('، ')} ليه أكتر من مكان في الشجرة، فمش هيتنقل ولا هيتخفي لحد ما تقرر.
          </p>
        )}
        {plan.budgetConflicts.length > 0 && (
          <p className="notice">{plan.budgetConflicts.length} سقف ميزانية هيقعوا على نفس التصنيف، فمش هيتنقلوا لحد ما تقرر.</p>
        )}
        {plan.untouched.length > 0 && (
          <p className="settings__hint">تصنيفات إنت عملتها بنفسك مش هتتلمس: {plan.untouched.map((u) => `«${u.name}»`).join('، ')}.</p>
        )}
        <p className="settings__hint">
          قبل الكتابة هيتحفظ ملف فيه المستندات اللي هتتعدل زي ما هي دلوقتي، في مجلد التطبيق من غير ما يسألك، ونتأكد من حجمه.
          المبالغ والتواريخ وتأكيداتك ما بتتلمسش — حقل التصنيف بس. خليك جوه التطبيق لحد ما يخلص.
        </p>
        <button type="button" className="btn" onClick={() => void migrate()} disabled={busy}>
          {busy ? (progress ? `بننقل… ${progress.written} من ${progress.total}` : 'بنحفظ النسخة…') : 'احفظ نسخة وانقل'}
        </button>
        <button type="button" className="btn btn--quiet" onClick={() => setPlan(null)}>إلغاء</button>
      </>}
    </section>
  )
}
