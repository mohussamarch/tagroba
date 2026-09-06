import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  computePeriodTotals,
  personalShareOf,
  sumByTag,
  computePersonBalance,
  checkSettlement,
  remainingOfObligation,
  splitGrossIntoPrincipalAndFee,
  LedgerError,
} from '../../src/domain/ledger'
import { parseMoney } from '../../src/domain/money'
import { formatAmount, savingsRatePercent, formatPercentOrNA, NOT_AVAILABLE } from '../../src/domain/formatMoney'
import type {
  Obligation,
  PersonAllocation,
  Settlement,
  Transaction,
} from '../../src/domain/entities/types'
import type { EconomicKind } from '../../src/domain/entities/economicKind'

/** حالات الدفتر من spec/06 و fixtures/ledger-cases.json. */

const LEDGER_CASES = JSON.parse(
  readFileSync(
    resolve(__dirname, '../../design-source/masroofi-claude-code/fixtures/ledger-cases.json'),
    'utf8',
  ),
) as {
  expected: {
    incomeMinor: number
    personalExpenseMinor: number
    remainingMinor: number
    payableLoanMinor: number
    payableCustodyMinor: number
  }
}

let counter = 0
function txn(kind: EconomicKind, amount: string, extra: Partial<Transaction> = {}): Transaction {
  counter++
  return {
    id: `t${counter}`,
    occurredAt: '2026-09-20',
    datePrecision: 'day',
    sourceOrder: counter,
    economicKind: kind,
    economicKindConfirmed: true,
    observedDirection: 'out',
    amountMinor: parseMoney(amount),
    currency: 'SAR',
    categoryConfirmed: false,
    excludedFromBudget: false,
    reviewState: 'confirmed',
    isCashTagged: false,
    createdAt: '2026-09-20T00:00:00Z',
    updatedAt: '2026-09-20T00:00:00Z',
    ...extra,
  }
}

const alloc = (
  id: string,
  transactionId: string,
  kind: PersonAllocation['allocationKind'],
  amount: string,
): PersonAllocation => ({
  id,
  transactionId,
  personId: 'demo-abdel',
  allocationKind: kind,
  amountMinor: parseMoney(amount),
  currency: 'SAR',
})

describe('fixture الأساسي — spec/06', () => {
  const salary = txn('salary', '7000.00', { observedDirection: 'in' })
  const albaik = txn('purchase', '32.00')
  const amazon = txn('purchase', '720.00')
  const cashTransfer = txn('internal_transfer', '500.00')
  const cashBuy = txn('purchase', '100.00', { isCashTagged: true })
  const loan = txn('loan_received', '500.00', { observedDirection: 'in' })
  const custody = txn('custody_received', '200.00', { observedDirection: 'in' })

  const all = [salary, albaik, amazon, cashTransfer, cashBuy, loan, custody]

  it('دخل 7000، مصروف 852، متبقي 6148 — دون احتساب قرض/أمانة/نقل داخلي', () => {
    const totals = computePeriodTotals(all, [])
    expect(formatAmount(totals.incomeMinor)).toBe('7,000.00')
    expect(formatAmount(totals.personalExpenseMinor)).toBe('852.00') // 32 + 720 + 100
    expect(formatAmount(totals.remainingMinor)).toBe('6,148.00')

    // مطابقة القيم المرجعية في ledger-cases.json بالهللة
    expect(totals.incomeMinor).toBe(LEDGER_CASES.expected.incomeMinor)
    expect(totals.personalExpenseMinor).toBe(LEDGER_CASES.expected.personalExpenseMinor)
    expect(totals.remainingMinor).toBe(LEDGER_CASES.expected.remainingMinor)
  })

  it('القرض والأمانة والنقل الداخلي لا تدخل الدخل ولا المصروف', () => {
    const withoutThem = computePeriodTotals([salary, albaik, amazon, cashBuy], [])
    const withThem = computePeriodTotals(all, [])
    expect(withThem.incomeMinor).toBe(withoutThem.incomeMinor)
    expect(withThem.personalExpenseMinor).toBe(withoutThem.personalExpenseMinor)
  })

  it('سحب 500 من البنك إلى الكاش لا يُعد مصروفًا — spec/02', () => {
    expect(personalShareOf(cashTransfer, [])).toBe(0)
  })

  it('شراء كاش 100 يزيد المصروف 100', () => {
    expect(formatAmount(personalShareOf(cashBuy, []))).toBe('100.00')
    expect(cashBuy.isCashTagged).toBe(true) // شارة كاش في كل العروض
  })
})

describe('الوسوم لا تضاعف المبلغ — spec/06', () => {
  const amazon = txn('purchase', '720.00')
  const albaik = txn('purchase', '32.00')
  const all = [amazon, albaik]

  it('وسم هدية وتسوق على Amazon720 ⇒ 720 لا 1440 ولا 2160', () => {
    // وسم واحد
    expect(formatAmount(sumByTag(all, [amazon.id]))).toBe('720.00')
    // نفس العملية مذكورة مرتين (محاكاة انضمام جدول الوسوم)
    expect(formatAmount(sumByTag(all, [amazon.id, amazon.id]))).toBe('720.00')
    // ثلاث مرات
    expect(formatAmount(sumByTag(all, [amazon.id, amazon.id, amazon.id]))).toBe('720.00')
  })

  it('إجمالي المصروف يبقى 752 مهما تعددت الوسوم', () => {
    const totals = computePeriodTotals(all, [])
    expect(formatAmount(totals.personalExpenseMinor)).toBe('752.00')
  })
})

describe('ربط الأشخاص — spec/06', () => {
  const albaik = txn('purchase', '32.00')
  const amazon = txn('purchase', '720.00')
  const cashBuy = txn('purchase', '100.00')
  const salary = txn('salary', '7000.00', { observedDirection: 'in' })
  const all = [salary, albaik, amazon, cashBuy]

  it('ربط البيك32 كاملًا كدين ⇒ المصروف من 852 إلى 820، ولك عنده 32', () => {
    const before = computePeriodTotals(all, [])
    expect(formatAmount(before.personalExpenseMinor)).toBe('852.00')

    const allocations = [alloc('a1', albaik.id, 'receivable', '32.00')]
    const after = computePeriodTotals(all, allocations)
    expect(formatAmount(after.personalExpenseMinor)).toBe('820.00')
    expect(personalShareOf(albaik, allocations)).toBe(0)
  })

  it('تخصيص 20 فقط من فاتورة 32 ⇒ المصروف ينخفض 20 ويبقى 12 عليك', () => {
    const allocations = [alloc('a1', albaik.id, 'receivable', '20.00')]
    expect(formatAmount(personalShareOf(albaik, allocations))).toBe('12.00')
    const totals = computePeriodTotals(all, allocations)
    expect(formatAmount(totals.personalExpenseMinor)).toBe('832.00') // 852 − 20
  })

  it('اختيار هدية لشخص ⇒ المصروف كامل؛ لا التزام عليه', () => {
    const allocations = [alloc('a1', albaik.id, 'gift', '32.00')]
    expect(formatAmount(personalShareOf(albaik, allocations))).toBe('32.00')
    const totals = computePeriodTotals(all, allocations)
    expect(formatAmount(totals.personalExpenseMinor)).toBe('852.00') // لم ينقص
  })

  it('مجموع التخصيصات لا يتجاوز قيمة الشراء — spec/03', () => {
    const allocations = [alloc('a1', albaik.id, 'receivable', '40.00')]
    expect(() => personalShareOf(albaik, allocations)).toThrow(LedgerError)
  })
})

describe('الديون والأمانات — spec/06', () => {
  const loanObl: Obligation = {
    id: 'obl-loan', personId: 'p1', originTransactionId: 't-loan',
    kind: 'loan_payable', originalMinor: parseMoney('500.00'), currency: 'SAR',
  }
  const custodyObl: Obligation = {
    id: 'obl-cust', personId: 'p1', originTransactionId: 't-cust',
    kind: 'custody_payable', originalMinor: parseMoney('200.00'), currency: 'SAR',
  }
  const receivable: Obligation = {
    id: 'obl-recv', personId: 'p2', originTransactionId: 't-albaik',
    kind: 'receivable', originalMinor: parseMoney('32.00'), currency: 'SAR',
  }

  it('قرض 500 وأمانة 200 منفصلان، لا يُجمعان ولا يُقاصّان', () => {
    const balance = computePersonBalance('p1', [loanObl, custodyObl], [])
    expect(formatAmount(balance.payableLoanMinor)).toBe('500.00')
    expect(formatAmount(balance.payableCustodyMinor)).toBe('200.00')
    expect(formatAmount(balance.receivableMinor)).toBe('0.00')
  })

  it('سداد قرض 300 ⇒ الدين ينخفض 300 والمصروف لا يتغير', () => {
    const settlements: Settlement[] = [
      { id: 's1', transactionId: 't-pay', obligationId: 'obl-loan', amountMinor: parseMoney('300.00') },
    ]
    const balance = computePersonBalance('p1', [loanObl, custodyObl], settlements)
    expect(formatAmount(balance.payableLoanMinor)).toBe('200.00')
    expect(formatAmount(balance.payableCustodyMinor)).toBe('200.00') // الأمانة لم تُمس
  })

  it('محاولة رد أمانة 300 والمتاح 200 ⇒ رفض مع تفسير، ولا سحب من القرض', () => {
    const check = checkSettlement(custodyObl, [], parseMoney('300.00'))
    expect(check.allowed).toBe(false)
    expect(check.reason).toContain('الأمانة')
    expect(check.settledMinor).toBe(parseMoney('200.00'))
    expect(check.surplusMinor).toBe(parseMoney('100.00'))

    // رصيد القرض لم يُمس إطلاقًا
    const balance = computePersonBalance('p1', [loanObl, custodyObl], [])
    expect(formatAmount(balance.payableLoanMinor)).toBe('500.00')
  })

  it('سداد أكبر من الدين ⇒ رفض مع تقسيم صريح، لا رصيد سالب صامت', () => {
    const check = checkSettlement(loanObl, [], parseMoney('600.00'))
    expect(check.allowed).toBe(false)
    expect(check.settledMinor).toBe(parseMoney('500.00'))
    expect(check.surplusMinor).toBe(parseMoney('100.00'))
    expect(check.reason).toContain('أمانة مستقلة')
  })

  it('تحصيل 32 من عبد الفتاح ⇒ لك عنده 0، والدخل لا يتغير', () => {
    const settlements: Settlement[] = [
      { id: 's1', transactionId: 't-collect', obligationId: 'obl-recv', amountMinor: parseMoney('32.00') },
    ]
    const balance = computePersonBalance('p2', [receivable], settlements)
    expect(formatAmount(balance.receivableMinor)).toBe('0.00')

    // التحصيل نوعه debt_collected ⇒ لا يزيد الدخل
    const totals = computePeriodTotals(
      [txn('salary', '7000.00', { observedDirection: 'in' }), txn('debt_collected', '32.00', { observedDirection: 'in' })],
      [],
    )
    expect(formatAmount(totals.incomeMinor)).toBe('7,000.00')
  })

  it('التسوية لا تتجاوز المتبقي، والتسويات المتراكمة تُحسب', () => {
    const settlements: Settlement[] = [
      { id: 's1', transactionId: 'x', obligationId: 'obl-loan', amountMinor: parseMoney('300.00') },
    ]
    expect(formatAmount(remainingOfObligation(loanObl, settlements))).toBe('200.00')
    const check = checkSettlement(loanObl, settlements, parseMoney('250.00'))
    expect(check.allowed).toBe(false)
    expect(check.settledMinor).toBe(parseMoney('200.00'))

    const exact = checkSettlement(loanObl, settlements, parseMoney('200.00'))
    expect(exact.allowed).toBe(true)
    expect(exact.surplusMinor).toBe(0)
  })

  it('تسوية على التزام مسدَّد بالكامل تُرفض', () => {
    const settlements: Settlement[] = [
      { id: 's1', transactionId: 'x', obligationId: 'obl-cust', amountMinor: parseMoney('200.00') },
    ]
    const check = checkSettlement(custodyObl, settlements, parseMoney('50.00'))
    expect(check.allowed).toBe(false)
    expect(check.reason).toContain('متسدد')
  })
})

describe('برق والرسوم — spec/06', () => {
  it('تحويل لحسابي في مصر: حركة داخلية، لا مصروف 1000 ولا دخل 1000', () => {
    const transfer = txn('internal_transfer', '1000.00')
    const totals = computePeriodTotals([transfer], [])
    expect(totals.personalExpenseMinor).toBe(0)
    expect(totals.incomeMinor).toBe(0)
  })

  it('تغيير الغرض إلى دعم ⇒ مصروف 1000 مرة واحدة', () => {
    const support = txn('support_gift', '1000.00')
    const totals = computePeriodTotals([support], [])
    expect(formatAmount(totals.personalExpenseMinor)).toBe('1,000.00')
  })

  it('رسوم 20 ضمن خصم 1020 ⇒ أصل 1000 + رسوم 20، لا إجمالي 1040', () => {
    const split = splitGrossIntoPrincipalAndFee(parseMoney('1020.00'), parseMoney('20.00'))
    expect(formatAmount(split.principalMinor)).toBe('1,000.00')
    expect(formatAmount(split.feeMinor)).toBe('20.00')
    expect(split.principalMinor + split.feeMinor).toBe(parseMoney('1020.00'))

    // الأصل تحويل داخلي (لا مصروف) + الرسوم مصروف مستقل = 20 فقط
    const principal = txn('internal_transfer', '1000.00')
    const fee = txn('fee', '20.00')
    const totals = computePeriodTotals([principal, fee], [])
    expect(formatAmount(totals.personalExpenseMinor)).toBe('20.00')
    expect(totals.personalExpenseMinor).not.toBe(parseMoney('1040.00'))
  })

  it('الرسوم لا تتجاوز الإجمالي', () => {
    expect(() => splitGrossIntoPrincipalAndFee(parseMoney('20.00'), parseMoney('30.00'))).toThrow(LedgerError)
  })
})

describe('حالات حدية في المجاميع — spec/06', () => {
  it('دخل 0 ⇒ الادخار غير متاح، لا Infinity ولا NaN', () => {
    const totals = computePeriodTotals([txn('purchase', '100.00')], [])
    expect(totals.incomeMinor).toBe(0)
    const rate = savingsRatePercent(totals.incomeMinor, totals.remainingMinor)
    expect(rate).toBeNull()
    expect(formatPercentOrNA(rate)).toBe(NOT_AVAILABLE)
  })

  it('مصروف أعلى من الدخل ⇒ متبقي ونسبة سالبان واضحان', () => {
    const totals = computePeriodTotals(
      [txn('salary', '1000.00', { observedDirection: 'in' }), txn('purchase', '1500.00')],
      [],
    )
    expect(formatAmount(totals.remainingMinor)).toBe('-500.00')
    const rate = savingsRatePercent(totals.incomeMinor, totals.remainingMinor)
    expect(rate).toBe(-50)
    expect(formatPercentOrNA(rate)).toBe('-50.0%')
  })

  it('فترة لا بيانات فيها ⇒ صفر عمليات، لا أرقام شهر آخر', () => {
    const totals = computePeriodTotals([], [])
    expect(totals.incomeMinor).toBe(0)
    expect(totals.personalExpenseMinor).toBe(0)
    expect(totals.remainingMinor).toBe(0)
    expect(savingsRatePercent(totals.incomeMinor, totals.remainingMinor)).toBeNull()
  })

  it('الاستبعاد من الميزانية يُعرض منفصلًا ولا يختفي', () => {
    const normal = txn('purchase', '100.00')
    const excluded = txn('purchase', '900.00', { excludedFromBudget: true })
    const totals = computePeriodTotals([normal, excluded], [])
    expect(formatAmount(totals.personalExpenseMinor)).toBe('100.00')
    expect(formatAmount(totals.excludedExpenseMinor)).toBe('900.00') // ظاهر لا مخفي
  })
})
