import type { AppLockSettingsPort, DeviceLockAvailability, DeviceLockPort, LockResult } from '../../application/ports/DeviceLockPort'

/** قفل جهاز وهمي للاختبار ووضع المعاينة: النتائج بتتسحب بالترتيب، وكل سؤال بيتسجل. */
export function memoryDeviceLock(options: { availability?: DeviceLockAvailability; results?: LockResult[] } = {}) {
  const prompts: string[] = []
  const results = [...(options.results ?? [])]
  const port: DeviceLockPort & { prompts: string[]; setAvailability(next: DeviceLockAvailability): void } = {
    supported: true,
    prompts,
    availability: async () => options.availability ?? { available: true, code: 'SUCCESS' },
    authenticate: async (title) => { prompts.push(title); return results.shift() ?? 'ok' },
    setAvailability(next) { options.availability = next },
  }
  return port
}

export function memoryAppLockSettings(initial = false): AppLockSettingsPort {
  let enabled = initial
  return { read: () => enabled, write: (next) => { enabled = next } }
}
