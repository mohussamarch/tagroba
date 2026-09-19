import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { parseCsv } from '../../src/infrastructure/import/csvReader'
import { detectSchema, legacyDateToIso, parseRows } from '../../src/infrastructure/import/schemas'
import { reconcileBalance, type LedgerMovement } from '../../src/domain/reconcile'
import { record, seeded } from './goldenKit'

const FIXTURES = resolve(__dirname, '../../design-source/masroofi-claude-code/fixtures')

/** قراية CSV والمخططات ومطابقة الرصيد — fixtures (بيانات تجريبية معلنة) + حالات مصنوعة. */
export function importGolden() {
  const rnd = seeded(4)
  const fixtures = ['baseline.csv', 'conflict.csv', 'invalid.csv', 'repeated-import.csv'].map((f) => readFileSync(resolve(FIXTURES, f), 'utf8'))
  const preview = 'date,name,amount,type,source,reference'
  const legacy = 'التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل'
  const crafted = [
    '', '﻿', 'a', '\n\n', `${preview}\n`, `﻿${preview}\r\n2026-09-01,TEST,25.00,expense,Bank,R1\r\n`,
    `${preview}\n2026-09-01,"A, B",1,income,S,\n2026-09-02,"He said ""hi""",2.5,transfer,S,R2`,
    `${preview}\r2026-09-01,X,1,expense,S,R\r`, `${preview}\n2026-09-01,"open,1,expense`, `${preview}\n\n2026-09-01,X,1,expense,S,R\n\n`,
    `  Date , NAME ,amount,type,source,reference,extra\n2026-02-30,X,1,expense,S,R\n2026-09-01,X,abc,expense,S,R\n2026-09-01,X,-5,expense,S,R\n2026-09-01,X,0,expense,S,R\n2026-09-01,X,5,refund,S,R\n2026-09-01\n`,
    `${legacy}\n2026/9/1,25.00,0,1000.00,TEST MART,بقالة,شراء,تفاصيل\n٢٠٢٦/٠٩/٠٢,0,500,1500,,,,\n2026/13/01,1,0,,,,,\n2026/09/03,1,1,,,,,\n2026/09/03,0,0,,,,,\n2026/09/03,x,0,,,,,\n2026/09/03,0,-1,,,,,\n2026-09-04,1,,,م,,,\n2026/09/05`,
    'unknown,header\n1,2', `${legacy.split(',').slice(0, 7).join(',')}\n1,2`,
  ]
  const randomCsv = Array.from({ length: 30 }, () => {
    const pieces = ['a', 'b', ',', '"', '""', '\n', '\r\n', '\r', ' ', 'مصروف', '1.5', '٢']
    return Array.from({ length: rnd.int(0, 40) }, () => rnd.pick(pieces)).join('')
  })
  const csvInputs = [...fixtures, ...crafted, ...randomCsv]

  const movementSets = Array.from({ length: 40 }, () => {
    let balance = rnd.int(0, 1_000_000)
    const opening = balance
    const days = ['2026-09-01', '2026-09-02', '2026-09-02', '2026-09-03']
    const movements: LedgerMovement[] = Array.from({ length: rnd.int(0, 12) }, (_, i) => {
      const out = rnd.next() < 0.6
      const amount = rnd.int(1, 50_000)
      balance += out ? -amount : amount
      const m: LedgerMovement = { date: rnd.pick(days), sourceOrder: i, debitMinor: out ? amount : 0, creditMinor: out ? 0 : amount }
      if (rnd.next() < 0.7) m.statedBalanceMinor = rnd.next() < 0.15 ? balance + rnd.int(-500, 500) : balance
      if (rnd.next() < 0.5) m.reference = `REF-${i}`
      if (rnd.next() < 0.5) m.label = `عملية ${i}`
      return m
    }).sort((a, b) => a.date.localeCompare(b.date))
    return { opening, openingAt: '2026-08-31', movements }
  })

  return {
    parseCsv: csvInputs.map((text) => record(text, () => parseCsv(text))),
    detectSchema: csvInputs.map((text) => record(text, () => detectSchema(parseCsv(text)))),
    parseRows: csvInputs.flatMap((text) => [undefined, 'preview', 'legacy', 'sms'].map((schema) => record({ text, schema: schema ?? null }, () => parseRows(parseCsv(text), schema as never)))),
    legacyDateToIso: ['2026/9/1', '2026-09-01', ' 2026/09/01 ', '٢٠٢٦/٩/١', '2026/02/29', '2028/02/29', '2026/9/1/1', '26/9/1', '2026/009/01', ''].map((s) => record(s, () => legacyDateToIso(s))),
    reconcileBalance: movementSets.map((s) => record(s, () => reconcileBalance(s.opening, s.openingAt, s.movements))),
  }
}
