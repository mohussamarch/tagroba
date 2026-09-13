import { describe, it, expect } from 'vitest'
import { makeManageProfile } from '../../src/application/useCases/manageProfile'
import { emptyProfile, parseStoredProfile } from '../../src/domain/userProfile'
import { MemoryProfileRepository } from '../../src/infrastructure/memory/memoryProfileRepository'
import { memoryAccount } from '../../src/infrastructure/memory/memoryAccount'

/** قسم الحساب وأسئلة البداية — OVERRIDES §26. */
const clock = { nowIso: () => '2026-09-13T13:40:00.000Z' }
const manageWith = (profiles = new MemoryProfileRepository(), account = memoryAccount()) =>
  ({ profiles, account, manage: makeManageProfile({ profiles, account, clock }) })

describe('ملف المستخدم — حالة الاستخدام', () => {
  it('مستخدم جديد ⇒ ملف فاضي بيوم راتب 28، ومش خطأ', async () => {
    const { profiles, manage } = manageWith()
    expect(await manage.load()).toEqual(emptyProfile())
    expect(await profiles.load()).toBeNull() // القراءة ما كتبتش حاجة
  })

  it('الحفظ بيتأكد الأول: حقل غلط ⇒ مفيش كتابة والخطأ جنب حقله', async () => {
    const { profiles, manage } = manageWith()
    expect(await manage.save({ ...emptyProfile(), payday: 40 })).toMatchObject({ ok: false, field: 'payday' })
    expect(await profiles.load()).toBeNull()
    expect(await manage.save({ ...emptyProfile(), displayName: ' محمد ', payday: 28, salaryMinor: 1_100_000 })).toMatchObject({ ok: true })
    expect(await manage.load()).toMatchObject({ displayName: 'محمد', payday: 28, salaryMinor: 1_100_000, gender: null })
  })

  it('إنهاء أسئلة البداية بيسجل وقتها حتى لو الاختياري فاضي', async () => {
    const { manage } = manageWith()
    expect(await manage.completeOnboarding({ ...emptyProfile(), payday: 27 })).toMatchObject({ ok: true })
    expect(await manage.load()).toEqual({ ...emptyProfile(), payday: 27, onboardedAt: '2026-09-13T13:40:00.000Z' })
  })

  it('الإيميل وتغيير كلمة السر من الحساب الداخل — بإيميل، من غير كلمة سر', async () => {
    const { account, manage } = manageWith()
    expect(manage.email()).toBe('demo@example.com')
    await manage.sendPasswordReset()
    expect(account.resetsSent).toBe(1)
    const { manage: noEmail } = manageWith(new MemoryProfileRepository(), memoryAccount(null))
    expect(noEmail.email()).toBeNull()
    await expect(noEmail.sendPasswordReset()).rejects.toThrow('مالوش إيميل')
  })

  it('ملف متخزن فيه حقول غلط أو ناقصة ما يوقعش القراءة', () => {
    expect(parseStoredProfile(undefined)).toEqual(emptyProfile())
    expect(parseStoredProfile({ displayName: 42, salaryMinor: -5, payday: 99, gender: 'x', supportsDependents: 'yes', onboardedAt: '' }))
      .toEqual(emptyProfile())
    expect(parseStoredProfile({ displayName: 'منى', salaryMinor: 900000, payday: 1, gender: 'female', supportsDependents: false, onboardedAt: 't' }))
      .toEqual({ displayName: 'منى', salaryMinor: 900000, payday: 1, gender: 'female', supportsDependents: false, onboardedAt: 't' })
  })
})
