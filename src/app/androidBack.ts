import { useEffect, useRef } from 'react'
import { App } from '@capacitor/app'
import { Capacitor } from '@capacitor/core'

export type BackAction = 'closeDialog' | 'ignore' | 'home' | 'minimize'

/**
 * قرار زرار الرجوع بتاع أندرويد — HANDOVER بند 18، ARCHITECTURE §30.
 * - نافذة مفتوحة ليها زرار «إغلاق» ⇒ تتقفل (نفس الزرار اللي المستخدم بيدوسه، فمنطق القفل واحد).
 * - نافذة **من غير** «إغلاق» (قفل البصمة، أسئلة البداية) ⇒ مفيش حاجة — ما ينفعش الرجوع يعدّيها.
 * - مفيش نوافذ والتبويب مش الرئيسية ⇒ الرئيسية.
 * - على الرئيسية ⇒ التطبيق يروح الخلفية بدل ما يتقفل.
 */
export function decideBack(input: { topDialog: boolean; topDialogCanClose: boolean; tab: string }): BackAction {
  if (input.topDialog) return input.topDialogCanClose ? 'closeDialog' : 'ignore'
  return input.tab === 'home' ? 'minimize' : 'home'
}

export function useAndroidBack(tab: string, goHome: () => void): void {
  const latest = useRef({ tab, goHome })
  latest.current = { tab, goHome }

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return
    const listener = App.addListener('backButton', () => {
      const dialogs = document.querySelectorAll<HTMLElement>('[role="dialog"]')
      const top = dialogs[dialogs.length - 1]
      const close = top?.querySelector<HTMLButtonElement>('button[aria-label="إغلاق"]:not(:disabled)') ?? null
      const action = decideBack({ topDialog: !!top, topDialogCanClose: !!close, tab: latest.current.tab })
      if (action === 'closeDialog') close!.click()
      else if (action === 'home') latest.current.goHome()
      else if (action === 'minimize') void App.minimizeApp()
    })
    return () => { void listener.then((handle) => handle.remove()) }
  }, [])
}
