import type { Category, Transaction } from './entities/types'
import type { RecurringCandidate, RecurringItem } from './entities/recurring'
import { normalizeText } from './normalize'
import { assertHalalas, sumMoney } from './money'
import { daysBetween, daysInMonth, formatIsoDate, parseIsoDate } from './period'

export function shiftMonths(date: string, delta: number): string {
  const p = parseIsoDate(date)
  const total = p.year * 12 + p.month - 1 + delta
  const year = Math.floor(total / 12), month = ((total % 12) + 12) % 12 + 1
  return formatIsoDate({ year, month, day: Math.min(p.day, daysInMonth(year, month)) })
}
export function recurringKey(t: Transaction): string {
  return t.merchantId ? 'id:' + t.merchantId : 'name:' + normalizeText(t.rawMerchantName ?? '')
}
const services = /اشتراك|اتصالات|فواتير|مرافق/
const knownService = /^(netflix|spotify|google one|youtube premium|icloud|stc|mobily|zain)(\b|$)/i

/** Three consecutive cycles, at most one charge per month, suitable service,
 * ±5% amounts and ±7 days. Candidates are never financial entries. */
export function detectRecurring(rows: readonly Transaction[], categories: readonly Category[]): RecurringCandidate[] {
  const names = new Map(categories.map(c => [c.id, c.name]))
  const groups = new Map<string, Transaction[]>()
  for (const t of new Map(rows.map(t => [t.id, t])).values()) {
    if (t.observedDirection !== 'out' || !['purchase', 'unclassified'].includes(t.economicKind)) continue
    const name = t.rawMerchantName?.trim()
    if (!name || !(services.test(names.get(t.categoryId ?? '') ?? t.sourceCategory ?? '') || knownService.test(name))) continue
    const key = recurringKey(t) + '|' + t.currency
    groups.set(key, [...(groups.get(key) ?? []), t])
  }
  const out: RecurringCandidate[] = []
  for (const group of groups.values()) {
    group.sort((a,b) => a.occurredAt.localeCompare(b.occurredAt))
    if (group.length < 3) continue
    const months = group.map(t => t.occurredAt.slice(0,7))
    if (new Set(months).size !== group.length) continue
    const recent = group.slice(-3)
    const p = recent.map(t => parseIsoDate(t.occurredAt))
    const gaps = [1,2].map(i => (p[i].year-p[i-1].year)*12+p[i].month-p[i-1].month)
    const cycle = gaps[0]
    if (![1,3,12].includes(cycle) || gaps[1] !== cycle) continue
    if (recent.slice(1).some((t,i) => Math.abs(daysBetween(shiftMonths(recent[i].occurredAt,cycle),t.occurredAt)) > 7)) continue
    const amounts = recent.map(t => t.amountMinor).sort((a,b) => a-b)
    const median = amounts[1]
    if (median <= 0 || amounts.some(a => !Number.isSafeInteger(a) ||
      BigInt(Math.abs(a-median))*100n > BigInt(median)*5n)) continue
    const last = recent[2]
    out.push({ name: last.rawMerchantName!, merchantKey: recurringKey(last), currency:last.currency,
      expectedMinor:median, nextDueAt:shiftMonths(last.occurredAt,cycle),
      cycleMonths:cycle as 1|3|12, transactionIds:group.map(t=>t.id),
      reason:'٣ دورات متتابعة لخدمة مناسبة، بمبلغ متقارب ±٥٪ وموعد متقارب ±٧ أيام.' })
  }
  return out
}
export function recurringSummary(item: RecurringItem, rows: readonly Transaction[], today: string) {
  const from = shiftMonths(today,-12)
  const matched = rows.filter(t => t.occurredAt > from && t.occurredAt <= today &&
    t.currency === item.currency && t.observedDirection === 'out' &&
    recurringKey(t) === item.merchantKey && ['purchase','unclassified'].includes(t.economicKind))
  const unique = [...new Map(matched.map(t => [t.id,t])).values()]
  const annual = item.expectedMinor * (12 / item.cycleMonths)
  assertHalalas(annual)
  return { paidMinor:item.merchantKey.startsWith('manual:') ? null : sumMoney(unique.map(t=>t.amountMinor)), paidCount:unique.length,
    annualMinor:annual, overdue:item.active && item.nextDueAt < today }
}
export function validateRecurring(item: RecurringItem) {
  if (!item.name.trim() || item.name.length > 120) throw new Error('اكتب اسم الخدمة، بحد أقصى ١٢٠ حرف.')
  if (![1,3,12].includes(item.cycleMonths)) throw new Error('اختار دورة شهرية أو كل ٣ شهور أو سنوية.')
  if (!['subscription','bill'].includes(item.kind)) throw new Error('نوع الالتزام غير صالح.')
  if (!['SAR','EGP','USD','EUR','GBP','AED'].includes(item.currency)) throw new Error('العملة غير مدعومة.')
  assertHalalas(item.expectedMinor)
  if (item.expectedMinor <= 0) throw new Error('قيمة الدورة لازم تكون أكبر من صفر.')
  parseIsoDate(item.nextDueAt)
  assertHalalas(item.expectedMinor*(12/item.cycleMonths))
  if(!item.merchantKey||item.merchantKey.length>240) throw new Error('ربط الخدمة غير صالح.')
}
