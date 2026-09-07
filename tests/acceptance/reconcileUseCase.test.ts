import { describe, it, expect } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { makeReconcileBalance } from '../../src/application/useCases/reconcileBalance'
import { makeImportStatement } from '../../src/application/useCases/importStatement'
import { makeSeedWallets, BANK_WALLET_ID } from '../../src/application/useCases/seedWallets'
import { makeAddTransaction } from '../../src/application/useCases/addTransaction'
import {
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
import { parseMoney } from '../../src/domain/money'
import { formatAmount } from '../../src/domain/formatMoney'

/**
 * OVERRIDES §7-ب داخل التطبيق لا في الاختبارات وحدها:
 * السلسلة من 4,837.83 @ 2025-01-01 تنتهي عند 2,707.25 @ 2026-09-04،
 * وتطابق عمود الرصيد **في كل سطر** بدقة الهللة.
 *
 * الفرق عن `realStatement.test.ts`: ذاك يختبر المحلل والـdomain مباشرة،
 * وهذا يختبر المسار الكامل — استيراد ⇒ تخزين ⇒ قراءة ⇒ مطابقة —
 * أي ما سيراه المستخدم فعلًا في الشاشة.
 */

const CSV_PATH = resolve(__dirname, '../../files/transactions_full.csv')

async function makeSystem() {
  const txns = new MemoryTransactionRepository()
  const wallets = new MemoryWalletRepository()
  await makeSeedWallets({ wallets })(true) // أرصدة المالك المعتمدة

  const deps = {
    txns,
    sources: new MemorySourceRecordRepository(),
    batches: new MemoryImportBatchRepository(),
    merchants: new MemoryMerchantRepository([]),
    categories: new MemoryCategoryRepository(buildCategories([])),
    rules: new MemoryRuleRepository([]),
    uow: new PassthroughUnitOfWork(),
    ids: new SequentialIdGenerator(),
    clock: new FixedClock('2026-09-07T00:00:00.000Z'),
  }

  return {
    txns,
    wallets,
    importer: makeImportStatement(deps),
    reconcile: makeReconcileBalance({ txns, wallets }),
  }
}

describe('زرع المحافظ — OVERRIDES §6', () => {
  it('الأرصدة المعتمدة للمالك', async () => {
    const wallets = new MemoryWalletRepository()
    const outcome = await makeSeedWallets({ wallets })(true)

    expect(outcome.seeded).toBe(true)
    const bank = outcome.wallets.find((w) => w.id === BANK_WALLET_ID)!
    expect(formatAmount(bank.openingBalanceMinor)).toBe('4,837.83')
    expect(bank.openingAt).toBe('2025-01-01')

    const cash = outcome.wallets.find((w) => w.kind === 'cash')!
    expect(formatAmount(cash.openingBalanceMinor)).toBe('1,000.00')
    expect(cash.openingAt).toBe('2026-09-06')
  })

  it('**مستخدم آخر يبدأ بصفر** — لا يرث أحد رصيد أحد', async () => {
    const wallets = new MemoryWalletRepository()
    const outcome = await makeSeedWallets({ wallets })(false)
    for (const w of outcome.wallets) expect(w.openingBalanceMinor).toBe(0)
  })

  it('الزرع مرة واحدة — لا يكتب فوق تعديلات المستخدم', async () => {
    const wallets = new MemoryWalletRepository()
    const seed = makeSeedWallets({ wallets })
    await seed(true)

    await wallets.save({
      id: BANK_WALLET_ID,
      name: 'الراجحي المعدّل',
      currency: 'SAR',
      kind: 'bank',
      openingBalanceMinor: parseMoney('9999.00'),
      openingAt: '2025-01-01',
    })

    const second = await seed(true)
    expect(second.seeded).toBe(false)
    const bank = (await wallets.findById(BANK_WALLET_ID))!
    expect(formatAmount(bank.openingBalanceMinor)).toBe('9,999.00') // تعديله باقٍ
  })
})

describe('المطابقة على بيانات مصطنعة', () => {
  it('سلسلة سليمة ⇒ صفر فروق', async () => {
    const sys = await makeSystem()
    const csv =
      '﻿التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل\n' +
      '2025/01/01,96.47,0.0,4741.36,ALSKARYAH,متفرقات,شراء,x\n' +
      '2025/01/02,79.55,0.0,4661.81,OTHAIM,بقالة وسوبرماركت,شراء,y\n' +
      '2025/01/03,0.0,375.0,5036.81,,تحويلات,تحويل,z\n'

    const request = {
      fileName: 'mini.csv',
      content: csv,
      accountIdentity: 'الراجحي',
      sourceType: 'csv_legacy' as const,
      walletId: BANK_WALLET_ID,
    }
    await sys.importer.commit(request, await sys.importer.preview(request))

    const outcome = await sys.reconcile({
      walletId: BANK_WALLET_ID,
      until: '2025-01-31',
      payday: 28,
    })

    expect(outcome.result.mismatches).toHaveLength(0)
    expect(outcome.result.checkedCount).toBe(3)
    expect(formatAmount(outcome.result.closingMinor)).toBe('5,036.81')
  })

  it('سطر رصيده غلط ⇒ يُكشف بمكانه وفرقه', async () => {
    const sys = await makeSystem()
    const csv =
      '﻿التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل\n' +
      '2025/01/01,96.47,0.0,4741.36,A,متفرقات,شراء,x\n' +
      '2025/01/02,79.55,0.0,4000.00,B,بقالة وسوبرماركت,شراء,y\n' // غلط

    const request = {
      fileName: 'bad.csv',
      content: csv,
      accountIdentity: 'الراجحي',
      sourceType: 'csv_legacy' as const,
      walletId: BANK_WALLET_ID,
    }
    await sys.importer.commit(request, await sys.importer.preview(request))

    const outcome = await sys.reconcile({ walletId: BANK_WALLET_ID, until: '2025-01-31', payday: 28 })

    expect(outcome.result.mismatches).toHaveLength(1)
    const m = outcome.result.mismatches[0]
    expect(m.date).toBe('2025-01-02')
    expect(formatAmount(m.computedMinor)).toBe('4,661.81')
    expect(formatAmount(m.statedMinor)).toBe('4,000.00')
    expect(outcome.result.ambiguityNote).toContain('حركة واحدة')
  })

  it('عملية بلا محفظة تُذكر ولا تُنسب بالتخمين', async () => {
    const sys = await makeSystem()
    const csv =
      '﻿التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل\n' +
      '2025/01/01,96.47,0.0,4741.36,A,متفرقات,شراء,x\n'

    // استيراد بلا walletId
    const request = {
      fileName: 'nowallet.csv',
      content: csv,
      accountIdentity: 'مصدر مجهول',
      sourceType: 'csv_legacy' as const,
    }
    await sys.importer.commit(request, await sys.importer.preview(request))

    const outcome = await sys.reconcile({ walletId: BANK_WALLET_ID, until: '2025-01-31', payday: 28 })
    expect(outcome.unassignedCount).toBe(1)
    expect(outcome.result.movementCount).toBe(0) // لم تُنسب للمحفظة
  })
})

describe.skipIf(!existsSync(CSV_PATH))('المطابقة الكاملة على الكشف الحقيقي', () => {
  it('معيار OVERRIDES §7-ب عبر المسار الكامل: صفر فروق من 1912', async () => {
    const sys = await makeSystem()
    const request = {
      fileName: 'transactions_full.csv',
      content: readFileSync(CSV_PATH, 'utf8'),
      accountIdentity: 'الراجحي',
      sourceType: 'csv_legacy' as const,
      walletId: BANK_WALLET_ID,
    }

    const preview = await sys.importer.preview(request)
    expect(preview.counts.newCount).toBe(1912)
    await sys.importer.commit(request, preview)
    expect(sys.txns.size()).toBe(1912)

    const outcome = await sys.reconcile({
      walletId: BANK_WALLET_ID,
      until: '2026-09-04',
      payday: 28,
    })

    // كل سطر قُورن فعلًا — لا سطر بلا رصيد معلن
    expect(outcome.withoutStatedBalance).toBe(0)
    expect(outcome.result.checkedCount).toBe(1912)

    if (outcome.result.mismatches.length > 0) {
      const sample = outcome.result.mismatches
        .slice(0, 3)
        .map(
          (m) =>
            `${m.date}: محسوب ${formatAmount(m.computedMinor)} مقابل ${formatAmount(m.statedMinor)}`,
        )
        .join(' · ')
      throw new Error(`${outcome.result.mismatches.length} فرق: ${sample}`)
    }

    expect(outcome.result.mismatches).toHaveLength(0)
    expect(formatAmount(outcome.result.openingMinor)).toBe('4,837.83')
    expect(formatAmount(outcome.result.closingMinor)).toBe('2,707.25')
    expect(outcome.result.closingAt).toBe('2026-09-04')
    expect(outcome.result.ambiguityNote).toBeNull()
  }, 60_000)
})

/**
 * spec/02: «عملية اقتصادية واحدة لها **طرف خصم وطرف إضافة**…
 *           وعرض الدخل والمصروف لا يعيد حساب الطرفين.»
 *
 * الخلل الذي أغلقه هذا الاختبار: التحويل الداخلي كان يُخصم من محفظة
 * **ولا يظهر في المحفظة الأخرى إطلاقًا** — فلوس تختفي من الحساب.
 */
describe('التحويل الداخلي بطرفين', () => {
  async function makeTwoWallets() {
    const txns = new MemoryTransactionRepository()
    const wallets = new MemoryWalletRepository()
    await wallets.save({
      id: 'w-bank', name: 'الراجحي', currency: 'SAR', kind: 'bank',
      openingBalanceMinor: parseMoney('1000.00'), openingAt: '2026-09-01',
    })
    await wallets.save({
      id: 'w-cash', name: 'كاش', currency: 'SAR', kind: 'cash',
      openingBalanceMinor: parseMoney('200.00'), openingAt: '2026-09-01',
    })
    return {
      txns,
      wallets,
      add: makeAddTransaction({
        txns, wallets,
        ids: new SequentialIdGenerator(),
        clock: new FixedClock('2026-09-07T00:00:00.000Z'),
      }),
      reconcile: makeReconcileBalance({ txns, wallets }),
    }
  }

  it('يخصم من المصدر ويزيد الهدف بنفس المبلغ', async () => {
    const sys = await makeTwoWallets()
    await sys.add({
      amountMinor: parseMoney('500.00'),
      occurredAt: '2026-09-05',
      walletId: 'w-bank',
      transferToWalletId: 'w-cash',
      economicKind: 'internal_transfer',
      merchantName: 'سحب للكاش',
    })

    const bank = await sys.reconcile({ walletId: 'w-bank', until: '2026-09-30', payday: 28 })
    const cash = await sys.reconcile({ walletId: 'w-cash', until: '2026-09-30', payday: 28 })

    // 1000 − 500 = 500
    expect(formatAmount(bank.result.closingMinor)).toBe('500.00')
    expect(formatAmount(bank.result.totalDebitMinor)).toBe('500.00')

    // 200 + 500 = 700 — دي اللي كانت ناقصة
    expect(formatAmount(cash.result.closingMinor)).toBe('700.00')
    expect(formatAmount(cash.result.totalCreditMinor)).toBe('500.00')

    // مجموع المحفظتين ثابت: التحويل نقل فلوس ولا خلقها ولا فقدها
    const before = parseMoney('1000.00') + parseMoney('200.00')
    expect(bank.result.closingMinor + cash.result.closingMinor).toBe(before)
  })

  it('الطرف الداخل لا يُقارَن برصيد معلن يخص محفظة تانية', async () => {
    const sys = await makeTwoWallets()
    await sys.add({
      amountMinor: parseMoney('500.00'),
      occurredAt: '2026-09-05',
      walletId: 'w-bank',
      transferToWalletId: 'w-cash',
      economicKind: 'internal_transfer',
      merchantName: 'سحب',
    })
    const cash = await sys.reconcile({ walletId: 'w-cash', until: '2026-09-30', payday: 28 })
    expect(cash.result.mismatches).toHaveLength(0)
    expect(cash.withoutStatedBalance).toBe(1) // بلا رصيد معلن، فلا يُقارَن
  })

  it('صرف الكاش بعد التحويل يخصم من رصيده الجديد — spec/02', async () => {
    const sys = await makeTwoWallets()
    await sys.add({
      amountMinor: parseMoney('500.00'), occurredAt: '2026-09-05',
      walletId: 'w-bank', transferToWalletId: 'w-cash',
      economicKind: 'internal_transfer', merchantName: 'سحب',
    })
    await sys.add({
      amountMinor: parseMoney('100.00'), occurredAt: '2026-09-06',
      walletId: 'w-cash', economicKind: 'purchase', merchantName: 'مشتريات كاش',
    })

    const cash = await sys.reconcile({ walletId: 'w-cash', until: '2026-09-30', payday: 28 })
    // 200 + 500 − 100 = 600
    expect(formatAmount(cash.result.closingMinor)).toBe('600.00')
  })
})
