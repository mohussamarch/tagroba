import { shouldLock } from '../../domain/appLock'
import type { AppLockSettingsPort, DeviceLockPort, LockResult } from '../ports/DeviceLockPort'

export type LockChange = { ok: true } | { ok: false; message: string }

const RESULT_MESSAGE: Record<Exclude<LockResult, 'ok'>, string> = {
  cancelled: 'اتلغى التأكيد — ما اتغيّرش حاجة.',
  failed: 'ما اتأكدش إنك صاحب الجوال — ما اتغيّرش حاجة.',
  unavailable: 'الجوال مالوش قفل شاشة ولا بصمة متسجلة. اعمل قفل شاشة من إعدادات الجوال الأول.',
}

const AVAILABILITY_MESSAGE: Record<string, string> = {
  NOT_ANDROID: 'القفل بالبصمة متاح في تطبيق أندرويد بس.',
  NONE_ENROLLED: RESULT_MESSAGE.unavailable,
  NO_HARDWARE: 'الجوال ده مافيهوش بصمة ولا قفل مدعوم.',
  HW_UNAVAILABLE: 'حساس البصمة مش متاح دلوقتي. جرّب تاني بعد شوية.',
  SECURITY_UPDATE_REQUIRED: 'الجوال محتاج تحديث أمان قبل ما يسمح بالبصمة.',
}

export interface AppLockDeps {
  device: DeviceLockPort
  settings: AppLockSettingsPort
  now: () => number
}

/**
 * AppLock — قفل التطبيق (OVERRIDES §21): اختياري، يتقفل عند الفتح وبعد خمس دقايق في
 * الخلفية، والبصمة ليها بديل رمز الجوال (النظام بيعرضه لوحده).
 *
 * التشغيل والإيقاف **الاتنين محتاجين تأكيد** — عشان حد ماسك الجوال وهو مفتوح ما يقدرش
 * يشيل القفل من الإعدادات.
 */
export function makeAppLock({ device, settings, now }: AppLockDeps) {
  async function confirmOwner(title: string): Promise<LockChange> {
    const availability = await device.availability()
    if (!availability.available) {
      return { ok: false, message: AVAILABILITY_MESSAGE[availability.code] ?? RESULT_MESSAGE.unavailable }
    }
    const result = await device.authenticate(title)
    return result === 'ok' ? { ok: true } : { ok: false, message: RESULT_MESSAGE[result] }
  }

  return {
    supported: device.supported,
    now,
    isEnabled: () => settings.read(),
    needsUnlock: (hiddenAt: number | null) => shouldLock({ enabled: settings.read(), hiddenAt, now: now() }),

    async enable(): Promise<LockChange> {
      const outcome = await confirmOwner('أكّد علشان تشغّل قفل مصروفي')
      if (outcome.ok) settings.write(true)
      return outcome
    },

    async disable(): Promise<LockChange> {
      const outcome = await confirmOwner('أكّد علشان توقف قفل مصروفي')
      if (outcome.ok) settings.write(false)
      return outcome
    },

    /** فتح التطبيق المقفول. */
    async unlock(): Promise<{ result: LockResult; message: string | null }> {
      const result = await device.authenticate('افتح مصروفي', 'بالبصمة أو برمز الجوال')
      return { result, message: result === 'ok' ? null : RESULT_MESSAGE[result] }
    },

    /**
     * القفل متشغّل بس الجوال بقى مالوش قفل خالص (اتشال من إعدادات الجوال): النظام
     * ما يقدرش يأكد حاجة، فالقفل يتوقف **ويتقال ده للمستخدم** بدل ما يتقفل بره التطبيق.
     * ❓ سلوك مبدئي مستني تأكيد المالك (HANDOVER).
     */
    async releaseIfDeviceHasNoLock(): Promise<boolean> {
      const availability = await device.availability()
      if (availability.available || availability.code !== 'NONE_ENROLLED') return false
      settings.write(false)
      return true
    },
  }
}

export type AppLock = ReturnType<typeof makeAppLock>
