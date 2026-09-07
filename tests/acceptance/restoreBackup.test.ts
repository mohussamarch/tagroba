import { describe, expect, it } from 'vitest'
import { makeRestoreBackup, RestoreError } from '../../src/application/useCases/restoreBackup'
import {
  BACKUP_SCHEMA_VERSION,
  type BackupFile,
} from '../../src/application/useCases/exportBackup'
import {
  MemoryCategoryRepository,
  MemoryMerchantRepository,
  MemoryRuleRepository,
  MemoryTransactionRepository,
  MemoryWalletRepository,
  PassthroughUnitOfWork,
} from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryBudgetRepository } from '../../src/infrastructure/memory/memoryBudgetRepository'
import { mergeTransactions, transactionContentKey } from '../../src/domain/mergeBackup'
import type { Transaction, Wallet } from '../../src/domain/entities/types'

/**
 * الاستعادة **بالدمج** — قرار المالك، `OVERRIDES §12`.
 *
 * القاعدة المختبَرة: **الموجود ما يتدهسش**، و`spec/03`
 * «الاستعادة لا تكرر العمليات أو تخلط العملات».
 */

const BANK: Wallet = {
  id: 'w-bank',
  name: 'الراجحي',
  kind: 'bank',
  currency: 'SAR',
  openingBalanceMinor: 483783,
  openingAt: '2025-01-01',
}

function txn(over: Partial<Transaction> & Pick<Transaction, 'id'>): Transaction {
  return {
    occurredAt: '2025-03-10',
    datePrecision: 'day',
    sourceOrder: 1,
    economicKind: 'purchase',
    economicKindConfirmed: false,
    observedDirection: 'out',
    amountMinor: 5000,
    currency: 'SAR',
    categoryConfirmed: false,
    excludedFromBudget: false,
    reviewState: 'needs_review',
    isCashTagged: false,
    walletId: BANK.id,
    createdAt: '2026-09-07T10:00:00.000Z',
    updatedAt: '2026-09-07T10:00:00.000Z',
    ...over,
  }
}

function backup(data: Partial<BackupFile['data']>): string {
  const full: BackupFile['data'] = {
    wallets: [],
    categories: [],
    rules: [],
    merchants: [],
    transactions: [],
    sourceRecords: [],
    importBatches: [],
    budgets: [],
    categoryBudgets: [],
    ...data,
  }
  const counts = Object.fromEntries(Object.entries(full).map(([k, v]) => [k, v.length]))
  return JSON.stringify({
    schemaVersion: BACKUP_SCHEMA_VERSION,
    app: 'masroufy',
    exportedAt: '2026-09-07T10:00:00.000Z',
    counts,
    data: full,
  })
}

async function build(existing: { txns?: Transaction[]; wallets?: Wallet[] } = {}) {
  const deps = {
    txns: new MemoryTransactionRepository(),
    wallets: new MemoryWalletRepository(existing.wallets ?? []),
    categories: new MemoryCategoryRepository(),
    rules: new MemoryRuleRepository(),
    merchants: new MemoryMerchantRepository(),
    budgets: new MemoryBudgetRepository(),
    uow: new PassthroughUnitOfWork(),
  }
  // المستودع بيتبذر بالحفظ لا بالباني
  if (existing.txns?.length) await deps.txns.saveMany(existing.txns)
  return { deps, useCase: makeRestoreBackup(deps) }
}

/** كل العمليات المخزّنة — المستودع مالوش listAll بقاعدة «لا فهارس مركّبة». */
const allTxns = (deps: { txns: MemoryTransactionRepository }) =>
  deps.txns.listByDateRange('0000-01-01', '9999-12-31')

describe('التحقق قبل أي كتابة', () => {
  it('ملف مش JSON بيترفض بسببه', async () => {
    const { useCase } = await build()
    await expect(useCase.plan('مش json')).rejects.toThrow(RestoreError)
  })

  it('ملف من تطبيق تاني بيترفض', async () => {
    const { useCase } = await build()
    await expect(useCase.plan(JSON.stringify({ app: 'حاجة تانية' }))).rejects.toThrow(RestoreError)
  })

  it('ملف عدده معلن غلط بيترفض — يعني متعدّل', async () => {
    const { useCase } = await build()
    const bad = JSON.parse(backup({ transactions: [txn({ id: 't1' })] }))
    bad.counts.transactions = 5
    await expect(useCase.plan(JSON.stringify(bad))).rejects.toThrow(/ناقص أو متعدّل/)
  })

  it('المعاينة ما بتكتبش حاجة', async () => {
    const { deps, useCase } = await build()
    await useCase.plan(backup({ transactions: [txn({ id: 't1' })], wallets: [BANK] }))
    expect(await allTxns(deps)).toHaveLength(0)
    expect(await deps.wallets.listAll()).toHaveLength(0)
  })
})

describe('الدمج — الموجود ما يتدهسش', () => {
  it('حساب فاضي: كل حاجة بتتضاف', async () => {
    const { deps, useCase } = await build()
    const raw = backup({
      wallets: [BANK],
      transactions: [txn({ id: 't1' }), txn({ id: 't2', amountMinor: 9900 })],
    })

    const plan = await useCase.plan(raw)
    expect(plan.totalToAdd).toBe(3)

    const outcome = await useCase.apply(plan.file)
    expect(outcome.added.transactions).toBe(2)
    expect(outcome.added.wallets).toBe(1)
    expect(await allTxns(deps)).toHaveLength(2)
  })

  it('نفس المعرّف ⇒ الموجود يفضل بقراراته', async () => {
    // الموجود اتعدّل بعد ما النسخة اتاخدت: النوع اتأكد
    const local = txn({ id: 't1', economicKind: 'support_gift', economicKindConfirmed: true })
    const { deps, useCase } = await build({ txns: [local] })

    // النسخة القديمة فيها نفس العملية بنوعها القديم
    const raw = backup({ transactions: [txn({ id: 't1', economicKind: 'purchase' })] })
    const plan = await useCase.plan(raw)
    expect(plan.lines[0].toAdd).toBe(0)
    expect(plan.lines[0].skipped).toBe(1)

    await useCase.apply(plan.file)
    const all = await allTxns(deps)
    expect(all).toHaveLength(1)
    // قرار المستخدم ما رجعش لورا
    expect(all[0].economicKind).toBe('support_gift')
    expect(all[0].economicKindConfirmed).toBe(true)
  })

  it('نفس المحتوى بمعرّف مختلف ⇒ يتخطى، فالمصروف ما يتضاعفش', async () => {
    const local = txn({ id: 'محلي-1', occurredAt: '2025-04-01', amountMinor: 12345 })
    const { deps, useCase } = await build({ txns: [local] })

    // نفس العملية بالظبط بس اتخزنت بمعرّف تاني على جهاز تاني
    const raw = backup({
      transactions: [txn({ id: 'نسخة-1', occurredAt: '2025-04-01', amountMinor: 12345 })],
    })

    const plan = await useCase.plan(raw)
    expect(plan.lines[0].toAdd).toBe(0)
    expect(plan.lines[0].note).toContain('المصروف ما يتضاعفش')

    await useCase.apply(plan.file)
    expect(await allTxns(deps)).toHaveLength(1)
  })

  it('اختلاف المحفظة يخلّيهم عمليتين لا واحدة', async () => {
    const local = txn({ id: 'a', walletId: 'w-bank' })
    const { deps, useCase } = await build({ txns: [local] })
    const raw = backup({ transactions: [txn({ id: 'b', walletId: 'w-cash' })] })

    const plan = await useCase.plan(raw)
    expect(plan.lines[0].toAdd).toBe(1)
    await useCase.apply(plan.file)
    expect(await allTxns(deps)).toHaveLength(2)
  })

  it('تكرار جوه الملف نفسه بيتخطى', async () => {
    const { deps, useCase } = await build()
    const raw = backup({
      transactions: [txn({ id: 'x1' }), txn({ id: 'x2' })], // نفس المحتوى
    })
    const plan = await useCase.plan(raw)
    expect(plan.lines[0].toAdd).toBe(1)
    await useCase.apply(plan.file)
    expect(await allTxns(deps)).toHaveLength(1)
  })

  it('التنفيذ بيعيد الحساب على الموجود دلوقتي مش على وقت المعاينة', async () => {
    const { deps, useCase } = await build()
    const raw = backup({ transactions: [txn({ id: 't1' })] })

    const plan = await useCase.plan(raw)
    expect(plan.totalToAdd).toBe(1)

    // بين المعاينة والتأكيد، نفس العملية وصلت من جهاز تاني
    await deps.txns.saveMany([txn({ id: 't1' })])

    const outcome = await useCase.apply(plan.file)
    expect(outcome.added.transactions).toBe(0)
    expect(await allTxns(deps)).toHaveLength(1)
  })
})

describe('العملات ما تختلطش', () => {
  it('عملة جديدة بتتقال كتحذير قبل التأكيد', async () => {
    const { useCase } = await build({ txns: [txn({ id: 'sar', currency: 'SAR' })] })
    const raw = backup({ transactions: [txn({ id: 'usd', currency: 'USD', amountMinor: 100 })] })

    const plan = await useCase.plan(raw)
    expect(plan.warnings.join(' ')).toContain('USD')
    expect(plan.warnings.join(' ')).toContain('مش هتتحوّل')
  })

  it('حساب فاضي: مفيش تحذير عملة', async () => {
    const { useCase } = await build()
    const raw = backup({ transactions: [txn({ id: 'usd', currency: 'USD' })] })
    expect((await useCase.plan(raw)).warnings.join(' ')).not.toContain('USD')
  })
})

describe('مفتاح المحتوى', () => {
  it('نفس البيانات ⇒ نفس المفتاح مهما اختلف المعرّف', () => {
    expect(transactionContentKey(txn({ id: 'a' }))).toBe(transactionContentKey(txn({ id: 'b' })))
  })

  it('اختلاف الاتجاه بيغيّر المفتاح — قبض 50 مش صرف 50', () => {
    expect(transactionContentKey(txn({ id: 'a', observedDirection: 'in' }))).not.toBe(
      transactionContentKey(txn({ id: 'a', observedDirection: 'out' })),
    )
  })

  it('الدمج بيرجّع سببًا مكتوبًا لكل متخطي', () => {
    const report = mergeTransactions([txn({ id: 't1' })], [txn({ id: 't1' })])
    expect(report.decisions[0].reason).toBeTruthy()
    expect(report.decisions[0].action).toBe('skipped_same_id')
  })
})

describe('النسخة الفاضية', () => {
  it('مفيش جديد ⇒ بيتقال صراحة', async () => {
    const local = txn({ id: 't1' })
    const { useCase } = await build({ txns: [local] })
    const plan = await useCase.plan(backup({ transactions: [txn({ id: 't1' })] }))
    expect(plan.totalToAdd).toBe(0)
    expect(plan.warnings.join(' ')).toContain('مفيش حاجة جديدة')
  })
})
