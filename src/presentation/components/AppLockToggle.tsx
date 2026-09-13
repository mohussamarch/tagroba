import { useContext, useState } from 'react'
import { AppLockContext } from './AppLockContext'

/**
 * زر قفل التطبيق في الإعدادات — OVERRIDES §21 (اختياري).
 * التشغيل والإيقاف الاتنين بيسألوا عن البصمة أو رمز الجوال (حالة الاستخدام).
 */
export function AppLockToggle() {
  const lock = useContext(AppLockContext)
  const [enabled, setEnabled] = useState(() => lock?.isEnabled() ?? false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  if (!lock) return null
  if (!lock.supported) {
    return <p className="settings__hint">قفل التطبيق بالبصمة متاح في تطبيق أندرويد بس.</p>
  }
  const current = lock

  async function toggle() {
    setBusy(true); setMessage('')
    try {
      const wasEnabled = current.isEnabled()
      const outcome = wasEnabled ? await current.disable() : await current.enable()
      if (outcome.ok) {
        setMessage(wasEnabled ? 'القفل اتوقف.' : 'القفل اتشغّل: هيسأل عند فتح التطبيق، وبعد خمس دقايق في الخلفية.')
      } else setMessage(outcome.message)
    } catch (error) {
      setMessage('ما اتغيّرش حاجة: ' + (error instanceof Error ? error.message : String(error)))
    } finally {
      setEnabled(current.isEnabled())
      setBusy(false)
    }
  }

  return (
    <div className="sheet__field">
      <p className="settings__hint">
        قفل التطبيق: {enabled ? 'متشغّل' : 'مقفول'}. لما يتشغّل، التطبيق يسأل عن البصمة أو رمز الجوال عند الفتح
        وبعد خمس دقايق في الخلفية.
      </p>
      <button type="button" className="btn btn--quiet" onClick={() => void toggle()} disabled={busy}>
        {busy ? 'مستنيين التأكيد…' : enabled ? 'وقّف قفل البصمة' : 'شغّل قفل البصمة'}
      </button>
      {message && <p className="notice" role="status">{message}</p>}
    </div>
  )
}
