import {
  addMoney, compareMoney, multiplyMoneyByInt, parseMoney, rateOfMoney, splitMoney, subtractMoney,
  tryParseMoney, withinRelativeTolerance, normalizeDigits, absMoney, negateMoney,
} from '../../src/domain/money'
import { formatAmount, formatMoney, formatMoneyOrNA, formatPercentOrNA, savingsRatePercent } from '../../src/domain/formatMoney'
import { record, seeded } from './goldenKit'

const MAX = Number.MAX_SAFE_INTEGER

/** حالات `money.ts` و`formatMoney.ts` — أرقام وهمية. */
export function moneyGolden() {
  const rnd = seeded(20260919)
  const parseInputs = [
    '96.47', '1,234.5', '٩٦٫٤٧', '١٬٢٣٤٫٥', '۱۲۳', '-12', '12.4-', '(12.40)', '(−3)', '−3.00', '+5', '.5', '5.', '  7 ',
    '12 345.6', '1 000', '1 234,00', '\t8\n', '﻿9', '1.230', '1.234', '1.2.3', '', '   ', 'NaN', 'Infinity',
    'abc', '12a', '(12', '12)', '()', '-', '.', '1e3', '0x10', '--5', '-(5)', '(-5)', '5--', '0', '-0', '0.00', '00012.50',
    '90071992547409.91', '90071992547409.92', '9007199254740991', '1234567890123456', '12345678901234567',
    '٠٫٠١', '1٫5', '١,٠٠٠', '1 000', '1　000', '５', '12.', '-.5', '+-5', '5+',
  ]
  const amounts = [0, 1, -1, 9, 10, 99, 100, 101, 9647, -9647, 100000, 123456789, -1000000000, 2**31, MAX, -MAX]
  for (let i = 0; i < 200; i++) amounts.push(rnd.int(-10_000_000_000, 10_000_000_000))
  const rates: [number, number][] = [[1, 2], [1, 3], [2, 3], [50, 1000], [-1, 2], [1, -2], [-1, -2], [7, 100], [15, 100], [0, 5], [3, 0], [1000, 1000], [1, 1_000_000]]
  const rateBases = [0, 1, -1, 2, 3, 5, 9647, -9647, 12345, 100000, 999_999]
  const percents: [number, number][] = [[0, 0], [100, 50], [100, -50], [8, 1], [2000, 1], [2000, -1], [3, 1], [3, 2], [7, -3], [1_000_000, 123_456], [123_456_789, -98_765_432]]
  for (let i = 0; i < 60; i++) percents.push([rnd.int(1, 5_000_000), rnd.int(-5_000_000, 5_000_000)])

  return {
    normalizeDigits: ['٠١٢٣٤٥٦٧٨٩', '۰۱۲۳۴۵۶۷۸۹', '١٫٥', '١٬٠٠٠', 'abc', '', 'مبلغ ٢٥ ريال'].map((s) => record(s, () => normalizeDigits(s))),
    parseMoney: parseInputs.map((s) => record(s, () => parseMoney(s))),
    tryParseMoney: parseInputs.map((s) => record(s, () => tryParseMoney(s))),
    formatAmount: amounts.flatMap((a) => [
      record({ amount: a }, () => formatAmount(a)),
      record({ amount: a, alwaysSign: true }, () => formatAmount(a, 'SAR', { alwaysSign: true })),
      record({ amount: a, grouping: false }, () => formatAmount(a, 'SAR', { grouping: false })),
    ]),
    formatMoney: [0, 9647, -9647, 100000].flatMap((a) => [
      record({ amount: a }, () => formatMoney(a)),
      record({ amount: a, hidden: true }, () => formatMoney(a, 'SAR', { hidden: true })),
      record({ amount: a, hidden: true, showCurrency: false }, () => formatMoney(a, 'SAR', { hidden: true, showCurrency: false })),
      record({ amount: a, showCurrency: false }, () => formatMoney(a, 'SAR', { showCurrency: false })),
    ]),
    formatMoneyOrNA: [null, 0, 9647].map((a) => record({ amount: a }, () => formatMoneyOrNA(a))),
    addMoney: [[], [1], [1, 2, 3], [-5, 5], [MAX, 0], [MAX, 1], [-MAX, -1], [9647, -1, 100]].map((list) => record(list, () => addMoney(...list))),
    subtractMoney: [[5, 3], [3, 5], [-MAX, 1], [MAX, -1], [0, 0]].map(([a, b]) => record([a, b], () => subtractMoney(a!, b!))),
    negateAbs: [0, 5, -5, MAX].map((a) => record(a, () => [negateMoney(a), absMoney(a)])),
    multiplyMoneyByInt: [[5, 3], [-5, 3], [9647, 12], [MAX, 2], [100, 0]].map(([a, t]) => record([a, t], () => multiplyMoneyByInt(a!, t!))),
    rateOfMoney: rateBases.flatMap((a) => rates.map(([n, d]) => record({ amount: a, numerator: n, denominator: d }, () => rateOfMoney(a, n, d)))),
    splitMoney: [0, 1, 2, 10, 100, 101, 9647, -9647, -1, 1_000_001].flatMap((a) => [1, 2, 3, 7, 0, -1].map((p) => record({ amount: a, parts: p }, () => splitMoney(a, p)))),
    compareMoney: [[1, 2], [2, 1], [3, 3], [-1, 0]].map(([a, b]) => record([a, b], () => compareMoney(a!, b!))),
    withinRelativeTolerance: [[100, 100, 50], [105, 100, 50], [106, 100, 50], [95, 100, 50], [94, 100, 50], [-100, -100, 50], [0, 0, 50], [19, 20, 50], [10500, 10000, 50], [10501, 10000, 50]].map(([v, t, p]) => record({ value: v, target: t, perThousand: p }, () => withinRelativeTolerance(v!, t!, p!))),
    savingsRatePercent: percents.map(([income, remaining]) => record({ income, remaining }, () => savingsRatePercent(income, remaining))),
    formatPercentOrNA: [null, 0, 12.5, -12.5, 100, -0.1, 0.1, 33.3, 250, 99.9].map((p) => record(p, () => formatPercentOrNA(p))),
  }
}
