import {
  checkSettlement,
  computePersonBalance,
  remainingOfObligation,
  type PersonBalance,
} from '../../domain/ledger'
import { formatMoney } from '../../domain/formatMoney'
import type { Halalas } from '../../domain/money'
import type {
  Id,
  Obligation,
  ObligationKind,
  Person,
  PersonAllocation,
  Settlement,
} from '../../domain/entities/types'
import type {
  AllocationRepository,
  Clock,
  IdGenerator,
  ObligationRepository,
  PersonRepository,
  SettlementRepository,
  TransactionRepository,
  UnitOfWork,
} from '../ports/repositories'

/**
 * ManagePeople — الأشخاص والديون والأمانات.
 *
 * منطق الحساب كله في `domain/ledger.ts` ومختبَر بـ25 حالة من `spec/06`.
 * هذا الملف يربطه بالتخزين ولا يعيد حسابًا.
 *
 * `spec/02`: «اعرض منفصلين: **لك عنده** و**له عندك**… سجّل نوع الالتزام
 *  داخليًا منفصلًا للديون والأمانات… **لا تقاص تلقائي** بين لك وعليك.»
 */

export class PeopleError extends Error {}

export interface PersonRow {
  person: Person
  balance: PersonBalance
  obligations: { obligation: Obligation; remainingMinor: Halalas }[]
}

export interface ManagePeopleDeps {
  people: PersonRepository
  obligations: ObligationRepository
  settlements: SettlementRepository
  allocations: AllocationRepository
  txns: TransactionRepository
  uow: UnitOfWork
  ids: IdGenerator
  clock: Clock
}

export function makeManagePeople(deps: ManagePeopleDeps) {
  /** كل الأشخاص بأرصدتهم. القراءة محدودة بعدد الأشخاص لا بالعمليات. */
  async function listWithBalances(): Promise<PersonRow[]> {
    const people = await deps.people.listAll()
    const rows: PersonRow[] = []

    for (const person of people) {
      const obligations = await deps.obligations.listByPerson(person.id)
      const settlements = await deps.settlements.listByObligations(obligations.map((o) => o.id))
      rows.push({
        person,
        balance: computePersonBalance(person.id, obligations, settlements),
        obligations: obligations
          .map((obligation) => ({
            obligation,
            remainingMinor: remainingOfObligation(obligation, settlements),
          }))
          // المسدَّد بالكامل لا يُعرض في القائمة النشطة
          .filter((o) => o.remainingMinor > 0),
      })
    }

    // الأكثر رصيدًا أولًا، والمؤرشف أخيرًا
    return rows.sort((a, b) => {
      if (a.person.archived !== b.person.archived) return a.person.archived ? 1 : -1
      const total = (r: PersonRow) =>
        r.balance.receivableMinor + r.balance.payableLoanMinor + r.balance.payableCustodyMinor
      return total(b) - total(a)
    })
  }

  async function addPerson(name: string): Promise<Person> {
    const trimmed = name.trim()
    if (!trimmed) throw new PeopleError('اكتب اسم الشخص')
    // spec/04: حد اسم الشخص 80 حرفًا، معلن ومتحقَّق منه
    if (trimmed.length > 80) throw new PeopleError('الاسم أطول من 80 حرف')

    const existing = await deps.people.listAll()
    if (existing.some((p) => p.name.trim() === trimmed)) {
      throw new PeopleError(`فيه شخص اسمه «${trimmed}» موجود قبل كده`)
    }

    const person: Person = { id: deps.ids.next('person'), name: trimmed, archived: false }
    await deps.people.save(person)
    return person
  }

  /**
   * أرشفة شخص. **لا حذف** — `spec/03`: «لا حذف للحساب ذي سجل».
   * المؤرشف يختفي من الاختيار ويبقى سجله ورصيده ظاهرين.
   */
  async function archivePerson(personId: Id, archived: boolean): Promise<void> {
    const people = await deps.people.listAll()
    const person = people.find((p) => p.id === personId)
    if (!person) throw new PeopleError('الشخص ده مش موجود')
    await deps.people.save({ ...person, archived })
  }

  /**
   * ينشئ التزامًا من عملية: دفعت عن حد، أو سلّفت، أو استلمت قرضًا/أمانة.
   *
   * `spec/02`: «مجموع التخصيصات للشخص **لا يتجاوز قيمة الشراء**».
   */
  async function linkToPerson(input: {
    transactionId: Id
    personId: Id
    kind: ObligationKind
    amountMinor: Halalas
    /** «هدية» لا تُنشئ التزامًا وتُبقي المصروف كاملًا (spec/06). */
    asGift?: boolean
  }): Promise<{ obligation: Obligation | null; allocation: PersonAllocation }> {
    if (input.amountMinor <= 0) throw new PeopleError('المبلغ لازم يكون أكبر من صفر')

    const [transaction] = await deps.txns.findByIds([input.transactionId])
    if (!transaction) throw new PeopleError('العملية دي مش موجودة')

    const existing = await deps.allocations.listByTransactionIds([input.transactionId])
    const already = existing.reduce((sum, a) => sum + a.amountMinor, 0)
    if (already + input.amountMinor > transaction.amountMinor) {
      throw new PeopleError(
        `مجموع التخصيصات (${formatMoney(already + input.amountMinor)}) أكبر من قيمة ` +
          `العملية (${formatMoney(transaction.amountMinor)})`,
      )
    }

    return deps.uow.run(async () => {
      const allocation: PersonAllocation = {
        id: deps.ids.next('alloc'),
        transactionId: input.transactionId,
        personId: input.personId,
        allocationKind: input.asGift ? 'gift' : 'receivable',
        amountMinor: input.amountMinor,
        currency: transaction.currency,
      }
      await deps.allocations.saveMany([allocation])

      // الهدية تخصيص بلا التزام — spec/06: «لا التزام عليه»
      if (input.asGift) return { obligation: null, allocation }

      const obligation: Obligation = {
        id: deps.ids.next('obl'),
        personId: input.personId,
        originTransactionId: input.transactionId,
        kind: input.kind,
        originalMinor: input.amountMinor,
        currency: transaction.currency,
      }
      await deps.obligations.saveMany([obligation])
      return { obligation, allocation }
    })
  }

  /**
   * يسوّي التزامًا. يمر على `checkSettlement` أولًا،
   * فالزيادة **تُرفض بتفسير** ولا تُبتلع ولا تُسحب من التزام آخر (spec/06).
   */
  async function settle(input: {
    obligationId: Id
    personId: Id
    amountMinor: Halalas
    /** العملية التي تمثل السداد، إن وُجدت. */
    transactionId?: Id
  }): Promise<Settlement> {
    const obligations = await deps.obligations.listByPerson(input.personId)
    const obligation = obligations.find((o) => o.id === input.obligationId)
    if (!obligation) throw new PeopleError('الالتزام ده مش موجود')

    const settlements = await deps.settlements.listByObligations([obligation.id])
    const check = checkSettlement(obligation, settlements, input.amountMinor)
    if (!check.allowed) throw new PeopleError(check.reason ?? 'التسوية مرفوضة')

    const settlement: Settlement = {
      id: deps.ids.next('stl'),
      transactionId: input.transactionId ?? '',
      obligationId: obligation.id,
      amountMinor: check.settledMinor,
    }
    await deps.settlements.saveMany([settlement])
    return settlement
  }

  return { listWithBalances, addPerson, archivePerson, linkToPerson, settle }
}
