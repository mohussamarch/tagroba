/**
 * المظهر والحساب — نزلوا من رأس التطبيق للإعدادات بقرار المالك
 * 2026-09-11 (OVERRIDES §20). مكوّن مستقل عشان حد الـ300 سطر.
 */
export function AppearanceCard({
  theme,
  onToggleTheme,
  onSignOut,
}: {
  theme: 'light' | 'dark'
  onToggleTheme: () => void
  onSignOut: () => void
}) {
  return (
    <section className="card" aria-label="المظهر والحساب">
      <h2 className="card__title">المظهر والحساب</h2>
      <div className="settings__row">
        <button type="button" className="btn btn--quiet" onClick={onToggleTheme}>
          {theme === 'light' ? 'الوضع الغامق' : 'الوضع الفاتح'}
        </button>
        <button type="button" className="btn btn--quiet" onClick={onSignOut}>
          تسجيل الخروج
        </button>
      </div>
    </section>
  )
}
