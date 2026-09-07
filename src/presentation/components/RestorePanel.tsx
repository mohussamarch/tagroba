import { useState, type ChangeEvent } from 'react'
import { ErrorNotice } from './ErrorNotice'
import type { RestorePlan } from '../../application/useCases/restoreBackup'
import type { UserContainer } from '../../app/container'
import '../screens/RulesScreen.css'

/**
 * الاستعادة من نسخة احتياطية — **بالدمج** (قرار المالك، `OVERRIDES §12`).
 *
 * خطوتان دايمًا: **المعاينة قبل الكتابة**. المستخدم بيشوف هيتضاف كام
 * وهيتخطى كام و**ليه** قبل ما يتكتب حرف. `spec/05`: «لا استبدال صامت».
 */
export function RestorePanel({ user, onDone }: { user: UserContainer; onDone: () => void }) {
  const [plan, setPlan] = useState<RestorePlan | null>(null)
  const [fileName, setFileName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [result, setResult] = useState<string | null>(null)

  async function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return
    setError(null)
    setPlan(null)
    setResult(null)
    setBusy(true)
    try {
      const text = await file.text()
      setPlan(await user.restoreBackup.plan(text))
      setFileName(file.name)
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  async function apply() {
    if (!plan) return
    setError(null)
    setBusy(true)
    try {
      const outcome = await user.restoreBackup.apply(plan.file)
      setResult(
        outcome.totalAdded === 0
          ? 'مفيش حاجة اتضافت — كل اللي في النسخة موجود عندك.'
          : `اتضاف ${outcome.totalAdded} عنصر، منهم ${outcome.added.transactions ?? 0} عملية.`,
      )
      setPlan(null)
      onDone()
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="sheet__field">
      <span className="sheet__label">استعادة من نسخة</span>
      <input
        className="sheet__input"
        type="file"
        accept=".json,application/json"
        onChange={onFile}
        disabled={busy}
      />
      <span className="sheet__hint">
        الاستعادة <strong>بتدمج</strong>: بتضيف اللي مش موجود عندك، وما بتدهسش أي حاجة
        موجودة. العملية اللي عندك بتفضل بقراراتك عليها.
      </span>

      {error ? <ErrorNotice cause={error} /> : null}

      {plan && (
        <div className="restore__plan">
          <p className="rules__order">
            <strong>{fileName}</strong> — هيتضاف {plan.totalToAdd} عنصر. مفيش حاجة اتكتبت لسه.
          </p>

          <ul className="rules__list">
            {plan.lines.map((line) => (
              <li key={line.key} className="rules__row">
                <div className="rules__main">
                  <span className="rules__text">{line.label}</span>
                  <span className="rules__cat">
                    {line.incoming} في الملف · هيتضاف {line.toAdd} · هيتخطى {line.skipped}
                  </span>
                  {/* سبب التخطي مكتوب — لا حالة بلا تفسير */}
                  {line.note ? <span className="rules__cat">{line.note}</span> : null}
                </div>
              </li>
            ))}
          </ul>

          {plan.warnings.map((warning) => (
            <p key={warning} className="notice" role="status">
              {warning}
            </p>
          ))}

          <button
            type="button"
            className="btn"
            onClick={apply}
            disabled={busy || plan.totalToAdd === 0}
          >
            {busy ? 'بندمج…' : `أكّد الدمج (${plan.totalToAdd})`}
          </button>
        </div>
      )}

      {result ? (
        <p className="notice" role="status">
          {result}
        </p>
      ) : null}
    </div>
  )
}
