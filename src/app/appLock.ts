import { makeAppLock } from '../application/useCases/appLock'
import { androidDeviceLock } from '../infrastructure/androidDeviceLock'
import { localAppLockSettings } from '../infrastructure/localAppLockSettings'

/**
 * قفل التطبيق — إعداد جهاز مش حساب، فمش جوه `forUser` (ARCHITECTURE §27).
 * التجميع يدوي زي `container.ts` (قاعدة 6).
 */
export const appLock = makeAppLock({
  device: androidDeviceLock,
  settings: localAppLockSettings,
  now: () => Date.now(),
})
