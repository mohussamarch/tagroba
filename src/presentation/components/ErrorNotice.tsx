import { toUserFacingError } from '../../infrastructure/firestore/firestoreErrors'
import './ErrorNotice.css'

interface Props {
  cause: unknown
  onRetry?: () => void
}

/**
 * عرض موحّد لأي خطأ — spec/04: «خطأ: بجانب الحقل وبنص عربي محدد،
 * مع الاحتفاظ بما أدخله المستخدم؛ **لا Toast وحده للأخطاء المهمة**».
 *
 * ثلاث طبقات مقصودة:
 *   ١. ما حدث، بلغة المستخدم
 *   ٢. ما يفعله — إجراء واضح لا نصيحة عامة
 *   ٣. الرمز الأصلي **مطويًا** — يفيدني لو احتاج يبعته، ولا يزعجه
 */
export function ErrorNotice({ cause, onRetry }: Props) {
  const error = toUserFacingError(cause)

  return (
    <div className="errNotice" role="alert">
      <div className="errNotice__head">
        <span aria-hidden="true">⚠</span>
        <strong>{error.message}</strong>
      </div>

      {error.action && <p className="errNotice__action">{error.action}</p>}

      {error.retryable && onRetry && (
        <button type="button" className="btn btn--quiet errNotice__retry" onClick={onRetry}>
          جرّب تاني
        </button>
      )}

      {error.code !== 'app' && (
        <details className="errNotice__tech">
          <summary>التفاصيل التقنية</summary>
          <code>{error.code}</code>
        </details>
      )}
    </div>
  )
}
