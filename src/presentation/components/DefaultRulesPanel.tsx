import { useState } from 'react'
import type { UserContainer } from '../../app/container'
import type { RepairProgress } from '../../application/useCases/repairStoredIds'

type Plan = Awaited<ReturnType<UserContainer['restoreDefaultReferences']['preview']>>

const kb = (bytes: number) => `${Math.max(1, Math.round(bytes / 1024))} ك.ب`
/** المسار الكامل كلمة واحدة طويلة كانت بتوسّع الصفحة لبره الشاشة — من «Android/data» بس. */
const shortPath = (location: string) => location.replace(/^.*?(?=Android\/data\/)/, '')

/**
 * رجوع القواعد والتجار الافتراضيين — OVERRIDES §28.1 («رجّعهم بمعاينة»، «رجّع لحسابي الأول وبعدين المشتركة»).
 * الفحص قراءة بس: كام قاعدة وتاجر ناقصين، وكام عملية من غير تصنيف هتاخد تصنيف **مقترح** وتروح لأنهي تصنيف.
 * التطبيق بيحفظ نسخة متأكدة للعمليات الأول، ومش بيكتب فوق قاعدة أو تاجر موجود ولا عملية مصنفة أو مؤكدة.
 */
export function DefaultRulesPanel({ user, onDone }: { user: UserContainer; onDone: () => void }) {
  const [plan, setPlan] = useState<Plan | null>(null)
  const [names, setNames] = useState<ReadonlyMap<string, string>>(new Map())
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState<RepairProgress | null>(null)
  const [message, setMessage] = useState('')

  async function check() {
    setBusy(true); setMessage(''); setPlan(null)
    try {
      const [result, categories] = await Promise.all([user.restoreDefaultReferences.preview(), user.manageCategories.list()])
      setNames(new Map(categories.map((c) => [c.id, c.name])))
      if (result.rules.length === 0 && result.merchants.length === 0 && result.suggestions.length === 0) {
        setMessage('القواعد والتجار الافتراضيين موجودين، ومفيش عملية جديدة تاخد اقتراح.')
      } else setPlan(result)
    } catch (error) {
      setMessage('الفحص ما كملش: ' + (error instanceof Error ? error.message : String(error)))
    } finally { setBusy(false) }
  }

  async function restore() {
    if (!plan) return
    setBusy(true); setMessage(''); setProgress(null)
    try {
      const outcome = await user.restoreDefaultReferences.apply(plan, setProgress)
      const saved = outcome.backup
      setMessage((saved
        ? saved.bytes === null
          ? 'النسخة اتبعتت لتنزيلات المتصفح (المتصفح مش بيأكد حجمها). '
          : `النسخة اتحفظت (${kb(saved.bytes)}) في ${shortPath(saved.location)}. `
        : '')
        + `اتضاف ${outcome.rules} قاعدة و${outcome.merchants} تاجر، واتقترح تصنيف لـ${outcome.suggested} عملية (مقترح مش مؤكد).`
        + (outcome.skipped ? ` و${outcome.skipped} عملية اتصنفت بعد المعاينة فما اتلمستش.` : ''))
      setPlan(null)
      onDone()
    } catch (error) {
      setMessage('ما كملش: ' + (error instanceof Error ? error.message : String(error)) + '. دوس «افحص» تاني وكمّل.')
    } finally { setBusy(false); setProgress(null) }
  }

  return (
    <section className="sheet__field" aria-label="رجوع القواعد والتجار الافتراضيين">
      <p className="settings__hint">
        القواعد والتجار الافتراضيين هما اللي بيصنفوا العمليات لوحدهم (زي BARQ وALDREES وUBER). الفحص بيقرا بس ومش بيغيّر حاجة.
      </p>
      <button type="button" className="btn btn--quiet" onClick={() => void check()} disabled={busy}>
        {busy && !plan ? 'بنفحص…' : 'افحص القواعد والتجار الافتراضيين'}
      </button>
      {message && <p className="notice" role="status">{message}</p>}
      {plan && <>
        <p>المعاينة بس — لسه ما اتكتبش حاجة.</p>
        <p>هيتضاف {plan.rules.length} قاعدة و{plan.merchants.length} تاجر. القواعد والتجار اللي عندك مش هيتغيروا.</p>
        <p>من {plan.uncategorized} عملية من غير تصنيف، {plan.suggestions.length} هتاخد تصنيف مقترح (تقدر تأكده أو تغيّره بعدين):</p>
        {plan.byCategory.length > 0 && (
          <ul>
            {plan.byCategory.slice(0, 12).map((row) => <li key={row.categoryId}>«{names.get(row.categoryId) ?? row.categoryId}»: {row.count} عملية</li>)}
          </ul>
        )}
        <p className="settings__hint">
          قبل الكتابة هيتحفظ ملف فيه العمليات اللي هتاخد اقتراح زي ما هي دلوقتي، ونتأكد من حجمه. المبالغ والتواريخ والتصنيفات المؤكدة ما بتتلمسش.
        </p>
        <button type="button" className="btn" onClick={() => void restore()} disabled={busy}>
          {busy ? (progress ? `بنكتب… ${progress.written} من ${progress.total}` : 'بنجهّز…') : 'احفظ نسخة ورجّعهم'}
        </button>
        <button type="button" className="btn btn--quiet" onClick={() => setPlan(null)}>إلغاء</button>
      </>}
    </section>
  )
}
