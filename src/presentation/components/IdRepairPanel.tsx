import { useState } from 'react'
import type { UserContainer } from '../../app/container'
import { BACKUP_LABELS, type BackupGroup } from '../../domain/fullBackup'
import type { RepairProgress } from '../../application/useCases/repairStoredIds'
import { IdRepairDetails } from './IdRepairDetails'

type Preview = Awaited<ReturnType<UserContainer['repairStoredIds']['preview']>>

const kb = (bytes: number) => `${Math.max(1, Math.round(bytes / 1024))} ك.ب`
/** المسار الكامل كلمة واحدة طويلة كانت بتوسّع الصفحة لبره الشاشة — من «Android/data» بس. */
const shortPath = (location: string) => location.replace(/^.*?(?=Android\/data\/)/, '')

/**
 * إصلاح بيانات قديمة — HANDOVER §23/§24.
 * الفحص قراءة فقط. التأكيد يحفظ نسخة من المستندات المتأثرة **في مجلد التطبيق من غير
 * نافذة** ويتأكد من حجمها قبل أي كتابة (النافذة كانت بترمي التطبيق للخلفية وتقطع الإصلاح).
 * زر الإلغاء لا يُعطَّل أبدًا، و`busy` يُصفَّر في finally (HANDOVER §9.3).
 */
export function IdRepairPanel({ user, onDone }: { user: UserContainer; onDone: () => void }) {
  const [preview, setPreview] = useState<Preview | null>(null)
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState<RepairProgress | null>(null)
  const [message, setMessage] = useState('')

  async function check() {
    setBusy(true); setMessage(''); setPreview(null)
    try {
      const result = await user.repairStoredIds.preview()
      const { plan, diagnosis } = result
      if (plan.patches.length === 0 && plan.stagedBatches.length === 0 && diagnosis.unresolved.length === 0) {
        setMessage(`اتفحص ${result.totalDocuments} مستند — مفيش حاجة محتاجة إصلاح.`)
      }
      if (plan.patches.length > 0 || plan.stagedBatches.length > 0 || diagnosis.unresolved.length > 0 || diagnosis.orphanTotal > 0) {
        setPreview(result)
      }
    } catch (error) {
      setMessage('الفحص ما كملش: ' + (error instanceof Error ? error.message : String(error)))
    } finally { setBusy(false) }
  }

  async function repair() {
    if (!preview) return
    setBusy(true); setMessage(''); setProgress(null)
    try {
      const outcome = await user.repairStoredIds.apply(preview.plan, setProgress)
      const saved = outcome.backup
      setMessage((saved
        ? saved.bytes === null
          ? `النسخة اتبعتت لتنزيلات المتصفح (المتصفح مش بيأكد حجمها). `
          : `النسخة اتحفظت (${kb(saved.bytes)}) في ${shortPath(saved.location)}. `
        : '')
        + `اتصلح ${outcome.written} مستند.`
        + (outcome.skipped ? ` و${outcome.skipped} ظهروا بعد المعاينة فما اتلمسوش — افحص تاني.` : '')
        + (preview.plan.stagedBatches.length ? ' اقفل التطبيق وافتحه تاني علشان يتنضّف الاستيراد اللي ما كملش.' : ''))
      setPreview(null)
      onDone()
    } catch (error) {
      setMessage('الإصلاح ما كملش: ' + (error instanceof Error ? error.message : String(error))
        + '. دوس «افحص» تاني وكمّل — اللي اتكتب قيمته صحيحة ومش هيظهر في الفحص.')
    } finally { setBusy(false); setProgress(null) }
  }

  const plan = preview?.plan
  return (
    <section className="sheet__field" aria-label="إصلاح بيانات قديمة">
      <p className="settings__hint">
        لو تعديل بعض العمليات بيفشل، أو الشاشات مش بتفتح، أو النسخة الشاملة رافضة الحساب: نسخة قديمة من
        التطبيق كانت بتحفظ جزء من المعرّفات الداخلية ناقص. الفحص بيقرا بس ومش بيغيّر حاجة.
      </p>
      <button type="button" className="btn btn--quiet" onClick={() => void check()} disabled={busy}>
        {busy && !preview ? 'بنفحص…' : 'افحص البيانات القديمة'}
      </button>
      {message && <p className="notice" role="status">{message}</p>}
      {preview && plan && <>
        <p>المعاينة بس — لسه ما اتكتبش حاجة. هيتعدّل {plan.patches.length} مستند من {preview.totalDocuments}:</p>
        <ul>
          {Object.entries(plan.affected).map(([group, count]) => (
            <li key={group}>{BACKUP_LABELS[group as BackupGroup]}: {count}</li>
          ))}
        </ul>
        {plan.unresolved.length > 0 && (
          <p className="notice">
            {plan.unresolved.length} رابط بيشاور على حاجة مش موجودة ومش ممكن نرجّعه بيقين، فالإصلاح ده مش هيلمسه.
            ده بند مفتوح بنحقق فيه — التفاصيل تحت.
          </p>
        )}
        <IdRepairDetails diagnosis={preview.diagnosis}/>
        {plan.stagedBatches.map((batch) => (
          <p className="notice" key={batch.docId}>
            استيراد «{batch.fileName}» ({batch.imported} عملية) ما كملش. بعد الإصلاح، أول ما تفتح التطبيق هيتنضّف
            ويتشال اللي تحته، وتقدر تستورد الكشف تاني.
          </p>
        ))}
        {plan.patches.length > 0 && <>
          <p className="settings__hint">
            قبل الكتابة هيتحفظ ملف فيه المستندات دي زي ما هي دلوقتي، في مجلد التطبيق من غير ما يسألك، ونتأكد من حجمه.
            خليك جوه التطبيق لحد ما يخلص.
          </p>
          <button type="button" className="btn" onClick={() => void repair()} disabled={busy}>
            {busy ? (progress ? `بنصلّح… ${progress.written} من ${progress.total}` : 'بنحفظ النسخة…') : 'احفظ نسخة وصلّح'}
          </button>
        </>}
        <button type="button" className="btn btn--quiet" onClick={() => setPreview(null)}>إلغاء</button>
      </>}
    </section>
  )
}
