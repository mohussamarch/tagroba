import { describe, it, expect } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { parseCsv } from '../../src/infrastructure/import/csvReader'
import { parseRows } from '../../src/infrastructure/import/schemas'
import { computeExpenseBreakdown } from '../../src/domain/expenseClassification'
import { reconcileBalance, type LedgerMovement } from '../../src/domain/reconcile'
import { formatAmount } from '../../src/domain/formatMoney'
import { parseMoney } from '../../src/domain/money'

/**
 * معايير القبول على البيانات الحقيقية — OVERRIDES §7 و CLAUDE.md.
 *
 * يعمل عبر مستودعات الذاكرة/التحليل النقي فقط. **بدون فايربيز إطلاقًا.**
 *
 * الملف مستبعد من Git (فيه كشف بنكي حقيقي — ARCHITECTURE.md §9.5).
 * عند غيابه يفشل الاختبار برسالة صريحة بدل أن يمر زورًا،
 * لأن «لا ادعاء نجاح لم يحدث» (CLAUDE.md #14).
 */

const CSV_PATH = resolve(__dirname, '../../files/transactions_full.csv')

const TARGETS = {
  debit: parseMoney('302171.45'),
  nonExpense: parseMoney('201703.18'),
  realExpense: parseMoney('100468.27'),
  credit: parseMoney('300040.87'),
  opening: parseMoney('4837.83'),
  closing: parseMoney('2707.25'),
  openingAt: '2025-01-01',
  closingAt: '2026-09-04',
  rowCount: 1912,
}

describe('معايير القبول على files/transactions_full.csv', () => {
  if (!existsSync(CSV_PATH)) {
    it('ملف الكشف الحقيقي غير موجود', () => {
      throw new Error(
        `الملف ${CSV_PATH} مش موجود. معايير القبول على البيانات الحقيقية ` +
          `مش ممكن التحقق منها من غيره. الاختبار ده بيفشل عمدًا بدل ما يعدّي زورًا.`,
      )
    })
    return
  }

  const raw = readFileSync(CSV_PATH, 'utf8')
  const doc = parseCsv(raw)
  const outcome = parseRows(doc)

  it('يقرأ الملف بالمخطط القديم بلا أخطاء صفوف', () => {
    expect(outcome.schema).toBe('legacy')
    expect(outcome.errors).toEqual([])
    expect(outcome.rows).toHaveLength(TARGETS.rowCount)
  })

  const classifiable = outcome.rows.map((r) => ({
    debitMinor: r.direction === 'out' ? r.amountMinor : 0,
    creditMinor: r.direction === 'in' ? r.amountMinor : 0,
    sourceCategory: r.sourceCategory,
  }))
  const breakdown = computeExpenseBreakdown(classifiable)

  it('معيار (ب): المجاميع المرجعية الأربعة مطابقة بالهللة', () => {
    expect(formatAmount(breakdown.totalDebitMinor)).toBe('302,171.45')
    expect(formatAmount(breakdown.nonExpenseMinor)).toBe('201,703.18')
    expect(formatAmount(breakdown.realExpenseMinor)).toBe('100,468.27')
    expect(formatAmount(breakdown.totalCreditMinor)).toBe('300,040.87')

    expect(breakdown.totalDebitMinor).toBe(TARGETS.debit)
    expect(breakdown.nonExpenseMinor).toBe(TARGETS.nonExpense)
    expect(breakdown.realExpenseMinor).toBe(TARGETS.realExpense)
    expect(breakdown.totalCreditMinor).toBe(TARGETS.credit)
  })

  it('المصروف الحقيقي = المدين − ما ليس مصروفًا، بلا فجوة', () => {
    expect(breakdown.totalDebitMinor - breakdown.nonExpenseMinor).toBe(breakdown.realExpenseMinor)
  })

  it('كل تصنيفات «ليس مصروفًا» الستة موجودة فعلًا في البيانات', () => {
    const found = Object.keys(breakdown.nonExpenseByCategory).sort()
    expect(found).toEqual(
      ['استثمار', 'تحويلات', 'تقسيط', 'ذهب', 'سحب نقدي', 'محافظ رقمية'].sort(),
    )
  })

  const movements: LedgerMovement[] = outcome.rows.map((r, i) => {
    const m: LedgerMovement = {
      date: r.date,
      sourceOrder: i,
      debitMinor: r.direction === 'out' ? r.amountMinor : 0,
      creditMinor: r.direction === 'in' ? r.amountMinor : 0,
      label: r.merchantName || r.sourceCategory,
    }
    if (r.statedBalanceMinor !== undefined) m.statedBalanceMinor = r.statedBalanceMinor
    return m
  })

  const result = reconcileBalance(TARGETS.opening, TARGETS.openingAt, movements)

  it('معيار (ج): سلسلة الرصيد تنتهي عند 2,707.25 بتاريخ 2026-09-04', () => {
    expect(formatAmount(result.closingMinor)).toBe('2,707.25')
    expect(result.closingMinor).toBe(TARGETS.closing)
    expect(result.closingAt).toBe(TARGETS.closingAt)
  })

  it('معيار (ج): عمود الرصيد مطابق في كل سطر بدقة الهللة — صفر فروق', () => {
    if (result.mismatches.length > 0) {
      const sample = result.mismatches
        .slice(0, 5)
        .map(
          (m) =>
            `  صف ${m.index + 2} (${m.date}): محسوب ${formatAmount(m.computedMinor)} ` +
            `مقابل معلن ${formatAmount(m.statedMinor)} — فرق ${formatAmount(m.differenceMinor)}`,
        )
        .join('\n')
      throw new Error(
        `${result.mismatches.length} سطر رصيده لا يطابق.\n${sample}\n${result.ambiguityNote ?? ''}`,
      )
    }
    expect(result.mismatches).toHaveLength(0)
    expect(result.checkedCount).toBe(TARGETS.rowCount) // كل سطر قُورن فعلًا
  })

  it('مجاميع المطابقة تساوي مجاميع الفصل — مصدر واحد للحقيقة', () => {
    expect(result.totalDebitMinor).toBe(breakdown.totalDebitMinor)
    expect(result.totalCreditMinor).toBe(breakdown.totalCreditMinor)
    // البرهان الحسابي: افتتاحي − مدين + دائن = ختامي
    expect(TARGETS.opening - TARGETS.debit + TARGETS.credit).toBe(TARGETS.closing)
  })
})
