import { describe, it, expect } from 'vitest'
import { makeAppLock } from '../../src/application/useCases/appLock'
import { LOCK_AFTER_BACKGROUND_MS } from '../../src/domain/appLock'
import { memoryAppLockSettings, memoryDeviceLock } from '../../src/infrastructure/memory/deviceLock'

/** OVERRIDES §21 — اختياري من الإعدادات، وتشغيله وإيقافه محتاجين تأكيد صاحب الجوال. */
describe('قفل التطبيق — حالة الاستخدام', () => {
  const t = 1_800_000_000_000
  const now = () => t

  it('مقفول في الأول، ومايسألش عند الفتح', () => {
    const lock = makeAppLock({ device: memoryDeviceLock(), settings: memoryAppLockSettings(), now })
    expect(lock.isEnabled()).toBe(false)
    expect(lock.needsUnlock(null)).toBe(false)
  })

  it('التشغيل محتاج تأكيد ناجح — الإلغاء ما يشغّلش حاجة', async () => {
    const device = memoryDeviceLock({ results: ['cancelled', 'ok'] })
    const lock = makeAppLock({ device, settings: memoryAppLockSettings(), now })
    expect(await lock.enable()).toEqual({ ok: false, message: expect.stringContaining('اتلغى') })
    expect(lock.isEnabled()).toBe(false)
    expect(await lock.enable()).toEqual({ ok: true })
    expect(lock.isEnabled()).toBe(true)
    expect(device.prompts).toHaveLength(2)
    expect(lock.needsUnlock(null)).toBe(true)
    expect(lock.needsUnlock(t - LOCK_AFTER_BACKGROUND_MS + 1)).toBe(false)
  })

  it('الإيقاف كمان محتاج تأكيد — حد ماسك الجوال مفتوح ما يقدرش يشيله', async () => {
    const lock = makeAppLock({ device: memoryDeviceLock({ results: ['failed'] }), settings: memoryAppLockSettings(true), now })
    expect((await lock.disable()).ok).toBe(false)
    expect(lock.isEnabled()).toBe(true)
  })

  it('جوال من غير قفل شاشة ⇒ رسالة واضحة ومفيش سؤال', async () => {
    const device = memoryDeviceLock({ availability: { available: false, code: 'NONE_ENROLLED' } })
    const lock = makeAppLock({ device, settings: memoryAppLockSettings(), now })
    expect(await lock.enable()).toEqual({ ok: false, message: expect.stringContaining('قفل شاشة') })
    expect(device.prompts).toEqual([])
  })

  it('القفل متشغّل والجوال بقى مالوش قفل خالص ⇒ يتوقف بدل ما يقفل المستخدم بره التطبيق', async () => {
    const device = memoryDeviceLock()
    const settings = memoryAppLockSettings(true)
    const lock = makeAppLock({ device, settings, now })
    expect(await lock.releaseIfDeviceHasNoLock()).toBe(false)
    device.setAvailability({ available: false, code: 'HW_UNAVAILABLE' })
    expect(await lock.releaseIfDeviceHasNoLock()).toBe(false) // عطل مؤقت: يفضل مقفول
    device.setAvailability({ available: false, code: 'NONE_ENROLLED' })
    expect(await lock.releaseIfDeviceHasNoLock()).toBe(true)
    expect(lock.isEnabled()).toBe(false)
  })

  it('الفتح: النتيجة ورسالتها', async () => {
    const lock = makeAppLock({ device: memoryDeviceLock({ results: ['failed', 'ok'] }), settings: memoryAppLockSettings(true), now })
    expect((await lock.unlock()).message).toContain('ما اتأكدش')
    expect(await lock.unlock()).toEqual({ result: 'ok', message: null })
  })
})
