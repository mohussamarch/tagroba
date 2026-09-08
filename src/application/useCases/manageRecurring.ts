import { detectRecurring, recurringKey, recurringSummary, shiftMonths, validateRecurring } from '../../domain/recurring'
import type { RecurringItem } from '../../domain/entities/recurring'
import type { RecurringRepository } from '../ports/RecurringRepository'
import type { CategoryRepository, IdGenerator, TransactionRepository } from '../ports/repositories'
import type { Transaction } from '../../domain/entities/types'
import { parseIsoDate, dayNumberToIso, toDayNumber } from '../../domain/period'

export function makeManageRecurring(deps: { items: RecurringRepository; txns: TransactionRepository;
  categories: CategoryRepository; ids: IdGenerator }) {
  async function readYear(today: string) {
    parseIsoDate(today)
    let from = shiftMonths(today,-12)
    const rows: Transaction[] = []
    for (let i=0;i<12;i++) {
      const next = shiftMonths(shiftMonths(today,-12),i+1)
      rows.push(...await deps.txns.listByDateRange(from,
        i===11 ? today : dayNumberToIso(toDayNumber(parseIsoDate(next))-1)))
      from = next
    }
    return [...new Map(rows.map(t=>[t.id,t])).values()]
  }
  async function load(today: string) {
    const [items, rows, categories] = await Promise.all([deps.items.listAll(),readYear(today),deps.categories.listAll()])
    return { from:shiftMonths(today,-12), to:today,
      choices:[...new Map(rows.filter(t=>t.rawMerchantName).map(t=>[recurringKey(t),{key:recurringKey(t),name:t.rawMerchantName!}])).values()],
      candidates:detectRecurring(rows,categories).filter(c=>!items.some(i=>
        i.merchantKey===c.merchantKey && i.currency===c.currency)),
      items:items.map(item=>({item,...recurringSummary(item,rows,today)})) }
  }
  async function save(input: Omit<RecurringItem,'id'|'confirmed'> & {id?:string}) {
    const items = await deps.items.listAll()
    if (input.id && !items.some(i=>i.id===input.id)) throw new Error('الالتزام مش موجود. حدّث الصفحة.')
    const duplicate = items.find(i=>i.merchantKey===input.merchantKey && i.currency===input.currency && i.id!==input.id)
    if (duplicate) throw new Error('الخدمة دي مسجلة بالفعل بنفس العملة. عدّلها من قائمتك.')
    const item: RecurringItem = {...input,id:input.id ?? 'recurring:'+input.merchantKey+'|'+input.currency,confirmed:true}
    validateRecurring(item)
    await deps.items.save(item)
    return item
  }
  return {load,save}
}
export type RecurringView = Awaited<ReturnType<ReturnType<typeof makeManageRecurring>['load']>>
