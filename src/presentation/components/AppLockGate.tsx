import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import type { AppLock } from '../../application/useCases/appLock'
import './AppLockGate.css'

/**
 * بوابة القفل — OVERRIDES §21. بتغطي التطبيق **من غير ما تشيله**: أي شغل شغال
 * (استيراد، إصلاح) ما يتقطعش لما التطبيق يتقفل.
 * القرار (إمتى يتقفل) في `domain/appLock.ts`؛ هنا بس بنسجل وقت دخول الخلفية ونعرض.
 */
export function AppLockGate({ lock, children }: { lock: AppLock; children: ReactNode }) {
  const [locked, setLocked] = useState(() => lock.needsUnlock(null))
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const hiddenAt = useRef<number | null>(null)
  const prompting = useRef(false)
  const content = useRef<HTMLDivElement>(null)

  // `inert` مش في أنواع React 18 — بيتحط على العنصر مباشرة: مفيش لمس ولا تركيز من تحت القفل
  useEffect(() => { if (content.current) content.current.inert = locked }, [locked])

  const unlock = useCallback(async () => {
    if (prompting.current) return
    prompting.current = true
    setBusy(true); setMessage(null)
    try {
      const { result, message: why } = await lock.unlock()
      if (result === 'ok') { setLocked(false); return }
      if (result === 'unavailable' && await lock.releaseIfDeviceHasNoLock()) {
        setLocked(false)
        window.alert('القفل اتوقف لأن الجوال مبقاش عليه قفل شاشة ولا بصمة. تقدر تشغّله تاني من الإعدادات بعد ما تعمل قفل للجوال.')
        return
      }
      setMessage(why)
    } catch {
      setMessage('مقدرناش نسأل عن البصمة. دوس «افتح» تاني.')
    } finally {
      prompting.current = false
      setBusy(false)
    }
  }, [lock])

  // أول فتح: السؤال بيطلع لوحده مرة واحدة
  useEffect(() => { if (locked) void unlock() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const onVisibility = () => {
      // حوار رمز الجوال نفسه بيخفي الصفحة؛ ما نسجلش خروج للخلفية وإحنا اللي فاتحينه
      if (document.hidden) { if (!prompting.current) hiddenAt.current = lock.now(); return }
      const since = hiddenAt.current
      hiddenAt.current = null
      if (since !== null && lock.needsUnlock(since)) { setLocked(true); void unlock() }
    }
    document.addEventListener('visibilitychange', onVisibility)
    return () => document.removeEventListener('visibilitychange', onVisibility)
  }, [lock, unlock])

  return <>
    <div ref={content} aria-hidden={locked || undefined}>{children}</div>
    {locked && (
      <div className="appLock" role="dialog" aria-modal="true" aria-label="مصروفي مقفول">
        <div className="appLock__card">
          <p className="appLock__title">مصروفي مقفول</p>
          <p className="appLock__hint">افتح بالبصمة أو برمز الجوال.</p>
          {message && <p className="notice" role="status">{message}</p>}
          <button type="button" className="btn" onClick={() => void unlock()} disabled={busy}>
            {busy ? 'مستنيين التأكيد…' : 'افتح'}
          </button>
        </div>
      </div>
    )}
  </>
}
