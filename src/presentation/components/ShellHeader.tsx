import '../screens/NotificationsSheet.css'

interface Props {
  title: string
  /** عدد التنبيهات غير المقروءة — صفر يعني لا عدّاد أصلًا. */
  unseenCount: number
  amountsHidden: boolean
  theme: 'light' | 'dark'
  onAdd: () => void
  onOpenNotifications: () => void
  onToggleAmounts: () => void
  onToggleTheme: () => void
  onSignOut: () => void
}

/** رأس التطبيق — مفصول عن `AppShell` لحد الملف 300 سطر. */
export function ShellHeader({
  title,
  unseenCount,
  amountsHidden,
  theme,
  onAdd,
  onOpenNotifications,
  onToggleAmounts,
  onToggleTheme,
  onSignOut,
}: Props) {
  return (
    <header className="shell__head">
      <h1 className="shell__title">{title}</h1>
      <div className="shell__actions">
        <button
          type="button"
          className="iconBtn iconBtn--primary"
          onClick={onAdd}
          aria-label="إضافة"
        >
          <span aria-hidden="true">＋</span>
        </button>

        <span className="bellWrap">
          <button
            type="button"
            className="iconBtn"
            onClick={onOpenNotifications}
            aria-label={unseenCount > 0 ? `الإشعارات، ${unseenCount} جديد` : 'الإشعارات'}
          >
            <span aria-hidden="true">🔔</span>
          </button>
          {/* العدّاد بيظهر بس لما يكون فيه جديد فعلًا */}
          {unseenCount > 0 && (
            <span className="bellWrap__count" aria-hidden="true">
              {unseenCount}
            </span>
          )}
        </span>

        <button
          type="button"
          className="iconBtn"
          onClick={onToggleAmounts}
          aria-label={amountsHidden ? 'إظهار المبالغ' : 'إخفاء المبالغ'}
          aria-pressed={amountsHidden}
        >
          <span aria-hidden="true">{amountsHidden ? '🙈' : '👁'}</span>
        </button>

        <button
          type="button"
          className="iconBtn"
          onClick={onToggleTheme}
          aria-label={theme === 'light' ? 'تحويل للوضع الغامق' : 'تحويل للوضع الفاتح'}
        >
          <span aria-hidden="true">{theme === 'light' ? '🌙' : '☀️'}</span>
        </button>

        <button type="button" className="iconBtn" onClick={onSignOut} aria-label="تسجيل الخروج">
          <span aria-hidden="true">⎋</span>
        </button>
      </div>
    </header>
  )
}
