import type { CSSProperties, ReactNode } from 'react'
import './DonutChart.css'

export interface DonutSegment {
  key: string
  /** النسبة بالعُشر في المية (مثلًا 125 = 12.5%) — جاية جاهزة من domain. */
  shareTenthPercent: number
  /** متغيرات لون التصنيف (`--cat-light` و`--cat-dark`). */
  style?: CSSProperties
}

const RADIUS = 42
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

/**
 * رسم دايري لتوزيع جاهز — OVERRIDES §31 («عايز دايمًا أشوف تشارتات»).
 * المكوّن **ما بيحسبش فلوس**: بيرسم النسب اللي جاية من domain بس، والحساب هنا هندسة الرسم.
 * الوصف الكامل في `label` لقارئ الشاشة، والقايمة اللي تحته فيها نفس الأرقام (spec/04: بديل للرسم).
 */
export function DonutChart({ segments, center, label }: {
  segments: readonly DonutSegment[]
  center: ReactNode
  label: string
}) {
  let offset = 0
  return (
    <figure className="donut" role="img" aria-label={label}>
      <svg viewBox="0 0 100 100" className="donut__svg" aria-hidden="true">
        <circle cx="50" cy="50" r={RADIUS} className="donut__track" />
        {segments.filter((s) => s.shareTenthPercent > 0).map((s, index) => {
          const length = (s.shareTenthPercent / 1000) * CIRCUMFERENCE
          const circle = (
            <circle
              key={s.key}
              cx="50"
              cy="50"
              r={RADIUS}
              className="donut__seg"
              style={{
                ...s.style,
                strokeDasharray: `${Math.max(length - 1.2, 0.6)} ${CIRCUMFERENCE}`,
                strokeDashoffset: -offset,
                animationDelay: `${index * 70}ms`,
              }}
            />
          )
          offset += length
          return circle
        })}
      </svg>
      <figcaption className="donut__center" aria-hidden="true">{center}</figcaption>
    </figure>
  )
}
