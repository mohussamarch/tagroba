import { ALL_ECONOMIC_KINDS, isConsistentWithObservedDirection, ruleFor, type EconomicKind } from '../../src/domain/entities/economicKind'
import type { Obligation, ObligationKind, PersonAllocation, Settlement, Transaction } from '../../src/domain/entities/types'
import {
  checkSettlement, computePeriodTotals, computePersonBalance, personalShareOf, remainingOfObligation, splitGrossIntoPrincipalAndFee, sumByTag,
} from '../../src/domain/ledger'
import { isBulkConfirmable, suggestEconomicKind, type SuggestionInput } from '../../src/domain/suggestEconomicKind'
import { withEstimatedKinds } from '../../src/domain/estimatedKinds'
import { record, seeded } from './goldenKit'

/** حالات الدفتر والأنواع والاقتراح و«التقريبي» — عمليات وأشخاص وهميين. */
export function ledgerGolden() {
  const rnd = seeded(2025)
  const CATS = ['سحب نقدي', 'استثمار', 'ذهب', 'رسوم بنكية', 'تقسيط', 'سداد', 'محافظ رقمية', 'تحويلات', 'إيداع', 'استرداد', 'بقالة', 'مطاعم وقهوة', 'غير مصنّف', 'غير مصنف', '', '  ', ' سحب  نقدي ']
  const NAMES = ['TEST MART', 'BARQ WALLET', 'barq', 'مطعم تجريبي', '', 'Transfer to X']
  const categoryNames = new Map(CATS.filter((c) => c.trim()).map((c, i) => [`cat-${i}`, c]))
  const categoryIds = [...categoryNames.keys(), 'cat-missing']

  const txn = (i: number, over: Partial<Transaction> = {}): Transaction => {
    const direction = rnd.next() < 0.7 ? 'out' : 'in'
    const t: Transaction = {
      id: `t-${i}`, occurredAt: '2026-09-10', datePrecision: 'day', sourceOrder: i,
      economicKind: rnd.pick(ALL_ECONOMIC_KINDS), economicKindConfirmed: rnd.next() < 0.5, observedDirection: direction,
      amountMinor: rnd.int(1, 5_000_000), currency: 'SAR', categoryConfirmed: false,
      excludedFromBudget: rnd.next() < 0.2, reviewState: 'suggested', isCashTagged: false,
      createdAt: '2026-09-10T00:00:00Z', updatedAt: '2026-09-10T00:00:00Z',
    }
    if (rnd.next() < 0.6) t.categoryId = rnd.pick(categoryIds)
    if (rnd.next() < 0.7) t.rawMerchantName = rnd.pick(NAMES)
    if (rnd.next() < 0.5) t.rawDescription = rnd.pick(NAMES)
    return { ...t, ...over }
  }
  const allocationsFor = (list: Transaction[], allowOver = false): PersonAllocation[] => {
    const out: PersonAllocation[] = []
    for (const t of list) {
      if (rnd.next() > 0.35) continue
      const parts = rnd.int(1, 3)
      for (let p = 0; p < parts; p++) {
        const cap = allowOver ? t.amountMinor : Math.floor(t.amountMinor / parts)
        out.push({ id: `a-${t.id}-${p}`, transactionId: t.id, personId: `p-${p}`, allocationKind: rnd.next() < 0.7 ? 'receivable' : 'gift', amountMinor: rnd.int(0, cap), currency: 'SAR' })
      }
    }
    return out
  }

  const sets = Array.from({ length: 60 }, (_, s) => {
    const list = Array.from({ length: rnd.int(0, 25) }, (_, i) => txn(s * 100 + i))
    return { transactions: list, allocations: allocationsFor(list, s % 10 === 0) }
  })

  const obligationCases = Array.from({ length: 80 }, (_, i) => {
    const kind = rnd.pick<ObligationKind>(['receivable', 'loan_payable', 'custody_payable'])
    const obligation: Obligation = { id: `o-${i}`, personId: `p-${i % 4}`, originTransactionId: i % 5 ? `t-${i}` : null, kind, originalMinor: rnd.int(100, 1_000_000), currency: 'SAR' }
    const settlements: Settlement[] = Array.from({ length: rnd.int(0, 4) }, (_, s) => ({ id: `s-${i}-${s}`, transactionId: `t-s-${s}`, obligationId: i % 9 ? obligation.id : 'other', amountMinor: rnd.int(1, Math.ceil(obligation.originalMinor / 2)) }))
    return { obligation, settlements }
  })

  const suggestionInputs: SuggestionInput[] = []
  for (const direction of ['in', 'out'] as const) {
    for (const c of CATS) {
      suggestionInputs.push({ direction, sourceCategory: c })
      suggestionInputs.push({ direction, categoryName: c, merchantName: 'TEST' })
      suggestionInputs.push({ direction, sourceCategory: ' ', categoryName: c })
    }
    for (const n of NAMES) suggestionInputs.push({ direction, merchantName: n, description: rnd.pick(NAMES) })
    suggestionInputs.push({ direction })
  }

  return {
    economicKindRules: ALL_ECONOMIC_KINDS.map((k) => record(k, () => ruleFor(k))),
    isConsistentWithObservedDirection: ALL_ECONOMIC_KINDS.flatMap((k) => (['in', 'out'] as const).map((d) => record({ kind: k, observed: d }, () => isConsistentWithObservedDirection(k, d)))),
    personalShareOf: sets.flatMap((set) => set.transactions.slice(0, 4).map((t) => {
      // تخصيصات العملية نفسها + واحد لعملية تانية (لازم يتجاهل)
      const allocations = [...set.allocations.filter((a) => a.transactionId === t.id), ...set.allocations.filter((a) => a.transactionId !== t.id).slice(0, 1)]
      return record({ transaction: t, allocations }, () => personalShareOf(t, allocations))
    })),
    computePeriodTotals: sets.map((set) => record(set, () => computePeriodTotals(set.transactions, set.allocations))),
    sumByTag: sets.slice(0, 20).map((set) => {
      const ids = set.transactions.filter(() => rnd.next() < 0.5).map((t) => t.id)
      const tagged = [...ids, ...ids.slice(0, 2), 'missing']
      return record({ transactions: set.transactions, tagged, allocations: set.allocations }, () => sumByTag(set.transactions, tagged, set.allocations))
    }),
    remainingOfObligation: obligationCases.map((c) => record(c, () => remainingOfObligation(c.obligation, c.settlements))),
    computePersonBalance: [0, 1, 2, 3, 9].map((p) => {
      const cases = obligationCases.filter((c) => c.settlements.reduce((n, s) => n + (s.obligationId === c.obligation.id ? s.amountMinor : 0), 0) <= c.obligation.originalMinor)
      const obligations = cases.map((c) => c.obligation), settlements = cases.flatMap((c) => c.settlements)
      return record({ personId: `p-${p}`, obligations, settlements }, () => computePersonBalance(`p-${p}`, obligations, settlements))
    }),
    checkSettlement: obligationCases.flatMap((c) => [0, -5, 1, c.obligation.originalMinor, c.obligation.originalMinor * 2, rnd.int(1, c.obligation.originalMinor)].map((amount) => record({ ...c, amount }, () => checkSettlement(c.obligation, c.settlements, amount)))),
    splitGrossIntoPrincipalAndFee: [[102000, 2000], [1000, 0], [1000, 1000], [1000, 1001], [1000, -1], [0, 0]].map(([g, f]) => record({ gross: g, fee: f }, () => splitGrossIntoPrincipalAndFee(g!, f!))),
    suggestEconomicKind: suggestionInputs.map((input) => record(input, () => {
      const s = suggestEconomicKind(input)
      return { ...s, bulkConfirmable: isBulkConfirmable(s) }
    })),
    withEstimatedKinds: sets.slice(0, 30).map((set) => record({ transactions: set.transactions, categoryNames: Object.fromEntries(categoryNames) }, () => {
      const view = withEstimatedKinds(set.transactions, categoryNames)
      return { kinds: view.transactions.map((t) => t.economicKind as EconomicKind), estimatedCount: view.estimatedCount, needsReviewCount: view.needsReviewCount }
    })),
  }
}
