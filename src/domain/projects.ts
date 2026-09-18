import { matchesText } from './categorize'
import { withEstimatedKinds } from './estimatedKinds'
import { computePeriodTotals } from './ledger'
import { addMoney, type Halalas } from './money'
import { normalizeText } from './normalize'
import type { Id, PersonAllocation, RuleMatchMode, Transaction } from './entities/types'
import type { Project, ProjectLink, ProjectRule, ProjectRuleDirection } from './entities/projectEntities'

/**
 * المشاريع — OVERRIDES §34. كل الحسابات هنا نقية؛ التخزين في `manageProjects`.
 * - «صرفت» = **نصيبك** (نفس مصروف الرئيسية) + المستبعد من الميزانية؛ «جالك» = الدخل. النوع اللي لسه
 *   ما اتأكدش بيتحسب تقريبي زي الرئيسية (`withEstimatedKinds`)، وكل عملية مرة واحدة.
 * - العملية ممكن تبقى في أكتر من مشروع.
 * - القاعدة بتضيف لوحدها العمليات اللي **اتسجلت بعدها** بس؛ القديمة بسؤال وقت عملها.
 */

export const PROJECT_NAME_MAX = 60
export const PROJECT_RULE_TEXT_MAX = 60

export class ProjectError extends Error {}

export function checkProjectName(name: string, projects: readonly Project[], selfId?: Id): { name: string; normalizedName: string } {
  const clean = name.trim().replace(/\s+/g, ' ')
  if (!clean || clean.length > PROJECT_NAME_MAX) throw new ProjectError(`اكتب اسم المشروع بحد أقصى ${PROJECT_NAME_MAX} حرف.`)
  const normalizedName = normalizeText(clean)
  if (projects.some((p) => p.id !== selfId && p.normalizedName === normalizedName)) throw new ProjectError('فيه مشروع بنفس الاسم.')
  return { name: clean, normalizedName }
}

const MODES: readonly RuleMatchMode[] = ['contains', 'startsWith', 'exact']
const DIRECTIONS: readonly ProjectRuleDirection[] = ['out', 'in', 'any']

export function checkProjectRule(input: { matchText: string; matchMode: RuleMatchMode; direction: ProjectRuleDirection }) {
  const matchText = input.matchText.trim().replace(/\s+/g, ' ')
  if (matchText.length < 2 || matchText.length > PROJECT_RULE_TEXT_MAX) {
    throw new ProjectError(`اكتب نص القاعدة من حرفين لـ ${PROJECT_RULE_TEXT_MAX} حرف.`)
  }
  if (!MODES.includes(input.matchMode)) throw new ProjectError('طريقة المطابقة مش معروفة.')
  if (!DIRECTIONS.includes(input.direction)) throw new ProjectError('الاتجاه مش معروف.')
  return { matchText, matchMode: input.matchMode, direction: input.direction }
}

/** معرّف ثابت من المشروع والعملية ⇒ ربط واحد بس لنفس العملية في نفس المشروع. */
export const projectLinkId = (projectId: Id, transactionId: Id) => `plink-${projectId}-${transactionId}`

/** العمليات اللي في المشروع فعلًا (المستبعدة لأ). */
export function memberIds(links: readonly ProjectLink[], projectId: Id): Set<Id> {
  return new Set(links.filter((l) => l.projectId === projectId && l.source !== 'excluded').map((l) => l.transactionId))
}

export function ruleMatchesTransaction(rule: Pick<ProjectRule, 'matchText' | 'matchMode' | 'direction'>, t: Transaction): boolean {
  if (rule.direction !== 'any' && t.observedDirection !== rule.direction) return false
  return [t.rawMerchantName, t.rawDescription].some((text) => !!text && matchesText(rule.matchText, rule.matchMode, text))
}

/** العمليات المطابقة للقاعدة ومش مربوطة بمشروعها بأي شكل (المستبعدة بإيد المستخدم ما بتتلمسش). */
export function ruleCandidates(rule: ProjectRule, transactions: readonly Transaction[], links: readonly ProjectLink[]): Transaction[] {
  const known = new Set(links.filter((l) => l.projectId === rule.projectId).map((l) => l.transactionId))
  return transactions.filter((t) => !known.has(t.id) && ruleMatchesTransaction(rule, t))
}

function ruleLink(rule: ProjectRule, t: Transaction, now: string): ProjectLink {
  return { id: projectLinkId(rule.projectId, t.id), projectId: rule.projectId, transactionId: t.id, source: 'rule', createdAt: now }
}

export function planRuleLinks(rule: ProjectRule, transactions: readonly Transaction[], links: readonly ProjectLink[], now: string): ProjectLink[] {
  return ruleCandidates(rule, transactions, links).map((t) => ruleLink(rule, t, now))
}

/**
 * العمليات الجديدة: كل قاعدة شغالة بتاخد العمليات اللي **اتسجلت بعدها** بس (القديمة اتسألت عنها وقت عملها).
 * قاعدتين في نفس المشروع على نفس العملية ⇒ ربط واحد.
 */
export function planSyncLinks(rules: readonly ProjectRule[], transactions: readonly Transaction[], links: readonly ProjectLink[], now: string): ProjectLink[] {
  const planned: ProjectLink[] = []
  for (const rule of rules) {
    if (!rule.enabled) continue
    const fresh = transactions.filter((t) => t.createdAt > rule.createdAt)
    planned.push(...planRuleLinks(rule, fresh, [...links, ...planned], now))
  }
  return planned
}

/** من إمتى نراجع العمليات الجديدة على الجهاز ده: آخر مراجعة، ومش قبل أقدم قاعدة شغالة. `null` = مفيش قواعد. */
export function syncStart(rules: readonly ProjectRule[], cursor: string | null): string | null {
  const oldest = rules.filter((r) => r.enabled).map((r) => r.createdAt).sort()[0]
  if (oldest === undefined) return null
  return cursor && cursor > oldest ? cursor : oldest
}

/**
 * إضافة أو شيل عملية من مشروع بإيد المستخدم — الروابط اللي هتتحفظ (فاضية = مفيش تغيير).
 * الشيل بيسيبها «مستبعدة» عشان قاعدة ما ترجعهاش. `existing` = روابط العملية دي في المشروع ده؛ ممكن تبقى
 * أكتر من واحد بمعرّف غير المعتاد (استعادة نسخة دمجت العملية في عملية موجودة بمعرّف تاني).
 */
export function membershipChanges(existing: readonly ProjectLink[], projectId: Id, transactionId: Id, member: boolean, now: string): ProjectLink[] {
  if (!member) return existing.filter((l) => l.source !== 'excluded').map((l) => ({ ...l, source: 'excluded' }))
  if (existing.some((l) => l.source !== 'excluded')) return []
  const first = existing[0]
  return [{ id: first?.id ?? projectLinkId(projectId, transactionId), projectId, transactionId, source: 'manual', createdAt: first?.createdAt ?? now }]
}

export interface ProjectSummary {
  spentMinor: Halalas
  receivedMinor: Halalas
  count: number
  /** كام عملية نوعها لسه مش مؤكد فالرقم «تقريبي» (زي الرئيسية). */
  estimatedCount: number
  /** منهم كام محتاجة تأكيد. */
  needsReviewCount: number
}

export function summarizeProject(
  transactions: readonly Transaction[],
  allocations: readonly PersonAllocation[],
  categoryNameById: ReadonlyMap<string, string>,
): ProjectSummary {
  const view = withEstimatedKinds(transactions, categoryNameById)
  const totals = computePeriodTotals(view.transactions, allocations)
  return {
    spentMinor: addMoney(totals.personalExpenseMinor, totals.excludedExpenseMinor),
    receivedMinor: totals.incomeMinor,
    count: transactions.length,
    estimatedCount: view.estimatedCount,
    needsReviewCount: view.needsReviewCount,
  }
}
