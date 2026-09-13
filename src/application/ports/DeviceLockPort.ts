/**
 * قفل الجهاز (بصمة، وجه، أو رمز الجوال) — OVERRIDES §21.
 * التطبيق ما بيشوفش البصمة ولا الرمز: النظام بيسأل ويرجّع نتيجة بس.
 */
export type LockResult = 'ok' | 'cancelled' | 'failed' | 'unavailable'

export interface DeviceLockAvailability {
  available: boolean
  /** سبب عدم الإتاحة بكود ثابت (NONE_ENROLLED = الجوال مالوش قفل شاشة ولا بصمة). */
  code: string
}

export interface DeviceLockPort {
  /** المنصة نفسها بتدعم القفل (أندرويد بس). */
  supported: boolean
  availability(): Promise<DeviceLockAvailability>
  authenticate(title: string, subtitle?: string): Promise<LockResult>
}

/** إعداد «القفل متشغّل» — على الجهاز نفسه، مش بيانات حساب ومش بيتزامن. */
export interface AppLockSettingsPort {
  read(): boolean
  write(enabled: boolean): void
}
