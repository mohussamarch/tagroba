import { makeManageProjects } from '../../src/application/useCases/manageProjects'
import { MemoryTransactionRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryAllocationRepository, MemoryCategoryRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryProjectLinkRepository, MemoryProjectRepository, MemoryProjectRuleRepository } from '../../src/infrastructure/memory/memoryProjectRepositories'
import { MemorySyncCursor } from '../../src/infrastructure/memory/memorySharedMerchantCatalog'
import { SequentialIdGenerator, FixedClock } from '../../src/infrastructure/memory/memorySupport'
import type { Project, ProjectLink, ProjectRule } from '../../src/domain/entities/projectEntities'
import type { Category, PersonAllocation, Transaction } from '../../src/domain/entities/types'
import { recordAsync } from './goldenKit'

/**
 * المشاريع — OVERRIDES §34. ⚠️ بيانات وهمية بالكامل.
 * القواعد الحاكمة: القاعدة بتضيف اللي اتسجل بعدها لوحدها والقديم بسؤال، الشيل بيسيب «مستبعدة»
 * عشان القاعدة ما ترجعهاش، ومفيش مسح للمشروع — أرشفة. «صرفت» = نصيبك إنت بس.
 */

const cats: Category[] = [
  { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
]

function txn(id: string, over: Partial<Transaction> = {}): Transaction {
  return {
    id, occurredAt: '2026-09-05', datePrecision: 'day', sourceOrder: 1,
    economicKind: 'purchase', economicKindConfirmed: true,
    observedDirection: 'out', amountMinor: 10000, currency: 'SAR',
    categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested',
    isCashTagged: false, createdAt: '2026-09-01T00:00:00.000Z', updatedAt: '2026-09-01T00:00:00.000Z',
    ...over,
  }
}

interface Seed {
  projects: Project[]
  links: ProjectLink[]
  rules: ProjectRule[]
  transactions: Transaction[]
  allocations: PersonAllocation[]
  cursor: string | null
}

type Action =
  | { kind: 'list' | 'syncOnly' }
  | { kind: 'create' | 'rename'; id?: string; name: string }
  | { kind: 'archive'; id: string; archived: boolean }
  | { kind: 'detail' | 'membership'; id: string }
  | { kind: 'setMember'; transactionId: string; projectId: string; member: boolean }
  | { kind: 'addRule'; projectId: string; matchText: string; matchMode: 'contains' | 'startsWith' | 'exact'; direction: 'out' | 'in' | 'any' }
  | { kind: 'applyRuleToOld' | 'setRuleEnabled'; ruleId: string; enabled?: boolean }

export async function projectsFlowGolden() {
  const cases = []

  async function run(seed: Seed, action: Action) {
    cases.push(await recordAsync({ seed, categories: cats, action }, async () => {
      const projects = new MemoryProjectRepository(seed.projects)
      const links = new MemoryProjectLinkRepository(seed.links)
      const rules = new MemoryProjectRuleRepository(seed.rules)
      const txnsRepo = new MemoryTransactionRepository()
      await txnsRepo.saveMany(seed.transactions)
      const allocations = new MemoryAllocationRepository()
      await allocations.saveMany(seed.allocations)
      const cursor = new MemorySyncCursor(seed.cursor)
      const manage = makeManageProjects({
        projects, links, rules, txns: txnsRepo, allocations,
        categories: new MemoryCategoryRepository(cats),
        ids: new SequentialIdGenerator(),
        clock: new FixedClock('2026-09-22T10:00:00.000Z'),
        cursor,
      })
      const stored = async () => ({
        storedProjects: await projects.listAll(),
        storedLinks: await links.listAll(),
        storedRules: await rules.listAll(),
        cursorAfter: cursor.read(),
      })
      switch (action.kind) {
        case 'syncOnly': return { linked: await manage.syncRules(), ...(await stored()) }
        case 'list': {
          const result = await manage.list()
          const row = (r: { project: Project; summary: unknown }) => ({ projectId: r.project.id, summary: r.summary })
          return { active: result.active.map(row), archived: result.archived.map(row), ...(await stored()) }
        }
        case 'create': return { created: await manage.create(action.name), ...(await stored()) }
        case 'rename': await manage.rename(action.id!, action.name); return await stored()
        case 'archive': await manage.setArchived(action.id, action.archived); return await stored()
        case 'detail': {
          const d = await manage.detail(action.id)
          return {
            projectId: d.project.id, summary: d.summary, rules: d.rules,
            transactions: d.transactions.map((t) => ({ transactionId: t.transaction.id, source: t.source })),
            ...(await stored()),
          }
        }
        case 'membership': return { rows: (await manage.membership(action.id)).map((m) => ({ projectId: m.project.id, member: m.member })) }
        case 'setMember': await manage.setMember(action.transactionId, action.projectId, action.member); return await stored()
        case 'addRule': {
          const result = await manage.addRule(action.projectId, { matchText: action.matchText, matchMode: action.matchMode, direction: action.direction })
          return { rule: result.rule, oldMatches: result.oldMatches, ...(await stored()) }
        }
        case 'applyRuleToOld': return { added: await manage.applyRuleToOld(action.ruleId), ...(await stored()) }
        case 'setRuleEnabled': await manage.setRuleEnabled(action.ruleId, action.enabled!); return await stored()
      }
    }))
  }

  /*
   * فرح أختي (شغال): عمليتين يدوي + قاعدة «قاعة» ليها عملية جديدة اتسجلت بعد الكشف الأخير
   *   (المزامنة بتضيفها لوحدها) وعملية مستبعدة بإيد المستخدم القاعدة ما ترجّعهاش.
   * سفر قديم (مؤرشف): عملية واحدة.
   */
  const baseSeed: Seed = {
    projects: [
      { id: 'pr-wedding', name: 'فرح أختي', normalizedName: 'فرح اختي', archived: false, createdAt: '2026-08-01T00:00:00.000Z' },
      { id: 'pr-trip', name: 'سفر العيد', normalizedName: 'سفر العيد', archived: true, createdAt: '2026-06-01T00:00:00.000Z' },
    ],
    links: [
      { id: 'pl-1', projectId: 'pr-wedding', transactionId: 't-hall', source: 'manual', createdAt: '2026-08-02T00:00:00.000Z' },
      { id: 'pl-2', projectId: 'pr-wedding', transactionId: 't-gift', source: 'manual', createdAt: '2026-08-03T00:00:00.000Z' },
      { id: 'plink-pr-wedding-t-out', projectId: 'pr-wedding', transactionId: 't-out', source: 'excluded', createdAt: '2026-08-04T00:00:00.000Z' },
      { id: 'pl-4', projectId: 'pr-trip', transactionId: 't-trip', source: 'manual', createdAt: '2026-06-02T00:00:00.000Z' },
    ],
    rules: [
      { id: 'rule-hall', projectId: 'pr-wedding', matchText: 'قاعة', matchMode: 'contains', direction: 'out', enabled: true, createdAt: '2026-08-05T00:00:00.000Z' },
    ],
    transactions: [
      txn('t-hall', { rawMerchantName: 'قاعة الأفراح', amountMinor: 500000 }),
      txn('t-gift', { observedDirection: 'in', economicKind: 'personal_sale', amountMinor: 200000 }),
      txn('t-out', { rawMerchantName: 'قاعة تانية', amountMinor: 30000 }),
      txn('t-trip', { amountMinor: 80000, occurredAt: '2026-06-15' }),
      // اتسجلت بعد آخر مزامنة والقاعدة بتطابقها ⇒ المزامنة بتضيفها
      txn('t-new', { rawMerchantName: 'قاعة الياسمين', amountMinor: 45000, createdAt: '2026-09-10T00:00:00.000Z' }),
      // قديمة (قبل القاعدة) وبتطابق نصها ⇒ «القديم» بسؤال مش تلقائي
      txn('t-old', { rawMerchantName: 'قاعة قديمة', amountMinor: 20000, occurredAt: '2026-07-01', createdAt: '2026-07-01T00:00:00.000Z' }),
    ],
    allocations: [
      { id: 'al-1', transactionId: 't-hall', personId: 'p-1', allocationKind: 'receivable', amountMinor: 100000, currency: 'SAR' },
    ],
    cursor: '2026-09-05T00:00:00.000Z',
  }

  await run(baseSeed, { kind: 'syncOnly' })
  await run(baseSeed, { kind: 'list' })
  await run(baseSeed, { kind: 'detail', id: 'pr-wedding' })
  await run(baseSeed, { kind: 'detail', id: 'pr-ghost' })
  await run(baseSeed, { kind: 'create', name: ' تجديد البيت ' })
  await run(baseSeed, { kind: 'create', name: 'فرح أختي' })
  await run(baseSeed, { kind: 'create', name: '  ' })
  await run(baseSeed, { kind: 'create', name: 'م'.repeat(61) })
  await run(baseSeed, { kind: 'rename', id: 'pr-trip', name: 'سفر الصيف' })
  await run(baseSeed, { kind: 'rename', id: 'pr-trip', name: 'فرح أختي' })
  await run(baseSeed, { kind: 'archive', id: 'pr-wedding', archived: true })
  await run(baseSeed, { kind: 'membership', id: 't-hall' })
  await run(baseSeed, { kind: 'membership', id: 't-new' })
  // شيل بإيد المستخدم ⇒ الرابط بيبقى «مستبعد» مش بيتمسح
  await run(baseSeed, { kind: 'setMember', transactionId: 't-hall', projectId: 'pr-wedding', member: false })
  // رجوع المستبعدة عضو تاني
  await run(baseSeed, { kind: 'setMember', transactionId: 't-out', projectId: 'pr-wedding', member: true })
  await run(baseSeed, { kind: 'addRule', projectId: 'pr-wedding', matchText: ' كوشة ', matchMode: 'contains', direction: 'out' })
  await run(baseSeed, { kind: 'addRule', projectId: 'pr-wedding', matchText: 'قديمة', matchMode: 'contains', direction: 'any' })
  await run(baseSeed, { kind: 'addRule', projectId: 'pr-wedding', matchText: '  ', matchMode: 'contains', direction: 'out' })
  await run(baseSeed, { kind: 'addRule', projectId: 'pr-ghost', matchText: 'حاجة', matchMode: 'contains', direction: 'out' })
  await run(baseSeed, { kind: 'applyRuleToOld', ruleId: 'rule-hall' })
  await run(baseSeed, { kind: 'applyRuleToOld', ruleId: 'rule-ghost' })
  await run(baseSeed, { kind: 'setRuleEnabled', ruleId: 'rule-hall', enabled: false })

  return { manageProjects: cases }
}
