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
 * - **معلومات بتظهر تصنيفات (OVERRIDES §28.1):** عنده سيارة، بيعول مين، ساكن بإيجار، عنده عمالة
 *   منزلية، عنده شغل خاص. `null` = ما اتجاوبش ⇒ التصنيف المشروط مخفي.
 */

export type Gender = 'male' | 'female'

/** «بتعول مين؟» — الزوج أو الزوجة أو الأولاد بيظهروا تصنيف «الأسرة والأطفال». */
export type DependentKind = 'spouse' | 'children' | 'parents'
export const DEPENDENT_KINDS: readonly DependentKind[] = ['spouse', 'children', 'parents']

export interface UserProfile {
  displayName: string | null
  salaryMinor: Halalas | null
  payday: number
  gender: Gender | null
  /** هل بيعول حد. */
  supportsDependents: boolean | null
  /** بيعول مين بالظبط؛ `null` = ما اتجاوبش. بيتسأل بس لو «بيعول حد» = أيوه. */
  dependentKinds: DependentKind[] | null
  hasCar: boolean | null
  renter: boolean | null
  domesticWorker: boolean | null
  business: boolean | null
  /**
   * وقت ما خلّص أسئلة البداية؛ null = لسه ما خلصهاش ⇒ الأسئلة بتظهر له
   * (أي حساب، جديد أو قديم — OVERRIDES §26).
   */
  onboardedAt: string | null
}

export const MAX_NAME_LENGTH = 60

export function emptyProfile(): UserProfile {
  return {
    displayName: null, salaryMinor: null, payday: DEFAULT_PAYDAY, gender: null, supportsDependents: null,
    dependentKinds: null, hasCar: null, renter: null, domesticWorker: null, business: null, onboardedAt: null,
  }
}

export type ProfileField =
  | 'displayName' | 'salaryMinor' | 'payday' | 'gender' | 'supportsDependents'
  | 'dependentKinds' | 'hasCar' | 'renter' | 'domesticWorker' | 'business'

export type ProfileCheck =
  | { ok: true; profile: UserProfile }
  | { ok: false; field: ProfileField; message: string }

const YES_NO_FIELDS = ['supportsDependents', 'hasCar', 'renter', 'domesticWorker', 'business'] as const

const isDependentKind = (value: unknown): value is DependentKind =>
  typeof value === 'string' && (DEPENDENT_KINDS as readonly string[]).includes(value)

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
  for (const field of YES_NO_FIELDS) if (typeof data[field] === 'boolean') profile[field] = data[field] as boolean
  if (Array.isArray(data.dependentKinds) && data.dependentKinds.every(isDependentKind)) {
    profile.dependentKinds = DEPENDENT_KINDS.filter((kind) => (data.dependentKinds as unknown[]).includes(kind))
  }
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
  for (const field of YES_NO_FIELDS) {
    if (input[field] !== null && typeof input[field] !== 'boolean') {
      return { ok: false, field, message: 'اختار أيوه أو لأ، أو سيبها فاضية' }
    }
  }
  if (input.dependentKinds !== null && (!Array.isArray(input.dependentKinds) || !input.dependentKinds.every(isDependentKind))) {
    return { ok: false, field: 'dependentKinds', message: 'اختار الزوج أو الزوجة أو الأولاد أو الأهل، أو سيبها فاضية' }
  }
  // «لأ» على بيعول حد ⇒ مفيش حد بيعوله؛ الترتيب ثابت ومن غير تكرار
  const dependentKinds = input.supportsDependents === false ? null
    : input.dependentKinds === null ? null
      : DEPENDENT_KINDS.filter((kind) => input.dependentKinds!.includes(kind))
  return { ok: true, profile: { ...input, displayName: name === '' ? null : name, dependentKinds } }
}
