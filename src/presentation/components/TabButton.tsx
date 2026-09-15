import type { ReactNode } from 'react'
import '../../app/AppShell.css'

/** زر تبويب في الشريط السفلي — مستخرج من AppShell لحد الـ300 سطر. */
export function TabButton({
  label,
  icon,
  active,
  onClick,
}: {
  label: string
  /** علامة مرسومة بلون واحد (OVERRIDES §31) — مش حرف ولا إيموجي. */
  icon: ReactNode
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
