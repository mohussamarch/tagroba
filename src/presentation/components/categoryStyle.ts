import type { CSSProperties } from 'react'

/** متغيرات لون التصنيف للـCSS (`--cat-light` و`--cat-dark`) — نفس طريقة `GroupDistribution`. */
export const catColorStyle = (colors: { lightColor: string; darkColor: string }) =>
  ({ '--cat-light': colors.lightColor, '--cat-dark': colors.darkColor }) as CSSProperties
