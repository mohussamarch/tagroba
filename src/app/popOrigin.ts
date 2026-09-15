import { useEffect } from 'react'

/**
 * مكان آخر دوسة — عشان النوافذ المنبثقة «تطلع من التبويب اللي اتضغط عليه» (OVERRIDES §32).
 * بيحط `--pop-x` و`--pop-y` على الصفحة، والحركة نفسها CSS بس (`feedback.css`). مفيش منطق ولا بيانات.
 */
export function usePopOrigin(): void {
  useEffect(() => {
    const root = document.documentElement
    const remember = (event: PointerEvent) => {
      root.style.setProperty('--pop-x', `${Math.round(event.clientX)}px`)
      root.style.setProperty('--pop-y', `${Math.round(event.clientY)}px`)
    }
    window.addEventListener('pointerdown', remember, { capture: true, passive: true })
    return () => window.removeEventListener('pointerdown', remember, { capture: true })
  }, [])
}
