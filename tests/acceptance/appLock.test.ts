import { describe, it, expect } from 'vitest'
import { shouldLock, LOCK_AFTER_BACKGROUND_MS } from '../../src/domain/appLock'

/** OVERRIDES §21: اختياري، يتقفل عند الفتح وبعد خمس دقايق في الخلفية. */
describe('قفل التطبيق — إمتى يسأل عن البصمة', () => {
  const t = 1_800_000_000_000

  it('مقفول من الإعدادات ⇒ عمره ما يسأل', () => {
    expect(shouldLock({ enabled: false, hiddenAt: null, now: t })).toBe(false)
    expect(shouldLock({ enabled: false, hiddenAt: t - 60 * 60_000, now: t })).toBe(false)
  })

  it('متشغّل وفتح التطبيق من الأول ⇒ يسأل', () => {
    expect(shouldLock({ enabled: true, hiddenAt: null, now: t })).toBe(true)
  })

  it('رجوع قبل خمس دقايق ⇒ ما يسألش، وعند الخمسة بالظبط ⇒ يسأل', () => {
    expect(shouldLock({ enabled: true, hiddenAt: t, now: t + LOCK_AFTER_BACKGROUND_MS - 1 })).toBe(false)
    expect(shouldLock({ enabled: true, hiddenAt: t, now: t + LOCK_AFTER_BACKGROUND_MS })).toBe(true)
  })

  it('ساعة الجهاز رجعت لورا ⇒ يقفل احتياطًا', () => {
    expect(shouldLock({ enabled: true, hiddenAt: t, now: t - 1000 })).toBe(true)
  })
})
