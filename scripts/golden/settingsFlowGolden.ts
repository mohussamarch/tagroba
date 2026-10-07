import { makeSharedMerchants } from '../../src/application/useCases/sharedMerchants'
import { makeAppLock } from '../../src/application/useCases/appLock'
import { MemorySharedMerchantCatalog, MemorySyncCursor } from '../../src/infrastructure/memory/memorySharedMerchantCatalog'
import { MemoryMerchantRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { memoryDeviceLock, memoryAppLockSettings } from '../../src/infrastructure/memory/deviceLock'
import { FixedClock } from '../../src/infrastructure/memory/memorySupport'
import type { SharedMerchantEntry } from '../../src/domain/sharedMerchantCatalog'
import type { DeviceLockAvailability, LockResult } from '../../src/application/ports/DeviceLockPort'
import type { EconomicKind } from '../../src/domain/entities/economicKind'
import type { Merchant } from '../../src/domain/entities/types'
import { recordAsync, type GoldenCase } from './goldenKit'

/**
 * قاعدة التجار المشتركة (OVERRIDES §25 و§25.1) + قفل التطبيق (§21). ⚠️ بيانات وهمية بالكامل — أسماء محلات مخترعة.
 * القاعدة المشتركة ما فيهاش مبالغ ولا تواريخ عمليات ولا مين ضاف — اسم وتصنيف من الشجرة بس.
 */

const NOW = '2026-09-22T10:00:00.000Z'
const tree = ['food', 'fuel', 'shopping']

const baseline: Merchant[] = [
  { id: 'base-cafe', displayName: 'TEST CAFE', normalizedName: 'test cafe', verifiedCategoryId: 'food' },
  { id: 'base-mart', displayName: 'TEST MART', normalizedName: 'test mart' },
]
const account: Merchant[] = [
  { id: 'acc-fuel', displayName: 'TEST FUEL', normalizedName: 'test fuel' },
  { id: 'acc-shop', displayName: 'TEST SHOP', normalizedName: 'test shop', verifiedCategoryId: 'shopping' },
]
const remote: SharedMerchantEntry[] = [
  { normalizedName: 'test fuel', displayName: 'TEST FUEL', aliases: [], categoryId: 'fuel', confirmed: true, updatedAt: '2026-09-20T00:00:00.000Z' },
  { normalizedName: 'test shop', displayName: 'TEST SHOP', aliases: [], categoryId: 'food', confirmed: true, updatedAt: '2026-09-20T00:00:00.000Z' },
  { normalizedName: 'new bakery', displayName: 'NEW BAKERY', aliases: ['bakery 2'], categoryId: 'food', confirmed: true, updatedAt: '2026-09-21T00:00:00.000Z' },
  { normalizedName: 'test cafe', displayName: 'TEST CAFE', aliases: [], categoryId: 'shopping', confirmed: false, updatedAt: '2026-09-21T00:00:00.000Z' },
  { normalizedName: 'maybe store', displayName: 'MAYBE STORE', aliases: [], categoryId: 'food', confirmed: false, updatedAt: '2026-09-21T00:00:00.000Z' },
  { normalizedName: 'odd place', displayName: 'ODD PLACE', aliases: [], categoryId: 'not-in-tree', confirmed: true, updatedAt: '2026-09-21T00:00:00.000Z' },
  // اتأكد من اللوحة من غير ما `updatedAt` يتغيّر ⇒ ما بيوصلش غير بالمراجعة اليومية
  { normalizedName: 'quiet grill', displayName: 'QUIET GRILL', aliases: [], categoryId: 'food', confirmed: true, updatedAt: '2026-08-01T00:00:00.000Z' },
]

type SharedStep =
  | { kind: 'sync' }
  | { kind: 'contribute'; economicKind: EconomicKind; observedDirection: 'in' | 'out'; rawMerchantName: string | null; categoryId: string }

async function sharedMerchantsCases() {
  const cases: GoldenCase[] = []
  const runs: { since: string | null; confirmedAt: string | null; steps: SharedStep[] }[] = [
    { since: null, confirmedAt: null, steps: [{ kind: 'sync' }, { kind: 'sync' }] },
    { since: '2026-09-10T00:00:00.000Z', confirmedAt: '2026-09-21T11:00:00.000Z', steps: [{ kind: 'sync' }] },
    { since: '2026-09-10T00:00:00.000Z', confirmedAt: '2026-09-21T09:59:59.999Z', steps: [{ kind: 'sync' }] },
    { since: '2026-09-10T00:00:00.000Z', confirmedAt: '2026-09-21T10:00:00.000Z', steps: [{ kind: 'sync' }] },
    { since: '2026-09-10T00:00:00.000Z', confirmedAt: null, steps: [{ kind: 'sync' }] },
    { since: '2026-09-25T00:00:00.000Z', confirmedAt: '2026-09-22T09:00:00.000Z', steps: [{ kind: 'sync' }] },
    {
      since: '2026-09-25T00:00:00.000Z', confirmedAt: '2026-09-22T09:00:00.000Z', steps: [
        { kind: 'contribute', economicKind: 'purchase', observedDirection: 'out', rawMerchantName: '  Brand   New Place ', categoryId: 'food' },
        { kind: 'contribute', economicKind: 'purchase', observedDirection: 'out', rawMerchantName: 'Maybe Store', categoryId: 'fuel' },
        { kind: 'contribute', economicKind: 'purchase', observedDirection: 'out', rawMerchantName: 'maybe store', categoryId: 'food' },
        { kind: 'contribute', economicKind: 'purchase', observedDirection: 'out', rawMerchantName: 'TEST CAFE', categoryId: 'shopping' },
        { kind: 'contribute', economicKind: 'purchase', observedDirection: 'out', rawMerchantName: 'New Bakery', categoryId: 'fuel' },
        { kind: 'contribute', economicKind: 'salary', observedDirection: 'in', rawMerchantName: 'EMPLOYER', categoryId: 'food' },
        { kind: 'contribute', economicKind: 'purchase', observedDirection: 'out', rawMerchantName: 'SHOP 12345', categoryId: 'food' },
        { kind: 'contribute', economicKind: 'purchase', observedDirection: 'out', rawMerchantName: 'Somewhere', categoryId: 'my-own-category' },
        { kind: 'contribute', economicKind: 'purchase', observedDirection: 'out', rawMerchantName: null, categoryId: 'food' },
      ],
    },
  ]
  for (const r of runs) {
    cases.push(await recordAsync({ baseline, account, remote, tree, ...r }, async () => {
      const catalog = new MemorySharedMerchantCatalog(remote)
      const merchants = new MemoryMerchantRepository(account)
      const cursor = new MemorySyncCursor(r.since)
      const confirmedCursor = new MemorySyncCursor(r.confirmedAt)
      const shared = makeSharedMerchants({ catalog, merchants, baseline, treeCategoryIds: new Set(tree), cursor, confirmedCursor, clock: new FixedClock(NOW) })
      const out: unknown[] = []
      for (const step of r.steps) {
        if (step.kind === 'sync') out.push(await shared.sync())
        else out.push(await shared.contribute({ economicKind: step.economicKind, observedDirection: step.observedDirection, rawMerchantName: step.rawMerchantName ?? undefined }, step.categoryId))
      }
      return {
        steps: out,
        cursorAfter: cursor.read(),
        confirmedCursorAfter: confirmedCursor.read(),
        storedMerchants: await merchants.listAll(),
        catalogAfter: await catalog.listChangedSince(null),
      }
    }))
  }
  return cases
}

type LockStep =
  | { kind: 'isEnabled' | 'enable' | 'disable' | 'unlock' | 'release' | 'supported' }
  | { kind: 'needsUnlock'; hiddenAt: number | null }

async function appLockCases() {
  const cases: GoldenCase[] = []
  const NOW_MS = 1_000_000_000
  const runs: { availability?: DeviceLockAvailability; results?: LockResult[]; enabled: boolean; steps: LockStep[] }[] = [
    { enabled: false, steps: [{ kind: 'supported' }, { kind: 'isEnabled' }, { kind: 'needsUnlock', hiddenAt: null }, { kind: 'enable' }, { kind: 'isEnabled' }, { kind: 'needsUnlock', hiddenAt: null }] },
    { enabled: true, steps: [{ kind: 'needsUnlock', hiddenAt: NOW_MS - 299_999 }, { kind: 'needsUnlock', hiddenAt: NOW_MS - 300_000 }, { kind: 'needsUnlock', hiddenAt: NOW_MS + 5 }] },
    { enabled: false, results: ['cancelled'], steps: [{ kind: 'enable' }, { kind: 'isEnabled' }] },
    { enabled: true, results: ['failed', 'ok'], steps: [{ kind: 'disable' }, { kind: 'isEnabled' }, { kind: 'disable' }, { kind: 'isEnabled' }] },
    { enabled: true, results: ['ok', 'cancelled', 'unavailable'], steps: [{ kind: 'unlock' }, { kind: 'unlock' }, { kind: 'unlock' }] },
    { enabled: false, availability: { available: false, code: 'NO_HARDWARE' }, steps: [{ kind: 'enable' }] },
    { enabled: false, availability: { available: false, code: 'SOMETHING_NEW' }, steps: [{ kind: 'enable' }] },
    { enabled: true, availability: { available: false, code: 'NONE_ENROLLED' }, steps: [{ kind: 'release' }, { kind: 'isEnabled' }] },
    { enabled: true, availability: { available: false, code: 'HW_UNAVAILABLE' }, steps: [{ kind: 'release' }, { kind: 'isEnabled' }] },
    { enabled: true, steps: [{ kind: 'release' }, { kind: 'isEnabled' }] },
  ]
  for (const r of runs) {
    cases.push(await recordAsync({ ...r, now: NOW_MS }, async () => {
      const device = memoryDeviceLock({ availability: r.availability, results: r.results })
      const lock = makeAppLock({ device, settings: memoryAppLockSettings(r.enabled), now: () => NOW_MS })
      const out: unknown[] = []
      for (const step of r.steps) {
        switch (step.kind) {
          case 'supported': out.push(lock.supported); break
          case 'isEnabled': out.push(lock.isEnabled()); break
          case 'needsUnlock': out.push(lock.needsUnlock(step.hiddenAt)); break
          case 'enable': out.push(await lock.enable()); break
          case 'disable': out.push(await lock.disable()); break
          case 'unlock': out.push(await lock.unlock()); break
          case 'release': out.push(await lock.releaseIfDeviceHasNoLock()); break
        }
      }
      return { steps: out, prompts: device.prompts }
    }))
  }
  return cases
}

export async function settingsFlowGolden() {
  return { sharedMerchants: await sharedMerchantsCases(), appLock: await appLockCases() }
}
