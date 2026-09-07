import { describe, it, expect } from 'vitest'
import {
  makeExportBackup,
  checkBackup,
  BACKUP_SCHEMA_VERSION,
} from '../../src/application/useCases/exportBackup'
import { makeImportStatement } from '../../src/application/useCases/importStatement'
import { makeSeedWallets, BANK_WALLET_ID } from '../../src/application/useCases/seedWallets'
import {
  MemoryBudgetRepository,
  MemoryCategoryRepository,
  MemoryImportBatchRepository,
  MemoryMerchantRepository,
  MemoryRuleRepository,
  MemorySourceRecordRepository,
  MemoryTransactionRepository,
  MemoryWalletRepository,
  PassthroughUnitOfWork,
  SequentialIdGenerator,
  FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import { buildCategories } from '../../src/infrastructure/import/referenceLoader'
import { parseCsv } from '../../src/infrastructure/import/csvReader'
import { parseRows } from '../../src/infrastructure/import/schemas'

/**
 * OVERRIDES §2: «التصدير والنسخ الاحتياطي المحلي يبقيان مطلبين،
 *                ولا يُلغيهما وجود السحابة.»
 * spec/03: «النسخة لها schemaVersion **وتحقق سلامة**، والاستعادة
 *           **لا تكرر العمليات** أو تخلط العملات.»
 */

const CSV =
  '﻿التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل\n' +
  '2025/01/01,96.47,0.0,4741.36,"ALSKARYAH, RIYADH",متفرقات,شراء,تفاصيل\n' +
  '2025/01/02,79.55,0.0,4661.81,OTHAIM,بقالة وسوبرماركت,شراء,تفاصيل\n' +
  '2025/01/03,0.0,375.0,5036.81,,تحويلات,تحويل,تفاصيل\n'

async function makeSystem() {
  const txns = new MemoryTransactionRepository()
  const sources = new MemorySourceRecordRepository()
  const batches = new MemoryImportBatchRepository()
  const wallets = new MemoryWalletRepository()
  const categories = new MemoryCategoryRepository(buildCategories([]))
  const rules = new MemoryRuleRepository([])
  const merchants = new MemoryMerchantRepository([])
  const budgets = new MemoryBudgetRepository()

  await makeSeedWallets({ wallets })(true)

  const importer = makeImportStatement({
    txns, sources, batches, merchants, categories, rules,
    uow: new PassthroughUnitOfWork(),
    ids: new SequentialIdGenerator(),
    clock: new FixedClock('2026-09-07T00:00:00.000Z'),
  })

  const request = {
    fileName: 'mini.csv',
    content: CSV,
    accountIdentity: 'الراجحي',
    sourceType: 'csv_legacy' as const,
    walletId: BANK_WALLET_ID,
  }
  await importer.commit(request, await importer.preview(request))

  return {
    txns,
    exporter: makeExportBackup({ txns, sources, batches, wallets, categories, rules, merchants, budgets }),
  }
}

describe('النسخة الاحتياطية', () => {
  it('تحمل إصدارًا وعدادات تطابق محتواها', async () => {
    const sys = await makeSystem()
    const file = await sys.exporter.backup({
      from: '2025-01-01',
      to: '2025-01-31',
      payday: 28,
      exportedAt: '2026-09-07T00:00:00.000Z',
    })

    expect(file.schemaVersion).toBe(BACKUP_SCHEMA_VERSION)
    expect(file.app).toBe('masroufy')
    expect(file.data.transactions).toHaveLength(3)
    expect(file.counts.transactions).toBe(3)
    expect(file.data.wallets).toHaveLength(2)
    expect(file.counts.wallets).toBe(2)
  })

  it('تحفظ المعرفات وعلاقات المصادر — spec/03', async () => {
    const sys = await makeSystem()
    const file = await sys.exporter.backup({
      from: '2025-01-01', to: '2025-01-31', payday: 28,
      exportedAt: '2026-09-07T00:00:00.000Z',
    })

    expect(file.data.sourceRecords).toHaveLength(3)
    const txnIds = new Set(file.data.transactions.map((t) => t.id))
    // كل سجل مصدر يشير إلى عملية موجودة في النسخة نفسها
    for (const record of file.data.sourceRecords) {
      expect(record.transactionId).not.toBeNull()
      expect(txnIds.has(record.transactionId!)).toBe(true)
    }
    expect(file.data.importBatches).toHaveLength(1)
  })

  it('تحفظ الرصيد المعلن والمحفظة — بدونهما تستحيل المطابقة بعد الاستعادة', async () => {
    const sys = await makeSystem()
    const file = await sys.exporter.backup({
      from: '2025-01-01', to: '2025-01-31', payday: 28,
      exportedAt: '2026-09-07T00:00:00.000Z',
    })

    for (const t of file.data.transactions) {
      expect(t.walletId).toBe(BANK_WALLET_ID)
      expect(t.statedBalanceMinor).toBeDefined()
    }
  })
})

describe('التحقق من النسخة قبل الاستعادة', () => {
  async function validFile() {
    const sys = await makeSystem()
    return sys.exporter.backup({
      from: '2025-01-01', to: '2025-01-31', payday: 28,
      exportedAt: '2026-09-07T00:00:00.000Z',
    })
  }

  it('النسخة السليمة تُقبل', async () => {
    const check = checkBackup(JSON.stringify(await validFile()))
    expect(check.valid).toBe(true)
    expect(check.problems).toEqual([])
    expect(check.file).not.toBeNull()
  })

  it('ملف مش JSON ⇒ رفض بتفسير', () => {
    const check = checkBackup('مش JSON خالص')
    expect(check.valid).toBe(false)
    expect(check.problems[0]).toContain('JSON')
    expect(check.file).toBeNull()
  })

  it('ملف من تطبيق تاني ⇒ رفض', () => {
    const check = checkBackup(JSON.stringify({ app: 'حاجة تانية', schemaVersion: 1, data: {} }))
    expect(check.valid).toBe(false)
    expect(check.problems.some((p) => p.includes('مش نسخة من مصروفي'))).toBe(true)
  })

  it('إصدار أحدث من التطبيق ⇒ رفض بدل قراءة ناقصة', () => {
    const check = checkBackup(
      JSON.stringify({ app: 'masroufy', schemaVersion: 99, data: {}, counts: {} }),
    )
    expect(check.valid).toBe(false)
    expect(check.problems.some((p) => p.includes('حدّث التطبيق'))).toBe(true)
  })

  it('عدّاد لا يطابق المحتوى ⇒ الملف ناقص أو متعدّل', async () => {
    const file = await validFile()
    file.counts.transactions = 99 // تلاعب
    const check = checkBackup(JSON.stringify(file))
    expect(check.valid).toBe(false)
    expect(check.problems.some((p) => p.includes('ناقص أو متعدّل'))).toBe(true)
  })

  it('معرّفات مكررة ⇒ رفض، لأن الاستعادة لا تكرر العمليات', async () => {
    const file = await validFile()
    file.data.transactions.push({ ...file.data.transactions[0] })
    file.counts.transactions = file.data.transactions.length
    const check = checkBackup(JSON.stringify(file))
    expect(check.valid).toBe(false)
    expect(check.problems.some((p) => p.includes('معرّفات مكررة'))).toBe(true)
  })
})

describe('تصدير CSV — قابل لإعادة الاستيراد', () => {
  it('الناتج يُقرأ بمخطط المعاينة بلا أخطاء', async () => {
    const sys = await makeSystem()
    const csv = await sys.exporter.exportCsv({ from: '2025-01-01', to: '2025-01-31', payday: 28 })

    const outcome = parseRows(parseCsv(csv))
    expect(outcome.schema).toBe('preview')
    expect(outcome.errors).toEqual([])
    expect(outcome.rows).toHaveLength(3)
  })

  it('الاسم اللي فيه فاصلة يخرج مقتبسًا ويرجع سليمًا', async () => {
    const sys = await makeSystem()
    const csv = await sys.exporter.exportCsv({ from: '2025-01-01', to: '2025-01-31', payday: 28 })

    expect(csv).toContain('"ALSKARYAH, RIYADH"')
    const outcome = parseRows(parseCsv(csv))
    expect(outcome.rows.some((r) => r.merchantName === 'ALSKARYAH, RIYADH')).toBe(true)
  })

  it('المبالغ والاتجاهات صحيحة، والملف يبدأ بـBOM', async () => {
    const sys = await makeSystem()
    const csv = await sys.exporter.exportCsv({ from: '2025-01-01', to: '2025-01-31', payday: 28 })

    expect(csv.charCodeAt(0)).toBe(0xfeff)
    const outcome = parseRows(parseCsv(csv))
    const incoming = outcome.rows.filter((r) => r.direction === 'in')
    expect(incoming).toHaveLength(1)
    expect(incoming[0].amountMinor).toBe(37500)
  })
})
