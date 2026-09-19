import { balanceKey, buildDedupeIndex, classifyCandidate, detailKey, hashContent, importFingerprint, referenceKey, type DedupeCandidate, type ExistingRecord } from '../../src/domain/dedupe'
import { planAmountEdit, sourceAmountMinor } from '../../src/domain/amountEdit'
import { record, seeded } from './goldenKit'

/** منع التكرار (الدرجات الخمس spec/05) وبصمة الملف وتعديل المبلغ — بيانات وهمية. */
export function dedupeGolden() {
  const rnd = seeded(505)
  const ACCOUNTS = ['الراجحي', 'كاش']
  const MERCHANTS = ['TEST MART', 'test  mart', 'مطعم تجريبي', 'مطعم  تجريبى', 'SHOP 1', '']
  const DATES = ['2026-09-01', '2026-09-02', '2026-09-03']
  const AMOUNTS = [925, 1000, 2500, 10038]
  const candidate = (i: number): DedupeCandidate => {
    const c: DedupeCandidate = {
      accountIdentity: rnd.pick(ACCOUNTS), sourceReference: rnd.pick([null, '', '  ', `REF-${rnd.int(1, 6)}`, ` REF-${rnd.int(1, 6)} `]),
      date: rnd.pick(DATES), amountMinor: rnd.pick(AMOUNTS), direction: rnd.pick(['in', 'out'] as const), merchantName: rnd.pick(MERCHANTS), rowIndex: i,
    }
    if (rnd.next() < 0.3) c.smsSource = true
    if (rnd.next() < 0.5) c.statedBalanceMinor = rnd.pick([50000, 60000, 70000])
    return c
  }
  const scenarios = Array.from({ length: 40 }, (_, s) => {
    const existing: ExistingRecord[] = Array.from({ length: rnd.int(0, 12) }, (_, i) => ({ ...candidate(i), transactionId: `tx-${s}-${i}` }))
    const incoming = Array.from({ length: 10 }, (_, i) => candidate(100 + i))
    const consumed: Record<string, number> = {}
    for (const e of existing.slice(0, 3)) { const k = balanceKey(e); if (k) consumed[k] = rnd.int(0, 2) }
    return { existing, incoming, consumed }
  })
  // سيناريوهات مقصودة: إعادة استيراد نفس الكشف (مكرر بالمرجع)، وتعارض، وسطرين بنفس الرصيد قصاد سجل واحد (واحد لواحد)
  const base = { accountIdentity: 'الراجحي', date: '2026-09-02', amountMinor: 2500, direction: 'out' as const, merchantName: 'TEST MART', rowIndex: 1 }
  scenarios.push(
    { existing: [{ ...base, sourceReference: 'R1', transactionId: 'x1' }, { ...base, sourceReference: 'R2', amountMinor: 900, transactionId: 'x2' }],
      incoming: [{ ...base, sourceReference: 'R1' }, { ...base, sourceReference: ' R1 ' }, { ...base, sourceReference: 'R2' }, { ...base, sourceReference: 'R1', accountIdentity: 'كاش' }, { ...base, sourceReference: 'R1', merchantName: 'test   mart' }], consumed: {} },
    { existing: [{ ...base, sourceReference: null, statedBalanceMinor: 70000, transactionId: 'y1' }],
      incoming: [{ ...base, sourceReference: null, statedBalanceMinor: 70000, merchantName: 'اسم مقروء غلط' }, { ...base, sourceReference: null, statedBalanceMinor: 70000 }], consumed: {} },
    { existing: [{ ...base, sourceReference: null, statedBalanceMinor: 70000, transactionId: 'y1' }, { ...base, sourceReference: null, statedBalanceMinor: 70000, transactionId: 'y2' }],
      incoming: [{ ...base, sourceReference: null, statedBalanceMinor: 70000 }], consumed: { 'الراجحي|2026-09-02|2500|out|70000': 1 } },
    { existing: [{ ...base, sourceReference: null, smsSource: true, transactionId: 'z1' }], incoming: [{ ...base, sourceReference: 'NEWREF', merchantName: 'اسم تاني' }, { ...base, sourceReference: null }], consumed: {} },
  )
  const texts = ['', 'a', 'abc', 'مصروفي', 'emoji 🙂', 'line1\nline2', 'x'.repeat(1000), 'التاريخ,المبلغ\n2026-09-01,25.00', String.fromCharCode(0, 0xff, 0x100, 0xffff), 'Ω≈ç√']
  for (let i = 0; i < 40; i++) texts.push(Array.from({ length: rnd.int(1, 80) }, () => String.fromCharCode(rnd.int(0, 0xffff))).join(''))

  return {
    keys: scenarios.slice(0, 10).flatMap((s) => s.incoming.map((c) => record(c, () => ({ detail: detailKey(c), reference: referenceKey(c), balance: balanceKey(c) })))),
    classifyCandidate: scenarios.map((s) => record(s, () => {
      const index = buildDedupeIndex(s.existing)
      const consumed = new Map(Object.entries(s.consumed))
      return s.incoming.map((c) => classifyCandidate(c, index, consumed))
    })),
    hashContent: texts.map((t) => record(t, () => hashContent(t))),
    importFingerprint: texts.slice(0, 20).flatMap((t) => ACCOUNTS.map((a) => record({ content: t, account: a }, () => importFingerprint(t, a)))),
    sourceAmountMinor: [{ amountMinor: 1800 }, { amountMinor: 1800, originalAmountMinor: 2000 }].map((t) => record(t, () => sourceAmountMinor(t))),
    planAmountEdit: [
      [{ amountMinor: 2000 }, 1800, [], []], [{ amountMinor: 1800, originalAmountMinor: 2000 }, 1500, [], []],
      [{ amountMinor: 2000 }, 0, [], []], [{ amountMinor: 2000 }, -5, [], []], [{ amountMinor: 2000 }, 1000, [{ amountMinor: 600 }, { amountMinor: 500 }], []],
      [{ amountMinor: 2000 }, 1000, [{ amountMinor: 400 }], [{ amountMinor: 1200 }]], [{ amountMinor: 2000 }, 1200, [{ amountMinor: 400 }], [{ amountMinor: 1200 }]],
      [{ amountMinor: 2000 }, 9007199254740992, [], []],
    ].map(([txn, amount, allocations, settlements]) => record({ txn, amount, allocations, settlements }, () =>
      planAmountEdit(txn as { amountMinor: number }, amount as number, allocations as { amountMinor: number }[], settlements as { amountMinor: number }[]))),
  }
}
