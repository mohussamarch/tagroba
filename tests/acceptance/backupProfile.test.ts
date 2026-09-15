import { describe, it, expect } from 'vitest'
import { makeFullBackup } from '../../src/application/useCases/fullBackup'
import { memoryFullBackup } from '../../src/infrastructure/memory/fullBackup'
import { canonicalBackup, emptyBackupData } from '../../src/domain/fullBackup'
import { backupDigest } from '../../src/infrastructure/backupDigest'

/** ملف الحساب في النسخة الشاملة — OVERRIDES §26. بيانات وهمية. */
const now = '2026-09-15T00:00:00Z'
const profile = {
  displayName: 'حساب تجريبي', salaryMinor: 900000, payday: 25, gender: 'male', supportsDependents: true,
  dependentKinds: ['children'], hasCar: false, renter: null, domesticWorker: null, business: null, onboardedAt: now,
}

describe('ملف الحساب في النسخة الشاملة', () => {
  it('النسخة بتشيل الملف، والاستعادة بتضيفه لحساب مالوش ملف', async () => {
    const file = await makeFullBackup(memoryFullBackup(emptyBackupData(), profile), backupDigest).create(now)
    expect(file.profile).toEqual(profile)
    const target = memoryFullBackup()
    const service = makeFullBackup(target, backupDigest)
    const plan = await service.plan(JSON.stringify(file))
    expect(plan.profile).toEqual({ incoming: true, toAdd: true })
    expect(plan.totalToAdd).toBe(1)
    const outcome = await service.apply(plan.file)
    expect(outcome.totalAdded).toBe(1)
    expect(await target.readProfile()).toEqual(profile)
    expect((await service.apply(file)).totalAdded).toBe(0)
  })

  it('الحساب اللي ليه ملف ما يتكتبش فوقه', async () => {
    const file = await makeFullBackup(memoryFullBackup(emptyBackupData(), profile), backupDigest).create(now)
    const mine = { ...profile, displayName: 'الملف الحالي', payday: 28 }
    const target = memoryFullBackup(emptyBackupData(), mine)
    const service = makeFullBackup(target, backupDigest)
    expect((await service.plan(JSON.stringify(file))).profile).toEqual({ incoming: true, toAdd: false })
    expect((await service.apply(file)).totalAdded).toBe(0)
    expect(await target.readProfile()).toEqual(mine)
  })

  it('البصمة بتغطي الملف: أي تعديل فيه بيترفض قبل أي كتابة', async () => {
    const file = await makeFullBackup(memoryFullBackup(emptyBackupData(), profile), backupDigest).create(now)
    ;(file.profile as Record<string, unknown>).salaryMinor = 1
    const target = memoryFullBackup()
    await expect(makeFullBackup(target, backupDigest).apply(file)).rejects.toThrow(/بصمة/)
    expect(await target.readProfile()).toBeNull()
  })

  it('ملف بقيمة غلط بيترفض، والنسخة القديمة اللي مالهاش ملف خالص لسه مقبولة', async () => {
    const bad = await makeFullBackup(memoryFullBackup(emptyBackupData(), { ...profile, payday: 40 }), backupDigest).create(now).catch((e) => e)
    expect(bad).toBeInstanceOf(Error)

    const old = await makeFullBackup(memoryFullBackup(), backupDigest).create(now)
    delete (old as { profile?: unknown }).profile
    old.checksum = await backupDigest(canonicalBackup(old.data))
    const target = memoryFullBackup()
    const plan = await makeFullBackup(target, backupDigest).plan(JSON.stringify(old))
    expect(plan.profile).toEqual({ incoming: false, toAdd: false })
    expect((await makeFullBackup(target, backupDigest).apply(old)).totalAdded).toBe(0)
  })
})
