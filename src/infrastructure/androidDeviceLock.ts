import { Capacitor, registerPlugin } from '@capacitor/core'
import type { DeviceLockPort, DeviceLockAvailability, LockResult } from '../application/ports/DeviceLockPort'

/** DeviceLockPlugin.java — BiometricPrompt من androidx.biometric (ARCHITECTURE §27). */
const native = registerPlugin<{
  availability(): Promise<DeviceLockAvailability>
  authenticate(options: { title: string; subtitle?: string }): Promise<{ result: LockResult }>
}>('DeviceLock')

const supported = Capacitor.getPlatform() === 'android'

export const androidDeviceLock: DeviceLockPort = {
  supported,
  async availability() {
    return supported ? native.availability() : { available: false, code: 'NOT_ANDROID' }
  },
  async authenticate(title, subtitle) {
    if (!supported) return 'unavailable'
    return (await native.authenticate({ title, ...(subtitle ? { subtitle } : {}) })).result
  },
}
