import { describe, it, expect } from 'vitest'
import { makeAddTransaction, AddTransactionError } from '../../src/application/useCases/addTransaction'
import { makeLoadHomeScreen } from '../../src/application/useCases/loadHomeScreen'
import {
  MemoryAllocationRepository,
  MemoryCategoryRepository,
  MemoryTransactionRepository,
  MemoryWalletRepository,
  SequentialIdGenerator,
  FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import { parseMoney } from '../../src/domain/money'
import { formatAmount } from '../../src/domain/formatMoney'
import { buildPeriod } from '../../src/domain/period'
import type { Category, Wallet } from '../../src/domain/entities/types'

/**
 * spec/01: «الإضافة: **فاتورة/شراء**، حركة أموال، كشف حساب، شخص جديد».
 *
 * spec/02 على الكاش: «الكاش محفظة فعلية… **شراء كاش 100 يخفض رصيد الكاش
 * ويزيد المصروف 100**» — وده المسار الوحيد لتسجيله، لأن الكاش
 * لا يظهر في أي كشف بنكي.
 */

const CASH: Wallet = {
  id: 'w-cash', name: 'كاش', currency: 'SAR', kind: 'cash',
  openingBalanceMinor: parseMoney('1000.00'), openingAt: '2026-09-01',
}
const BANK: Wallet = {
  id: 'w-bank', name: 'الراجحي', currency: 'SAR', kind: 'bank',
  openingBalanceMinor: parseMoney('5000.00'), openingAt: '2026-09-01',
}
const CATEGORIES: Category[] = [
  { id: 'cat-food', parentId: null, name: 'مطاعم وقهوة', iconKey: 'u', lightColor: '#CB428E', darkColor: '#CB428E', active: true, order: 0 },
]

function makeSystem() {
  const txns = new MemoryTransactionRepository()
  const wallets = new MemoryWalletRepository([CASH, BANK])
  return {
    txns,
    add: makeAddTransaction({
      txns,
      wallets,
      ids: new SequentialIdGenerator(),
      clock: new FixedClock('2026-09-07T00:00:00.000Z'),
    }),
    home: makeLoadHomeScreen({
      txns,
      categories: new MemoryCategoryRepository(CATEGORIES),
      allocations: new MemoryAllocationRepository(),
    }),
  }
}

const PERIOD = buildPeriod(2026, 8, 28) // 2026-08-28 → 2026-09-27

describe('قهوة بالكاش — الحالة اللي كشفت النقص', () => {
  it('تتسجّل، وتتوسم كاش تلقائيًا، وتزيد المصروف', async () => {
    const sys = makeSystem()
    const txn = await sys.add({
      amountMinor: parseMoney('18.00'),
      occurredAt: '2026-09-07',
      walletId: CASH.id,
      economicKind: 'purchase',
      merchantName: 'قهوة الصباح',
      categoryId: 'cat-food',
    })

    expect(formatAmount(txn.amountMinor)).toBe('18.00')
    expect(txn.observedDirection).toBe('out')
    expect(txn.isCashTagged).toBe(true) // شارة الكاش تلقائية من نوع المحفظة
    expect(txn.walletId).toBe(CASH.id)

    // ✅ مؤكَّد فورًا: المستخدم اختاره بنفسه فلا يمر بدورة الاقتراح
    expect(txn.economicKindConfirmed).toBe(true)
    expect(txn.categoryConfirmed).toBe(true)

    const data = await sys.home({ period: PERIOD, today: '2026-09-07', payday: 28 })
    expect(formatAmount(data.expenseMinor!)).toBe('18.00')
    // ولا تنبيه «محتاجة تحديد نوعها» — النوع محدد أصلًا
    expect(data.coverage.unclassified).toBe(0)
    expect(data.partial).toBe(false)
  })

  it('العملية البنكية متتوسمش كاش', async () => {
    const sys = makeSystem()
    const txn = await sys.add({
      amountMinor: parseMoney('50.00'),
      occurredAt: '2026-09-07',
      walletId: BANK.id,
      economicKind: 'purchase',
      merchantName: 'بنزين',
    })
    expect(txn.isCashTagged).toBe(false)
  })
})

describe('النوع الاقتصادي يحدد الاتجاه والأثر', () => {
  it('التحويل الداخلي مش مصروف ولا دخل — spec/02', async () => {
    const sys = makeSystem()
    await sys.add({
      amountMinor: parseMoney('500.00'),
      occurredAt: '2026-09-07',
      walletId: BANK.id,
      transferToWalletId: CASH.id,
      economicKind: 'internal_transfer',
      merchantName: 'سحب للكاش',
    })

    const data = await sys.home({ period: PERIOD, today: '2026-09-07', payday: 28 })
    expect(data.expenseMinor).toBe(0)
    expect(data.incomeMinor).toBe(0)
  })

  it('التحويل بلا محفظة مستقبِلة **مرفوض** — الفلوس متختفيش', async () => {
    const sys = makeSystem()
    await expect(
      sys.add({
        amountMinor: parseMoney('500.00'),
        occurredAt: '2026-09-07',
        walletId: BANK.id,
        economicKind: 'internal_transfer',
        merchantName: 'سحب',
      }),
    ).rejects.toThrow(/راح لأنهي محفظة/)
    expect(sys.txns.size()).toBe(0)
  })

  it('التحويل لنفس المحفظة مرفوض', async () => {
    const sys = makeSystem()
    await expect(
      sys.add({
        amountMinor: parseMoney('100.00'),
        occurredAt: '2026-09-07',
        walletId: BANK.id,
        transferToWalletId: BANK.id,
        economicKind: 'internal_transfer',
        merchantName: 'x',
      }),
    ).rejects.toThrow(/لنفسها/)
  })

  it('المحفظة المستقبِلة تتحدد للتحويل بس', async () => {
    const sys = makeSystem()
    await expect(
      sys.add({
        amountMinor: parseMoney('30.00'),
        occurredAt: '2026-09-07',
        walletId: CASH.id,
        transferToWalletId: BANK.id,
        economicKind: 'purchase',
        merchantName: 'قهوة',
      }),
    ).rejects.toThrow(/للتحويل الداخلي بس/)
  })

  it('الراتب وارد ويزيد الدخل', async () => {
    const sys = makeSystem()
    const txn = await sys.add({
      amountMinor: parseMoney('7000.00'),
      occurredAt: '2026-09-01',
      walletId: BANK.id,
      economicKind: 'salary',
      merchantName: 'راتب',
    })
    expect(txn.observedDirection).toBe('in')

    const data = await sys.home({ period: PERIOD, today: '2026-09-07', payday: 28 })
    expect(formatAmount(data.incomeMinor!)).toBe('7,000.00')
  })

  it('القرض الممنوح صادر ومش مصروف', async () => {
    const sys = makeSystem()
    await sys.add({
      amountMinor: parseMoney('300.00'),
      occurredAt: '2026-09-07',
      walletId: BANK.id,
      economicKind: 'loan_granted',
      merchantName: 'سلّفت أحمد',
    })
    const data = await sys.home({ period: PERIOD, today: '2026-09-07', payday: 28 })
    expect(data.expenseMinor).toBe(0)
  })

  it('القرض المستلم وارد ومش دخل — spec/06', async () => {
    const sys = makeSystem()
    const txn = await sys.add({
      amountMinor: parseMoney('500.00'),
      occurredAt: '2026-09-07',
      walletId: BANK.id,
      economicKind: 'loan_received',
      merchantName: 'قرض',
    })
    expect(txn.observedDirection).toBe('in')
    const data = await sys.home({ period: PERIOD, today: '2026-09-07', payday: 28 })
    expect(data.incomeMinor).toBe(0)
  })
})

describe('الرفض بتفسير — لا حفظ صامت لبيانات غلط', () => {
  const base = {
    occurredAt: '2026-09-07',
    walletId: CASH.id,
    economicKind: 'purchase' as const,
    merchantName: 'x',
  }

  it('مبلغ صفر أو سالب', async () => {
    const sys = makeSystem()
    await expect(sys.add({ ...base, amountMinor: 0 })).rejects.toThrow(AddTransactionError)
    await expect(sys.add({ ...base, amountMinor: -100 })).rejects.toThrow(/أكبر من صفر/)
  })

  it('مبلغ مش عدد صحيح بالهللة', async () => {
    const sys = makeSystem()
    await expect(sys.add({ ...base, amountMinor: 18.5 })).rejects.toThrow(/عدد صحيح/)
  })

  it('تاريخ مستحيل', async () => {
    const sys = makeSystem()
    await expect(
      sys.add({ ...base, amountMinor: parseMoney('10.00'), occurredAt: '2026-02-30' }),
    ).rejects.toThrow(/التاريخ/)
  })

  it('محفظة مش موجودة', async () => {
    const sys = makeSystem()
    await expect(
      sys.add({ ...base, amountMinor: parseMoney('10.00'), walletId: 'مش-موجودة' }),
    ).rejects.toThrow(/محفظة/)
  })

  it('نوع غير محدد — الإضافة اليدوية يعرف صاحبها نوعها', async () => {
    const sys = makeSystem()
    await expect(
      sys.add({ ...base, amountMinor: parseMoney('10.00'), economicKind: 'unclassified' }),
    ).rejects.toThrow(/تحدد نوع/)
  })

  it('اسم أطول من 120 حرف وملاحظة أطول من 1000 — spec/04', async () => {
    const sys = makeSystem()
    await expect(
      sys.add({ ...base, amountMinor: parseMoney('10.00'), merchantName: 'ا'.repeat(121) }),
    ).rejects.toThrow(/120/)
    await expect(
      sys.add({ ...base, amountMinor: parseMoney('10.00'), note: 'ن'.repeat(1001) }),
    ).rejects.toThrow(/1000/)
  })

  it('ولا عملية اتحفظت من كل المحاولات الفاشلة دي', async () => {
    const sys = makeSystem()
    for (const bad of [
      { ...base, amountMinor: 0 },
      { ...base, amountMinor: parseMoney('10.00'), occurredAt: '2026-02-30' },
      { ...base, amountMinor: parseMoney('10.00'), walletId: 'x' },
    ]) {
      await expect(sys.add(bad)).rejects.toThrow()
    }
    expect(sys.txns.size()).toBe(0)
  })
})

describe('العملية اليدوية بلا تصنيف', () => {
  it('تُعرض «محتاجة مراجعة» بدل تصنيف مخترع', async () => {
    const sys = makeSystem()
    const txn = await sys.add({
      amountMinor: parseMoney('25.00'),
      occurredAt: '2026-09-07',
      walletId: CASH.id,
      economicKind: 'purchase',
      merchantName: '',
    })
    expect(txn.categoryId).toBeUndefined()
    expect(txn.categoryConfirmed).toBe(false)
    expect(txn.reviewState).toBe('needs_review')
    expect(txn.rawMerchantName).toBe('بلا اسم')
  })
})
