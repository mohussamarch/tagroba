import { describe, it, expect } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { createDemoContainer } from '../../src/app/demoContainer'
import { periodForDate } from '../../src/domain/period'
import { makeResumeStagedBatch } from '../../src/application/useCases/resumeStagedBatch'
import {
  MemorySourceRecordRepository,
  MemoryTransactionRepository,
} from '../../src/infrastructure/memory/memoryRepositories'
import type { ImportBatch } from '../../src/domain/entities/types'

/**
 * بلاغ المالك 2026-09-11: «مفيش ولا عملية ظاهرة، وحتى لما حملت الكشف
 * طلع مسجل آلاف العمليات وبرضه مش ظاهرة».
 * (١) الاستيراد ثم العرض عبر نفس حالات الاستخدام، على الكشف الحقيقي.
 * (٢) فتح التطبيق مع دفعة معلّقة معرّفها المحفوظ اتقص (عطل sanitize).
 */

const CSV_PATH = resolve(__dirname, '../../files/transactions_full.csv')

describe('الاستيراد ثم ظهور العمليات', () => {
  if (!existsSync(CSV_PATH)) {
    it('ملف الكشف الحقيقي غير موجود', () => { throw new Error(`${CSV_PATH} مش موجود — الاختبار بيفشل عمدًا`) })
    return
  }

  it('العمليات المستوردة تظهر في شاشة العمليات والرئيسية لفترة آخر الكشف', async () => {
    const user = createDemoContainer().forUser('visibility')
    await user.seedWallets(false)
    const request = { fileName: 'statement.csv', content: readFileSync(CSV_PATH, 'utf8'),
      accountIdentity: 'wallet-bank', walletId: 'wallet-bank', sourceType: 'csv_legacy' as const }
    const preview = await user.importStatement.preview(request)
    expect(preview.counts.newCount).toBeGreaterThan(1000)
    const batch = await user.importStatement.commit(request, preview)
    expect(batch.counts.imported).toBeGreaterThan(1000)

    const period = periodForDate('2026-09-04', 28)
    const screen = await user.loadTransactionsScreen({ period })
    expect(screen.totalCount).toBeGreaterThan(0)
    expect(screen.transactions.every((t) => /^\d{4}-\d{2}-\d{2}$/.test(t.occurredAt))).toBe(true)
    const home = await user.loadHomeScreen({ period, today: '2026-09-11', payday: 28, includeHistory: false })
    expect(JSON.stringify(home)).not.toContain('"transactionCount":0')

    // إعادة تحميل نفس الكشف: «مسجل قبل كده» ولا يضيف شيئًا
    const again = await user.importStatement.preview(request)
    expect(again.previousBatch).not.toBeNull()
  }, 60_000)
})

it('دفعة معلّقة معرّفها المحفوظ مختلف عن مسار وثيقتها لا تعطل فتح التطبيق', async () => {
  const stored: ImportBatch = { id: 'batch-0mtqh4w****0000-4n444r1n275n', sourceType: 'csv_legacy', fileHash: 'h',
    fileName: 'statement.csv', importedAt: '2026-09-10T00:00:00Z', state: 'staged',
    counts: { total: 1, imported: 1, duplicates: 0, similar: 0, conflicts: 0, invalid: 0 } }
  const batches = {
    listRecent: async () => [stored], // Firestore بيرجع الحقل المقصوص
    findById: async (id: string) => (id === 'batch-0mtqh4w00000-4n444r1n275n' ? stored : null), // الوثيقة بمسارها الأصلي
    updateState: async () => {}, save: async () => {}, findByFileHash: async () => null,
  }
  const resume = makeResumeStagedBatch({ txns: new MemoryTransactionRepository(), sources: new MemorySourceRecordRepository(), batches: batches as never })
  await expect(resume.cleanupAll()).resolves.toBeDefined()
})
