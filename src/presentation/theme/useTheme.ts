import { useCallback, useEffect, useState } from 'react'

export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'masroufy.theme'

function readStored(): ThemeMode | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

function systemPreference(): ThemeMode {
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  } catch {
    return 'light' // فاتح افتراضيًا — spec/01
  }
}

/**
 * الثيم: فاتح افتراضيًا، وغامق قابل للاختيار والحفظ (spec/01).
 * التبديل لا يفقد موضع المستخدم ولا القيم غير المحفوظة (spec/04) —
 * لأنه يغيّر سمة على <html> فقط ولا يعيد تركيب الشجرة.
 */
export function useTheme(): { theme: ThemeMode; toggleTheme: () => void } {
  const [theme, setTheme] = useState<ThemeMode>(() => readStored() ?? systemPreference())

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    const meta = document.querySelector('meta[name="theme-color"]')
    if (meta) meta.setAttribute('content', theme === 'dark' ? '#141719' : '#F7F8F9')
    try {
      localStorage.setItem(STORAGE_KEY, theme)
    } catch {
      // التخزين المحلي ممنوع في بعض المتصفحات — الثيم يشتغل للجلسة الحالية فقط
    }
  }, [theme])

  const toggleTheme = useCallback(() => {
    setTheme((current) => (current === 'light' ? 'dark' : 'light'))
  }, [])

  return { theme, toggleTheme }
}
