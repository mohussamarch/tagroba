import '../../app/AppShell.css'

/** زر تبويب في الشريط السفلي — مستخرج من AppShell لحد الـ300 سطر. */
export function TabButton({
  label,
  icon,
  active,
  onClick,
}: {
  label: string
  icon: string
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      className={`tab${active ? ' tab--on' : ''}`}
      onClick={onClick}
      aria-current={active ? 'page' : undefined}
    >
      <span aria-hidden="true" className="tab__icon">
        {icon}
      </span>
      <span className="tab__label">{label}</span>
    </button>
  )
}
