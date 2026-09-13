import { describe, it, expect } from 'vitest'
import { planIdRepair, emptyStoredData, type StoredData } from '../../src/domain/idRepair'
import type { BackupRow } from '../../src/domain/fullBackup'
import { makeRepairStoredIds, REPAIR_CHUNK } from '../../src/application/useCases/repairStoredIds'
import type { IdRepairPort } from '../../src/application/ports/IdRepairPort'
import { memoryIdRepair } from '../../src/infrastructure/memory/idRepair'
import { memoryRepairBackup } from '../../src/infrastructure/memory/repairBackup'
import { splitForBridge } from '../../src/infrastructure/repairBackup'
import { sanitizeAccountNumbers as redact } from '../../src/infrastructure/firestore/firestoreRepositories'

/**
 * الإصلاح على الموبايل اتقطع لأن حفظ النسخة فتح نافذة نظام رمت التطبيق للخلفية
 * (HANDOVER «إصلاح البيانات القديمة اتقطع»). المطلوب: نسخة مؤكدة الحجم قبل أي كتابة،
 * وكتابة على دفعات تكمّل من مكان ما وقفت.
 */

/** حروف بس — عشان كل معرّف يفضل فريد بعد القص. */
const letters = (n: number) => Array.from({ length: 4 }, (_, i) => String.fromCharCode(97 + (Math.floor(n / 26 ** i) % 26))).join('')

function account(count: number): StoredData {
  const s = emptyStoredData()
  for (let i = 0; i < count; i++) {
    const id = `txn-0mtqh4w${String(i).padStart(5, '0')}-${letters(i)}zzzzzzzz`
    const data: BackupRow = { id: redact(id), occurredAt: '2026-09-01', amountMinor: 100, observedDirection: 'out', createdAt: '2026-09-07T10:34:00.000Z' }
    s.transactions.push({ docId: id, data })
  }
  return s
}

let tick = 0
const clock = { nowIso: () => new Date(Date.UTC(2026, 8, 13, 0, 0, tick++)).toISOString() }

function cutAfter(store: IdRepairPort, chunks: number): IdRepairPort {
  let calls = 0
  return { readAll: () => store.readAll(), apply: async (p) => { if (calls++ >= chunks) throw new Error('انقطع الاتصال'); return store.apply(p) } }
}

describe('نسخة ما قبل الإصلاح والاستئناف', () => {
  it('يحفظ نسخة فيها بالظبط المستندات اللي هتتكتب، ويتأكد من حجمها، قبل أول كتابة', async () => {
    const store = memoryIdRepair(account(3))
    const backup = memoryRepairBackup()
    const order: string[] = []
    const port: IdRepairPort = { readAll: store.readAll, apply: async (p) => { order.push(`write:${backup.files.size}`); return store.apply(p) } }
    const repair = makeRepairStoredIds({ port, redact, backup, clock })
    const outcome = await repair.apply((await repair.preview()).plan)
    expect(order).toEqual(['write:1']) // الملف كان موجود قبل أول كتابة
    const [name, content] = [...backup.files][0]
    expect(name).toMatch(/^masroufy-before-repair-2026-09-13T00-00-\d\d-\d{3}Z\.json$/)
    const saved = JSON.parse(content)
    expect(saved.documents).toHaveLength(3)
    expect(saved.documents[0].data.id).toContain('****') // الشكل قبل الإصلاح
    expect(outcome.backup?.bytes).toBe(outcome.backup?.expectedBytes)
    expect(outcome.backup?.bytes).toBeGreaterThan(0)
  })

  it('نسخة ناقصة ⇒ يرفض وما يكتبش ولا مستند', async () => {
    const store = memoryIdRepair(account(3))
    const repair = makeRepairStoredIds({ port: store, redact, backup: memoryRepairBackup({ truncate: true }), clock })
    await expect(repair.apply((await repair.preview()).plan)).rejects.toThrow('ناقصة')
    expect(planIdRepair(store.snapshot(), redact).patches).toHaveLength(3)
  })

  it('انقطاع في النص ⇒ اللي اتكتب يفضل صحيح، والفحص التاني يلاقي الباقي بس ويكمّله', async () => {
    const total = REPAIR_CHUNK + 50
    const store = memoryIdRepair(account(total))
    const backup = memoryRepairBackup()
    const progress: number[] = []
    const first = makeRepairStoredIds({ port: cutAfter(store, 1), redact, backup, clock })
    await expect(first.apply((await first.preview()).plan, (p) => progress.push(p.written)))
      .rejects.toThrow(`اتكتب ${REPAIR_CHUNK} من ${total}`)
    expect(progress).toEqual([0, REPAIR_CHUNK])

    const second = makeRepairStoredIds({ port: store, redact, backup, clock })
    const again = await second.preview()
    expect(again.plan.patches).toHaveLength(50)
    expect((await second.apply(again.plan)).written).toBe(50)
    expect(planIdRepair(store.snapshot(), redact).patches).toEqual([])
    expect(backup.files.size).toBe(2) // نسخة لكل تشغيل، ما حدش بيكتب فوق التاني
  })

  it('مفيش جديد يتكتب ⇒ لا نسخة ولا كتابة', async () => {
    const backup = memoryRepairBackup()
    const repair = makeRepairStoredIds({ port: memoryIdRepair(), redact, backup, clock })
    expect(await repair.apply((await repair.preview()).plan)).toEqual({ written: 0, skipped: 0, backup: null })
    expect(backup.files.size).toBe(0)
  })

  it('تقسيم النص لجسر أندرويد ما يفصلش رمز تعبيري بين قطعتين', () => {
    const text = 'ab😀cd'
    const parts = splitForBridge(text, 3)
    expect(parts.join('')).toBe(text)
    expect(parts.every((part) => !/[\uD800-\uDBFF]$/.test(part))).toBe(true)
    expect(splitForBridge('')).toEqual([''])
  })
})
