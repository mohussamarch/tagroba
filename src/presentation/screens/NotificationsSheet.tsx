import { useEffect, useRef, useState } from 'react'
import type { NotificationEvent } from '../../domain/notifications'
import type { NotificationsView } from '../../application/useCases/loadNotifications'
import type { UserContainer } from '../../app/container'
import './NotificationsSheet.css'

interface Props {
  user: UserContainer
  view: NotificationsView | null
  loading?: boolean
  error?: unknown
  onClose: () => void
  onSeen: () => void
}

const SEVERITY_LABEL = {
  info: 'تنبيه',
  warn: 'قرّبت',
  over: 'تجاوز',
} as const

/**
 * صفحة الإشعارات — `spec/01` بتذكرها في «صفحات فرعية… والإشعارات».
 *
 * ⚠️ **القيد مكتوب هنا للمستخدم بالنص.** التطبيق ما بيقدرش يبعت إشعارًا
 * والموبايل مقفول: ده محتاج Cloud Functions وهي مش متاحة في باقة Spark
 * (CLAUDE.md #12). الإخفاء هنا كان هيخلي محمد يستنى تنبيه عمره ما هييجي.
 */
export function NotificationsSheet({ user, view, onClose, onSeen, loading, error }: Props) {
  const [unseenKeys, setUnseenKeys] = useState<Set<string>>(new Set())

  const marked = useRef(false)
  // التعليم كمقروء بيحصل **بعد** ما تتعرض فعلًا لا قبلها
  useEffect(() => {
    if (!view || loading || marked.current || view.unseen.length === 0) return
    marked.current = true
    setUnseenKeys(new Set(view.unseen.map((e) => e.eventKey)))
    let alive = true
    user.loadNotifications
      .markSeen(view.unseen)
      .then(() => alive && onSeen())
      // فشل التعليم مش كارثة: التنبيه هيتعرض تاني، وده أأمن من اختفائه
      .catch(() => {})
    return () => {
      alive = false
    }
    // مرة واحدة لكل فتح — الاعتماد على `view` هيعلّم في كل إعادة تحميل
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, loading])

  const events = view?.all ?? []

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="الإشعارات">
      <div className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">الإشعارات</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <span aria-hidden="true">✕</span>
          </button>
        </header>

        <div className="sheet__body">
          {loading ? <p role="status">بنراجع التنبيهات…</p> : error ? <p role="alert">تعذر تحميل التنبيهات. اقفل النافذة وجرّب تاني.</p> : events.length === 0 ? (
            <div className="empty">
              <span className="empty__title">مفيش تنبيهات</span>
              <span>
                التنبيه بيظهر لما تعدّي عتبة حطيتها بنفسك على الميزانية أو على تصنيف.
                من غير سقف، مفيش تنبيه.
              </span>
            </div>
          ) : (
            <ul className="notif__list">
              {events.map((event) => (
                <Row key={event.eventKey} event={event} isNew={unseenKeys.has(event.eventKey)} />
              ))}
            </ul>
          )}

          {/* الحد التقني مكتوب، مش متهرب منه */}
          <p className="notif__limit">
            دي تنبيهات جوّه التطبيق بتظهرلك لما تفتحه. التطبيق مش بيقدر يرن على
            موبايلك وهو مقفول — ده محتاج خدمة سيرفر مدفوعة، والباقة الحالية مجانية.
          </p>
        </div>
      </div>
    </div>
  )
}

function Row({ event, isNew }: { event: NotificationEvent; isNew: boolean }) {
  return (
    <li className={`notif__row notif__row--${event.severity}`}>
      <div className="notif__head">
        <span className="notif__title">
          {event.title}
          {isNew && <span className="badge">جديد</span>}
        </span>
        <span className="badge">{SEVERITY_LABEL[event.severity]}</span>
      </div>
      <span className="notif__body num">{event.body}</span>
    </li>
  )
}
