import { describe, it, expect } from 'vitest'
import { makeManagePeople } from '../../src/application/useCases/managePeople'
import { makeAddTransaction } from '../../src/application/useCases/addTransaction'
import { makeLoadHomeScreen } from '../../src/application/useCases/loadHomeScreen'
import {
  MemoryAllocationRepository,
  MemoryCategoryRepository,
  MemoryObligationRepository,
  MemoryPersonRepository,
  MemorySettlementRepository,
  MemoryTransactionRepository,
  MemoryWalletRepository,
  PassthroughUnitOfWork,
  SequentialIdGenerator,
  FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import { parseMoney } from '../../src/domain/money'
import { formatAmount } from '../../src/domain/formatMoney'
import { buildPeriod } from '../../src/domain/period'
import type { Wallet } from '../../src/domain/entities/types'

/**
 * حالات الأشخاص والديون من `spec/06` — عبر التخزين لا في المنطق وحده.
 *
 * منطق الحساب مختبَر أصلًا في `ledgerCases.test.ts`. هنا نتأكد أن الربط
 * بالتخزين يحفظ نفس القواعد: لا تقاص تلقائي، لا سداد زائد، لا حذف لشخص
 * له سجل، والهدية لا تُنشئ التزامًا.
 */

const BANK: Wallet = {
  id: 'w-bank', name: 'الراجحي', currency: 'SAR', kind: 'bank',
  openingBalanceMinor: parseMoney('5000.00'), openingAt: '2026-09-01',
}
const PERIOD = buildPeriod(2026, 8, 28)

function makeSystem() {
  const txns = new MemoryTransactionRepository()
  const people = new MemoryPersonRepository()
  const obligations = new MemoryObligationRepository()
  const settlements = new MemorySettlementRepository()
  const allocations = new MemoryAllocationRepository()
  const wallets = new MemoryWalletRepository([BANK])
  const clock = new FixedClock('2026-09-07T00:00:00.000Z')
  const ids = new SequentialIdGenerator()

  return {
    txns,
    allocations,
    manage: makeManagePeople({
      people, obligations, settlements, allocations, txns,
      uow: new PassthroughUnitOfWork(), ids, clock,
    }),
    add: makeAddTransaction({ txns, wallets, ids, clock }),
    home: makeLoadHomeScreen({
      txns,
      categories: new MemoryCategoryRepository([]),
      allocations,
    }),
  }
}

/** فاتورة البيك 32 ريال — حالة spec/06 الأساسية. */
async function albaik(sys: ReturnType<typeof makeSystem>) {
  return sys.add({
    amountMinor: parseMoney('32.00'),
    occurredAt: '2026-09-05',
    walletId: BANK.id,
    economicKind: 'purchase',
    merchantName: 'البيك',
  })
}

describe('إدارة الأشخاص', () => {
  it('يضيف شخصًا ويرفض الاسم المكرر والفاضي والطويل', async () => {
    const sys = makeSystem()
    const person = await sys.manage.addPerson('عبد الفتاح')
    expect(person.name).toBe('عبد الفتاح')
    expect(person.archived).toBe(false)

    await expect(sys.manage.addPerson('عبد الفتاح')).rejects.toThrow(/موجود قبل كده/)
    await expect(sys.manage.addPerson('   ')).rejects.toThrow(/اكتب اسم/)
    await expect(sys.manage.addPerson('ا'.repeat(81))).rejects.toThrow(/80/)
  })

  it('الأرشفة بدل الحذف — spec/03: لا حذف للحساب ذي سجل', async () => {
    const sys = makeSystem()
    const person = await sys.manage.addPerson('أحمد')
    await sys.manage.archivePerson(person.id, true)

    const rows = await sys.manage.listWithBalances()
    expect(rows).toHaveLength(1) // موجود لا محذوف
    expect(rows[0].person.archived).toBe(true)
  })
})

describe('ربط عملية بشخص — spec/06', () => {
  it('ربط البيك 32 كاملًا كدين ⇒ المصروف يقل 32 ولك عنده 32', async () => {
    const sys = makeSystem()
    const person = await sys.manage.addPerson('عبد الفتاح')
    const bill = await albaik(sys)

    const before = await sys.home({ period: PERIOD, today: '2026-09-07', payday: 28 })
    expect(formatAmount(before.expenseMinor!)).toBe('32.00')

    await sys.manage.linkToPerson({
      transactionId: bill.id,
      personId: person.id,
      kind: 'receivable',
      amountMinor: parseMoney('32.00'),
    })

    const after = await sys.home({ period: PERIOD, today: '2026-09-07', payday: 28 })
    expect(formatAmount(after.expenseMinor!)).toBe('0.00')

    const rows = await sys.manage.listWithBalances()
    expect(formatAmount(rows[0].balance.receivableMinor)).toBe('32.00')
  })

  it('تخصيص 20 من فاتورة 32 ⇒ المصروف 12 والمستحق 20', async () => {
    const sys = makeSystem()
    const person = await sys.manage.addPerson('عبد الفتاح')
    const bill = await albaik(sys)

    await sys.manage.linkToPerson({
      transactionId: bill.id, personId: person.id,
      kind: 'receivable', amountMinor: parseMoney('20.00'),
    })

    const data = await sys.home({ period: PERIOD, today: '2026-09-07', payday: 28 })
    expect(formatAmount(data.expenseMinor!)).toBe('12.00')
    const rows = await sys.manage.listWithBalances()
    expect(formatAmount(rows[0].balance.receivableMinor)).toBe('20.00')
  })

  it('الهدية ⇒ المصروف كامل ولا التزام عليه — spec/06', async () => {
    const sys = makeSystem()
    const person = await sys.manage.addPerson('عبد الفتاح')
    const bill = await albaik(sys)

    const result = await sys.manage.linkToPerson({
      transactionId: bill.id, personId: person.id,
      kind: 'receivable', amountMinor: parseMoney('32.00'), asGift: true,
    })

    expect(result.obligation).toBeNull()
    const data = await sys.home({ period: PERIOD, today: '2026-09-07', payday: 28 })
    expect(formatAmount(data.expenseMinor!)).toBe('32.00') // لم ينقص
    const rows = await sys.manage.listWithBalances()
    expect(rows[0].balance.receivableMinor).toBe(0)
  })

  it('مجموع التخصيصات لا يتجاوز قيمة العملية — spec/03', async () => {
    const sys = makeSystem()
    const person = await sys.manage.addPerson('عبد الفتاح')
    const bill = await albaik(sys)

    await sys.manage.linkToPerson({
      transactionId: bill.id, personId: person.id,
      kind: 'receivable', amountMinor: parseMoney('20.00'),
    })
    await expect(
      sys.manage.linkToPerson({
        transactionId: bill.id, personId: person.id,
        kind: 'receivable', amountMinor: parseMoney('20.00'),
      }),
    ).rejects.toThrow(/أكبر من قيمة العملية/)

    // ولا تخصيص زائد اتحفظ
    expect(await sys.allocations.listByTransactionIds([bill.id])).toHaveLength(1)
  })
})

describe('التسوية — spec/06', () => {
  async function withReceivable() {
    const sys = makeSystem()
    const person = await sys.manage.addPerson('عبد الفتاح')
    const bill = await albaik(sys)
    const { obligation } = await sys.manage.linkToPerson({
      transactionId: bill.id, personId: person.id,
      kind: 'receivable', amountMinor: parseMoney('32.00'),
    })
    return { sys, person, obligation: obligation! }
  }

  it('تحصيل 32 ⇒ لك عنده 0، والدخل ما اتغيرش', async () => {
    const { sys, person, obligation } = await withReceivable()
    await sys.manage.settle({
      obligationId: obligation.id, personId: person.id, amountMinor: parseMoney('32.00'),
    })

    const rows = await sys.manage.listWithBalances()
    expect(rows[0].balance.receivableMinor).toBe(0)
    expect(rows[0].obligations).toHaveLength(0) // المسدَّد يختفي من النشط

    const data = await sys.home({ period: PERIOD, today: '2026-09-07', payday: 28 })
    expect(data.incomeMinor).toBe(0) // التحصيل مش دخل
  })

  it('سداد أكبر من المتبقي **مرفوض** بتفسير — لا رصيد سالب صامت', async () => {
    const { sys, person, obligation } = await withReceivable()
    await expect(
      sys.manage.settle({
        obligationId: obligation.id, personId: person.id, amountMinor: parseMoney('50.00'),
      }),
    ).rejects.toThrow(/أمانة مستقلة/)

    const rows = await sys.manage.listWithBalances()
    expect(formatAmount(rows[0].balance.receivableMinor)).toBe('32.00') // ما اتغيرش
  })

  it('التسوية الجزئية بتتراكم صح', async () => {
    const { sys, person, obligation } = await withReceivable()
    await sys.manage.settle({ obligationId: obligation.id, personId: person.id, amountMinor: parseMoney('12.00') })
    await sys.manage.settle({ obligationId: obligation.id, personId: person.id, amountMinor: parseMoney('20.00') })

    const rows = await sys.manage.listWithBalances()
    expect(rows[0].balance.receivableMinor).toBe(0)
  })
})

describe('لا تقاص تلقائي بين «لك» و«عليك» — spec/02', () => {
  it('القرض والأمانة والمستحق ثلاثة أرصدة منفصلة', async () => {
    const sys = makeSystem()
    const person = await sys.manage.addPerson('أحمد')

    const bill = await albaik(sys)
    await sys.manage.linkToPerson({
      transactionId: bill.id, personId: person.id,
      kind: 'receivable', amountMinor: parseMoney('32.00'),
    })

    const loan = await sys.add({
      amountMinor: parseMoney('500.00'), occurredAt: '2026-09-05',
      walletId: BANK.id, economicKind: 'loan_received', merchantName: 'قرض من أحمد',
    })
    await sys.manage.linkToPerson({
      transactionId: loan.id, personId: person.id,
      kind: 'loan_payable', amountMinor: parseMoney('500.00'),
    })

    const custody = await sys.add({
      amountMinor: parseMoney('200.00'), occurredAt: '2026-09-05',
      walletId: BANK.id, economicKind: 'custody_received', merchantName: 'أمانة',
    })
    await sys.manage.linkToPerson({
      transactionId: custody.id, personId: person.id,
      kind: 'custody_payable', amountMinor: parseMoney('200.00'),
    })

    const rows = await sys.manage.listWithBalances()
    const balance = rows[0].balance

    // ثلاثة أرقام منفصلة — ولا واحد اتقاص مع التاني
    expect(formatAmount(balance.receivableMinor)).toBe('32.00')
    expect(formatAmount(balance.payableLoanMinor)).toBe('500.00')
    expect(formatAmount(balance.payableCustodyMinor)).toBe('200.00')
  })

  it('رد أمانة 300 والمتاح 200 ⇒ رفض، ولا سحب من رصيد القرض', async () => {
    const sys = makeSystem()
    const person = await sys.manage.addPerson('أحمد')

    const loan = await sys.add({
      amountMinor: parseMoney('500.00'), occurredAt: '2026-09-05',
      walletId: BANK.id, economicKind: 'loan_received', merchantName: 'قرض',
    })
    await sys.manage.linkToPerson({
      transactionId: loan.id, personId: person.id,
      kind: 'loan_payable', amountMinor: parseMoney('500.00'),
    })

    const custody = await sys.add({
      amountMinor: parseMoney('200.00'), occurredAt: '2026-09-05',
      walletId: BANK.id, economicKind: 'custody_received', merchantName: 'أمانة',
    })
    const { obligation } = await sys.manage.linkToPerson({
      transactionId: custody.id, personId: person.id,
      kind: 'custody_payable', amountMinor: parseMoney('200.00'),
    })

    await expect(
      sys.manage.settle({
        obligationId: obligation!.id, personId: person.id, amountMinor: parseMoney('300.00'),
      }),
    ).rejects.toThrow(/الأمانة/)

    const balance = (await sys.manage.listWithBalances())[0].balance
    expect(formatAmount(balance.payableLoanMinor)).toBe('500.00') // ما اتمسّش
    expect(formatAmount(balance.payableCustodyMinor)).toBe('200.00')
  })
})
