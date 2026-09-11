import '../screens/NotificationsSheet.css'

interface Props {
  title: string
  /** عدد التنبيهات غير المقروءة — صفر يعني لا عدّاد أصلًا. */
  unseenCount: number
  amountsHidden: boolean
  onOpenNotifications: () => void
  onToggleAmounts: () => void
}

/**
 * رأس التطبيق — مفصول عن `AppShell` لحد الملف 300 سطر.
 *
 * إعادة تصميم 2026-09-11 (OVERRIDES §20): الرأس فيه **الإشعارات وإخفاء
 * المبالغ بس**. زر الإضافة نزل لنص شريط التنقل، والوضع الغامق وتسجيل
 * الخروج راحوا للإعدادات — الشكوى كانت «الأزرار فوق المحتوى».
 */
export function ShellHeader({
  title,
  unseenCount,
  amountsHidden,
  onOpenNotifications,
  onToggleAmounts,
}: Props) {
  return (
    <header className="shell__head">
      <h1 className="shell__title">{title}</h1>
      <div className="shell__actions">
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
      </div>
    </header>
  )
}
