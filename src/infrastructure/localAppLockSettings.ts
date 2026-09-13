import type { AppLockSettingsPort } from '../application/ports/DeviceLockPort'

const KEY = 'masroufy.appLock.enabled'

/**
 * إعداد الجهاز نفسه (مش بيانات حساب). التخزين ممكن يرمي (وضع خاص/ممنوع) — ساعتها
 * القراءة بترجع «مقفول» والكتابة بترمي، فالشاشة تقول إن الإعداد ما اتحفظش.
 */
export const localAppLockSettings: AppLockSettingsPort = {
  read() {
    try { return window.localStorage.getItem(KEY) === '1' } catch { return false }
  },
  write(enabled) {
    window.localStorage.setItem(KEY, enabled ? '1' : '0')
  },
}
