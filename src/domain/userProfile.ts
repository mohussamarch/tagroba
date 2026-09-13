import { DEFAULT_PAYDAY } from './period'
import type { Halalas } from './money'

/**
 * ملف المستخدم — قسم «الحساب» وأسئلة البداية (OVERRIDES §26، قرار المالك 2026-09-13).
 *
 * - **يوم الراتب لكل مستخدم** والفترات المالية بتتحسب منه. لو مش محدد ⇒ 28 (المالك).
 *   الأيام 29–31 بتتقيد بآخر يوم في الشهر في `period.ts` (`clampPaydayToMonth`) مش هنا.
 * - **الاسم والمرتب والنوع و«بيعول حد» اختياريين:** `null` = ما اتجاوبش، ومش «لأ».
 *   (قاعدة 10: لا صفر مؤكد مكان المجهول — مرتب مش متسجل ≠ مرتب صفر.)
 * - المرتب عدد صحيح بالهللة (قاعدة 1).
 */

export type Gender = 'male' | 'female'

export interface UserProfile {
  displayName: string | null
  salaryMinor: Halalas | null
  payday: number
  gender: Gender | null
  /** هل بيعول حد. بعدين هيتضاف مين بالظبط (HANDOVER §37). */
  supportsDependents: boolean | null
  /**
   * وقت ما خلّص أسئلة البداية؛ null = لسه ما خلصهاش ⇒ الأسئلة بتظهر له
   * (أي حساب، جديد أو قديم — OVERRIDES §26).
   */
  onboardedAt: string | null
}

export const MAX_NAME_LENGTH = 60

export function emptyProfile(): UserProfile {
  return { displayName: null, salaryMinor: null, payday: DEFAULT_PAYDAY, gender: null, supportsDependents: null, onboardedAt: null }
}

export type ProfileField = 'displayName' | 'salaryMinor' | 'payday' | 'gender' | 'supportsDependents'

export type ProfileCheck =
  | { ok: true; profile: UserProfile }
  | { ok: false; field: ProfileField; message: string }

/**
 * قراءة ملف متخزن بأمان: حقل ناقص أو بنوع غلط بيبقى «ما اتجاوبش» (أو 28 ليوم الراتب)
 * بدل ما يوقع فتح التطبيق. مش بيكتب حاجة — التصحيح بيحصل أول حفظ من المستخدم.
 */
export function parseStoredProfile(raw: unknown): UserProfile {
  const data = raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : {}
  const profile = emptyProfile()
  if (typeof data.displayName === 'string' && data.displayName.trim() && data.displayName.trim().length <= MAX_NAME_LENGTH) {
    profile.displayName = data.displayName.trim()
  }
  if (Number.isSafeInteger(data.salaryMinor) && (data.salaryMinor as number) >= 0) profile.salaryMinor = data.salaryMinor as number
  if (Number.isInteger(data.payday) && (data.payday as number) >= 1 && (data.payday as number) <= 31) profile.payday = data.payday as number
  if (data.gender === 'male' || data.gender === 'female') profile.gender = data.gender
  if (typeof data.supportsDependents === 'boolean') profile.supportsDependents = data.supportsDependents
  if (typeof data.onboardedAt === 'string' && data.onboardedAt) profile.onboardedAt = data.onboardedAt
  return profile
}

/** بيتأكد من قيم الملف قبل الحفظ، وبيرجّع الخطأ بجانب حقله (spec/04). */
export function checkProfile(input: UserProfile): ProfileCheck {
  const name = input.displayName === null ? null : input.displayName.trim()
  if (name !== null && name.length > MAX_NAME_LENGTH) {
    return { ok: false, field: 'displayName', message: `الاسم أطول من ${MAX_NAME_LENGTH} حرف` }
  }
  if (input.salaryMinor !== null && (!Number.isSafeInteger(input.salaryMinor) || input.salaryMinor < 0)) {
    return { ok: false, field: 'salaryMinor', message: 'المرتب لازم يكون مبلغ صحيح مش سالب' }
  }
  if (!Number.isInteger(input.payday) || input.payday < 1 || input.payday > 31) {
    return { ok: false, field: 'payday', message: 'يوم الراتب لازم يكون من 1 لـ 31' }
  }
  if (input.gender !== null && input.gender !== 'male' && input.gender !== 'female') {
    return { ok: false, field: 'gender', message: 'اختار ذكر أو أنثى، أو سيبها فاضية' }
  }
  if (input.supportsDependents !== null && typeof input.supportsDependents !== 'boolean') {
    return { ok: false, field: 'supportsDependents', message: 'اختار أيوه أو لأ، أو سيبها فاضية' }
  }
  return { ok: true, profile: { ...input, displayName: name === '' ? null : name } }
}
