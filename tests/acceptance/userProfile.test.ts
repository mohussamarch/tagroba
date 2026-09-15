import { describe, it, expect } from 'vitest'
import { checkProfile, emptyProfile, MAX_NAME_LENGTH, parseStoredProfile } from '../../src/domain/userProfile'

/** ملف المستخدم — OVERRIDES §26: الراتب لكل مستخدم، والباقي اختياري. */
describe('ملف المستخدم', () => {
  it('الملف الفاضي صالح: يوم الراتب 28 والباقي «ما اتجاوبش» مش صفر', () => {
    const profile = emptyProfile()
    expect(profile).toEqual({
      displayName: null, salaryMinor: null, payday: 28, gender: null, supportsDependents: null,
      dependentKinds: null, hasCar: null, renter: null, domesticWorker: null, business: null, onboardedAt: null,
    })
    expect(checkProfile(profile)).toEqual({ ok: true, profile })
  })

  it('الاسم بيتقص من المسافات، والاسم الفاضي بيبقى «ما اتجاوبش»', () => {
    expect(checkProfile({ ...emptyProfile(), displayName: '  محمد  ' })).toMatchObject({ ok: true, profile: { displayName: 'محمد' } })
    expect(checkProfile({ ...emptyProfile(), displayName: '   ' })).toMatchObject({ ok: true, profile: { displayName: null } })
    expect(checkProfile({ ...emptyProfile(), displayName: 'ا'.repeat(MAX_NAME_LENGTH + 1) })).toMatchObject({ ok: false, field: 'displayName' })
  })

  it('المرتب عدد صحيح بالهللة ومش سالب', () => {
    expect(checkProfile({ ...emptyProfile(), salaryMinor: 1_100_000 }).ok).toBe(true)
    expect(checkProfile({ ...emptyProfile(), salaryMinor: 0 }).ok).toBe(true)
    expect(checkProfile({ ...emptyProfile(), salaryMinor: -1 })).toMatchObject({ ok: false, field: 'salaryMinor' })
    expect(checkProfile({ ...emptyProfile(), salaryMinor: 10.5 })).toMatchObject({ ok: false, field: 'salaryMinor' })
  })

  it('يوم الراتب من 1 لـ 31', () => {
    expect(checkProfile({ ...emptyProfile(), payday: 1 }).ok).toBe(true)
    expect(checkProfile({ ...emptyProfile(), payday: 31 }).ok).toBe(true)
    expect(checkProfile({ ...emptyProfile(), payday: 0 })).toMatchObject({ ok: false, field: 'payday' })
    expect(checkProfile({ ...emptyProfile(), payday: 32 })).toMatchObject({ ok: false, field: 'payday' })
    expect(checkProfile({ ...emptyProfile(), payday: 27.5 })).toMatchObject({ ok: false, field: 'payday' })
  })

  it('النوع و«بيعول حد» يا إما قيمة معروفة يا إما فاضيين', () => {
    expect(checkProfile({ ...emptyProfile(), gender: 'female', supportsDependents: true }).ok).toBe(true)
    expect(checkProfile({ ...emptyProfile(), gender: 'other' as never })).toMatchObject({ ok: false, field: 'gender' })
    expect(checkProfile({ ...emptyProfile(), supportsDependents: 'yes' as never })).toMatchObject({ ok: false, field: 'supportsDependents' })
  })

  it('أسئلة التصنيفات (OVERRIDES §28.1): أيوه أو لأ أو فاضية، و«بيعول مين» من القايمة بس', () => {
    expect(checkProfile({ ...emptyProfile(), hasCar: true, renter: false, domesticWorker: null, business: true }).ok).toBe(true)
    expect(checkProfile({ ...emptyProfile(), hasCar: 'yes' as never })).toMatchObject({ ok: false, field: 'hasCar' })
    expect(checkProfile({ ...emptyProfile(), business: 1 as never })).toMatchObject({ ok: false, field: 'business' })
    expect(checkProfile({ ...emptyProfile(), dependentKinds: ['cousin'] as never })).toMatchObject({ ok: false, field: 'dependentKinds' })
    expect(checkProfile({ ...emptyProfile(), supportsDependents: true, dependentKinds: ['parents', 'spouse', 'spouse'] }))
      .toMatchObject({ ok: true, profile: { dependentKinds: ['spouse', 'parents'] } })
    // «لأ» على بيعول حد بتمسح «مين»
    expect(checkProfile({ ...emptyProfile(), supportsDependents: false, dependentKinds: ['children'] }))
      .toMatchObject({ ok: true, profile: { dependentKinds: null } })
  })

  it('الملف المتخزن القديم من غير الأسئلة الجديدة بيتقري «ما اتجاوبش»، والقيم الغلط بتتجاهل', () => {
    expect(parseStoredProfile({ payday: 25, supportsDependents: true })).toEqual({ ...emptyProfile(), payday: 25, supportsDependents: true })
    expect(parseStoredProfile({ hasCar: true, renter: 'no', dependentKinds: ['children', 'spouse'] }))
      .toMatchObject({ hasCar: true, renter: null, dependentKinds: ['spouse', 'children'] })
    expect(parseStoredProfile({ dependentKinds: ['spouse', 'x'] }).dependentKinds).toBeNull()
  })
})
