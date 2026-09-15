import { useState } from 'react'
import type { UserContainer } from '../../app/container'
import type { RepairProgress } from '../../application/useCases/repairStoredIds'
import { groupCategoryOptions } from '../../domain/categoryOptions'
import { CategoryOptions } from './CategoryOptions'

type Preview = Awaited<ReturnType<UserContainer['migrateCategories']['preview']>>

const kb = (bytes: number) => `${Math.max(1, Math.round(bytes / 1024))} ك.ب`
/** المسار الكامل كلمة واحدة طويلة كانت بتوسّع الصفحة لبره الشاشة — من «Android/data» بس. */
const shortPath = (location: string) => location.replace(/^.*?(?=Android\/data\/)/, '')
const counts = (c: { transactions: number; rules: number; merchants: number; budgets: number }) =>
  [c.transactions && `${c.transactions} عملية`, c.rules && `${c.rules} قاعدة`, c.merchants && `${c.merchants} تاجر`, c.budgets && `${c.budgets} سقف`]
    .filter(Boolean).join('، ') || 'مفيش حاجة عليه'

/**
 * نقل التصنيفات القديمة للشجرة الجديدة — OVERRIDES §28 و§28.1
 * («يضيف الناقص وبس» + «انقلها للتصنيف الجديد» بمعاينة بالعدد ونسخة).
 * الفحص قراءة بس. التصنيف الغامض («تأمين») بيتنقل بس لو المستخدم اختار وجهته، واختيار الوجهة بيعيد الفحص.
 * التطبيق بيحفظ نسخة متأكدة الأول، وبينقل المعروض بس. زر الإلغاء لا يُعطَّل، و`busy` يُصفَّر في finally.
 */
export function CategoryMigrationPanel({ user, onDone }: { user: UserContainer; onDone: () => void }) {
  const [preview, setPreview] = useState<Preview | null>(null)
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState<RepairProgress | null>(null)
  const [message, setMessage] = useState('')

  async function check(choices: Readonly<Record<string, string>> = {}) {
    setBusy(true); setMessage('')
    try {
      const result = await user.migrateCategories.preview(choices)
      const nothing = result.create.length === 0 && result.update.length === 0 && result.patches.length === 0 && result.moves.length === 0 && result.ambiguous.length === 0
      if (nothing) { setPreview(null); setMessage('حسابك على شجرة التصنيفات الجديدة — مفيش حاجة تتنقل.') }
      else setPreview(result)
    } catch (error) {
      setMessage('الفحص ما كملش: ' + (error instanceof Error ? error.message : String(error)))
    } finally { setBusy(false) }
  }

  function choose(fromId: string, toId: string) {
    if (!preview) return
    const next = { ...preview.choices }
    if (toId) next[fromId] = toId
    else delete next[fromId]
    void check(next)
  }

  async function migrate() {
    if (!preview) return
    setBusy(true); setMessage(''); setProgress(null)
    try {
      const outcome = await user.migrateCategories.apply(preview, setProgress)
      const saved = outcome.backup
      setMessage((saved
        ? saved.bytes === null
          ? 'النسخة اتبعتت لتنزيلات المتصفح (المتصفح مش بيأكد حجمها). '
          : `النسخة اتحفظت (${kb(saved.bytes)}) في ${shortPath(saved.location)}. `
        : '')
        + `اتضاف ${outcome.created} تصنيف، واتحدث ${outcome.updated} تصنيف موجود، واتنقل ${outcome.written} مستند، واتخفى ${outcome.hidden} تصنيف قديم.`
        + (outcome.skipped ? ` و${outcome.skipped} اتغيروا بعد المعاينة فما اتلمسوش — افحص تاني.` : ''))
      setPreview(null)
      onDone()
    } catch (error) {
      setMessage('النقل ما كملش: ' + (error instanceof Error ? error.message : String(error))
        + '. دوس «افحص» تاني وكمّل — اللي اتنقل مش هيتنقل تاني.')
    } finally { setBusy(false); setProgress(null) }
  }

  const chosenIds = new Set(Object.keys(preview?.choices ?? {}))
  const choosable = preview ? [...preview.ambiguous, ...preview.moves.filter((m) => chosenIds.has(m.fromId)).map((m) => ({ ...m, id: m.fromId, name: m.fromName }))] : []

  return (
    <section className="sheet__field" aria-label="نقل التصنيفات للشجرة الجديدة">
      <p className="settings__hint">
        شجرة التصنيفات الجديدة (المجموعات والفروع والرموز). الفحص بيقرا بس ويقولك هيتضاف إيه وهيتنقل إيه، ومش بيغيّر حاجة.
      </p>
      <button type="button" className="btn btn--quiet" onClick={() => void check()} disabled={busy}>
        {busy && !preview ? 'بنفحص…' : 'افحص نقل التصنيفات'}
      </button>
      {message && <p className="notice" role="status">{message}</p>}
      {preview && <>
        <p>المعاينة بس — لسه ما اتكتبش حاجة.</p>
        <p>هيتضاف {preview.create.length} تصنيف (أساسي وفرعي) ناقصين.</p>
        {preview.update.length > 0 && <p>هياخد {preview.update.length} تصنيف موجود مجموعته ورمزه ولونه الجديد — اسمه وظهوره زي ما هم.</p>}
        {preview.moves.length > 0 && <>
          <p>التصنيفات القديمة اللي اتدمجت، وهتتخفي بعد النقل (مش هتتمسح):</p>
          <ul>
            {preview.moves.map((move) => (
              <li key={move.fromId}>«{move.fromName}» ← «{move.toName}»: {counts(move)}</li>
            ))}
          </ul>
        </>}
        {choosable.map((item) => (
          <label key={item.id} className="sheet__field">
            <span>«{item.name}» ({counts(item)}) ليه أكتر من مكان في الشجرة. يروح فين؟</span>
            <select className="sheet__input" value={preview.choices[item.id] ?? ''} disabled={busy}
              onChange={(e) => choose(item.id, e.target.value)}>
              <option value="">يفضل زي ما هو (مش هيتنقل ولا هيتخفي)</option>
              <CategoryOptions groups={groupCategoryOptions(preview.targets)} />
            </select>
          </label>
        ))}
        {preview.budgetConflicts.length > 0 && (
          <p className="notice">{preview.budgetConflicts.length} سقف ميزانية هيقعوا على نفس التصنيف، فمش هيتنقلوا لحد ما تقرر.</p>
        )}
        {preview.untouched.length > 0 && (
          <p className="settings__hint">تصنيفات إنت عملتها بنفسك مش هتتلمس: {preview.untouched.map((u) => `«${u.name}»`).join('، ')}.</p>
        )}
        <p className="settings__hint">
          قبل الكتابة هيتحفظ ملف فيه المستندات اللي هتتعدل زي ما هي دلوقتي، في مجلد التطبيق من غير ما يسألك، ونتأكد من حجمه.
          المبالغ والتواريخ وتأكيداتك ما بتتلمسش — حقل التصنيف بس. خليك جوه التطبيق لحد ما يخلص.
        </p>
        <button type="button" className="btn" onClick={() => void migrate()} disabled={busy}>
          {busy ? (progress ? `بننقل… ${progress.written} من ${progress.total}` : 'بنجهّز…') : 'احفظ نسخة وانقل'}
        </button>
        <button type="button" className="btn btn--quiet" onClick={() => setPreview(null)}>إلغاء</button>
      </>}
    </section>
  )
}
