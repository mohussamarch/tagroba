import { makeManagePeople } from '../../src/application/useCases/managePeople'
import { MemoryTransactionRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryAllocationRepository, MemoryObligationRepository, MemorySettlementRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryPersonRepository } from '../../src/infrastructure/memory/memoryPeopleRepositories'
import { memorySettlementWriter } from '../../src/infrastructure/memory/settlementWriter'
import { MemoryUnitOfWork, SequentialIdGenerator, FixedClock } from '../../src/infrastructure/memory/memorySupport'
import type { Obligation, Person, PersonAllocation, Settlement, Transaction } from '../../src/domain/entities/types'
import { recordAsync } from './goldenKit'

/**
 * الأشخاص والديون والأمانات — نقل تدفق `managePeople`. ⚠️ بيانات وهمية بالكامل.
 * القواعد الحاكمة: مفيش تقاص تلقائي بين «لك» و«عليك»، القرض والأمانة منفصلين،
 * الزيادة في التسوية بتترفض بتفسير، والهدية تخصيص بلا التزام (spec/02 و spec/06).
 */

function txn(id: string, amountMinor: number): Transaction {
  return {
    id, occurredAt: '2026-09-05', datePrecision: 'day', sourceOrder: 1,
    economicKind: 'purchase', economicKindConfirmed: true,
    observedDirection: 'out', amountMinor, currency: 'SAR',
    categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested',
    isCashTagged: false, createdAt: '2026-09-01T00:00:00.000Z', updatedAt: '2026-09-01T00:00:00.000Z',
  }
}

interface Seed {
  people: Person[]
  obligations: Obligation[]
  settlements: Settlement[]
  allocations: PersonAllocation[]
  transactions: Transaction[]
}

type Action =
  | { kind: 'list' }
  | { kind: 'addPerson'; name: string }
  | { kind: 'archive'; personId: string; archived: boolean }
  | { kind: 'link'; transactionId: string; personId: string; obligationKind: string; amountMinor: number; asGift?: boolean }
  | { kind: 'settle'; obligationId: string; personId: string; amountMinor: number; transactionId?: string; requestId?: string; repeat?: boolean; secondAmountMinor?: number }
  | { kind: 'openingDebt'; personId: string; obligationKind: 'receivable' | 'loan_payable'; amountMinor: number }

export async function peopleFlowGolden() {
  const cases = []

  async function run(seed: Seed, action: Action) {
    cases.push(await recordAsync({ seed, action }, async () => {
      const people = new MemoryPersonRepository(seed.people)
      const obligations = new MemoryObligationRepository()
      await obligations.saveMany(seed.obligations)
      const settlements = new MemorySettlementRepository()
      await settlements.saveMany(seed.settlements)
      const allocations = new MemoryAllocationRepository()
      await allocations.saveMany(seed.allocations)
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(seed.transactions)
      const manage = makeManagePeople({
        people, obligations, settlements, allocations, txns,
        settlementWriter: memorySettlementWriter(obligations, settlements),
        uow: new MemoryUnitOfWork([txns]),
        ids: new SequentialIdGenerator(),
        clock: new FixedClock('2026-09-22T10:00:00.000Z'),
      })
      const stored = async () => ({
        storedPeople: await people.listAll(),
        storedObligations: await obligations.all(),
        storedSettlements: await settlements.all(),
        storedAllocations: (await allocations.listByTransactionIds(seed.transactions.map((t) => t.id))),
      })
      switch (action.kind) {
        case 'list': {
          const rows = await manage.listWithBalances()
          return {
            rows: rows.map((r) => ({
              personId: r.person.id,
              archived: r.person.archived,
              balance: r.balance,
              obligations: r.obligations.map((o) => ({ obligationId: o.obligation.id, remainingMinor: o.remainingMinor })),
            })),
          }
        }
        case 'addPerson': return { created: await manage.addPerson(action.name), ...(await stored()) }
        case 'archive': await manage.archivePerson(action.personId, action.archived); return await stored()
        case 'link': {
          const result = await manage.linkToPerson({
            transactionId: action.transactionId, personId: action.personId,
            kind: action.obligationKind as Obligation['kind'], amountMinor: action.amountMinor, asGift: action.asGift,
          })
          return { obligation: result.obligation, allocation: result.allocation, ...(await stored()) }
        }
        case 'settle': {
          const first = await manage.settle({
            obligationId: action.obligationId, personId: action.personId,
            amountMinor: action.amountMinor, transactionId: action.transactionId, requestId: action.requestId,
          })
          // التكرار بنفس المعرّف: لازم يرجّع نفس التسوية من غير ما يكرر الخصم
          const second = action.repeat
            ? await manage.settle({
                obligationId: action.obligationId, personId: action.personId,
                amountMinor: action.secondAmountMinor ?? action.amountMinor,
                transactionId: action.transactionId, requestId: action.requestId,
              })
            : null
          return { first, second, ...(await stored()) }
        }
        case 'openingDebt':
          return { obligation: await manage.addOpeningDebt({ personId: action.personId, kind: action.obligationKind, amountMinor: action.amountMinor }), ...(await stored()) }
      }
    }))
  }

  /*
   * سعد: «ليا عنده» 300 اتسدد منها 100 ⇒ رصيد 200 · أمانة 50 كاملة.
   * منى: «عليا ليها» قرض 500 · التزام متسدد بالكامل (بيختفي من القايمة النشطة).
   * وليد: مؤرشف برصيد صفر — بيترتب آخر واحد.
   */
  const baseSeed: Seed = {
    people: [
      { id: 'p-saad', name: 'سعد', archived: false },
      { id: 'p-mona', name: 'منى', archived: false },
      { id: 'p-waleed', name: 'وليد', archived: true },
    ],
    obligations: [
      { id: 'ob-1', personId: 'p-saad', originTransactionId: 't-1', kind: 'receivable', originalMinor: 30000, currency: 'SAR' },
      { id: 'ob-2', personId: 'p-saad', originTransactionId: null, kind: 'custody_payable', originalMinor: 5000, currency: 'SAR' },
      { id: 'ob-3', personId: 'p-mona', originTransactionId: null, kind: 'loan_payable', originalMinor: 50000, currency: 'SAR' },
      { id: 'ob-done', personId: 'p-mona', originTransactionId: null, kind: 'receivable', originalMinor: 2000, currency: 'SAR' },
    ],
    settlements: [
      { id: 'st-1', transactionId: 't-pay', obligationId: 'ob-1', amountMinor: 10000 },
      { id: 'st-2', transactionId: '', obligationId: 'ob-done', amountMinor: 2000 },
    ],
    allocations: [],
    transactions: [txn('t-1', 30000), txn('t-2', 20000), txn('t-pay', 10000)],
  }

  await run(baseSeed, { kind: 'list' })
  await run(baseSeed, { kind: 'addPerson', name: '  خالد  ' })
  await run(baseSeed, { kind: 'addPerson', name: '   ' })
  await run(baseSeed, { kind: 'addPerson', name: 'س'.repeat(81) })
  await run(baseSeed, { kind: 'addPerson', name: 'سعد' })
  await run(baseSeed, { kind: 'archive', personId: 'p-saad', archived: true })
  await run(baseSeed, { kind: 'archive', personId: 'p-ghost', archived: true })
  // ربط عملية: دين ليك · هدية (تخصيص بلا التزام) · تجاوز قيمة العملية · عملية مش موجودة · مبلغ صفر
  await run(baseSeed, { kind: 'link', transactionId: 't-2', personId: 'p-saad', obligationKind: 'receivable', amountMinor: 12000 })
  await run(baseSeed, { kind: 'link', transactionId: 't-2', personId: 'p-mona', obligationKind: 'receivable', amountMinor: 8000, asGift: true })
  await run(
    { ...baseSeed, allocations: [{ id: 'al-x', transactionId: 't-2', personId: 'p-saad', allocationKind: 'receivable', amountMinor: 15000, currency: 'SAR' }] },
    { kind: 'link', transactionId: 't-2', personId: 'p-mona', obligationKind: 'receivable', amountMinor: 6000 },
  )
  await run(baseSeed, { kind: 'link', transactionId: 't-ghost', personId: 'p-saad', obligationKind: 'receivable', amountMinor: 100 })
  await run(baseSeed, { kind: 'link', transactionId: 't-2', personId: 'p-saad', obligationKind: 'receivable', amountMinor: 0 })
  // تسوية: عادية · بمعرّف طلب متكرر (نفس النتيجة من غير تكرار) · نفس المعرّف بمبلغ مختلف · زيادة مرفوضة · معرّف طلب غلط · شخص غلط
  await run(baseSeed, { kind: 'settle', obligationId: 'ob-1', personId: 'p-saad', amountMinor: 5000, transactionId: 't-pay' })
  await run(baseSeed, { kind: 'settle', obligationId: 'ob-1', personId: 'p-saad', amountMinor: 5000, requestId: 'req-1', repeat: true })
  await run(baseSeed, { kind: 'settle', obligationId: 'ob-1', personId: 'p-saad', amountMinor: 5000, requestId: 'req-2', repeat: true, secondAmountMinor: 7000 })
  await run(baseSeed, { kind: 'settle', obligationId: 'ob-1', personId: 'p-saad', amountMinor: 25000 })
  await run(baseSeed, { kind: 'settle', obligationId: 'ob-1', personId: 'p-saad', amountMinor: 5000, requestId: 'مش صالح!' })
  await run(baseSeed, { kind: 'settle', obligationId: 'ob-1', personId: 'p-mona', amountMinor: 5000 })
  // دين قديم من غير عملية (OVERRIDES §27)
  await run(baseSeed, { kind: 'openingDebt', personId: 'p-mona', obligationKind: 'receivable', amountMinor: 40000 })
  await run(baseSeed, { kind: 'openingDebt', personId: 'p-ghost', obligationKind: 'loan_payable', amountMinor: 100 })
  await run(baseSeed, { kind: 'openingDebt', personId: 'p-mona', obligationKind: 'receivable', amountMinor: 0 })

  return { managePeople: cases }
}
