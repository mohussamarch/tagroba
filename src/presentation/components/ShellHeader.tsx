import { Bell, Eye, EyeOff, Settings } from 'lucide-react'
import '../screens/NotificationsSheet.css'

interface Props {
  /** اسم الشاشة الحالية — لقارئ الشاشة بس؛ الظاهر فوق اسم البرنامج (طلب المالك 2026-09-15). */
  screenName: string
  /** عدد التنبيهات غير المقروءة — صفر يعني لا عدّاد أصلًا. */
  unseenCount: number
  amountsHidden: boolean
  onOpenNotifications: () => void
  onToggleAmounts: () => void
  /** أيقونة الإعدادات في الرأس — OVERRIDES §23. */
  onOpenSettings?: () => void
}

/**
 * رأس التطبيق — مفصول عن `AppShell` لحد الملف 300 سطر.
 *
 * إعادة تصميم 2026-09-11 (OVERRIDES §20): الرأس فيه **الإشعارات وإخفاء
 * المبالغ بس**. زر الإضافة نزل لنص شريط التنقل، والوضع الغامق وتسجيل
 * الخروج راحوا للإعدادات — الشكوى كانت «الأزرار فوق المحتوى».
 *
 * 2026-09-15 (نص المالك): «على اليمين فوق يتحط اسم البرنامج فقط ونشيل كلمة (الرئيسية)، ونكتفي إنه عارف هو واقف
 * فين من تحت». اسم الشاشة بقى لقارئ الشاشة بس.
 */
export function ShellHeader({
  screenName,
  unseenCount,
  amountsHidden,
  onOpenNotifications,
  onToggleAmounts,
  onOpenSettings,
}: Props) {
  return (
    <header className="shell__head">
      <h1 className="shell__title">مصروفي<span className="visually-hidden"> — {screenName}</span></h1>
      <div className="shell__actions">
        <span className="bellWrap">
          <button
            type="button"
            className="iconBtn"
            onClick={onOpenNotifications}
            aria-label={unseenCount > 0 ? `الإشعارات، ${unseenCount} جديد` : 'الإشعارات'}
          >
            <Bell size={20} aria-hidden="true" />
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
          {amountsHidden ? <EyeOff size={20} aria-hidden="true" /> : <Eye size={20} aria-hidden="true" />}
        </button>

        {onOpenSettings && (
          <button type="button" className="iconBtn" onClick={onOpenSettings} aria-label="الإعدادات">
            <Settings size={20} aria-hidden="true" />
          </button>
        )}
      </div>
    </header>
  )
}
