import { createContext } from 'react'
import type { AppLock } from '../../application/useCases/appLock'

/**
 * قفل التطبيق بيتبني مرة واحدة في `app/appLock.ts` ويتوصّل من جذر التطبيق.
 * سياق بدل تمرير خاصية عشان AppShell وSettingsScreen على حد الـ300 سطر.
 * null = مفيش قفل متوصّل (اختبارات، معاينة).
 */
export const AppLockContext = createContext<AppLock | null>(null)
