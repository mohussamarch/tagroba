import { useState } from 'react'
import type { UserContainer } from '../../app/container'
import { BACKUP_LABELS, type BackupGroup } from '../../domain/fullBackup'

type Preview = Awaited<ReturnType<UserContainer['repairStoredIds']['preview']>>

/**
 * إصلاح بيانات قديمة — HANDOVER §23/§24.
 * الفحص قراءة فقط. التأكيد يحفظ نسخة من المستندات المتأثرة على الجهاز **قبل** أي كتابة.
 * زر الإلغاء لا يُعطَّل أبدًا، و`busy` يُصفَّر في finally (HANDOVER §9.3).
 */
export function IdRepairPanel({ user, today, onDone }: { user: UserContainer; today: string; onDone: () => void }) {
  const [preview, setPreview] = useState<Preview | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  async function check() {
    setBusy(true); setMessage(''); setPreview(null)
    try {
      const result = await user.repairStoredIds.preview()
      if (result.plan.patches.length === 0 && result.plan.stagedBatches.length === 0) {
        setMessage(`اتفحص ${result.totalDocuments} مستند — مفيش حاجة محتاجة إصلاح.`)
      } else setPreview(result)
    } catch (error) {
      setMessage('الفحص ما كملش: ' + (error instanceof Error ? error.message : String(error)))
    } finally { setBusy(false) }
  }

  async function repair() {
    if (!preview) return
    setBusy(true); setMessage('')
    try {
      await user.saveTextFile(
        JSON.stringify({ app: 'masroufy', kind: 'before-id-repair', savedAt: new Date().toISOString(), documents: preview.before }, null, 2),
        `masroufy-before-repair-${today}.json`, 'application/json')
      const outcome = await user.repairStoredIds.apply(preview.plan)
      setMessage(`اتصلح ${outcome.written} مستند.`
        + (outcome.skipped ? ` و${outcome.skipped} ظهروا بعد المعاينة فما اتلمسوش — افحص تاني.` : '')
        + (preview.plan.stagedBatches.length ? ' اقفل التطبيق وافتحه تاني علشان يتنضّف الاستيراد اللي ما كملش.' : ''))
      setPreview(null)
      onDone()
    } catch (error) {
      setMessage('الإصلاح ما كملش: ' + (error instanceof Error ? error.message : String(error))
        + '. افحص تاني. أي جزء اتكتب قبل الانقطاع قيمته صحيحة.')
    } finally { setBusy(false) }
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
          <p className="notice">{plan.unresolved.length} رابط مش مطابق لحاجة ومش ممكن نرجّعه بيقين — هيفضل زي ما هو.</p>
        )}
        {plan.stagedBatches.map((batch) => (
          <p className="notice" key={batch.docId}>
            استيراد «{batch.fileName}» ({batch.imported} عملية) ما كملش. بعد الإصلاح، أول ما تفتح التطبيق هيتنضّف
            ويتشال اللي تحته، وتقدر تستورد الكشف تاني.
          </p>
        ))}
        <p className="settings__hint">قبل الكتابة هيتحفظ على جهازك ملف فيه المستندات دي زي ما هي دلوقتي.</p>
        <button type="button" className="btn" onClick={() => void repair()} disabled={busy || plan.patches.length === 0}>
          {busy ? 'بنصلّح…' : 'احفظ نسخة وصلّح'}
        </button>
        <button type="button" className="btn btn--quiet" onClick={() => setPreview(null)}>إلغاء</button>
      </>}
    </section>
  )
}
