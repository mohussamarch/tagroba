import type { AllocationRepository, CategoryRepository, Clock, IdGenerator, TransactionRepository } from '../ports/repositories'
import type { ProjectLinkRepository, ProjectRepository, ProjectRuleRepository } from '../ports/ProjectPorts'
import type { SyncCursorPort } from '../ports/SharedMerchantCatalogPort'
import type { Id, PersonAllocation, RuleMatchMode, Transaction } from '../../domain/entities/types'
import type { Project, ProjectLink, ProjectRule, ProjectRuleDirection } from '../../domain/entities/projectEntities'
import {
  ProjectError,
  checkProjectName,
  checkProjectRule,
  memberIds,
  membershipChanges,
  planRuleLinks,
  planSyncLinks,
  ruleCandidates,
  summarizeProject,
  syncStart,
  type ProjectSummary,
} from '../../domain/projects'

/**
 * المشاريع — OVERRIDES §34. الحسابات في `domain/projects.ts`؛ هنا قراية وكتابة بس.
 * القاعدة بتضيف لوحدها اللي اتسجل بعدها (بتتراجع وقت فتح المشاريع)، والقديم بسؤال المستخدم وقت عملها.
 */
export interface ProjectsDeps {
  projects: ProjectRepository
  links: ProjectLinkRepository
  rules: ProjectRuleRepository
  txns: TransactionRepository
  allocations: AllocationRepository
  categories: CategoryRepository
  ids: IdGenerator
  clock: Clock
  /** آخر مراجعة للعمليات الجديدة على الجهاز ده (ضياعها = مراجعة تانية من أقدم قاعدة، من غير تكرار). */
  cursor: SyncCursorPort
}

export interface ProjectRow { project: Project; summary: ProjectSummary }

export interface ProjectDetail extends ProjectRow {
  rules: ProjectRule[]
  /** الأحدث الأول. `rule` = قاعدة ضافتها، `manual` = إنت. */
  transactions: { transaction: Transaction; source: 'manual' | 'rule' }[]
}

export interface ProjectRuleInput { matchText: string; matchMode: RuleMatchMode; direction: ProjectRuleDirection }

/** العمليات «القديمة» لقاعدة جديدة: آخر 3 سنين بس — قراية واحدة بطلب المستخدم (ARCHITECTURE §5.6: كل استعلام محدود). */
const OLD_YEARS = 3

export function makeManageProjects(deps: ProjectsDeps) {
  async function syncRules(): Promise<number> {
    const rules = await deps.rules.listAll()
    const since = syncStart(rules, deps.cursor.read())
    if (!since) return 0
    const fresh = await deps.txns.listCreatedAfter(since)
    if (fresh.length === 0) return 0
    const planned = planSyncLinks(rules, fresh, await deps.links.listAll(), deps.clock.nowIso())
    if (planned.length) await deps.links.saveMany(planned)
    deps.cursor.write(fresh.reduce((max, t) => (t.createdAt > max ? t.createdAt : max), since))
    return planned.length
  }

  async function load(projectId?: Id) {
    const [projects, links, categories] = await Promise.all([deps.projects.listAll(), deps.links.listAll(), deps.categories.listAll()])
    const ids = [...new Set(links.filter((l) => l.source !== 'excluded' && (!projectId || l.projectId === projectId)).map((l) => l.transactionId))]
    const [transactions, allocations] = await Promise.all([
      deps.txns.findByIds(ids),
      ids.length ? deps.allocations.listByTransactionIds(ids) : Promise.resolve([] as PersonAllocation[]),
    ])
    return { projects, links, transactions, allocations, names: new Map(categories.map((c) => [c.id, c.name])) }
  }

  function summaryOf(projectId: Id, data: Awaited<ReturnType<typeof load>>): ProjectSummary {
    const members = memberIds(data.links, projectId)
    return summarizeProject(
      data.transactions.filter((t) => members.has(t.id)),
      data.allocations.filter((a) => members.has(a.transactionId)),
      data.names,
    )
  }

  async function findProject(id: Id, projects?: readonly Project[]): Promise<Project> {
    const found = (projects ?? (await deps.projects.listAll())).find((p) => p.id === id)
    if (!found) throw new ProjectError('المشروع مش موجود.')
    return found
  }

  async function findRule(id: Id): Promise<ProjectRule> {
    const found = (await deps.rules.listAll()).find((r) => r.id === id)
    if (!found) throw new ProjectError('القاعدة مش موجودة.')
    return found
  }

  async function oldCandidates(rule: ProjectRule, links: readonly ProjectLink[]): Promise<Transaction[]> {
    const today = deps.clock.nowIso().slice(0, 10)
    const from = `${Number(today.slice(0, 4)) - OLD_YEARS}${today.slice(4)}`
    const old = (await deps.txns.listByDateRange(from, today)).filter((t) => t.createdAt <= rule.createdAt)
    return ruleCandidates(rule, old, links)
  }

  return {
    syncRules,

    /** كل المشاريع بمجاميعها — الشغالة بالأحدث، والمؤرشفة لوحدها. */
    async list(): Promise<{ active: ProjectRow[]; archived: ProjectRow[] }> {
      await syncRules()
      const data = await load()
      const rows = [...data.projects]
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map((project) => ({ project, summary: summaryOf(project.id, data) }))
      return { active: rows.filter((r) => !r.project.archived), archived: rows.filter((r) => r.project.archived) }
    },

    async create(name: string): Promise<Project> {
      const checked = checkProjectName(name, await deps.projects.listAll())
      const project: Project = { id: deps.ids.next('project'), ...checked, archived: false, createdAt: deps.clock.nowIso() }
      await deps.projects.save(project)
      return project
    },

    async rename(id: Id, name: string): Promise<void> {
      const all = await deps.projects.listAll()
      const project = await findProject(id, all)
      await deps.projects.save({ ...project, ...checkProjectName(name, all, id) })
    },

    /** مفيش مسح للمشروع — أرشفة، وعملياته ومجاميعه بتفضل. */
    async setArchived(id: Id, archived: boolean): Promise<void> {
      await deps.projects.save({ ...(await findProject(id)), archived })
    },

    async detail(projectId: Id): Promise<ProjectDetail> {
      await syncRules()
      const data = await load(projectId)
      const project = await findProject(projectId, data.projects)
      const sources = new Map(
        data.links
          .filter((l) => l.projectId === projectId && l.source !== 'excluded')
          .map((l) => [l.transactionId, l.source as 'manual' | 'rule']),
      )
      const transactions = data.transactions
        .filter((t) => sources.has(t.id))
        .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt) || b.sourceOrder - a.sourceOrder)
        .map((transaction) => ({ transaction, source: sources.get(transaction.id)! }))
      const rules = (await deps.rules.listAll())
        .filter((r) => r.projectId === projectId)
        .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      return { project, summary: summaryOf(projectId, data), rules, transactions }
    },

    /** مشاريع عملية (لـ«ضيف لمشروع»). المؤرشف بيظهر بس لو العملية فيه. */
    async membership(transactionId: Id): Promise<{ project: Project; member: boolean }[]> {
      const [projects, links] = await Promise.all([deps.projects.listAll(), deps.links.listByTransaction(transactionId)])
      const member = new Set(links.filter((l) => l.source !== 'excluded').map((l) => l.projectId))
      return projects
        .filter((p) => !p.archived || member.has(p.id))
        .sort((a, b) => a.name.localeCompare(b.name, 'ar'))
        .map((project) => ({ project, member: member.has(project.id) }))
    },

    /** إضافة أو شيل بإيد المستخدم؛ الشيل بيسيبها «مستبعدة» عشان قاعدة ما ترجعهاش. */
    async setMember(transactionId: Id, projectId: Id, member: boolean): Promise<void> {
      await findProject(projectId)
      const existing = (await deps.links.listByTransaction(transactionId)).filter((l) => l.projectId === projectId)
      const changes = membershipChanges(existing, projectId, transactionId, member, deps.clock.nowIso())
      if (changes.length) await deps.links.saveMany(changes)
    },

    /** بتحفظ القاعدة وبترجّع كام عملية قديمة بتنطبق عليها — المستخدم بيختار يضيفها ولا لأ (§34). */
    async addRule(projectId: Id, input: ProjectRuleInput): Promise<{ rule: ProjectRule; oldMatches: number }> {
      await findProject(projectId)
      const rule: ProjectRule = { id: deps.ids.next('prule'), projectId, ...checkProjectRule(input), enabled: true, createdAt: deps.clock.nowIso() }
      await deps.rules.save(rule)
      return { rule, oldMatches: (await oldCandidates(rule, await deps.links.listAll())).length }
    },

    /** «ضيف القديم كمان» — بعد ما المستخدم شاف العدد. بترجّع كام اتضاف. */
    async applyRuleToOld(ruleId: Id): Promise<number> {
      const rule = await findRule(ruleId)
      const links = await deps.links.listAll()
      const planned = planRuleLinks(rule, await oldCandidates(rule, links), links, deps.clock.nowIso())
      if (planned.length) await deps.links.saveMany(planned)
      return planned.length
    },

    /** القاعدة ما بتتمسحش — بتتقفل (زي قواعد التصنيف). اللي ضافته بيفضل. */
    async setRuleEnabled(ruleId: Id, enabled: boolean): Promise<void> {
      await deps.rules.save({ ...(await findRule(ruleId)), enabled })
    },
  }
}
